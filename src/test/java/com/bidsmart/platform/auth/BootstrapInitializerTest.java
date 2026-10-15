package com.bidsmart.platform.auth;

import com.bidsmart.platform.audit.AuditEvent;
import com.bidsmart.platform.audit.AuditRepository;
import com.bidsmart.platform.audit.AuditService;
import com.bidsmart.platform.config.PlatformProperties;
import com.bidsmart.platform.crypto.Sm3PasswordEncoder;
import java.time.Clock;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BootstrapInitializerTest {
    @Test void defaultInitializationDoesNothing() {
        UserRepository users = mock(UserRepository.class); AuditRepository audit = mock(AuditRepository.class);
        new BootstrapInitializer(new PlatformProperties(), users, new Sm3PasswordEncoder(1000),
                new AuditService(audit, new PlatformProperties(), Clock.systemUTC()))
                .run(new DefaultApplicationArguments(new String[0]));
        verifyNoInteractions(users, audit);
    }
    @Test void explicitInitializationRequiresProvidedUsernameAndLongPassword() {
        PlatformProperties p = new PlatformProperties(); p.setBootstrapEnabled(true);
        UserRepository users = mock(UserRepository.class); AuditRepository repository = mock(AuditRepository.class);
        BootstrapInitializer initializer = new BootstrapInitializer(p, users, new Sm3PasswordEncoder(1000),
                new AuditService(repository, p, Clock.systemUTC()));
        assertThatThrownBy(() -> initializer.run(new DefaultApplicationArguments(new String[0])))
                .hasMessageContaining("BOOTSTRAP_ADMIN_USERNAME");
        p.setBootstrapUsername("synthetic"); p.setBootstrapPassword("short");
        assertThatThrownBy(() -> initializer.run(new DefaultApplicationArguments(new String[0])))
                .hasMessageContaining("12 至 128");
        verifyNoInteractions(users, repository);
    }
    @Test void createsOnlyViaExplicitAdditiveRepositoryContractAndNeverOverwritesExisting() {
        PlatformProperties p = new PlatformProperties(); p.setBootstrapEnabled(true);
        p.setBootstrapUsername("synthetic"); p.setBootstrapPassword("合成现场初始化 password 42");
        UserRepository users = mock(UserRepository.class); AuditRepository repository = mock(AuditRepository.class);
        Sm3PasswordEncoder passwords = new Sm3PasswordEncoder(1000);
        BootstrapInitializer initializer = new BootstrapInitializer(p, users, passwords,
                new AuditService(repository, p, Clock.systemUTC()));
        when(users.createBootstrap(eq("synthetic"), anyString(), eq(1001L), eq(1001L))).thenReturn(false);
        initializer.run(new DefaultApplicationArguments(new String[0]));
        verifyNoInteractions(repository);
        ArgumentCaptor<String> hash = ArgumentCaptor.forClass(String.class);
        verify(users).createBootstrap(eq("synthetic"), hash.capture(), eq(1001L), eq(1001L));
        assertThat(hash.getValue()).doesNotContain(p.getBootstrapPassword());
        assertThat(passwords.matches(p.getBootstrapPassword(), hash.getValue())).isTrue();
    }
}
