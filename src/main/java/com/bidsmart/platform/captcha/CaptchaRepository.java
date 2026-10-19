package com.bidsmart.platform.captcha;

import java.time.Instant;
import java.util.Optional;

public interface CaptchaRepository {
    void replace(CaptchaRecord record);
    /** Atomically consume once, only when both the challenge and session binding match. */
    Optional<CaptchaRecord> consume(String id, String binding);
    void deleteExpired(Instant now);
}
