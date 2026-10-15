package com.bidsmart.platform.auth;

import java.io.Serializable;
import java.security.Principal;
import java.time.Instant;

/** No password, password hash, CSRF token or database credentials belong in a session principal. */
public final class SessionPrincipal implements Principal, Serializable {
    private static final long serialVersionUID = 1L;
    private final long userId;
    private final String username;
    private final long passwordVersion;
    private final Instant authenticatedAt;

    public SessionPrincipal(UserAccount account, Instant authenticatedAt) {
        this.userId = account.getId(); this.username = account.getUsername();
        this.passwordVersion = account.getPasswordVersion(); this.authenticatedAt = authenticatedAt;
    }
    public long getUserId() { return userId; }
    @Override public String getName() { return username; }
    public long getPasswordVersion() { return passwordVersion; }
    public Instant getAuthenticatedAt() { return authenticatedAt; }
}
