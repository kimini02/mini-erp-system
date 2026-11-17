package com.minierp.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minierp.backend.domain.user.dto.PasswordResetRequestDto;
import com.minierp.backend.domain.user.entity.User;
import com.minierp.backend.domain.user.repository.UserRepository;
import com.minierp.backend.domain.user.service.AuthService;
import com.minierp.backend.global.service.PasswordResetStoreService;
import com.minierp.backend.support.MariaDbIntegrationTest;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@AutoConfigureMockMvc
@TestPropertySource(properties = {"app.mail.enabled=true", "app.mail.from=workflow@example.test"})
class PasswordResetRecoveryTest extends MariaDbIntegrationTest {
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired PlatformTransactionManager transactions;
    @Autowired PasswordResetStoreService codes;
    @MockitoBean JavaMailSender sender;
    User user;

    @BeforeEach
    void account() {
        user = users.saveAndFlush(User.create("Reset Fixture", "reset-" + UUID.randomUUID(),
                UUID.randomUUID() + "@example.test", "unused-fixture-hash", "01", "사원"));
    }

    private PasswordResetRequestDto body() throws Exception {
        return mapper.readValue(mapper.writeValueAsString(Map.of("email", user.getUserEmail())),
                PasswordResetRequestDto.class);
    }

    private org.springframework.mock.web.MockHttpServletResponse request(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/password/reset/request").contentType("application/json")
                        .content(mapper.writeValueAsString(Map.of("email", email))))
                .andReturn().getResponse();
    }

    @Test
    void matchingAndAbsentRequestsHaveIdenticalResponsesAndOnlyMatchedSendsStoredCode() throws Exception {
        var absent = request(UUID.randomUUID() + "@example.test");
        verifyNoInteractions(sender);
        var matched = request(user.getUserEmail());
        assertThat(matched.getStatus()).isEqualTo(200);
        assertThat(absent.getStatus()).isEqualTo(200);
        assertThat(matched.getContentAsString()).isEqualTo(absent.getContentAsString());
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, timeout(2000).times(1)).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly(user.getUserEmail());
        String code = codes.getVerificationCode(user.getUserEmail());
        assertThat(code).matches("[0-9]{6}");
        assertThat(message.getValue().getText()).contains(code);
    }

    @Test
    void resetMailWaitsForCommitWhileCodeStorageIsUnchanged() throws Exception {
        var request = body();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            auth.requestPasswordReset(request);
            assertThat(codes.getVerificationCode(user.getUserEmail())).matches("[0-9]{6}");
            verify(sender, after(100).never()).send(any(SimpleMailMessage.class));
        });
        verify(sender, timeout(2000).times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void rolledBackResetDoesNotSendMail() throws Exception {
        var request = body();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            auth.requestPasswordReset(request);
            status.setRollbackOnly();
        });
        verify(sender, after(200).never()).send(any(SimpleMailMessage.class));
        // Existing Redis storage is outside the DB transaction and intentionally unchanged.
        assertThat(codes.getVerificationCode(user.getUserEmail())).matches("[0-9]{6}");
    }

    @Test
    void smtpWaitDoesNotHoldResetHttpResponse() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        doAnswer(call -> {
            entered.countDown();
            try {
                if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("fixture gate timed out");
            } finally {
                finished.countDown();
            }
            return null;
        }).when(sender).send(any(SimpleMailMessage.class));
        var executor = Executors.newSingleThreadExecutor();
        try {
            var response = executor.submit(() -> request(user.getUserEmail()));
            assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();
            assertThat(response.get(2, TimeUnit.SECONDS).getStatus()).isEqualTo(200);
            assertThat(finished.getCount()).isEqualTo(1);
        } finally {
            release.countDown();
            executor.shutdownNow();
            finished.await(2, TimeUnit.SECONDS);
        }
    }

    @Test
    void smtpFailureDoesNotExposeWhetherResetAccountExists() throws Exception {
        doThrow(new MailSendException("fixture SMTP failure")).when(sender).send(any(SimpleMailMessage.class));
        var matched = request(user.getUserEmail());
        var absent = request(UUID.randomUUID() + "@example.test");
        assertThat(matched.getStatus()).isEqualTo(200);
        assertThat(absent.getStatus()).isEqualTo(200);
        assertThat(matched.getContentAsString()).isEqualTo(absent.getContentAsString());
        verify(sender, timeout(2000).times(1)).send(any(SimpleMailMessage.class));
    }

    @Test
    void resetEmailLimitStillRejectsSixthRequestWithoutReplacingCodeOrSending() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(request(user.getUserEmail()).getStatus()).isEqualTo(200);
        }
        verify(sender, timeout(2000).times(5)).send(any(SimpleMailMessage.class));
        String before = codes.getVerificationCode(user.getUserEmail());
        var limited = request(user.getUserEmail());
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getContentAsString()).contains("TOO_MANY_REQUESTS");
        assertThat(codes.getVerificationCode(user.getUserEmail())).isEqualTo(before);
        verify(sender, after(100).times(5)).send(any(SimpleMailMessage.class));
    }
}
