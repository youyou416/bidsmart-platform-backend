package com.bidsmart.platform.auth;

import com.bidsmart.platform.audit.AuditService;
import com.bidsmart.platform.common.ApiException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final LoginRateLimiter limiter;
    private final AuditService audit;
    private final String dummyHash;

    public AuthService(UserRepository users, PasswordEncoder passwords, LoginRateLimiter limiter, AuditService audit) {
        this.users = users; this.passwords = passwords; this.limiter = limiter; this.audit = audit;
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        this.dummyHash = passwords.encode(Base64.getEncoder().encodeToString(bytes));
    }

    public UserAccount authenticate(AuthRequests.Login input, HttpServletRequest request) {
        String ip = AuditService.sourceIp(request);
        if (limiter.limited(input.getUsername(), ip)) {
            audit.record("LOGIN_RATE_LIMITED", null, "DENIED", request);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "登录失败次数过多，请稍后重试");
        }
        Optional<UserAccount> account = users.findByUsername(input.getUsername());
        // Use the stored canonical name for existing accounts so DB collation aliases cannot split a limit bucket.
        String rateAccount = account.map(UserAccount::getUsername).orElse(input.getUsername());
        if (limiter.limited(rateAccount, ip)) {
            audit.record("LOGIN_RATE_LIMITED", null, "DENIED", request);
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED", "登录失败次数过多，请稍后重试");
        }
        boolean valid;
        try { valid = passwords.matches(input.getPassword(), account.map(UserAccount::getPasswordHash).orElse(dummyHash)); }
        catch (IllegalArgumentException error) { valid = false; }
        if (!valid || !account.isPresent() || !account.get().isEnabled()) {
            limiter.failure(rateAccount, ip);
            // Do not persist attacker-supplied usernames, passwords or bodies.
            audit.record("LOGIN_FAILURE", null, "FAILURE", request);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "LOGIN_FAILED", "账号或密码错误");
        }
        limiter.success(rateAccount);
        return account.get();
    }

    @Transactional
    public void changePassword(UserAccount account, AuthRequests.ChangePassword input, HttpServletRequest request) {
        PasswordPolicy.validate(input.getNewPassword());
        if (!passwords.matches(input.getCurrentPassword(), account.getPasswordHash())) {
            audit.record("PASSWORD_CHANGE_DENIED", account.getId(), "DENIED", request);
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_CHANGE_FAILED", "原密码错误");
        }
        if (input.getNewPassword().equals(input.getCurrentPassword())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "PASSWORD_POLICY", "新密码必须不同于原密码");
        }
        if (!users.changePassword(account.getId(), account.getPasswordVersion(), passwords.encode(input.getNewPassword()))) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "账号状态已变化，请重新登录");
        }
        audit.record(account.isPasswordChangeRequired() ? "FIRST_PASSWORD_CHANGED" : "PASSWORD_CHANGED",
                account.getId(), "SUCCESS", request);
    }
}
