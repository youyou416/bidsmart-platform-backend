package com.bidsmart.platform.audit;

import com.bidsmart.platform.auth.SessionPrincipal;
import com.bidsmart.platform.common.TraceFilter;
import com.bidsmart.platform.config.PlatformProperties;
import java.time.Clock;
import java.time.Duration;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public final class AuditService {
    private static final Logger LOG = LoggerFactory.getLogger(AuditService.class);
    private final AuditRepository repository;
    private final PlatformProperties properties;
    private final Clock clock;

    public AuditService(AuditRepository repository, PlatformProperties properties, Clock clock) {
        this.repository = repository; this.properties = properties; this.clock = clock;
    }

    public void record(String event, Long userId, String outcome, HttpServletRequest request) {
        append(new AuditEvent(event, userId, outcome, request == null ? "local" : sourceIp(request),
                request == null ? "startup" : TraceFilter.traceId(request), clock.instant()));
    }

    public void denied(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Long userId = authentication != null && authentication.getPrincipal() instanceof SessionPrincipal
                ? ((SessionPrincipal) authentication.getPrincipal()).getUserId() : null;
        record("AUTHORIZATION_DENIED", userId, "DENIED", request);
    }

    public static String sourceIp(HttpServletRequest request) {
        // Forwarded headers are intentionally ignored. Only the socket peer is currently trusted.
        String value = request.getRemoteAddr();
        return value != null && value.matches("[A-Fa-f0-9:.]{1,64}") ? value : "unknown";
    }

    private void append(AuditEvent event) {
        LOG.info("event={} userId={} outcome={} source={} traceId={}", event.getEventType(),
                event.getUserId(), event.getOutcome(), event.getSourceIp(), event.getTraceId());
        try { repository.append(event); }
        catch (RuntimeException error) {
            // Do not print JDBC messages, SQL, URLs, request bodies or exception payloads.
            LOG.error("event=audit_persistence_failed traceId={} exceptionType={}",
                    event.getTraceId(), error.getClass().getSimpleName());
            throw error;
        }
    }

    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void retentionCleanup() {
        try { repository.deleteBefore(clock.instant().minus(Duration.ofDays(properties.getAuditRetentionDays()))); }
        catch (RuntimeException error) {
            LOG.error("event=audit_retention_failed exceptionType={}", error.getClass().getSimpleName());
        }
    }
}
