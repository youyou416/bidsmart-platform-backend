package com.bidsmart.platform.auth;

import com.bidsmart.platform.audit.AuditService;
import com.bidsmart.platform.common.JsonResponses;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.DefaultCorsProcessor;

public final class JsonCorsProcessor extends DefaultCorsProcessor {
    private final JsonResponses json;
    private final AuditService audit;
    public JsonCorsProcessor(JsonResponses json, AuditService audit) { this.json = json; this.audit = audit; }
    @Override protected void rejectRequest(ServerHttpResponse response) { response.setStatusCode(HttpStatus.FORBIDDEN); }
    @Override public boolean processRequest(CorsConfiguration configuration, HttpServletRequest request,
                                           HttpServletResponse response) throws IOException {
        boolean allowed = super.processRequest(configuration, request, response);
        if (!allowed) {
            try { audit.denied(request); }
            catch (RuntimeException error) {
                json.error(request, response, 500, "SECURITY_UNAVAILABLE", "安全服务暂不可用"); return false;
            }
            json.error(request, response, 403, "FORBIDDEN_ORIGIN", "请求来源不被允许");
        }
        return allowed;
    }
}
