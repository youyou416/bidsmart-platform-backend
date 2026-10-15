package com.bidsmart.platform.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public final class JsonResponses {
    private final ObjectMapper mapper;
    public JsonResponses(ObjectMapper mapper) { this.mapper = mapper; }

    public void error(HttpServletRequest request, HttpServletResponse response,
                      int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(response.getOutputStream(),
                new ApiResponse<>(code, message, null, TraceFilter.traceId(request)));
    }
}
