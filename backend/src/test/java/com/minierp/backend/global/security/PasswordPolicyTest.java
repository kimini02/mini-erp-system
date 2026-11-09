package com.minierp.backend.global.security;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class PasswordPolicyTest {
    @Test
    void usesExistingEightToTwentyCharacterPolicy() {
        assertThat(PasswordPolicy.accepts("Aa1!")).isFalse();
        assertThat(PasswordPolicy.accepts("Aa1!" + "x".repeat(17))).isFalse();
        assertThat(PasswordPolicy.accepts("Aa1!" + "x".repeat(4))).isTrue();
        assertThat(PasswordPolicy.accepts("Aa1!" + "x".repeat(16))).isTrue();
        assertThat(PasswordPolicy.accepts("aa1!xxxx")).isFalse();
        assertThat(PasswordPolicy.accepts(null)).isFalse();
    }
}
