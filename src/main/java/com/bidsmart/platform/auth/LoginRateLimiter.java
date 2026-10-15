package com.bidsmart.platform.auth;

import com.bidsmart.platform.config.PlatformProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public final class LoginRateLimiter {
    private static final int MAX_KEYS = 20000;
    private final Map<String, Bucket> failures = new HashMap<>();
    private final Map<String, Bucket> csrf = new HashMap<>();
    private final PlatformProperties properties;
    private final Clock clock;

    public LoginRateLimiter(PlatformProperties properties, Clock clock) {
        this.properties = properties; this.clock = clock;
    }

    public synchronized boolean limited(String account, String ip) {
        cleanup(failures, properties.getFailureWindow().plus(properties.getLockout()));
        return failures.size() >= MAX_KEYS || locked(accountKey(account)) || locked("ip:" + ip);
    }

    public synchronized void failure(String account, String ip) {
        fail(accountKey(account)); fail("ip:" + ip);
    }

    public synchronized void success(String account) {
        failures.remove(accountKey(account));
    }

    public synchronized boolean csrfAllowed(String ip) {
        cleanup(csrf, Duration.ofMinutes(1));
        if (csrf.size() >= MAX_KEYS && !csrf.containsKey(ip)) { return false; }
        Instant now = clock.instant();
        Bucket bucket = csrf.get(ip);
        if (bucket == null || !now.isBefore(bucket.first.plus(Duration.ofMinutes(1)))) {
            bucket = new Bucket(now); csrf.put(ip, bucket);
        }
        return ++bucket.count <= properties.getCsrfRequestsPerMinute();
    }

    private String accountKey(String account) { return "account:" + account.toLowerCase(Locale.ROOT); }
    private boolean locked(String key) {
        Bucket bucket = failures.get(key);
        return bucket != null && bucket.lockedUntil != null && clock.instant().isBefore(bucket.lockedUntil);
    }
    private void fail(String key) {
        Instant now = clock.instant();
        Bucket bucket = failures.get(key);
        if (bucket == null || !now.isBefore(bucket.first.plus(properties.getFailureWindow()))
                && (bucket.lockedUntil == null || !now.isBefore(bucket.lockedUntil))) {
            bucket = new Bucket(now); failures.put(key, bucket);
        }
        if (++bucket.count >= properties.getMaxLoginFailures()) { bucket.lockedUntil = now.plus(properties.getLockout()); }
    }
    private void cleanup(Map<String, Bucket> map, Duration maxAge) {
        Instant now = clock.instant();
        Iterator<Bucket> iterator = map.values().iterator();
        while (iterator.hasNext()) {
            Bucket bucket = iterator.next();
            if (!now.isBefore(bucket.first.plus(maxAge))
                    && (bucket.lockedUntil == null || !now.isBefore(bucket.lockedUntil))) { iterator.remove(); }
        }
    }
    private static final class Bucket {
        private final Instant first;
        private int count;
        private Instant lockedUntil;
        private Bucket(Instant first) { this.first = first; }
    }
}
