package com.bidsmart.platform.captcha;

import java.time.Instant;

public final class CaptchaChallenge {
    private final String captchaId;
    private final String imageDataUrl;
    private final Instant expiresAt;
    private final long expiresInSeconds;
    public CaptchaChallenge(String captchaId, String imageDataUrl, Instant expiresAt, long expiresInSeconds) {
        this.captchaId = captchaId; this.imageDataUrl = imageDataUrl;
        this.expiresAt = expiresAt; this.expiresInSeconds = expiresInSeconds;
    }
    public String getCaptchaId() { return captchaId; }
    public String getImageDataUrl() { return imageDataUrl; }
    public Instant getExpiresAt() { return expiresAt; }
    public long getExpiresInSeconds() { return expiresInSeconds; }
}
