package com.bidsmart.platform.captcha;

import java.time.Duration;
import javax.annotation.PostConstruct;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component @Validated @ConfigurationProperties(prefix = "platform.captcha")
public class CaptchaProperties {
    @NotNull private Duration ttl = Duration.ofMinutes(2);
    @Min(1) @Max(120) private int requestsPerMinute = 20;
    public Duration getTtl() { return ttl; }
    public void setTtl(Duration value) { ttl = value; }
    public int getRequestsPerMinute() { return requestsPerMinute; }
    public void setRequestsPerMinute(int value) { requestsPerMinute = value; }
    @PostConstruct public void validate() {
        if (ttl == null || ttl.compareTo(Duration.ofSeconds(30)) < 0 || ttl.compareTo(Duration.ofMinutes(5)) > 0
                || ttl.getNano() != 0) {
            throw new IllegalStateException("platform.captcha.ttl must be whole seconds between 30s and 5m");
        }
    }
}
