package com.bidsmart.platform.captcha;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class JdbcCaptchaRepository implements CaptchaRepository {
    private final NamedParameterJdbcTemplate jdbc;
    public JdbcCaptchaRepository(NamedParameterJdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override @Transactional
    public void replace(CaptchaRecord record) {
        jdbc.update("DELETE FROM sys_login_captcha WHERE session_binding_hash=:binding",
                Collections.singletonMap("binding", record.getBinding()));
        jdbc.update("INSERT INTO sys_login_captcha (captcha_id,session_binding_hash,answer_hash,created_at,expires_at) "
                        + "VALUES (:id,:binding,:answer,:created,:expires)",
                new MapSqlParameterSource("id", record.getId()).addValue("binding", record.getBinding())
                        .addValue("answer", record.getAnswerHash()).addValue("created", Timestamp.from(record.getCreatedAt()))
                        .addValue("expires", Timestamp.from(record.getExpiresAt())));
    }

    @Override
    public Optional<CaptchaRecord> consume(String id, String binding) {
        Map<String, String> params = Map.of("id", id, "binding", binding);
        List<CaptchaRecord> rows = jdbc.query("SELECT captcha_id,session_binding_hash,answer_hash,created_at,expires_at "
                        + "FROM sys_login_captcha WHERE captcha_id=:id AND session_binding_hash=:binding", params,
                (rs, index) -> new CaptchaRecord(rs.getString("captcha_id"), rs.getString("session_binding_hash"),
                        rs.getString("answer_hash"), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("expires_at").toInstant()));
        if (rows.isEmpty()) { return Optional.empty(); }
        // The affected-row check is essential: concurrent readers cannot both accept the same challenge.
        int removed = jdbc.update("DELETE FROM sys_login_captcha WHERE captcha_id=:id AND session_binding_hash=:binding", params);
        return removed == 1 ? Optional.of(rows.get(0)) : Optional.empty();
    }

    @Override public void deleteExpired(Instant now) {
        jdbc.update("DELETE FROM sys_login_captcha WHERE expires_at<=:now", Collections.singletonMap("now", Timestamp.from(now)));
    }
}
