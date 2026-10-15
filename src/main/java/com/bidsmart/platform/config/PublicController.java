package com.bidsmart.platform.config;

import com.bidsmart.platform.common.ApiResponse;
import com.bidsmart.platform.common.TraceFilter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public final class PublicController {
    private final PlatformProperties properties;
    public PublicController(PlatformProperties properties) { this.properties = properties; }
    @GetMapping("/app/public-config")
    public ApiResponse<Map<String, Object>> publicConfig(HttpServletRequest request) {
        Map<String, Object> publicValues = new LinkedHashMap<>();
        publicValues.put("systemName", properties.getSystemName());
        publicValues.put("logoPath", properties.getLogoPath());
        publicValues.put("themeColor", properties.getThemeColor());
        publicValues.put("tabsEnabled", properties.isTabsEnabled());
        return ApiResponse.success(publicValues, TraceFilter.traceId(request));
    }
    @GetMapping("/health")
    public ApiResponse<Map<String, String>> health(HttpServletRequest request) {
        // Liveness only: no claim that the database or an external system has been verified.
        return ApiResponse.success(Collections.singletonMap("status", "UP"), TraceFilter.traceId(request));
    }
}
