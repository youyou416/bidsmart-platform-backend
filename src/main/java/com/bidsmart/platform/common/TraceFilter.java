package com.bidsmart.platform.common;

import java.io.IOException;
import java.util.UUID;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

public final class TraceFilter extends OncePerRequestFilter {
    public static final String TRACE_ID = "platform.traceId";

    public static String traceId(HttpServletRequest request) {
        Object value = request.getAttribute(TRACE_ID);
        return value == null ? "unavailable" : value.toString();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String traceId = UUID.randomUUID().toString();
        request.setAttribute(TRACE_ID, traceId);
        response.setHeader("X-Trace-Id", traceId);
        MDC.put("traceId", traceId);
        try { chain.doFilter(request, response); }
        finally { MDC.remove("traceId"); }
    }
}
