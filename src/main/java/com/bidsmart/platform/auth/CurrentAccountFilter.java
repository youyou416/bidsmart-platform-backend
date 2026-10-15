package com.bidsmart.platform.auth;

import com.bidsmart.platform.audit.AuditService;
import com.bidsmart.platform.common.JsonResponses;
import com.bidsmart.platform.config.PlatformProperties;
import com.bidsmart.platform.permission.PermissionService;
import com.bidsmart.platform.permission.PermissionSnapshot;
import java.io.IOException;
import java.time.Clock;
import java.util.Collections;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public final class CurrentAccountFilter extends OncePerRequestFilter {
    public static final String ACCOUNT = "platform.currentAccount";
    public static final String PERMISSIONS = "platform.currentPermissions";
    private static final Logger LOG = LoggerFactory.getLogger(CurrentAccountFilter.class);
    private final UserRepository users;
    private final PermissionService permissions;
    private final SessionStore sessions;
    private final PlatformProperties properties;
    private final Clock clock;
    private final JsonResponses json;
    private final AuditService audit;

    public CurrentAccountFilter(UserRepository users, PermissionService permissions, SessionStore sessions,
                                PlatformProperties properties, Clock clock, JsonResponses json, AuditService audit) {
        this.users = users; this.permissions = permissions; this.sessions = sessions;
        this.properties = properties; this.clock = clock; this.json = json; this.audit = audit;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws IOException, ServletException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof SessionPrincipal)) {
            chain.doFilter(request, response); return;
        }
        SessionPrincipal principal = (SessionPrincipal) auth.getPrincipal();
        try {
            Optional<UserAccount> loaded = users.findById(principal.getUserId());
            if (!loaded.isPresent() || !loaded.get().isEnabled()
                    || loaded.get().getPasswordVersion() != principal.getPasswordVersion()
                    || !clock.instant().isBefore(principal.getAuthenticatedAt().plus(properties.getAbsoluteSessionTimeout()))) {
                try { audit.record("SESSION_REVOKED", principal.getUserId(), "REVOKED", request); }
                finally { sessions.invalidate(request, response); }
                json.error(request, response, 401, "UNAUTHENTICATED", "会话已失效，请重新登录");
                return;
            }
            UserAccount account = loaded.get();
            request.setAttribute(ACCOUNT, account);
            PermissionSnapshot snapshot = account.isPasswordChangeRequired() ? null : permissions.loadForUser(account.getId());
            request.setAttribute(PERMISSIONS, snapshot);
            Authentication refreshed = new UsernamePasswordAuthenticationToken(principal, null,
                    snapshot == null ? Collections.emptyList() : snapshot.getAuthorities().stream()
                            .map(SimpleGrantedAuthority::new).collect(Collectors.toList()));
            SecurityContextHolder.getContext().setAuthentication(refreshed);
            if (account.isPasswordChangeRequired() && !initialPasswordAllowed(request)) {
                audit.denied(request);
                json.error(request, response, 403, "PASSWORD_CHANGE_REQUIRED", "请先修改初始密码");
                return;
            }
        } catch (RuntimeException error) {
            LOG.error("event=account_validation_failure exceptionType={}", error.getClass().getSimpleName());
            json.error(request, response, 500, "SECURITY_UNAVAILABLE", "安全服务暂不可用");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean initialPasswordAllowed(HttpServletRequest request) {
        String path = request.getServletPath();
        if (path == null || path.isEmpty()) { path = request.getRequestURI(); }
        return "/api/v1/auth/password".equals(path) || "/api/v1/auth/logout".equals(path)
                || "/api/v1/auth/me".equals(path) || "/api/v1/security/csrf".equals(path)
                || "/api/v1/app/public-config".equals(path) || "/api/v1/health".equals(path);
    }
}
