package com.minierp.backend.integration;

import com.minierp.backend.global.config.MailConfig;
import com.minierp.backend.global.config.ProdSecretsConfig;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import static org.assertj.core.api.Assertions.assertThat;

/** Loads real prod configuration. SMTP probes use a loopback-only, non-speaking socket. */
class ProdMailConfigTest {
    private Map<String, Object> settings() {
        Map<String, Object> settings = new LinkedHashMap<>();
        for (String key : List.of("DB_APP_USERNAME", "DB_APP_PASSWORD", "DB_MIGRATOR_USERNAME",
                "DB_MIGRATOR_PASSWORD", "JWT_SECRET")) {
            settings.put(key, UUID.randomUUID().toString());
        }
        settings.put("APP_MAIL_ENABLED", "true");
        settings.put("SMTP_HOST", "smtp.example.test");
        settings.put("SMTP_PORT", "587");
        settings.put("SMTP_USERNAME", "test-sender@example.test");
        settings.put("SMTP_PASSWORD", UUID.randomUUID().toString());
        settings.put("APP_MAIL_FROM", "workflow@example.test");
        settings.put("APP_INVITATION_SETUP_URL", "https://workflow.example.test/password/setup");
        return settings;
    }

    private ApplicationContextRunner runner(Map<String, Object> settings) {
        return new ApplicationContextRunner()
                .withInitializer(context -> {
                    var environment = context.getEnvironment();
                    environment.setActiveProfiles("prod");
                    environment.getPropertySources().remove("systemEnvironment");
                    environment.getPropertySources().remove("systemProperties");
                    environment.getPropertySources().addFirst(new MapPropertySource("test-settings", settings));
                    try {
                        environment.getPropertySources().addLast(
                                new ResourcePropertySource("classpath:application-prod.properties"));
                    } catch (java.io.IOException exception) {
                        throw new java.io.UncheckedIOException(exception);
                    }
                })
                .withUserConfiguration(ProdSecretsConfig.class, MailConfig.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SMTP_HOST", "SMTP_PORT", "SMTP_USERNAME", "SMTP_PASSWORD", "APP_MAIL_FROM",
            "APP_INVITATION_SETUP_URL"})
    void enabledMailRejectsMissingSetting(String key) {
        var settings = settings();
        settings.remove(key);
        runner(settings).run(context -> assertThat(context.getStartupFailure())
                .hasStackTraceContaining("Required production setting is missing: " + key));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SMTP_HOST", "SMTP_PORT", "SMTP_USERNAME", "SMTP_PASSWORD", "APP_MAIL_FROM",
            "APP_INVITATION_SETUP_URL"})
    void enabledMailRejectsBlankSetting(String key) {
        var settings = settings();
        settings.put(key, " ");
        runner(settings).run(context -> assertThat(context.getStartupFailure())
                .hasStackTraceContaining("Required production setting is missing: " + key));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost:5173/password/setup", "https://LOCALHOST/password/setup",
            "https://localhost./password/setup", "https://127.0.0.1/password/setup",
            "https://[::1]/password/setup", "/password/setup", "http://workflow.example.test/password/setup"})
    void enabledMailRejectsLocalOrNonHttpsInvitation(String url) {
        var settings = settings();
        settings.put("APP_INVITATION_SETUP_URL", url);
        runner(settings).run(context -> assertThat(context.getStartupFailure())
                .hasStackTraceContaining("Invalid production setting: APP_INVITATION_SETUP_URL"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "65536", "invalid"})
    void enabledMailRejectsInvalidPort(String port) {
        var settings = settings();
        settings.put("SMTP_PORT", port);
        runner(settings).run(context -> assertThat(context.getStartupFailure())
                .hasStackTraceContaining("Invalid production setting: SMTP_PORT"));
    }

    @Test
    void enabledMailUsesEnvironmentSettingsWithoutSending() {
        var settings = settings();
        runner(settings).run(context -> {
            assertThat(context).hasNotFailed();
            var sender = context.getBean(JavaMailSenderImpl.class);
            assertThat(sender.getHost()).isEqualTo(settings.get("SMTP_HOST"));
            assertThat(sender.getPort()).isEqualTo(587);
            assertThat(sender.getUsername()).isEqualTo(settings.get("SMTP_USERNAME"));
            // Compare secrets without including either value in assertion failure output.
            assertThat(settings.get("SMTP_PASSWORD").equals(sender.getPassword())).isTrue();
            assertThat(context.getEnvironment().getProperty("app.mail.from")).isEqualTo(settings.get("APP_MAIL_FROM"));
            assertThat(context.getEnvironment().getProperty("app.invitation.setup-url"))
                    .isEqualTo(settings.get("APP_INVITATION_SETUP_URL"));
        });
    }

    @Test
    void disabledMailCanStartWithoutSmtpSettings() {
        var settings = settings();
        settings.keySet().removeIf(key -> key.startsWith("SMTP_") || key.equals("APP_MAIL_FROM")
                || key.equals("APP_INVITATION_SETUP_URL"));
        settings.put("APP_MAIL_ENABLED", "false");
        runner(settings).run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void productionSmtpTimeoutsDefaultToFiveSeconds() {
        runner(settings()).run(context -> {
            var properties = context.getBean(JavaMailSenderImpl.class).getJavaMailProperties();
            assertThat(properties.getProperty("mail.smtp.connectiontimeout")).isEqualTo("5000");
            assertThat(properties.getProperty("mail.smtp.timeout")).isEqualTo("5000");
            assertThat(properties.getProperty("mail.smtp.writetimeout")).isEqualTo("5000");
        });
    }

    @Test
    void productionSmtpTimeoutsAcceptEnvironmentOverrides() {
        var settings = settings();
        settings.put("SMTP_CONNECTION_TIMEOUT_MS", "1100");
        settings.put("SMTP_READ_TIMEOUT_MS", "1200");
        settings.put("SMTP_WRITE_TIMEOUT_MS", "1300");
        runner(settings).run(context -> {
            var properties = context.getBean(JavaMailSenderImpl.class).getJavaMailProperties();
            assertThat(properties.getProperty("mail.smtp.connectiontimeout")).isEqualTo("1100");
            assertThat(properties.getProperty("mail.smtp.timeout")).isEqualTo("1200");
            assertThat(properties.getProperty("mail.smtp.writetimeout")).isEqualTo("1300");
        });
    }

    @Test
    void silentLocalSmtpFailsWithinReadTimeout() throws Exception {
        var accepted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var failure = new AtomicReference<Throwable>();
        var workers = Executors.newFixedThreadPool(2);
        try (var server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            server.setSoTimeout(5000);
            var peer = workers.submit(() -> {
                try (var socket = server.accept()) {
                    accepted.countDown();
                    // Never send a greeting, accept credentials or deliver a message.
                    release.await(5, TimeUnit.SECONDS);
                } catch (Exception exception) {
                    throw new RuntimeException(exception);
                }
            });
            var settings = settings();
            settings.put("SMTP_HOST", "127.0.0.1");
            settings.put("SMTP_PORT", Integer.toString(server.getLocalPort()));
            settings.put("SMTP_CONNECTION_TIMEOUT_MS", "300");
            settings.put("SMTP_READ_TIMEOUT_MS", "350");
            settings.put("SMTP_WRITE_TIMEOUT_MS", "400");
            runner(settings).run(context -> {
                var sender = context.getBean(JavaMailSenderImpl.class);
                var message = new SimpleMailMessage();
                message.setFrom("sender@example.test");
                message.setTo("recipient@example.test");
                message.setText("Local timeout fixture");
                long started = System.nanoTime();
                var sending = workers.submit(() -> {
                    try {
                        sender.send(message);
                    } catch (Throwable exception) {
                        failure.set(exception);
                    } finally {
                        finished.countDown();
                    }
                });
                try {
                    assertThat(accepted.await(2, TimeUnit.SECONDS)).isTrue();
                    assertThat(finished.await(2, TimeUnit.SECONDS))
                            .as("SMTP send must finish while the silent socket remains open").isTrue();
                    assertThat(failure.get()).isInstanceOf(MailSendException.class)
                            .hasRootCauseInstanceOf(SocketTimeoutException.class);
                    System.out.printf("SMTP_SILENT_PROBE elapsed_ms=%d result=READ_TIMEOUT%n",
                            TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                } finally {
                    release.countDown();
                    sending.get(3, TimeUnit.SECONDS);
                }
            });
            peer.get(3, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            workers.shutdownNow();
            workers.awaitTermination(3, TimeUnit.SECONDS);
        }
    }

}
