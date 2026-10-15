package com.bidsmart.platform.auth;

import com.bidsmart.platform.audit.AuditService;
import com.bidsmart.platform.common.JsonResponses;
import com.bidsmart.platform.config.PlatformProperties;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ReadListener;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/** Bound JSON bodies including chunked requests before controllers or password derivation run. */
public final class RequestBoundaryFilter extends OncePerRequestFilter {
    private final PlatformProperties properties;
    private final LoginRateLimiter limiter;
    private final JsonResponses json;
    public RequestBoundaryFilter(PlatformProperties properties, LoginRateLimiter limiter, JsonResponses json) {
        this.properties = properties; this.limiter = limiter; this.json = json;
    }

    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                              FilterChain chain) throws IOException, ServletException {
        response.setHeader("Cache-Control", "no-store");
        if ("/api/v1/security/csrf".equals(request.getRequestURI())
                && !limiter.csrfAllowed(AuditService.sourceIp(request))) {
            response.setHeader("Retry-After", "60");
            json.error(request, response, 429, "RATE_LIMITED", "请求过于频繁，请稍后重试"); return;
        }
        if ("GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod())
                || "OPTIONS".equals(request.getMethod())) { chain.doFilter(request, response); return; }
        int maximum = properties.getMaxRequestBodyBytes();
        if (request.getContentLengthLong() > maximum) {
            json.error(request, response, 413, "PAYLOAD_TOO_LARGE", "请求体过大"); return;
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int count;
        while ((count = request.getInputStream().read(buffer)) != -1) {
            if (bytes.size() + count > maximum) {
                json.error(request, response, 413, "PAYLOAD_TOO_LARGE", "请求体过大"); return;
            }
            bytes.write(buffer, 0, count);
        }
        byte[] body = bytes.toByteArray();
        HttpServletRequest wrapped = new HttpServletRequestWrapper(request) {
            @Override public ServletInputStream getInputStream() {
                ByteArrayInputStream input = new ByteArrayInputStream(body);
                return new ServletInputStream() {
                    @Override public int read() { return input.read(); }
                    @Override public int read(byte[] values, int offset, int length) { return input.read(values, offset, length); }
                    @Override public boolean isFinished() { return input.available() == 0; }
                    @Override public boolean isReady() { return true; }
                    @Override public void setReadListener(ReadListener listener) {
                        throw new UnsupportedOperationException("Synchronous request bodies only");
                    }
                };
            }
        };
        chain.doFilter(wrapped, response);
    }
}
