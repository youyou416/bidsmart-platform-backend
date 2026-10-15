package com.bidsmart.platform.audit;

import java.time.Instant;

public final class AuditEvent {
    private final String eventType;
    private final Long userId;
    private final String outcome;
    private final String sourceIp;
    private final String traceId;
    private final Instant time;

    public AuditEvent(String eventType, Long userId, String outcome, String sourceIp,
                      String traceId, Instant time) {
        this.eventType = eventType; this.userId = userId; this.outcome = outcome;
        this.sourceIp = sourceIp; this.traceId = traceId; this.time = time;
    }
    public String getEventType() { return eventType; }
    public Long getUserId() { return userId; }
    public String getOutcome() { return outcome; }
    public String getSourceIp() { return sourceIp; }
    public String getTraceId() { return traceId; }
    public Instant getTime() { return time; }
}
