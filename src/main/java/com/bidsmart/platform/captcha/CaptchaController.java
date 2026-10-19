package com.bidsmart.platform.captcha;

import com.bidsmart.platform.common.ApiResponse;
import com.bidsmart.platform.common.TraceFilter;
import javax.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class CaptchaController {
    private final CaptchaService service;
    public CaptchaController(CaptchaService service) { this.service = service; }
    @PostMapping("/api/v1/security/captcha")
    public ApiResponse<CaptchaChallenge> issue(HttpServletRequest request) {
        return ApiResponse.success(service.issue(request), TraceFilter.traceId(request));
    }
}
