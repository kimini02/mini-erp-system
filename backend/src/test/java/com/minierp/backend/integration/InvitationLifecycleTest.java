package com.minierp.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minierp.backend.domain.user.dto.EmployeeRequests;
import com.minierp.backend.domain.user.dto.RegistrationResult;
import com.minierp.backend.domain.user.entity.User;
import com.minierp.backend.domain.user.entity.UserRole;
import com.minierp.backend.domain.user.repository.UserRepository;
import com.minierp.backend.domain.user.service.EmployeeService;
import com.minierp.backend.domain.user.service.InvitationService;
import com.minierp.backend.global.service.InvitationTokenStore;
import com.minierp.backend.support.MariaDbIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "app.mail.enabled=true", "app.mail.from=no-reply@example.test"
})
class InvitationLifecycleTest extends MariaDbIntegrationTest {
    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        r.add("spring.data.redis.password", () -> "");
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    EmployeeService employees;
    @Autowired
    InvitationService invitations;
    @Autowired
    InvitationTokenStore tokens;
    @Autowired
    UserRepository users;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    Clock clock;
    @MockitoBean
    JavaMailSender mail;
    private final List<SimpleMailMessage> sent = new CopyOnWriteArrayList<>();
    private String chiefToken;
    private String password;
    @BeforeEach
    void setUp() throws Exception {
        password = "Aa1!" + UUID.randomUUID().toString().substring(0, 12);
        String login = "chief-" + UUID.randomUUID();
        var chief = User.create("chief", login, login + "@example.test", encoder.encode(password), "01", "관리소장");
        chief.changeRole(UserRole.ADMIN);
        users.saveAndFlush(chief);
        String response = mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("id", login, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        chiefToken = mapper.readTree(response).path("data").path("accessToken").asText();
        sent.clear();
        reset(mail);
        doAnswer(inv -> {
            sent.add(new SimpleMailMessage(inv.getArgument(0)));
            return null;
        }).when(mail).send(any(SimpleMailMessage.class));
    }

    private RegistrationResult register() {
        return employees.register(new EmployeeRequests
                .Create("초대검증", UUID.randomUUID() + "@example.test", LocalDate.now(clock), 2L, 1L, List
                .of("EMPLOYEE")));
    }

    private String linkToken(int index) {
        String text = sent.get(index).getText();
        return text.substring(text.indexOf("setupToken=") + 11).split("\\s")[0];
    }

    private void changeEmail(long id, String email) throws Exception {
        mvc.perform(patch("/api/v1/users/" + id).header("Authorization", "Bearer " + chiefToken)
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("email", email))))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions setup(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/password/setup").contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("setupToken", token, "newPassword", password))));
    }

    private void invalid(String token) throws Exception {
        setup(token).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SETUP_TOKEN_INVALID"));
    }

    @Test
    void emailChangeAndResendInvalidateOriginalLink() throws Exception {
        var employee = register();
        String a = linkToken(0);
        String email = UUID.randomUUID() + "@example.test";
        changeEmail(employee.getId(), email);
        employees.invitation(employee.getId());
        String b = linkToken(1);
        assertThat(sent.get(1).getTo()).containsExactly(email);
        invalid(a);
        setup(b).andExpect(status().isOk());
        invalid(b);
    }

    @Test
    void emailChangeInvalidatesLinkEvenWithoutResend() throws Exception {
        var employee = register();
        String a = linkToken(0);
        changeEmail(employee.getId(), UUID.randomUUID() + "@example.test");
        invalid(a);
        assertThat(users.findById(employee.getId()).orElseThrow().getUserPw()).isNull();
    }

    @Test
    void resendAloneInvalidatesPreviousLink() throws Exception {
        var employee = register();
        String a = linkToken(0);
        employees.invitation(employee.getId());
        String b = linkToken(1);
        invalid(a);
        setup(b).andExpect(status().isOk());
    }

    @Test
    void delayedEventForOldEmailDoesNotIssueAnotherLink() throws Exception {
        var employee = register();
        String oldEmail = sent.get(0).getTo()[0];
        changeEmail(employee.getId(), UUID.randomUUID() + "@example.test");
        invitations.send(new EmployeeService
                .InvitationRequested(employee.getId(), oldEmail,
                new RegistrationResult(employee.getId(), employee.getLoginId())));
        assertThat(sent).hasSize(1);
    }

    @Test
    void concurrentReplacementLeavesExactlyOneUsableToken() throws Exception {
        var employee = register();
        var candidates = new ArrayList<String>();
        for (int i = 0; i < 8; i++) {
            candidates.add(UUID.randomUUID().toString());
        }
        var start = new CyclicBarrier(8);
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> results = new ArrayList<>();
            for (String token : candidates) {
                results.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    tokens.save(token, employee.getId());
                    return null;
                }));
            }
            for (var result : results) {
                result.get(20, TimeUnit.SECONDS);
            }
            long usable = candidates.stream().map(tokens::consume).filter(Objects::nonNull).count();
            assertThat(usable).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Autowired
    org.springframework.transaction.PlatformTransactionManager transactionManager;
    @Test
    void emailChangeRevokesOnlyAfterCommit() throws Exception {
        var employee = register();
        String a = linkToken(0);
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            try {
                changeEmail(employee.getId(), UUID.randomUUID() + "@example.test");
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            assertThat(tokens.owner(a)).isEqualTo(employee.getId());
        });
        invalid(a);
    }

    @Test
    void rolledBackEmailChangePreservesOriginalInvitation() throws Exception {
        var employee = register();
        String a = linkToken(0);
        String oldEmail = sent.get(0).getTo()[0];
        new org.springframework.transaction.support.TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            try {
                changeEmail(employee.getId(), UUID.randomUUID() + "@example.test");
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
            tx.setRollbackOnly();
        });
        assertThat(users.findById(employee.getId()).orElseThrow().getUserEmail()).isEqualTo(oldEmail);
        setup(a).andExpect(status().isOk());
    }

    @Test
    void delayedDeletionPreservesNewEmailToken() throws Exception {
        var employee = register();
        String oldEmail = sent.get(0).getTo()[0];
        changeEmail(employee.getId(), UUID.randomUUID() + "@example.test");
        employees.invitation(employee.getId());
        String b = linkToken(1);
        tokens.revokeForEmail(employee.getId(), oldEmail);
        setup(b).andExpect(status().isOk());
    }

    @Test
    void tokenBoundToOldEmailCannotSetPasswordEvenBeforeDeletion() throws Exception {
        var employee = register();
        String oldEmail = sent.get(0).getTo()[0];
        changeEmail(employee.getId(), UUID.randomUUID() + "@example.test");
        String stale = UUID.randomUUID().toString();
        // Simulate a token left behind while an after-commit Redis deletion is delayed.
        tokens.save(stale, employee.getId(), oldEmail);
        invalid(stale);
        assertThat(users.findById(employee.getId()).orElseThrow().getUserPw()).isNull();
    }
}
