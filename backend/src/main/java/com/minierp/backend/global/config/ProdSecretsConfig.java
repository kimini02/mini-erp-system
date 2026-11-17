package com.minierp.backend.global.config;

import com.minierp.backend.global.security.PasswordPolicy;
import org.springframework.util.StringUtils;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;

/** Fail before creating any DB/Redis/SMTP clients. Never include values in diagnostics. */
@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ProdSecretsConfig {
    @Bean
    static BeanFactoryPostProcessor requiredProductionSecrets(Environment environment) {
        return factory -> {
            for (String key : List.of("DB_APP_USERNAME", "DB_APP_PASSWORD", "DB_MIGRATOR_USERNAME",
                    "DB_MIGRATOR_PASSWORD", "JWT_SECRET")) {
                requireSetting(environment, key);
            }
            var initialAdminKeys = List.of("INITIAL_ADMIN_LOGIN_ID", "INITIAL_ADMIN_NAME", "INITIAL_ADMIN_EMAIL",
                    "INITIAL_ADMIN_PASSWORD");
            // clear-initial-password.sh intentionally leaves the other three values in place.
            if (initialAdminKeys.stream().allMatch(key -> StringUtils.hasText(environment.getProperty(key)))
                    && !PasswordPolicy.accepts(environment.getProperty("INITIAL_ADMIN_PASSWORD"))) {
                throw new IllegalStateException("Invalid production setting: INITIAL_ADMIN_PASSWORD");
            }
            if (environment.getProperty("app.mail.enabled", Boolean.class, false)) {
                for (String key : List.of("SMTP_HOST", "SMTP_PORT", "SMTP_USERNAME", "SMTP_PASSWORD",
                        "APP_MAIL_FROM", "APP_INVITATION_SETUP_URL")) {
                    requireSetting(environment, key);
                }
                validatePort(environment.getProperty("SMTP_PORT"));
                validateInvitationUrl(environment.getProperty("app.invitation.setup-url"));
            }
        };
    }

    private static void requireSetting(Environment environment, String key) {
        String value = environment.getProperty(key);
        if (value == null || value.isBlank() || value.equals("CHANGE_ME")) {
            throw new IllegalStateException("Required production setting is missing: " + key);
        }
    }

    private static void validatePort(String value) {
        try {
            int port = Integer.parseInt(value);
            if (port >= 1 && port <= 65535) {
                return;
            }
        } catch (NumberFormatException ignored) {
            // Do not attach the original exception: its message contains the supplied value.
        }
        throw new IllegalStateException("Invalid production setting: SMTP_PORT");
    }

    private static void validateInvitationUrl(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            if (host != null) {
                host = host.toLowerCase(Locale.ROOT).replaceAll("\\.$", "");
                boolean local = host.equals("localhost") || host.endsWith(".localhost")
                        || host.startsWith("127.") || host.equals("[::1]") || host.equals("0.0.0.0");
                if ("https".equalsIgnoreCase(uri.getScheme()) && !local && uri.getUserInfo() == null) {
                    return;
                }
            }
        } catch (IllegalArgumentException ignored) {
            // Never echo a configured URL or its parse exception.
        }
        throw new IllegalStateException("Invalid production setting: APP_INVITATION_SETUP_URL");
    }
}
