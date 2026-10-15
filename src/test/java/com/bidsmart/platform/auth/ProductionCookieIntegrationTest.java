package com.bidsmart.platform.auth;

import com.bidsmart.platform.audit.AuditRepository;
import com.bidsmart.platform.crypto.Sm3PasswordEncoder;
import com.bidsmart.platform.permission.PermissionService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import static org.assertj.core.api.Assertions.assertThat;

/** Exercises prod cookie serialization over a loopback test connector; it does not claim a TLS deployment. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "APP_ENV=prod", "DB_PASSWORD=synthetic-test-only", "DB_HOST=database.example", "DB_NAME=framework_test",
        "DB_USERNAME=framework_app@business", "TRANSPORT_VERIFIED=true", "CORS_ALLOWED_ORIGINS=https://platform.example",
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"})
@Import(ProductionCookieIntegrationTest.TestBeans.class)
class ProductionCookieIntegrationTest {
    @LocalServerPort int port;
    @MockBean UserRepository users;
    @MockBean PermissionService permissions;
    @MockBean AuditRepository audit;
    @MockBean org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate unusedJdbc;
    @TestConfiguration static class TestBeans {
        @Bean @Primary PasswordEncoder fastSyntheticTestPasswords() { return new Sm3PasswordEncoder(1000); }
    }
    @Test void actualProdCookieIsSecureHttpOnlyAndLaxWithoutDomain() throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/api/v1/security/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        String cookie = response.headers().firstValue("set-cookie").orElseThrow();
        assertThat(cookie).contains("BIDSMARTSESSION=", "Secure", "HttpOnly", "SameSite=Lax", "Path=/").doesNotContain("Domain=");
        HttpResponse<String> details = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/actuator/env")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(details.statusCode()).isEqualTo(401);
        assertThat(details.body()).doesNotContain("synthetic-test-only", "jdbc:mysql", "framework_app");
    }
}
