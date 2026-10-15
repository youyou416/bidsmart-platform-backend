package com.bidsmart.platform.auth;

import com.bidsmart.platform.audit.AuditService;
import com.bidsmart.platform.common.ApiException;
import com.bidsmart.platform.permission.PermissionSnapshot;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public final class PermissionInterceptor implements HandlerInterceptor {
    private static final Set<String> FRAMEWORK_ENDPOINTS = new HashSet<>(Arrays.asList(
            "/api/v1/security/csrf", "/api/v1/auth/login", "/api/v1/auth/logout", "/api/v1/auth/me",
            "/api/v1/auth/menus", "/api/v1/auth/password", "/api/v1/app/public-config", "/api/v1/health"));
    private final AuditService audit;
    public PermissionInterceptor(AuditService audit) { this.audit = audit; }

    @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod)) { return true; }
        HandlerMethod method = (HandlerMethod) handler;
        RequiresPermission required = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), RequiresPermission.class);
        if (required == null) {
            required = AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), RequiresPermission.class);
        }
        if (required == null && FRAMEWORK_ENDPOINTS.contains(request.getRequestURI())) { return true; }
        PermissionSnapshot snapshot = (PermissionSnapshot) request.getAttribute(CurrentAccountFilter.PERMISSIONS);
        if (required == null || snapshot == null || required.value().isEmpty()
                || !snapshot.getAuthorities().contains(required.value())) {
            audit.denied(request);
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "没有访问权限");
        }
        return true;
    }
}
