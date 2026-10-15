package com.bidsmart.platform.auth;

import com.bidsmart.platform.audit.AuditService;
import com.bidsmart.platform.common.ApiResponse;
import com.bidsmart.platform.common.TraceFilter;
import com.bidsmart.platform.permission.PermissionService;
import com.bidsmart.platform.permission.PermissionSnapshot;
import java.time.Clock;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public final class AuthController {
    private final AuthService auth;
    private final PermissionService permissions;
    private final SessionAuthenticationStrategy strategy;
    private final SessionStore sessions;
    private final Clock clock;
    private final AuditService audit;

    public AuthController(AuthService auth, PermissionService permissions, SessionAuthenticationStrategy strategy,
                          SessionStore sessions, Clock clock, AuditService audit) {
        this.auth = auth; this.permissions = permissions; this.strategy = strategy;
        this.sessions = sessions; this.clock = clock; this.audit = audit;
    }

    @GetMapping("/security/csrf")
    public ApiResponse<Map<String, String>> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        Map<String, String> result = new LinkedHashMap<>();
        result.put("token", token.getToken()); result.put("headerName", token.getHeaderName());
        return ApiResponse.success(result, TraceFilter.traceId(request));
    }

    @PostMapping("/auth/login")
    public ApiResponse<CurrentUser> login(@Valid @RequestBody AuthRequests.Login input,
                                         HttpServletRequest request, HttpServletResponse response) {
        UserAccount account = auth.authenticate(input, request);
        PermissionSnapshot snapshot = account.isPasswordChangeRequired() ? null : permissions.loadForUser(account.getId());
        audit.record("LOGIN_SUCCESS", account.getId(), "SUCCESS", request);
        UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                new SessionPrincipal(account, clock.instant()), null, Collections.emptyList());
        strategy.onAuthentication(authentication, request, response);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication); SecurityContextHolder.setContext(context);
        sessions.save(context, request, response);
        return ApiResponse.success(new CurrentUser(account, snapshot), TraceFilter.traceId(request));
    }

    @GetMapping("/auth/me")
    public ApiResponse<CurrentUser> me(HttpServletRequest request) {
        return ApiResponse.success(new CurrentUser(account(request),
                (PermissionSnapshot) request.getAttribute(CurrentAccountFilter.PERMISSIONS)), TraceFilter.traceId(request));
    }

    @GetMapping("/auth/menus")
    public ApiResponse<?> menus(HttpServletRequest request) {
        PermissionSnapshot snapshot = (PermissionSnapshot) request.getAttribute(CurrentAccountFilter.PERMISSIONS);
        return ApiResponse.success(snapshot.getMenus(), TraceFilter.traceId(request));
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Object> logout(HttpServletRequest request, HttpServletResponse response) {
        try { audit.record("LOGOUT", account(request).getId(), "SUCCESS", request); }
        finally { sessions.invalidate(request, response); }
        return ApiResponse.success(null, TraceFilter.traceId(request));
    }

    @PostMapping("/auth/password")
    public ApiResponse<Object> password(@Valid @RequestBody AuthRequests.ChangePassword input,
                                       HttpServletRequest request, HttpServletResponse response) {
        auth.changePassword(account(request), input, request);
        sessions.invalidate(request, response);
        return ApiResponse.success(null, TraceFilter.traceId(request));
    }

    private UserAccount account(HttpServletRequest request) {
        return (UserAccount) request.getAttribute(CurrentAccountFilter.ACCOUNT);
    }
}
