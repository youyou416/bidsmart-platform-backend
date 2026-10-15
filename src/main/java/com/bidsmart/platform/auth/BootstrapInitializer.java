package com.bidsmart.platform.auth;

import com.bidsmart.platform.audit.AuditService;
import com.bidsmart.platform.config.PlatformProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Explicit, additive account creation only; never creates databases/tables or replaces an existing account. */
@Component
public class BootstrapInitializer implements ApplicationRunner {
    private final PlatformProperties properties;
    private final UserRepository users;
    private final PasswordEncoder passwords;
    private final AuditService audit;
    public BootstrapInitializer(PlatformProperties properties, UserRepository users,
                                PasswordEncoder passwords, AuditService audit) {
        this.properties = properties; this.users = users; this.passwords = passwords; this.audit = audit;
    }
    @Override @Transactional public void run(ApplicationArguments args) {
        if (!properties.isBootstrapEnabled()) { return; }
        if (properties.getBootstrapUsername() == null || properties.getBootstrapUsername().trim().isEmpty()
                || properties.getBootstrapUsername().length() > 128) {
            throw new IllegalStateException("BOOTSTRAP_ADMIN_USERNAME is required for explicit initialization");
        }
        PasswordPolicy.validate(properties.getBootstrapPassword());
        if (users.createBootstrap(properties.getBootstrapUsername(), passwords.encode(properties.getBootstrapPassword()),
                properties.getBootstrapRoleId(), properties.getBootstrapDepartmentId())) {
            UserAccount account = users.findByUsername(properties.getBootstrapUsername())
                    .orElseThrow(() -> new IllegalStateException("Created account cannot be read"));
            audit.record("BOOTSTRAP_CREATED", account.getId(), "SUCCESS", null);
        }
    }
}
