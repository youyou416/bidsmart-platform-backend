package com.bidsmart.platform.captcha;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Validates affected-row and binding semantics; actual OceanBase execution is a separate check. */
class JdbcCaptchaRepositoryTest {
    @Test void competingDeleteCannotReusePreviouslySelectedAnswer() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        CaptchaRecord row = new CaptchaRecord("synthetic-id", "synthetic-binding", "digest", Instant.EPOCH, Instant.EPOCH.plusSeconds(120));
        when(jdbc.query(anyString(), anyMap(), org.mockito.ArgumentMatchers.<RowMapper<CaptchaRecord>>any())).thenReturn(List.of(row));
        when(jdbc.update(anyString(), anyMap())).thenReturn(0);
        assertThat(new JdbcCaptchaRepository(jdbc).consume(row.getId(), row.getBinding())).isEmpty();
        verify(jdbc).update(contains("captcha_id=:id AND session_binding_hash=:binding"), eq(Map.of("id", row.getId(), "binding", row.getBinding())));
    }
    @Test void onlySuccessfulAtomicDeletionReturnsAnswer() {
        NamedParameterJdbcTemplate jdbc = mock(NamedParameterJdbcTemplate.class);
        CaptchaRecord row = new CaptchaRecord("synthetic-id", "synthetic-binding", "digest", Instant.EPOCH, Instant.EPOCH.plusSeconds(120));
        when(jdbc.query(anyString(), anyMap(), org.mockito.ArgumentMatchers.<RowMapper<CaptchaRecord>>any())).thenReturn(List.of(row));
        when(jdbc.update(anyString(), anyMap())).thenReturn(1).thenReturn(0);
        JdbcCaptchaRepository repository = new JdbcCaptchaRepository(jdbc);
        assertThat(repository.consume(row.getId(), row.getBinding())).contains(row);
        assertThat(repository.consume(row.getId(), row.getBinding())).isEmpty();
    }
}
