package com.bidsmart.platform.audit;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcAuditRepository implements AuditRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcAuditRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public void append(AuditEvent event) {
        // Table selection is solely a fixed server-owned constant, never client input.
        String table = event.getEventType().startsWith("LOGIN_") ? "sys_login_log" : "sys_operation_log";
        jdbc.update("INSERT INTO " + table + " (event_type,user_id,outcome,source_ip,trace_id,event_time) "
                        + "VALUES (:event,:user,:outcome,:ip,:trace,:time)",
                new MapSqlParameterSource("event", event.getEventType()).addValue("user", event.getUserId())
                        .addValue("outcome", event.getOutcome()).addValue("ip", event.getSourceIp())
                        .addValue("trace", event.getTraceId()).addValue("time", Timestamp.from(event.getTime())));
    }

    @Override
    @Transactional
    public void deleteBefore(Instant cutoff) {
        jdbc.update("DELETE FROM sys_login_log WHERE event_time<:cutoff",
                Collections.singletonMap("cutoff", Timestamp.from(cutoff)));
        jdbc.update("DELETE FROM sys_operation_log WHERE event_time<:cutoff",
                Collections.singletonMap("cutoff", Timestamp.from(cutoff)));
    }
}
