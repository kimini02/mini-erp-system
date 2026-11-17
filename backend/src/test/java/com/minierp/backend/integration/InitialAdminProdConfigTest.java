package com.minierp.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minierp.backend.global.config.ProdSecretsConfig;
import com.minierp.backend.global.security.PasswordPolicy;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

class InitialAdminProdConfigTest {
    static Stream<Arguments> policyCases() throws Exception {
        try (var input = InitialAdminProdConfigTest.class.getResourceAsStream("/onboarding/password-policy-cases.json")) {
            var cases = new ObjectMapper().readTree(input);
            var arguments = new ArrayList<Arguments>();
            cases.forEach(item -> arguments.add(Arguments.of(item.path("label").asText(),
                    item.path("value").asText(), item.path("accepted").asBoolean())));
            return arguments.stream();
        }
    }

    private Map<String, Object> settings(String password) {
        var values = new LinkedHashMap<String, Object>();
        for (String key : List.of("DB_APP_USERNAME", "DB_APP_PASSWORD", "DB_MIGRATOR_USERNAME",
                "DB_MIGRATOR_PASSWORD", "JWT_SECRET")) {
            values.put(key, UUID.randomUUID().toString());
        }
        values.put("INITIAL_ADMIN_LOGIN_ID", "fixture-admin");
        values.put("INITIAL_ADMIN_NAME", "Fixture");
        values.put("INITIAL_ADMIN_EMAIL", "fixture@example.test");
        values.put("INITIAL_ADMIN_PASSWORD", password);
        values.put("app.mail.enabled", "false");
        return values;
    }

    private ApplicationContextRunner runner(String profile, Map<String, Object> settings) {
        return new ApplicationContextRunner().withInitializer(context -> {
            var environment = context.getEnvironment();
            environment.setActiveProfiles(profile);
            environment.getPropertySources().remove("systemEnvironment");
            environment.getPropertySources().remove("systemProperties");
            environment.getPropertySources().addFirst(new MapPropertySource("fixture", settings));
        }).withUserConfiguration(ProdSecretsConfig.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("policyCases")
    void productionMatchesBackendPolicyWithoutEchoingValues(String label, String value, boolean accepted) {
        assertThat(PasswordPolicy.accepts(value)).as("backend policy: %s", label).isEqualTo(accepted);
        var settings = settings(value);
        runner("prod", settings).run(context -> {
            if (accepted) {
                assertThat(context).hasNotFailed();
            } else {
                assertThat(context.getStartupFailure()).isNotNull();
                var failure = context.getStartupFailure();
                assertThat(failure).hasStackTraceContaining("INITIAL_ADMIN_PASSWORD");
                for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
                    for (var configured : settings.values()) {
                        assertThat(String.valueOf(cause.getMessage()).contains(configured.toString()))
                                .as("startup error must not echo configured values").isFalse();
                    }
                }
            }
        });
    }

    @Test
    void allInitialAdminValuesAbsentAllowsRestart() {
        var settings = settings("unused");
        settings.keySet().removeIf(key -> key.startsWith("INITIAL_ADMIN_"));
        runner("prod", settings).run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"LOGIN_ID", "NAME", "EMAIL", "PASSWORD"})
    void partialConfigurationKeepsExistingSkipBehavior(String missing) {
        var settings = settings("invalid-policy-fixture");
        settings.remove("INITIAL_ADMIN_" + missing);
        runner("prod", settings).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void blankInitialAdminPasswordKeepsExistingSkipBehavior() {
        var settings = settings(" ");
        runner("prod", settings).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void localInvalidPasswordDoesNotEnableProductionFailFast() {
        runner("local", settings("invalid-policy-fixture")).run(context -> assertThat(context).hasNotFailed());
    }
}
