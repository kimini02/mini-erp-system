package com.minierp.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minierp.backend.domain.user.dto.FindIdRequestDto;
import com.minierp.backend.domain.user.entity.User;
import com.minierp.backend.domain.user.repository.UserRepository;
import com.minierp.backend.domain.user.service.AuthService;
import com.minierp.backend.support.MariaDbIntegrationTest;
import java.time.Duration;
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
import org.springframework.data.redis.core.StringRedisTemplate;
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
class FindIdRecoveryTest extends MariaDbIntegrationTest {
    @Container
    static final GenericContainer<?> redis =
            new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired StringRedisTemplate redisTemplate;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired PlatformTransactionManager transactions;
    @MockitoBean JavaMailSender sender;
    User user;

    @BeforeEach
    void account() {
        user = users.saveAndFlush(User.create("Find ID Fixture", "find-" + UUID.randomUUID(),
                UUID.randomUUID() + "@example.test", "unused-fixture-hash", "01", "사원"));
    }

    private org.springframework.mock.web.MockHttpServletResponse request(String name, String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/find-id/request").contentType("application/json")
                        .content(mapper.writeValueAsString(Map.of("name", name, "email", email))))
                .andReturn().getResponse();
    }

    @Test
    void matchingAndNonMatchingRequestsHaveIdenticalPublicResponse() throws Exception {
        var matched = request(user.getUserName(), user.getUserEmail());
        var wrongName = request("Different name", user.getUserEmail());
        var absent = request(user.getUserName(), "absent@example.test");
        assertThat(matched.getStatus()).isEqualTo(200);
        assertThat(wrongName.getStatus()).isEqualTo(200);
        assertThat(absent.getStatus()).isEqualTo(200);
        assertThat(wrongName.getContentAsString()).isEqualTo(matched.getContentAsString());
        assertThat(absent.getContentAsString()).isEqualTo(matched.getContentAsString());
        assertThat(matched.getContentAsString()).doesNotContain("loginId", user.getLoginId());
        verify(sender, timeout(2000)).send(any(SimpleMailMessage.class));
    }

    @Test
    void sendsIdOnlyToMatchingNameAndEmail() throws Exception {
        request("Different name", user.getUserEmail());
        request(user.getUserName(), "absent@example.test");
        verifyNoInteractions(sender);
        request(user.getUserName(), user.getUserEmail());
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender, timeout(2000).times(1)).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly(user.getUserEmail());
        assertThat(message.getValue().getText()).contains(user.getLoginId());
        assertThat(message.getValue().getFrom()).isEqualTo("workflow@example.test");
    }

    @Test
    void smtpWaitDoesNotHoldHttpResponse() throws Exception {
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
            var response = executor.submit(() -> request(user.getUserName(), user.getUserEmail()));
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
    void mailFailureDoesNotExposeAccountExistence() throws Exception {
        doThrow(new MailSendException("fixture SMTP failure")).when(sender).send(any(SimpleMailMessage.class));
        var matched = request(user.getUserName(), user.getUserEmail());
        var absent = request(user.getUserName(), "absent@example.test");
        assertThat(matched.getStatus()).isEqualTo(200);
        assertThat(absent.getStatus()).isEqualTo(200);
        assertThat(matched.getContentAsString()).isEqualTo(absent.getContentAsString());
        verify(sender, timeout(2000)).send(any(SimpleMailMessage.class));
    }

    @Test
    void rolledBackRequestSendsNothing() throws Exception {
        var body = mapper.readValue(mapper.writeValueAsString(
                Map.of("name", user.getUserName(), "email", user.getUserEmail())), FindIdRequestDto.class);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            auth.findId(body);
            status.setRollbackOnly();
        });
        verify(sender, after(100).never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void findIdLimitsEachEmailAndDoesNotConsumeResetAllowance() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(request(user.getUserName(), user.getUserEmail()).getStatus()).isEqualTo(200);
        }
        verify(sender, timeout(2000).times(5)).send(any(SimpleMailMessage.class));
        var limited = request(user.getUserName(), user.getUserEmail());
        assertThat(limited.getStatus()).isEqualTo(429);
        assertThat(limited.getContentAsString()).contains("TOO_MANY_REQUESTS");
        verify(sender, after(100).times(5)).send(any(SimpleMailMessage.class));
        assertThat(redisTemplate.getExpire("findid:req:" + user.getUserEmail(), TimeUnit.SECONDS))
                .isBetween(1L, 60L);
        var another = users.saveAndFlush(User.create("Another Fixture", "other-" + UUID.randomUUID(),
                UUID.randomUUID() + "@example.test", "unused-fixture-hash", "01", "사원"));
        assertThat(request(another.getUserName(), another.getUserEmail()).getStatus()).isEqualTo(200);
        verify(sender, timeout(2000).times(6)).send(any(SimpleMailMessage.class));
        var reset = mvc.perform(post("/api/v1/auth/password/reset/request").contentType("application/json")
                        .content(mapper.writeValueAsString(Map.of("email", user.getUserEmail()))))
                .andReturn().getResponse();
        assertThat(reset.getStatus()).isEqualTo(200);
        verify(sender, timeout(2000).times(7)).send(any(SimpleMailMessage.class));
    }

    @Test
    void nonMatchingRequestsUseTheSameEmailLimitWithoutSending() throws Exception {
        String absent = UUID.randomUUID() + "@example.test";
        for (int attempt = 0; attempt < 5; attempt++) {
            var wrongName = request("Different name", user.getUserEmail());
            var missing = request(user.getUserName(), absent);
            assertThat(wrongName.getStatus()).isEqualTo(200);
            assertThat(missing.getContentAsString()).isEqualTo(wrongName.getContentAsString());
        }
        var wrongName = request("Different name", user.getUserEmail());
        var missing = request(user.getUserName(), absent);
        assertThat(wrongName.getStatus()).isEqualTo(429);
        assertThat(missing.getStatus()).isEqualTo(429);
        assertThat(missing.getContentAsString()).isEqualTo(wrongName.getContentAsString());
        verify(sender, after(100).never()).send(any(SimpleMailMessage.class));
    }

    @Test
    void expiredFindIdWindowAllowsAnotherRequest() throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThat(request(user.getUserName(), user.getUserEmail()).getStatus()).isEqualTo(200);
        }
        assertThat(request(user.getUserName(), user.getUserEmail()).getStatus()).isEqualTo(429);
        verify(sender, timeout(2000).times(5)).send(any(SimpleMailMessage.class));
        // Advance only this disposable Redis key's expiry; do not sleep for one minute.
        redisTemplate.expire("findid:req:" + user.getUserEmail(), Duration.ZERO);
        assertThat(request(user.getUserName(), user.getUserEmail()).getStatus()).isEqualTo(200);
        verify(sender, timeout(2000).times(6)).send(any(SimpleMailMessage.class));
    }

}
