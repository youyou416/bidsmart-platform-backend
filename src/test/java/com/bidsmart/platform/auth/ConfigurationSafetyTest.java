package com.bidsmart.platform.auth;

import com.bidsmart.platform.config.ConfigurationSafety;
import com.bidsmart.platform.config.PlatformProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

class ConfigurationSafetyTest {
    @Test void passwordMissingFailsWithoutConnecting() {
        assertThatThrownBy(() -> ConfigurationSafety.validate(new PlatformProperties(), "127.0.0.1"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("DB_PASSWORD");
    }
    @Test void devRequiresLoopback() {
        assertThatThrownBy(() -> ConfigurationSafety.validate(local(), "0.0.0.0"))
                .hasMessageContaining("loopback");
    }
    @Test void actualTestEnvironmentRequiresExplicitIndependentDatabaseAndVerifiedTransport() {
        PlatformProperties p = local(); p.setEnvironment("test");
        assertThatThrownBy(() -> ConfigurationSafety.validate(p, "127.0.0.1")).hasMessageContaining("VERIFY_IDENTITY");
        p.setDatabaseSslMode("VERIFY_IDENTITY");
        assertThatThrownBy(() -> ConfigurationSafety.validate(p, "127.0.0.1")).hasMessageContaining("independent test database");
        p.setDatabaseName("framework_test"); p.setDatabaseUsername("framework_app@synthetic");
        assertThatCode(() -> ConfigurationSafety.validate(p, "127.0.0.1")).doesNotThrowAnyException();
    }
    @Test void prodRejectsSystemTenantAndUnverifiedTransport() {
        PlatformProperties p = production(); p.setDatabaseUsername("root@sys");
        assertThatThrownBy(() -> ConfigurationSafety.validate(p, "127.0.0.1"))
                .hasMessageContaining("business tenant");
        PlatformProperties transport = production(); transport.setTransportVerified(false);
        assertThatThrownBy(() -> ConfigurationSafety.validate(transport, "127.0.0.1"))
                .hasMessageContaining("verified transport");
    }
    @Test void prodRejectsDebugAndBootstrap() {
        PlatformProperties p = production(); p.setDebugEnabled(true);
        assertThatThrownBy(() -> ConfigurationSafety.validate(p, "127.0.0.1"))
                .hasMessageContaining("disabled debug");
        p.setDebugEnabled(false); p.setBootstrapEnabled(true);
        assertThatThrownBy(() -> ConfigurationSafety.validate(p, "127.0.0.1"))
                .hasMessageContaining("forbidden in prod");
    }
    @Test void remoteDatabaseRequiresVerifiedTransportAndBusinessTenant() {
        PlatformProperties p = local(); p.setDatabaseHost("database.example");
        assertThatThrownBy(() -> ConfigurationSafety.validate(p, "127.0.0.1"))
                .hasMessageContaining("VERIFY_IDENTITY");
        p.setDatabaseSslMode("VERIFY_IDENTITY");
        assertThatThrownBy(() -> ConfigurationSafety.validate(p, "127.0.0.1"))
                .hasMessageContaining("System-tenant credentials");
        p.setDatabaseUsername("framework_app@business");
        assertThatCode(() -> ConfigurationSafety.validate(p, "127.0.0.1")).doesNotThrowAnyException();
    }
    @Test void unsafeJdbcParameterInjectionAndPermissiveOriginRejected() {
        PlatformProperties p = local(); p.setDatabaseHost("127.0.0.1?allowMultiQueries=true");
        assertThatThrownBy(() -> ConfigurationSafety.validate(p, "127.0.0.1")).hasMessageContaining("DB_HOST");
        p.setDatabaseHost("127.0.0.1"); p.setAllowedOrigins(java.util.List.of("*"));
        assertThatThrownBy(() -> ConfigurationSafety.validate(p, "127.0.0.1")).hasMessageContaining("origins");
    }
    @Test void manualProdProfileCannotBypassEnvironmentChecks() {
        PlatformProperties p = local(); MockEnvironment env = configured(p); env.setActiveProfiles("prod");
        assertThatThrownBy(() -> new ConfigurationSafety(p, env).afterSingletonsInstantiated())
                .hasMessageContaining("APP_ENV");
    }
    @Test void actualServletCookieFlagsCannotBeOverriddenInProd() {
        PlatformProperties p = production(); MockEnvironment env = configured(p);
        env.setProperty("server.servlet.session.cookie.secure", "false");
        assertThatThrownBy(() -> new ConfigurationSafety(p, env).afterSingletonsInstantiated())
                .hasMessageContaining("effective Servlet");
        env.setProperty("server.servlet.session.cookie.secure", "true");
        env.setProperty("server.servlet.session.cookie.http-only", "false");
        assertThatThrownBy(() -> new ConfigurationSafety(p, env).afterSingletonsInstantiated())
                .hasMessageContaining("HttpOnly");
    }
    @Test void actualDatasourceOverrideAndForwardedHeadersAreRejected() {
        PlatformProperties p = local(); MockEnvironment env = configured(p);
        env.setProperty("spring.datasource.username", "another-account");
        assertThatThrownBy(() -> new ConfigurationSafety(p, env).afterSingletonsInstantiated())
                .hasMessageContaining("Effective data source");
        MockEnvironment forwarded = configured(p); forwarded.setProperty("server.forward-headers-strategy", "framework");
        assertThatThrownBy(() -> new ConfigurationSafety(p, forwarded).afterSingletonsInstantiated())
                .hasMessageContaining("trusted-proxy");
    }
    @Test void validLocalAndProductionSettingsPassSafetyChecks() {
        PlatformProperties p = local();
        assertThatCode(() -> new ConfigurationSafety(p, configured(p)).afterSingletonsInstantiated()).doesNotThrowAnyException();
        PlatformProperties prod = production();
        assertThatCode(() -> new ConfigurationSafety(prod, configured(prod)).afterSingletonsInstantiated()).doesNotThrowAnyException();
    }
    private PlatformProperties local() {
        PlatformProperties p = new PlatformProperties(); p.setDatabasePassword("synthetic-test-only"); return p;
    }
    private PlatformProperties production() {
        PlatformProperties p = local(); p.setEnvironment("prod"); p.setDatabaseUsername("framework_app@business");
        p.setDatabaseSslMode("VERIFY_IDENTITY"); p.setSecureCookie(true); p.setTransportVerified(true);
        p.setAllowedOrigins(java.util.List.of("https://platform.example")); return p;
    }
    private MockEnvironment configured(PlatformProperties p) {
        MockEnvironment env = new MockEnvironment(); env.setActiveProfiles(p.getEnvironment());
        env.setProperty("server.address", "127.0.0.1");
        env.setProperty("server.servlet.session.cookie.secure", Boolean.toString(p.isSecureCookie()));
        env.setProperty("spring.datasource.username", p.getDatabaseUsername());
        env.setProperty("spring.datasource.url", "jdbc:mysql://" + p.getDatabaseHost() + ":" + p.getDatabasePort()
                + "/" + p.getDatabaseName() + "?sslMode=" + p.getDatabaseSslMode()
                + "&allowMultiQueries=false&allowPublicKeyRetrieval=false&connectionTimeZone=UTC"
                + "&forceConnectionTimeZoneToSession=true&connectTimeout=5000&socketTimeout=10000&characterEncoding=UTF-8");
        return env;
    }
}
