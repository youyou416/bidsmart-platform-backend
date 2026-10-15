package com.bidsmart.platform.config;

import com.bidsmart.platform.auth.RequiresPermission;
import com.bidsmart.platform.common.ApiResponse;
import com.bidsmart.platform.common.TraceFilter;
import java.util.Collections;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Static framework information is the minimal real target for explicit interface permission enforcement. */
@RestController
public final class FrameworkController {
    @GetMapping("/api/v1/framework/info")
    @RequiresPermission("framework:home:view")
    public ApiResponse<Map<String, String>> info(HttpServletRequest request) {
        return ApiResponse.success(Collections.singletonMap("description", "前后端分离基础框架"), TraceFilter.traceId(request));
    }
}
