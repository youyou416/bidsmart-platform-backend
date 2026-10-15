package com.bidsmart.platform.audit;

import java.time.Instant;

public interface AuditRepository {
    void append(AuditEvent event);
    void deleteBefore(Instant cutoff);
}
