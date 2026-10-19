package com.bidsmart.platform.config;

import com.bidsmart.platform.audit.AuditService;
import com.bidsmart.platform.auth.CurrentAccountFilter;
import com.bidsmart.platform.auth.LoginRateLimiter;
import com.bidsmart.platform.auth.JsonCorsProcessor;
import com.bidsmart.platform.auth.PermissionInterceptor;
import com.bidsmart.platform.auth.RequestBoundaryFilter;
import com.bidsmart.platform.auth.SessionStore;
import com.bidsmart.platform.auth.UserRepository;
import com.bidsmart.platform.common.JsonResponses;
import com.bidsmart.platform.crypto.Sm3PasswordEncoder;
import com.bidsmart.platform.permission.PermissionService;
import java.time.Clock;
import java.util.Arrays;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextPersistenceFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableWebSecurity
public class SecurityConfiguration implements WebMvcConfigurer {
    private final PermissionInterceptor permissionInterceptor;
    public SecurityConfiguration(PermissionInterceptor permissionInterceptor) { this.permissionInterceptor = permissionInterceptor; }
    @Override public void addInterceptors(InterceptorRegistry registry) { registry.addInterceptor(permissionInterceptor); }

    @Bean public PasswordEncoder passwordEncoder() { return new Sm3PasswordEncoder(); }

    @Bean public CsrfTokenRepository csrfTokens() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-CSRF-TOKEN");
        return repository;
    }

    @Bean public SecurityContextRepository securityContexts() {
        HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository();
        repository.setDisableUrlRewriting(true);
        return repository;
    }

    @Bean public SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(Arrays.asList(
                new ChangeSessionIdAuthenticationStrategy(), new CsrfAuthenticationStrategy(csrf)));
    }

    @Bean public CorsConfigurationSource corsConfiguration(PlatformProperties properties) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.getAllowedOrigins());
        configuration.setAllowCredentials(true);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList("Content-Type", "X-CSRF-TOKEN"));
        configuration.setExposedHeaders(Arrays.asList("X-Trace-Id", "Retry-After"));
        configuration.setMaxAge(600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    @Bean public SecurityFilterChain apiSecurity(HttpSecurity http, CsrfTokenRepository csrf,
            SecurityContextRepository contexts, @Qualifier("corsConfiguration") CorsConfigurationSource cors, UserRepository users,
            PermissionService permissions, SessionStore sessions, PlatformProperties properties,
            Clock clock, JsonResponses json, AuditService audit, LoginRateLimiter limiter) throws Exception {
        http.csrf().csrfTokenRepository(csrf).and()
                .securityContext().securityContextRepository(contexts).and()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                .sessionFixation().changeSessionId().and()
                .exceptionHandling().authenticationEntryPoint((request, response, error) ->
                        json.error(request, response, 401, "UNAUTHENTICATED", "请先登录"))
                .accessDeniedHandler((request, response, error) -> {
                    try { audit.denied(request); }
                    catch (RuntimeException persistenceFailure) {
                        json.error(request, response, 500, "SECURITY_UNAVAILABLE", "安全服务暂不可用"); return;
                    }
                    json.error(request, response, 403, error instanceof CsrfException ? "CSRF_INVALID" : "FORBIDDEN",
                            error instanceof CsrfException ? "安全令牌无效，请刷新后重试" : "没有访问权限");
                }).and()
                .authorizeRequests()
                .antMatchers("/api/v1/security/csrf", "/api/v1/security/captcha", "/api/v1/auth/login", "/api/v1/app/public-config", "/api/v1/health").permitAll()
                .antMatchers("/api/v1/**").authenticated()
                .anyRequest().denyAll();
        http.cors().disable();
        http.formLogin().disable();
        http.httpBasic().disable();
        http.logout().disable();
        http.requestCache().disable();
        http.headers().contentTypeOptions().and().frameOptions().deny()
                .referrerPolicy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER).and()
                .contentSecurityPolicy("default-src 'none'; frame-ancestors 'none'; base-uri 'none'");
        http.addFilterAfter(new CurrentAccountFilter(users, permissions, sessions, properties, clock, json, audit),
                SecurityContextPersistenceFilter.class);
        http.addFilterBefore(new RequestBoundaryFilter(properties, limiter, json), CsrfFilter.class);
        CorsFilter corsFilter = new CorsFilter(cors);
        corsFilter.setCorsProcessor(new JsonCorsProcessor(json, audit));
        http.addFilterBefore(corsFilter, SecurityContextPersistenceFilter.class);
        return http.build();
    }
}
