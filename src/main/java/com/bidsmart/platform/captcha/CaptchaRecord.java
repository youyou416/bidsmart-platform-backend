package com.bidsmart.platform.captcha;

import java.time.Instant;

/** Contains digests only; never a clear answer or an HTTP session identifier. */
public final class CaptchaRecord {
    private final String id;
    private final String binding;
    private final String answerHash;
    private final Instant createdAt;
    private final Instant expiresAt;

    public CaptchaRecord(String id, String binding, String answerHash, Instant createdAt, Instant expiresAt) {
        this.id = id; this.binding = binding; this.answerHash = answerHash;
        this.createdAt = createdAt; this.expiresAt = expiresAt;
    }
    public String getId() { return id; }
    public String getBinding() { return binding; }
    public String getAnswerHash() { return answerHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
}
