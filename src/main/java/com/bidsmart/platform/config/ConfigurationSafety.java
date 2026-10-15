package com.bidsmart.platform.config;

import java.net.URI;
import java.util.Locale;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public final class ConfigurationSafety implements SmartInitializingSingleton {
    private final PlatformProperties properties;
    private final Environment environment;

    public ConfigurationSafety(PlatformProperties properties, Environment environment) {
        this.properties = properties;
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        for (String profile : environment.getActiveProfiles()) {
            if (("dev".equals(profile) || "test".equals(profile) || "prod".equals(profile))
                    && !profile.equals(properties.getEnvironment())) {
                throw new IllegalStateException("APP_ENV must match the effective Spring environment profile");
            }
        }
        validate(properties, environment.getProperty("server.address", "127.0.0.1"));
        if ("prod".equals(properties.getEnvironment())
                && !Boolean.parseBoolean(environment.getProperty("server.servlet.session.cookie.secure", "false"))) {
            throw new IllegalStateException("prod requires the effective Servlet cookie Secure flag");
        }
        if (!Boolean.parseBoolean(environment.getProperty("server.servlet.session.cookie.http-only", "true"))
                || !"BIDSMARTSESSION".equals(environment.getProperty("server.servlet.session.cookie.name", "BIDSMARTSESSION"))
                || !"/".equals(environment.getProperty("server.servlet.session.cookie.path", "/"))
                || environment.getProperty("server.servlet.session.cookie.domain") != null) {
            throw new IllegalStateException("Session cookies require HttpOnly, the configured name/path, and no Domain override");
        }
        if (!properties.getDatabaseUsername().equals(environment.getProperty("spring.datasource.username"))
                || !safeJdbcUrl(properties).equals(environment.getProperty("spring.datasource.url"))) {
            throw new IllegalStateException("Effective data source must match validated DB_* parameters and safe JDBC options");
        }
        if (!"none".equals(environment.getProperty("server.forward-headers-strategy", "none"))) {
            throw new IllegalStateException("Forwarded headers require a separately configured trusted-proxy adapter");
        }
    }

    public static void validate(PlatformProperties p, String address) {
        if (!"dev".equals(p.getEnvironment()) && !"test".equals(p.getEnvironment())
                && !"prod".equals(p.getEnvironment())) {
            throw new IllegalStateException("platform.environment must be dev, test or prod");
        }
        if (p.getDatabasePassword() == null || p.getDatabasePassword().isEmpty()) {
            throw new IllegalStateException("DB_PASSWORD is required; provide it outside the repository");
        }
        if (p.getDatabaseUsername() == null || p.getDatabaseUsername().trim().isEmpty()) {
            throw new IllegalStateException("DB_USERNAME is required");
        }
        if (p.getDatabaseName() == null || !p.getDatabaseName().matches("[A-Za-z][A-Za-z0-9_]{0,63}")) {
            throw new IllegalStateException("DB_NAME is invalid");
        }
        if (p.getDatabaseHost() == null || !p.getDatabaseHost().matches("[A-Za-z0-9][A-Za-z0-9.-]{0,252}")
                || p.getDatabasePort() < 1 || p.getDatabasePort() > 65535) {
            throw new IllegalStateException("DB_HOST or DB_PORT is invalid");
        }
        boolean localDatabase = "127.0.0.1".equals(p.getDatabaseHost()) || "localhost".equals(p.getDatabaseHost());
        if (!("DISABLED".equals(p.getDatabaseSslMode()) || "VERIFY_IDENTITY".equals(p.getDatabaseSslMode()))
                || !localDatabase && !"VERIFY_IDENTITY".equals(p.getDatabaseSslMode())
                || !"dev".equals(p.getEnvironment()) && !"VERIFY_IDENTITY".equals(p.getDatabaseSslMode())) {
            throw new IllegalStateException("Remote/test/prod database transport requires VERIFY_IDENTITY; DISABLED is local dev only");
        }
        String normalizedUser = p.getDatabaseUsername().toLowerCase(Locale.ROOT);
        if (!localDatabase && (normalizedUser.contains("@sys") || normalizedUser.contains("#sys"))) {
            throw new IllegalStateException("System-tenant credentials are restricted to isolated local development");
        }
        if ("test".equals(p.getEnvironment()) && ("hr_score_dev".equals(p.getDatabaseName())
                || normalizedUser.matches("root(?:@.*|#.*)?") || normalizedUser.contains("@sys")
                || normalizedUser.contains("#sys"))) {
            throw new IllegalStateException("test requires an explicit independent test database and application account");
        }
        boolean loopback = "127.0.0.1".equals(address) || "::1".equals(address);
        if (!"prod".equals(p.getEnvironment()) && !loopback) {
            throw new IllegalStateException("dev/test must listen only on a loopback address");
        }
        if ("prod".equals(p.getEnvironment())) {
            String user = p.getDatabaseUsername().toLowerCase(Locale.ROOT);
            if (user.matches("root(?:@.*|#.*)?") || user.contains("@sys")
                    || user.contains("#sys")) {
                throw new IllegalStateException("prod requires a least-privilege application account in a business tenant");
            }
            if (!p.isSecureCookie() || !p.isTransportVerified() || p.isDebugEnabled()) {
                throw new IllegalStateException("prod requires Secure cookies, verified transport, and disabled debug");
            }
            if (p.isBootstrapEnabled()) {
                throw new IllegalStateException("bootstrap is a local initialization operation and is forbidden in prod");
            }
        }
        if (p.getAbsoluteSessionTimeout().isNegative() || p.getAbsoluteSessionTimeout().isZero()
                || p.getFailureWindow().isNegative() || p.getFailureWindow().isZero()
                || p.getLockout().isNegative() || p.getLockout().isZero()
                || p.getMaxLoginFailures() < 1 || p.getMaxRequestBodyBytes() < 1024
                || p.getCsrfRequestsPerMinute() < 1 || p.getAuditRetentionDays() < 1) {
            throw new IllegalStateException("Security limits must be positive and valid");
        }
        if (p.getAllowedOrigins() == null || p.getAllowedOrigins().isEmpty()) {
            throw new IllegalStateException("At least one explicit allowed origin is required");
        }
        for (String origin : p.getAllowedOrigins()) {
            URI uri;
            try { uri = URI.create(origin); }
            catch (IllegalArgumentException error) { throw new IllegalStateException("Invalid allowed origin"); }
            if (uri.getHost() == null || uri.getRawUserInfo() != null || uri.getRawQuery() != null
                    || uri.getRawFragment() != null || uri.getPath() != null && !uri.getPath().isEmpty()
                    || !("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    || "prod".equals(p.getEnvironment()) && !"https".equals(uri.getScheme())) {
                throw new IllegalStateException("Allowed origins must be explicit HTTP(S) origins; prod requires HTTPS");
            }
        }
        if (p.getLogoPath() == null || !p.getLogoPath().matches("/[A-Za-z0-9/_ .-]+")
                || p.getLogoPath().contains("..") || !p.getThemeColor().matches("#[A-Fa-f0-9]{6}")) {
            throw new IllegalStateException("Public configuration must use a local logo and hexadecimal theme color");
        }
    }

    private static String safeJdbcUrl(PlatformProperties p) {
        return "jdbc:mysql://" + p.getDatabaseHost() + ":" + p.getDatabasePort() + "/" + p.getDatabaseName()
                + "?sslMode=" + p.getDatabaseSslMode() + "&allowMultiQueries=false&allowPublicKeyRetrieval=false"
                + "&connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true&connectTimeout=5000"
                + "&socketTimeout=10000&characterEncoding=UTF-8";
    }
}
