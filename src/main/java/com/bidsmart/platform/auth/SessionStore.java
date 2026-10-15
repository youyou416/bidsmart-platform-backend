package com.bidsmart.platform.auth;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.core.context.SecurityContext;

/** Adapter boundary for a future reviewed shared session solution. Current implementation is single-instance. */
public interface SessionStore {
    void save(SecurityContext context, HttpServletRequest request, HttpServletResponse response);
    void invalidate(HttpServletRequest request, HttpServletResponse response);
}
