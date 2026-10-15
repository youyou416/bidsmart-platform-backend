package com.bidsmart.platform.auth;

import com.bidsmart.platform.audit.AuditEvent;
import com.bidsmart.platform.audit.AuditRepository;
import com.bidsmart.platform.crypto.Sm3PasswordEncoder;
import com.bidsmart.platform.permission.PermissionService;
import com.bidsmart.platform.permission.PermissionSnapshot;
import com.bidsmart.platform.permission.Role;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Synthetic test repository mocks exercise the actual servlet security chain; no DB compatibility claim. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "DB_PASSWORD=synthetic-test-only", "APP_ENV=test", "DB_HOST=127.0.0.1", "DB_NAME=framework_test",
        "DB_USERNAME=synthetic_app@synthetic", "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"})
@AutoConfigureMockMvc
@Import(SecurityIntegrationTest.TestBeans.class)
@ExtendWith(OutputCaptureExtension.class)
class SecurityIntegrationTest {
    private static final String PASSWORD = "合成测试 password 42";
    private static final long ID = 9007199254740993L;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext webContext;
    @LocalServerPort int port;
    @MockBean UserRepository users;
    @MockBean PermissionService permissionService;
    @MockBean AuditRepository audit;
    @MockBean org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate unusedJdbc;
    @MockBean Clock clock;
    private final AtomicReference<UserAccount> current = new AtomicReference<>();
    private static final java.util.concurrent.atomic.AtomicInteger TEST_DAY = new java.util.concurrent.atomic.AtomicInteger();
    private Instant now;

    @TestConfiguration static class TestBeans {
        // A deliberately lower parameter is confined to this test configuration; production always uses 600000.
        @Bean @Primary PasswordEncoder fastSyntheticTestPasswords() { return new Sm3PasswordEncoder(1000); }
    }

    @BeforeEach void setUp() {
        now = Instant.parse("2026-10-14T00:00:00Z").plus(java.time.Duration.ofDays(TEST_DAY.incrementAndGet()));
        when(clock.instant()).thenReturn(now);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        current.set(account(true, false, 1, passwords.encode(PASSWORD)));
        when(users.findByUsername(anyString())).thenAnswer(invocation ->
                "synthetic".equals(invocation.getArgument(0)) ? Optional.ofNullable(current.get()) : Optional.empty());
        when(users.findById(ID)).thenAnswer(invocation -> Optional.ofNullable(current.get()));
        when(permissionService.loadForUser(ID)).thenReturn(snapshot(Set.of("framework:home:view")));
        when(users.changePassword(anyLong(), anyLong(), anyString())).thenAnswer(invocation -> {
            UserAccount previous = current.get();
            if (previous == null || previous.getPasswordVersion() != (long) invocation.getArgument(1)) { return false; }
            current.set(account(true, false, previous.getPasswordVersion() + 1, invocation.getArgument(2)));
            return true;
        });
    }

    @Test void unauthenticatedAndPublicContractsAreSafe() throws Exception {
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
        mvc.perform(get("/api/v1/app/public-config")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.logoPath").value("/mark.svg"))
                .andExpect(jsonPath("$.data.databasePassword").doesNotExist());
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test void csrfRequiredForLoginAndProtectedWrites() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginJson(PASSWORD)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        Handshake handshake = handshake(null, "127.0.0.2");
        mvc.perform(post("/api/v1/auth/login").session(handshake.session).header("X-CSRF-TOKEN", "forged")
                .contentType(MediaType.APPLICATION_JSON).content(loginJson(PASSWORD)))
                .andExpect(status().isForbidden());
        Handshake logged = login("127.0.0.3");
        mvc.perform(post("/api/v1/auth/logout").session(logged.session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/password").session(logged.session).contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"x\",\"newPassword\":\"y\"}"))
                .andExpect(status().isForbidden());
    }

    @Test void loginRotatesSessionAndCsrfAndSavesContextWithoutExposingSecrets() throws Exception {
        Handshake before = handshake(null, "127.0.0.4");
        String oldId = before.session.getId();
        MvcResult response = performLogin(before, PASSWORD, "127.0.0.4").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(Long.toString(ID)))
                .andExpect(jsonPath("$.data.firstPasswordChangeRequired").value(false))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist()).andReturn();
        assertThat(before.session.getId()).isNotEqualTo(oldId);
        assertThat(before.session.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNotNull();
        assertThat(response.getResponse().getContentAsString()).doesNotContain(PASSWORD, current.get().getPasswordHash(), "BIDSMARTSESSION", "token");
        Handshake after = handshake(before.session, "127.0.0.4");
        assertThat(after.token).isNotEqualTo(before.token);
        mvc.perform(post("/api/v1/auth/logout").session(after.session).header("X-CSRF-TOKEN", before.token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        mvc.perform(get("/api/v1/auth/me").session(after.session)).andExpect(status().isOk());
    }

    @Test void nonexistentDisabledAndWrongPasswordsUseSameFailure() throws Exception {
        Handshake handshake = handshake(null, "127.0.0.5");
        String bad = performLogin(handshake, "a bad synthetic password", "127.0.0.5")
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        MvcResult unknown = mvc.perform(post("/api/v1/auth/login").session(handshake.session)
                .header("X-CSRF-TOKEN", handshake.token).with(request -> { request.setRemoteAddr("127.0.0.6"); return request; })
                .contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"missing\",\"password\":\"a bad synthetic password\"}"))
                .andExpect(status().isUnauthorized()).andReturn();
        current.set(account(false, false, 1, current.get().getPasswordHash()));
        String disabled = performLogin(handshake, PASSWORD, "127.0.0.7").andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(bad).get("message")).isEqualTo(json.readTree(unknown.getResponse().getContentAsString()).get("message"))
                .isEqualTo(json.readTree(disabled).get("message"));
    }

    @Test void latestGrantRevocationRejectsDirectApiAccess() throws Exception {
        Handshake logged = login("127.0.0.8");
        mvc.perform(get("/api/v1/framework/info").session(logged.session)).andExpect(status().isOk());
        when(permissionService.loadForUser(ID)).thenReturn(snapshot(Collections.emptySet()));
        mvc.perform(get("/api/v1/framework/info").session(logged.session)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/v1/auth/me").session(logged.session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions").isEmpty());
        mvc.perform(get("/api/v1/not-a-registered-interface").session(logged.session))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test void firstPasswordChangeRestrictsMenusAndRevokesAllOldSessions() throws Exception {
        current.set(account(true, true, 1, current.get().getPasswordHash()));
        Handshake first = login("127.0.0.9");
        Handshake second = login("127.0.0.10");
        mvc.perform(get("/api/v1/auth/me").session(first.session)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstPasswordChangeRequired").value(true))
                .andExpect(jsonPath("$.data.permissions").isEmpty());
        mvc.perform(get("/api/v1/auth/menus").session(first.session)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mvc.perform(get("/api/v1/framework/info").session(first.session)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/auth/password").session(first.session).header("X-CSRF-TOKEN", first.token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPassword", "新合成安全口令 2026 !"))))
                .andExpect(status().isOk());
        assertThat(first.session.isInvalid()).isTrue();
        mvc.perform(get("/api/v1/auth/me").session(second.session)).andExpect(status().isUnauthorized());
        assertThat(second.session.isInvalid()).isTrue();
        Handshake fresh = handshake(null, "127.0.0.11");
        performLogin(fresh, "新合成安全口令 2026 !", "127.0.0.11").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.firstPasswordChangeRequired").value(false));
    }

    @Test void logoutInvalidatesServerSessionAndClearsCookieThenRefreshesCsrf() throws Exception {
        Handshake logged = login("127.0.0.12");
        mvc.perform(post("/api/v1/auth/logout").session(logged.session).header("X-CSRF-TOKEN", logged.token))
                .andExpect(status().isOk()).andExpect(header().string("Set-Cookie", org.hamcrest.Matchers.containsString("Max-Age=0")));
        assertThat(logged.session.isInvalid()).isTrue();
        mvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        assertThat(handshake(null, "127.0.0.12").token).isNotEqualTo(logged.token);
    }

    @Test void disablingAccountAndAbsoluteTimeoutRevokeExistingSession() throws Exception {
        Handshake disabled = login("127.0.0.13");
        current.set(account(false, false, 1, current.get().getPasswordHash()));
        mvc.perform(get("/api/v1/auth/me").session(disabled.session)).andExpect(status().isUnauthorized());
        assertThat(disabled.session.isInvalid()).isTrue();
        current.set(account(true, false, 1, current.get().getPasswordHash()));
        Handshake expired = login("127.0.0.14");
        when(clock.instant()).thenReturn(now.plus(java.time.Duration.ofHours(8)));
        mvc.perform(get("/api/v1/auth/me").session(expired.session)).andExpect(status().isUnauthorized());
        assertThat(expired.session.isInvalid()).isTrue();
    }

    @Test void revokedSessionsAndLogoutAreDestroyedEvenWhenDatabaseAuditFails(CapturedOutput logs) throws Exception {
        Handshake revoked = login("127.0.0.21");
        current.set(account(false, false, 1, current.get().getPasswordHash()));
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("private-audit-password-value"))
                .when(audit).append(any(AuditEvent.class));
        mvc.perform(get("/api/v1/auth/me").session(revoked.session)).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("SECURITY_UNAVAILABLE"));
        assertThat(revoked.session.isInvalid()).isTrue();

        reset(audit);
        current.set(account(true, false, 1, current.get().getPasswordHash()));
        Handshake logout = login("127.0.0.22");
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("private-audit-password-value"))
                .when(audit).append(any(AuditEvent.class));
        mvc.perform(post("/api/v1/auth/logout").session(logout.session).header("X-CSRF-TOKEN", logout.token))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"));
        assertThat(logout.session.isInvalid()).isTrue();
        assertThat(logs.getAll()).doesNotContain("private-audit-password-value", PASSWORD, logout.token, revoked.token);
    }

    @Test void loginLimitsAccountAndPeerIgnoresForwardedHeader() throws Exception {
        Handshake handshake = handshake(null, "127.0.0.15");
        for (int i = 0; i < 5; i++) {
            performLogin(handshake, "wrong synthetic password", "127.0.0.15").andExpect(status().isUnauthorized());
        }
        performLogin(handshake, PASSWORD, "127.0.0.16").andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
        // After 15 minutes the bounded lock expires; success remains possible.
        when(clock.instant()).thenReturn(now.plusSeconds(901));
        performLogin(handshake, PASSWORD, "127.0.0.15").andExpect(status().isOk());
    }

    @Test void corsRejectsUnknownOriginsAndAllowsExplicitOrigin() throws Exception {
        mvc.perform(get("/api/v1/security/csrf").header("Origin", "https://attacker.example"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN_ORIGIN"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        mvc.perform(options("/api/v1/auth/login").header("Origin", "http://127.0.0.1:5173")
                .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "X-CSRF-TOKEN"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://127.0.0.1:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
    }

    @Test void oversizedBodiesRejectBeforePasswordVerification() throws Exception {
        Handshake handshake = handshake(null, "127.0.0.17");
        mvc.perform(post("/api/v1/auth/login").session(handshake.session).header("X-CSRF-TOKEN", handshake.token)
                .contentType(MediaType.APPLICATION_JSON).content("x".repeat(17000)))
                .andExpect(status().isPayloadTooLarge()).andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test void persistedAuditHasOnlyWhitelistedMetadata() throws Exception {
        Handshake logged = login("127.0.0.18");
        mvc.perform(post("/api/v1/auth/logout").session(logged.session).header("X-CSRF-TOKEN", logged.token))
                .andExpect(status().isOk());
        ArgumentCaptor<AuditEvent> captured = ArgumentCaptor.forClass(AuditEvent.class);
        verify(audit, atLeastOnce()).append(captured.capture());
        String serialized = json.writeValueAsString(captured.getAllValues());
        assertThat(serialized).contains("LOGIN_SUCCESS", "LOGOUT").doesNotContain(PASSWORD, logged.token,
                current.get().getPasswordHash(), "Cookie", "synthetic-test-only");
    }

    @Test void databaseFailuresDoNotExposeSqlCredentialsOrStackTraces(CapturedOutput logs) throws Exception {
        Handshake handshake = handshake(null, "127.0.0.20");
        when(users.findByUsername("synthetic")).thenThrow(new org.springframework.jdbc.BadSqlGrammarException(
                "secret_operation", "SELECT secret_column FROM secret_table", new java.sql.SQLException("private-db-password-and-url")));
        MvcResult failure = performLogin(handshake, PASSWORD, "127.0.0.20")
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE")).andReturn();
        assertThat(failure.getResponse().getContentAsString()).doesNotContain("secret_column", "secret_table", "private-db-password", "SQLException", PASSWORD);
        assertThat(logs.getAll()).doesNotContain("secret_column", "secret_table", "private-db-password-and-url", PASSWORD, handshake.token);
    }

    @Test void actualTomcatSendsLaxHttpOnlyCookieAndIdleTimeout() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> response = client.send(HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + port + "/api/v1/security/csrf")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        String cookie = response.headers().firstValue("set-cookie").orElseThrow();
        assertThat(cookie).contains("BIDSMARTSESSION=", "HttpOnly", "SameSite=Lax", "Path=/").doesNotContain("Domain=", "Secure");
        Handshake handshake = handshake(null, "127.0.0.19");
        org.springframework.boot.web.embedded.tomcat.TomcatWebServer server =
                (org.springframework.boot.web.embedded.tomcat.TomcatWebServer) webContext.getWebServer();
        org.apache.catalina.Context context = (org.apache.catalina.Context) server.getTomcat().getHost().findChild("");
        assertThat(context.getSessionTimeout()).isEqualTo(30);
    }

    private UserAccount account(boolean enabled, boolean first, long version, String hash) {
        return new UserAccount(ID, "synthetic", "合成账户", 1001L, "合成根部门", "BUYER", null,
                "SELF_AND_SPECIFIED", hash, enabled, first, version);
    }
    private PermissionSnapshot snapshot(Set<String> permissions) {
        return new PermissionSnapshot(permissions, Collections.singletonList(new Role(1001L, null,
                "合成角色", true, Set.of(1001L))), Collections.emptyList(), null, Set.of(1001L), Collections.emptyMap());
    }
    private String loginJson(String password) throws Exception { return json.writeValueAsString(Map.of("username", "synthetic", "password", password)); }
    private Handshake handshake(MockHttpSession session, String ip) throws Exception {
        org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request = get("/api/v1/security/csrf")
                .with(r -> { r.setRemoteAddr(ip); return r; });
        if (session != null) { request.session(session); }
        MvcResult result = mvc.perform(request).andExpect(status().isOk()).andReturn();
        return new Handshake((MockHttpSession) result.getRequest().getSession(false),
                json.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText());
    }
    private org.springframework.test.web.servlet.ResultActions performLogin(Handshake handshake, String password, String ip) throws Exception {
        return mvc.perform(post("/api/v1/auth/login").session(handshake.session).header("X-CSRF-TOKEN", handshake.token)
                .header("X-Forwarded-For", "203.0.113.99").with(r -> { r.setRemoteAddr(ip); return r; })
                .contentType(MediaType.APPLICATION_JSON).content(loginJson(password)));
    }
    private Handshake login(String ip) throws Exception {
        Handshake handshake = handshake(null, ip);
        performLogin(handshake, PASSWORD, ip).andExpect(status().isOk());
        return handshake(handshake.session, ip);
    }
    private static final class Handshake {
        private final MockHttpSession session;
        private final String token;
        private Handshake(MockHttpSession session, String token) { this.session = session; this.token = token; }
    }
}
