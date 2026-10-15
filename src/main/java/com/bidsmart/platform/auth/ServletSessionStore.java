package com.bidsmart.platform.auth;

import com.bidsmart.platform.config.PlatformProperties;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

@Component
public final class ServletSessionStore implements SessionStore {
    private final SecurityContextRepository contexts;
    private final PlatformProperties properties;
    public ServletSessionStore(SecurityContextRepository contexts, PlatformProperties properties) {
        this.contexts = contexts; this.properties = properties;
    }
    @Override public void save(SecurityContext context, HttpServletRequest request, HttpServletResponse response) {
        contexts.saveContext(context, request, response);
    }
    @Override public void invalidate(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) { session.invalidate(); }
        SecurityContextHolder.clearContext();
        response.addHeader("Set-Cookie", "BIDSMARTSESSION=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax"
                + (properties.isSecureCookie() ? "; Secure" : ""));
    }
}
