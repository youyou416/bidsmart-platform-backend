package com.bidsmart.platform.captcha;

import com.bidsmart.platform.audit.AuditService;
import com.bidsmart.platform.common.ApiException;
import com.bidsmart.platform.crypto.Sm3;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.crypto.macs.HMac;
import org.bouncycastle.crypto.params.KeyParameter;
import org.bouncycastle.util.encoders.Hex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public final class CaptchaService {
    private static final Logger LOG = LoggerFactory.getLogger(CaptchaService.class);
    private static final String SESSION_KEY = CaptchaService.class.getName();
    private final CaptchaRepository repository;
    private final CaptchaImageRenderer renderer;
    private final CaptchaProperties properties;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();
    private final Map<String, Window> windows = new HashMap<>();

    public CaptchaService(CaptchaRepository repository, CaptchaImageRenderer renderer, CaptchaProperties properties, Clock clock) {
        this.repository = repository; this.renderer = renderer; this.properties = properties; this.clock = clock;
    }

    public CaptchaChallenge issue(HttpServletRequest request) {
        checkRate(AuditService.sourceIp(request));
        State state = state(request.getSession());
        synchronized (state) {
            byte[] bytes = new byte[24]; random.nextBytes(bytes);
            String id = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            StringBuilder digits = new StringBuilder(4);
            for (int i = 0; i < 4; i++) { digits.append(random.nextInt(10)); }
            String code = digits.toString();
            String image = renderer.render(code);
            Instant now = clock.instant();
            Instant expires = now.plus(properties.getTtl());
            repository.replace(new CaptchaRecord(id, binding(state), answerHash(state, id, code), now, expires));
            state.currentId = id;
            return new CaptchaChallenge(id, image, expires, properties.getTtl().getSeconds());
        }
    }

    public void verify(String id, String code, HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        State state = session == null ? null : (State) session.getAttribute(SESSION_KEY);
        if (state == null) { throw invalid(); }
        synchronized (state) {
            String current = state.currentId;
            state.currentId = null;
            if (current == null) { throw invalid(); }
            // Consume our own latest challenge even on a wrong ID/code; never delete another session's row.
            Optional<CaptchaRecord> consumed = repository.consume(current, binding(state));
            String normalized = code == null ? "" : code.trim();
            if (!current.equals(id) || !normalized.matches("[0-9]{4}") || !consumed.isPresent()
                    || !clock.instant().isBefore(consumed.get().getExpiresAt())
                    || !MessageDigest.isEqual(answerHash(state, current, normalized).getBytes(StandardCharsets.US_ASCII),
                            consumed.get().getAnswerHash().getBytes(StandardCharsets.US_ASCII))) {
                throw invalid();
            }
        }
    }

    private State state(HttpSession session) {
        synchronized (session) {
            State state = (State) session.getAttribute(SESSION_KEY);
            if (state == null) {
                byte[] secret = new byte[32]; random.nextBytes(secret);
                state = new State(secret); session.setAttribute(SESSION_KEY, state);
            }
            return state;
        }
    }
    private String binding(State state) { return Hex.toHexString(Sm3.digest(state.secret)); }
    private String answerHash(State state, String id, String code) {
        HMac mac = new HMac(new SM3Digest()); mac.init(new KeyParameter(state.secret));
        byte[] input = (id + ":" + code).getBytes(StandardCharsets.US_ASCII);
        mac.update(input, 0, input.length); byte[] result = new byte[mac.getMacSize()]; mac.doFinal(result, 0);
        return Hex.toHexString(result);
    }
    private synchronized void checkRate(String ip) {
        Instant now = clock.instant();
        windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().until));
        Window window = windows.get(ip);
        if (window == null) {
            if (windows.size() >= 20000) { throw limited(); }
            window = new Window(now.plusSeconds(60)); windows.put(ip, window);
        }
        if (window.count >= properties.getRequestsPerMinute()) { throw limited(); }
        window.count++;
    }
    private ApiException invalid() { return new ApiException(HttpStatus.BAD_REQUEST, "CAPTCHA_INVALID", "验证码错误或已失效，请刷新后重试"); }
    private ApiException limited() { return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "验证码获取过于频繁，请稍后重试"); }
    @Scheduled(fixedDelay = 60000, initialDelay = 60000)
    public void cleanup() {
        try { repository.deleteExpired(clock.instant()); }
        catch (RuntimeException error) { LOG.error("event=captcha_cleanup_failed exceptionType={}", error.getClass().getSimpleName()); }
    }
    private static final class State implements Serializable {
        private static final long serialVersionUID = 1L;
        private final byte[] secret;
        private String currentId;
        private State(byte[] secret) { this.secret = secret; }
    }
    private static final class Window {
        private final Instant until;
        private int count;
        private Window(Instant until) { this.until = until; }
    }
}
