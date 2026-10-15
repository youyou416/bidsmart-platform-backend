package com.bidsmart.platform.auth;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PasswordPolicyTest {
    @Test void unicodeAndSpacesArePreservedAndCountedAsCodePoints() {
        assertThatCode(() -> PasswordPolicy.validate("  合成安全密码abcdefgh  ")).doesNotThrowAnyException();
        assertThatCode(() -> PasswordPolicy.validate("😀".repeat(128))).doesNotThrowAnyException();
        assertThatThrownBy(() -> PasswordPolicy.validate("😀".repeat(129))).isInstanceOf(RuntimeException.class);
    }
    @Test void malformedAndShortPasswordsAreRejected() {
        assertThatThrownBy(() -> PasswordPolicy.validate("short")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("twelvecharacters\uD800")).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> PasswordPolicy.validate("twelvecharacters\0")).isInstanceOf(RuntimeException.class);
    }
}
