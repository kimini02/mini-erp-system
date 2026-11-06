package com.minierp.backend.global.security;

import com.minierp.backend.global.exception.*;

public final class PasswordPolicy {
    private PasswordPolicy() {
    }

    public static boolean accepts(String value) {
        return value != null && value.length() >= 8 && value.length() <= 20 && value.matches(".*[A-Z].*")
                && value.matches(".*[a-z].*") && value.matches(".*[0-9].*")
                && value.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>/?].*");
    }

    public static void validate(String value) {
        if (!accepts(value)) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION);
        }
    }
}
