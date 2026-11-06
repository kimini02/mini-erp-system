package com.minierp.backend.integration;

import com.fasterxml.jackson.databind.*;
import com.minierp.backend.support.MariaDbIntegrationTest;
import com.minierp.backend.domain.user.entity.*;
import com.minierp.backend.domain.user.repository.UserRepository;
import com.minierp.backend.domain.user.service.EmployeeService;
import com.minierp.backend.domain.user.dto.EmployeeRequests;
import com.minierp.backend.domain.org.service.OrgService;
import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.service.InvitationTokenStore;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.test.web.servlet.*;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "app.mail.enabled=true", "app.mail.from=no-reply@example.test",
            "app.invitation.setup-url=https://workflow.example.test/password/setup"
})
class M1AcceptanceTest extends MariaDbIntegrationTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    UserRepository users;
    @Autowired
    PasswordEncoder encoder;
    @Autowired
    EmployeeService employees;
    @Autowired
    OrgService organizations;
    @Autowired
    com.minierp.backend.domain.user.service.InvitationService invitations;
    @Autowired
    SqlStore db;
    @Autowired
    Clock clock;
    @Autowired
    PlatformTransactionManager transactionManager;
    @MockitoSpyBean
    JdbcTemplate jdbc;
    @MockitoBean
    InvitationTokenStore tokens;
    @MockitoBean
    JavaMailSender mail;
    private User chief;
    private User leader;
    private User employee;
    private User outsider;
    private String password;
    private String chiefToken;
    private String leaderToken;
    private String employeeToken;
    private final Map<String, Long> links = new ConcurrentHashMap<>();
    @BeforeEach
    void setUp() throws Exception {
        db.update("UPDATE departments SET head_user_id=NULL", Map.of());
        for (String table : List.of("notifications", "leave_ledger", "approval_histories", "approval_lines",
                "general_requests", "attendances", "leave_requests", "overtime_requests", "approval_documents",
                "task_assignments", "project_members", "tasks", "projects", "user_roles", "users")) {
            db.update("DELETE FROM " + table, Map.of());
        }
        db.update("UPDATE departments SET parent_id=1 WHERE id>1", Map.of());
        db.update("DELETE FROM departments WHERE id>4", Map.of());
        db.update("DELETE FROM role_permissions", Map.of());
        db.update("DELETE FROM roles WHERE id>3", Map.of());
        db.update("DELETE FROM holidays", Map.of());
        db.update("DELETE FROM login_id_sequences", Map.of());
        String seed = new ClassPathResource("data-integration.sql").getContentAsString(StandardCharsets.UTF_8);
        for (String statement : seed.split(";")) {
            if (statement.trim().startsWith("INSERT INTO role_permissions")) {
                jdbc.execute(statement);
            }
        }
        password = "Aa1!" + UUID.randomUUID().toString().substring(0, 12);
        chief = user("chief", "01", UserRole.ADMIN);
        leader = user("leader", "01", UserRole.TEAM_LEADER);
        employee = user("employee", "01", UserRole.USER);
        outsider = user("outside", "02", UserRole.USER);
        links.clear();
        reset(tokens, mail);
        doAnswer(inv -> {
            Long id = inv.getArgument(1);
            links.values().removeIf(id::equals);
            links.put(inv.getArgument(0), id);
            return null;
        }).when(tokens).save(anyString(), anyLong(), anyString());
        doAnswer(inv -> {
            Long id = inv.getArgument(0);
            links.values().removeIf(id::equals);
            return null;
        }).when(tokens).revoke(anyLong());
        doAnswer(inv -> {
            Long id = inv.getArgument(0);
            links.values().removeIf(id::equals);
            return null;
        }).when(tokens).revokeForEmail(anyLong(), anyString());
        when(tokens.owner(anyString())).thenAnswer(inv -> links.get(inv.getArgument(0)));
        when(tokens.consume(anyString(), anyString())).thenAnswer(inv -> links.remove(inv.getArgument(0)));
        chiefToken = login(chief.getLoginId());
        leaderToken = login(leader.getLoginId());
        employeeToken = login(employee.getLoginId());
    }

    private User user(String prefix, String department, UserRole role) {
        String id = prefix + UUID.randomUUID().toString().substring(0, 8);
        User u = User.create(prefix, id, id + "@example.test", encoder.encode(password), department, "사원");
        u.changeRole(role);
        return users.saveAndFlush(u);
    }

    private String json(Object value) throws Exception {
        return mapper.writeValueAsString(value);
    }

    private String login(String id) throws Exception {
        return body(
                mvc.perform(
                        post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(map("id", id, "password", password)))))
                .path("data")
                .path("accessToken")
                .asText();
    }

    private JsonNode body(ResultActions action) throws Exception {
        return mapper.readTree(
                action.andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString());
    }

    private String auth(String token) {
        return "Bearer " + token;
    }

    private long department(String name, long parent) throws Exception {
        return body(
                mvc.perform(
                        post("/api/v1/departments")
                                .header("Authorization", auth(chiefToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(map("name", name, "parentId", parent, "sortOrder", 1)))))
                .path("data")
                .path("id")
                .asLong();
    }

    private EmployeeRequests.Create create(String prefix) {
        return new EmployeeRequests.Create(prefix, prefix + UUID.randomUUID() + "@example.test", LocalDate.now(clock),
                2L, 1L, List.of("EMPLOYEE"));
    }

    @Test
    void chiefCanFillLegacyHireDateWithoutChangingLoginId() throws Exception {
        String loginId = employee.getLoginId();
        LocalDate hireDate = LocalDate.now(clock).minusYears(3);
        assertThat(users.findById(employee.getId()).orElseThrow().getHireDate()).isNull();
        mvc.perform(
                patch("/api/v1/users/" + employee.getId())
                        .header("Authorization", auth(chiefToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(map("hireDate", hireDate.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hireDate").value(hireDate.toString()))
                .andExpect(jsonPath("$.data.loginId").value(loginId));
        var updated = users.findById(employee.getId()).orElseThrow();
        assertThat(updated.getHireDate()).isEqualTo(hireDate);
        assertThat(updated.getLoginId()).isEqualTo(loginId);
        assertThat(db.count("SELECT COUNT(*) FROM login_id_sequences", Map.of())).isZero();
        mvc.perform(
                patch("/api/v1/users/" + employee.getId())
                        .header("Authorization", auth(chiefToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(map("name", "입사일유지"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hireDate").value(hireDate.toString()));
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "2024/02/01", "2024-2-01", "2024-02-30"
    })
    void hireDatePatchRejectsMalformedOrImpossibleDates(String date) throws Exception {
        mvc.perform(
                patch("/api/v1/users/" + employee.getId())
                        .header("Authorization", auth(chiefToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(map("hireDate", date))))
                .andExpect(status().isBadRequest());
        assertThat(users.findById(employee.getId()).orElseThrow().getHireDate()).isNull();
    }

    @Test
    void employeeCannotEditTheirHireDate() throws Exception {
        mvc.perform(
                patch("/api/v1/users/" + employee.getId())
                        .header("Authorization", auth(employeeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(map("hireDate", LocalDate.now(clock).toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void employeeRegistrationRequiresHireDate() throws Exception {
        var request = create("입사일필수");
        var body = map("name", request.name(), "email", request.email(), "departmentId", 2, "positionId", 1,
                "roleCodes", List.of("EMPLOYEE"));
        mvc.perform(
                post("/api/v1/users")
                        .header("Authorization", auth(chiefToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest());
        body.put("hireDate", null);
        mvc.perform(
                post("/api/v1/users")
                        .header("Authorization", auth(chiefToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest());
        assertThat(users.existsByUserEmail(request.email())).isFalse();
    }

    private long grantEmployeeCreateOnly() {
        long role = db.insert("INSERT INTO roles(code,name,is_system) VALUES('HR_RECRUITER','사원 등록 담당',0)", Map.of());
        db.update("INSERT INTO role_permissions VALUES(:role,8,0,1,0,0,'ALL')", map("role", role));
        db.update("DELETE FROM user_roles WHERE user_id=:user", map("user", employee.getId()));
        db.update("INSERT INTO user_roles VALUES(:user,:role)", map("user", employee.getId(), "role", role));
        return role;
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {
        "CHIEF", "LEADER", "HR_RECRUITER"
    })
    void employeeCreatorCannotAssignOtherRoles(String role) throws Exception {
        grantEmployeeCreateOnly();
        long before = users.count();
        var request = create("권한검증");
        var restricted = new EmployeeRequests.Create(request.name(), request.email(), request.hireDate(),
                request.departmentId(), request.positionId(), List.of(role));
        mvc.perform(
                post("/api/v1/users")
                        .header("Authorization", auth(employeeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(restricted)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
        assertThat(users.count()).isEqualTo(before);
        assertThat(db.count("SELECT COUNT(*) FROM login_id_sequences", Map.of())).isZero();
        verifyNoInteractions(tokens, mail);
    }

    @Test
    void employeeCreatorCannotHidePrivilegedRoleAlongsideEmployee() throws Exception {
        grantEmployeeCreateOnly();
        var request = create("복수역할검증");
        var restricted = new EmployeeRequests.Create(request.name(), request.email(), request.hireDate(),
                request.departmentId(), request.positionId(), List.of("EMPLOYEE", "CHIEF"));
        mvc.perform(
                post("/api/v1/users")
                        .header("Authorization", auth(employeeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(restricted)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
        assertThat(users.existsByUserEmail(request.email())).isFalse();
        verifyNoInteractions(tokens, mail);
    }

    @Test
    void employeeCreatorCanRegisterEmployeeWithoutRoleUpdate() throws Exception {
        grantEmployeeCreateOnly();
        var request = create("일반사원등록");
        mvc.perform(
                post("/api/v1/users")
                        .header("Authorization", auth(employeeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(request)))
                .andExpect(status().isCreated());
        assertThat(users.findByUserEmail(request.email()).orElseThrow().roleCodeList()).containsExactly("EMPLOYEE");
    }

    @Test
    void employeeCreatorWithRoleUpdateCanAssignChief() throws Exception {
        long role = grantEmployeeCreateOnly();
        db.update("INSERT INTO role_permissions VALUES(:role,9,0,0,1,0,NULL)", map("role", role));
        var request = create("승인된역할등록");
        var permitted = new EmployeeRequests.Create(request.name(), request.email(), request.hireDate(),
                request.departmentId(), request.positionId(), List.of("CHIEF"));
        mvc.perform(
                post("/api/v1/users")
                        .header("Authorization", auth(employeeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(permitted)))
                .andExpect(status().isCreated());
        assertThat(users.findByUserEmail(request.email()).orElseThrow().roleCodeList()).containsExactly("CHIEF");
    }

    @Test
    void concurrentIdAllocationIsUniqueAndUnsafeMaxPlusOneCollides() throws Exception {
        int year = LocalDate.now(clock).getYear();
        var pool = Executors.newFixedThreadPool(8);
        try {
            var rendezvous = new CyclicBarrier(2);
            List<Future<Boolean>> unsafe = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                unsafe.add(pool.submit(() -> {
                    try {
                        return new TransactionTemplate(transactionManager).execute(status -> {
                            long next = db.count(
                                    "SELECT COALESCE(MAX(CAST(SUBSTRING(login_id,5) AS UNSIGNED)),0)+1 FROM users WHERE login_id LIKE :prefix",
                                    map("prefix", year + "%"));
                            try {
                                rendezvous.await(10, TimeUnit.SECONDS);
                            } catch (Exception ex) {
                                throw new RuntimeException(ex);
                            }
                            db.update(
                                    "INSERT INTO users(login_id,user_name,user_email,department_id,position_id,status,must_change_pw,token_version,created_at,updated_at) VALUES(:login,'unsafe',:email,2,1,'ACTIVE',0,0,:now,:now)",
                                    map("login", String.format("%d%03d", year, next),
                                            "email", UUID.randomUUID() + "@example.test",
                                            "now", LocalDateTime.now(clock)));
                            return true;
                        });
                    } catch (org.springframework.dao.DataIntegrityViolationException expected) {
                        return false;
                    }
                }));
            }
            assertThat(List.of(unsafe.get(0).get(20, TimeUnit.SECONDS), unsafe.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            db.update("DELETE FROM users WHERE user_name='unsafe'", Map.of());
            var start = new CyclicBarrier(8);
            List<Future<String>> safe = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                safe.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return employees.register(create("동시등록")).getLoginId();
                }));
            }
            Set<String> ids = new HashSet<>();
            for (var future : safe) {
                ids.add(future.get(30, TimeUnit.SECONDS));
            }
            assertThat(ids).hasSize(8);
            assertThat(ids).allMatch(id -> id.startsWith(Integer.toString(year)));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void departmentCycleMoveIs422() throws Exception {
        long child = department("개발 하위", 2);
        mvc.perform(
                patch("/api/v1/departments/2/parent")
                        .header("Authorization", auth(chiefToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(map("parentId", child))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("INVALID_DEPARTMENT_MOVE"));
    }

    @Test
    void leaderSeesDepartmentTreeAndOutsideFilterIs403() throws Exception {
        long child = department("하위팀", 2);
        db.update("UPDATE users SET department_id=:dept WHERE id=:id", map("dept", child, "id", employee.getId()));
        var content = body(mvc.perform(get("/api/v1/users").header("Authorization", auth(leaderToken))))
                .path("data")
                .path("content");
        List<Long> ids = new ArrayList<>();
        content.forEach(n -> ids.add(n.path("id").asLong()));
        assertThat(ids).contains(employee.getId()).doesNotContain(outsider.getId());
        mvc.perform(get("/api/v1/users").param("departmentId", "3").header("Authorization", auth(leaderToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users").header("Authorization", auth(employeeToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/" + outsider.getId()).header("Authorization", auth(leaderToken)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users/" + employee.getId()).header("Authorization", auth(employeeToken)))
                .andExpect(status().isOk());
    }

    @Test
    void retirementRevokesExistingTokenImmediately() throws Exception {
        mvc.perform(
                patch("/api/v1/users/" + employee.getId() + "/status")
                        .header("Authorization", auth(chiefToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(map("status", "RETIRED"))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/me").header("Authorization", auth(employeeToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("TOKEN_REVOKED"));
        mvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(map("id", employee.getLoginId(), "password", password))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCOUNT_INACTIVE"));
    }

    @Test
    void lastChiefCannotRetireOrLoseRole() throws Exception {
        mvc.perform(
                patch("/api/v1/users/" + chief.getId() + "/status")
                        .header("Authorization", auth(chiefToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(map("status", "RETIRED"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LAST_CHIEF"));
        mvc.perform(
                put("/api/v1/users/" + chief.getId() + "/roles")
                        .header("Authorization", auth(chiefToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(map("roleCodes", List.of("EMPLOYEE")))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LAST_CHIEF"));
    }

    @Test
    void permissionChangeAppliesToSameTokenOnNextRequest() throws Exception {
        mvc.perform(get("/api/v1/users").header("Authorization", auth(leaderToken))).andExpect(status().isOk());
        mvc.perform(
                put("/api/v1/roles/2/permissions")
                        .header("Authorization", auth(chiefToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/users").header("Authorization", auth(leaderToken))).andExpect(status().isForbidden());
    }

    @Test
    void organizationTreeAlwaysUsesTwoBusinessQueries() throws Exception {
        assertThat(treeQueryCount()).isEqualTo(2);
        for (int i = 0; i < 12; i++) {
            department("쿼리검증" + i, 2);
        }
        assertThat(treeQueryCount()).isEqualTo(2);
    }

    private long treeQueryCount() {
        var tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(true);
        return tx.execute(status -> {
            long before = Long.parseLong(
                    jdbc.queryForMap("SHOW SESSION STATUS LIKE 'Com_select'").get("Value").toString());
            organizations.tree();
            long after = Long.parseLong(
                    jdbc.queryForMap("SHOW SESSION STATUS LIKE 'Com_select'").get("Value").toString());
            return after - before;
        });
    }

    @Test
    void organizationCrudAndDefaultScheduleAndPositionOrdering() throws Exception {
        long d = department("임시부서", 2);
        body(
                mvc.perform(
                        put("/api/v1/departments/2/head")
                                .header("Authorization", auth(chiefToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(map("userId", leader.getId())))));
        var detail = body(mvc.perform(get("/api/v1/departments/" + d).header("Authorization", auth(employeeToken))))
                .path("data");
        assertThat(detail.path("fallbackHead").path("id").asLong()).isEqualTo(leader.getId());
        assertThat(detail.path("path").size()).isEqualTo(2);
        body(
                mvc.perform(
                        patch("/api/v1/departments/" + d)
                                .header("Authorization", auth(chiefToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(map("name", "이름변경", "sortOrder", 5)))));
        var custom = body(
                mvc.perform(
                        put("/api/v1/departments/" + d + "/work-schedule")
                                .header("Authorization", auth(chiefToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json(
                                                map("useDefault", false, "startTime", "10:00", "endTime", "19:00",
                                                        "lateGraceMin", 5)))))
                .path("data")
                .path("workSchedule");
        assertThat(custom.path("useDefault").asBoolean()).isFalse();
        assertThat(custom.path("startTime").asText()).isEqualTo("10:00");
        body(
                mvc.perform(
                        put("/api/v1/departments/" + d + "/work-schedule")
                                .header("Authorization", auth(chiefToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"useDefault\":true}")));
        body(mvc.perform(delete("/api/v1/departments/2/head").header("Authorization", auth(chiefToken))));
        body(mvc.perform(delete("/api/v1/departments/" + d).header("Authorization", auth(chiefToken))));
        var position = body(
                mvc.perform(
                        post("/api/v1/positions")
                                .header("Authorization", auth(chiefToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"선임\"}")))
                .path("data")
                .path("id")
                .asLong();
        body(
                mvc.perform(
                        patch("/api/v1/positions/" + position)
                                .header("Authorization", auth(chiefToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"name\":\"수석\"}")));
        var positions = body(
                mvc.perform(
                        put("/api/v1/positions/order")
                                .header("Authorization", auth(chiefToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(List.of(map("id", position, "rankOrder", -1))))))
                .path("data");
        assertThat(positions.get(0).path("id").asLong()).isEqualTo(position);
        body(mvc.perform(delete("/api/v1/positions/" + position).header("Authorization", auth(chiefToken))));
    }

    @Test
    void employeeRegistrationSearchTabsAndCompatibilityFields() throws Exception {
        var request = create("목록검색");
        String preview = body(
                mvc.perform(
                        get("/api/v1/users/login-id-preview")
                                .param("hireDate", request.hireDate().toString())
                                .header("Authorization", auth(chiefToken))))
                .path("data")
                .path("loginId")
                .asText();
        var created = body(
                mvc.perform(
                        post("/api/v1/users")
                                .header("Authorization", auth(chiefToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(request))))
                .path("data");
        assertThat(created.path("loginId").asText()).isEqualTo(preview);
        long id = created.path("id").asLong();
        body(
                mvc.perform(
                        patch("/api/v1/users/" + id + "/status")
                                .header("Authorization", auth(chiefToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"status\":\"ON_LEAVE\"}")));
        var list = body(
                mvc.perform(
                        get("/api/v1/users")
                                .param("q", "목록")
                                .param("status", "ACTIVE")
                                .header("Authorization", auth(chiefToken))))
                .path("data");
        assertThat(list.path("content").size()).isZero();
        assertThat(list.path("statusCounts").path("ON_LEAVE").asInt()).isEqualTo(1);
        var detail = body(mvc.perform(get("/api/v1/users/" + id).header("Authorization", auth(chiefToken))))
                .path("data");
        assertThat(
                detail.has("departmentCode") && detail.has("departmentName") && detail.has("position") && detail.has(
                        "role"))
                .isTrue();
        assertThat(detail.path("invitationPending").asBoolean()).isTrue();
        mvc.perform(
                post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(map("id", preview, "password", password))))
                .andExpect(status().isUnauthorized());
        body(
                mvc.perform(
                        patch("/api/v1/users/" + id)
                                .header("Authorization", auth(chiefToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(map("name", "수정이름", "email", "updated@example.test", "positionId", 2)))));
    }

    @Test
    void invitationIsSentAfterCommitAndSetupIsOneUse() throws Exception {
        AtomicBoolean committed = new AtomicBoolean();
        doAnswer(inv -> {
            SimpleMailMessage message = inv.getArgument(0);
            var tx = new TransactionTemplate(transactionManager);
            tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            committed.set(Boolean.TRUE.equals(
                    tx.execute(s -> db.count("SELECT COUNT(*) FROM users WHERE user_email=:email",
                            map("email", message.getTo()[0])) == 1)));
            return null;
        }).when(mail).send(any(SimpleMailMessage.class));
        var created = employees.register(create("초대검증"));
        assertThat(created.isInvitationSent()).isTrue();
        assertThat(committed).isTrue();
        var capture = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(capture.capture());
        assertThat(capture.getValue().getText().contains("https://workflow.example.test/password/setup?setupToken="))
                .isTrue();
        String token = links.keySet().iterator().next();
        String nextPassword = "Aa2!" + UUID.randomUUID().toString().substring(0, 12);
        mvc.perform(
                post("/api/v1/auth/password/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(map("setupToken", token, "newPassword", nextPassword))))
                .andExpect(status().isOk());
        mvc.perform(
                post("/api/v1/auth/password/setup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(map("setupToken", token, "newPassword", nextPassword))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SETUP_TOKEN_INVALID"));
        mvc.perform(post("/api/v1/users/" + created.getId() + "/invitation").header("Authorization", auth(chiefToken)))
                .andExpect(status().isConflict());
    }

    @Test
    void rollbackDoesNotSendInvitation() {
        var tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> {
            employees.register(create("롤백검증"));
            status.setRollbackOnly();
        });
        verifyNoInteractions(mail, tokens);
    }

    @Test
    void disabledMailDoesNotCreateTokenOrSend() {
        org.springframework.test.util.ReflectionTestUtils.setField(invitations, "enabled", false);
        try {
            assertThat(employees.register(create("메일끔")).isInvitationSent()).isFalse();
            verifyNoInteractions(mail, tokens);
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(invitations, "enabled", true);
        }
    }

    @Test
    void concurrentInvitationLinksCannotOverwriteFirstPassword() throws Exception {
        var created = employees.register(create("초대경쟁"));
        var invitationTokens = new ArrayList<>(links.keySet());
        employees.invitation(created.getId());
        invitationTokens.addAll(links.keySet());
        var pool = Executors.newFixedThreadPool(2);
        var start = new CyclicBarrier(2);
        try {
            List<Future<Boolean>> results = new ArrayList<>();
            for (String token : invitationTokens) {
                results.add(pool.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    try {
                        invitations.setup(token, "Aa1!" + UUID.randomUUID().toString().substring(0, 12));
                        return true;
                    } catch (com.minierp.backend.global.exception.BusinessException ex) {
                        assertThat(ex.getErrorCode())
                                .isEqualTo(com.minierp.backend.global.exception.ErrorCode.SETUP_TOKEN_INVALID);
                        return false;
                    }
                }));
            }
            assertThat(List.of(results.get(0).get(20, TimeUnit.SECONDS), results.get(1).get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void permissionWithoutAnyActionDoesNotWidenAnotherRolesScope() throws Exception {
        long role = db.insert("INSERT INTO roles(code,name,is_system) VALUES('NO_ACTION','비활성 권한',0)", Map.of());
        db.update("INSERT INTO user_roles VALUES(:user,:role)", map("user", leader.getId(), "role", role));
        db.update("INSERT INTO role_permissions VALUES(:role,8,0,0,0,0,'ALL')", map("role", role));
        mvc.perform(get("/api/v1/users").param("departmentId", "3").header("Authorization", auth(leaderToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void mailFailureKeepsRegisteredEmployee() {
        doThrow(new org.springframework.mail.MailSendException("fake failure"))
                .when(mail)
                .send(any(SimpleMailMessage.class));
        var created = employees.register(create("메일실패"));
        assertThat(created.isInvitationSent()).isFalse();
        assertThat(users.existsById(created.getId())).isTrue();
    }

    @Test
    void forcedPasswordChangeBlocksOtherRoutesAndRotatesToken() throws Exception {
        db.update("UPDATE users SET must_change_pw=1 WHERE id=:id", map("id", chief.getId()));
        mvc.perform(get("/api/v1/me").header("Authorization", auth(chiefToken))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/me/permissions").header("Authorization", auth(chiefToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("PASSWORD_CHANGE_REQUIRED"));
        var result = body(
                mvc.perform(
                        patch("/api/v1/me/password")
                                .header("Authorization", auth(chiefToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json(
                                                map("currentPassword", password,
                                                        "newPassword", "Aa2!" + UUID.randomUUID()
                                                                .toString()
                                                                .substring(0, 12))))));
        mvc.perform(get("/api/v1/me").header("Authorization", auth(chiefToken))).andExpect(status().isUnauthorized());
        mvc.perform(
                get("/api/v1/me/permissions")
                        .header("Authorization", auth(result.path("data").path("accessToken").asText())))
                .andExpect(status().isOk());
    }

    @Test
    void organizationAndPermissionValidationProtectsReferences() throws Exception {
        mvc.perform(
                put("/api/v1/departments/2/head")
                        .header("Authorization", auth(chiefToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(map("userId", outsider.getId()))))
                .andExpect(status().isUnprocessableEntity());
        mvc.perform(
                put("/api/v1/departments/2/head")
                        .header("Authorization", auth(chiefToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(map("userId", leader.getId()))))
                .andExpect(status().isOk());
        mvc.perform(
                patch("/api/v1/users/" + leader.getId())
                        .header("Authorization", auth(chiefToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(map("departmentId", 3))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("HEAD_MUST_BE_REASSIGNED"));
        mvc.perform(
                put("/api/v1/work-schedules/default")
                        .header("Authorization", auth(chiefToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(map("startTime", "18:00", "endTime", "09:00", "lateGraceMin", 10))))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/v1/positions/1").header("Authorization", auth(chiefToken)))
                .andExpect(status().isConflict());
        mvc.perform(
                put("/api/v1/roles/1/permissions")
                        .header("Authorization", auth(chiefToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[]"))
                .andExpect(status().isForbidden());
        mvc.perform(
                put("/api/v1/roles/2/permissions")
                        .header("Authorization", auth(chiefToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(List.of(map("menu", "APPROVAL_INBOX", "create", true)))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void holidayAndRoleCrudAndMultipleRoleUnion() throws Exception {
        String holiday = json(map("date", LocalDate.now(clock).plusDays(1), "name", "테스트휴일", "substitute", true));
        var h = body(
                mvc.perform(
                        post("/api/v1/holidays")
                                .header("Authorization", auth(chiefToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(holiday)))
                .path("data")
                .path("id")
                .asLong();
        mvc.perform(
                post("/api/v1/holidays")
                        .header("Authorization", auth(chiefToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(holiday))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/holidays/" + h).header("Authorization", auth(chiefToken)))
                .andExpect(status().isOk());
        var role = body(
                mvc.perform(
                        post("/api/v1/roles")
                                .header("Authorization", auth(chiefToken))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(map("name", "인사담당")))))
                .path("data");
        long roleId = role.path("id").asLong();
        mvc.perform(
                put("/api/v1/roles/" + roleId + "/permissions")
                        .header("Authorization", auth(chiefToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(List.of(map("menu", "EMPLOYEE", "view", true, "scope", "ALL")))))
                .andExpect(status().isOk());
        mvc.perform(
                post("/api/v1/roles/" + roleId + "/members")
                        .header("Authorization", auth(chiefToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(map("userIds", List.of(leader.getId())))))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/users").param("departmentId", "3").header("Authorization", auth(leaderToken)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/v1/roles/" + roleId).header("Authorization", auth(chiefToken)))
                .andExpect(status().isConflict());
    }

    @Test
    void privilegedResendRequiresRolePermissionBeforeAcceptedStateCheck() throws Exception {
        employeeOperator(true, false, false);
        clearInvocations(tokens, mail);
        mvc.perform(
                post("/api/v1/users/" + leader.getId() + "/invitation").header("Authorization", auth(employeeToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
        verifyNoInteractions(tokens, mail);
    }

    private long employeeOperator(boolean create, boolean update, boolean roleUpdate) {
        long role = db.insert("INSERT INTO roles(code,name,is_system) VALUES('REVIEW_OPERATOR','리뷰 담당',0)", Map.of());
        db.update("DELETE FROM user_roles WHERE user_id=:id", map("id", employee.getId()));
        db.update("INSERT INTO user_roles VALUES(:user,:role)", map("user", employee.getId(), "role", role));
        db.update("INSERT INTO role_permissions VALUES(:role,8,0,:c,:u,0,'ALL')",
                map("role", role, "c", create, "u", update));
        if (roleUpdate) {
            db.update("INSERT INTO role_permissions VALUES(:role,9,0,0,1,0,NULL)", map("role", role));
        }
        return role;
    }

    @Test
    void employeeUpdateCannotChangeChiefEmail() throws Exception {
        employeeOperator(false, true, false);
        var before = new HashMap<>(links);
        clearInvocations(tokens, mail);
        mvc.perform(
                patch("/api/v1/users/" + chief.getId())
                        .header("Authorization", auth(employeeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(map("email", "chief-change@example.test"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
        assertThat(users.findById(chief.getId()).orElseThrow().getUserEmail()).isEqualTo(chief.getUserEmail());
        assertThat(links).isEqualTo(before);
        verifyNoInteractions(tokens, mail);
    }

    @Test
    void employeeUpdateCanChangeEmployeeOnlyEmail() throws Exception {
        employeeOperator(false, true, false);
        mvc.perform(
                patch("/api/v1/users/" + outsider.getId())
                        .header("Authorization", auth(employeeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(map("email", "employee-change@example.test"))))
                .andExpect(status().isOk());
        assertThat(users.findById(outsider.getId()).orElseThrow().getUserEmail())
                .isEqualTo("employee-change@example.test");
    }

    @Test
    void roleUpdateAllowsChiefEmailChange() throws Exception {
        employeeOperator(false, true, true);
        mvc.perform(
                patch("/api/v1/users/" + chief.getId())
                        .header("Authorization", auth(employeeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(map("email", "allowed-chief@example.test"))))
                .andExpect(status().isOk());
        assertThat(users.findById(chief.getId()).orElseThrow().getUserEmail()).isEqualTo("allowed-chief@example.test");
    }

    @Test
    void employeeCreateCannotResendPendingLeaderInvitation() throws Exception {
        employeeOperator(true, false, false);
        var pending = employees.register(create("초대대기팀장"));
        db.update("INSERT INTO user_roles VALUES(:id,2)", map("id", pending.getId()));
        var before = new HashMap<>(links);
        var row = employees.raw(pending.getId());
        clearInvocations(tokens, mail);
        mvc.perform(
                post("/api/v1/users/" + pending.getId() + "/invitation").header("Authorization", auth(employeeToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("ACCESS_DENIED"));
        assertThat(employees.raw(pending.getId())).isEqualTo(row);
        assertThat(links).isEqualTo(before);
        verifyNoInteractions(tokens, mail);
    }

    @Test
    void loginIgnoresRevokedBearerToken() throws Exception {
        db.update("UPDATE users SET token_version=token_version+1 WHERE id=:id", map("id", employee.getId()));
        mvc.perform(
                post("/api/v1/auth/login")
                        .header("Authorization", auth(employeeToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(map("id", employee.getLoginId(), "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    void passwordSetupIgnoresMalformedBearerToken() throws Exception {
        var created = employees.register(create("인증헤더무시"));
        String token = links.keySet().iterator().next();
        mvc.perform(
                post("/api/v1/auth/password/setup")
                        .header("Authorization", "Bearer not-a-jwt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(map("setupToken", token, "newPassword", password))))
                .andExpect(status().isOk());
        assertThat(encoder.matches(password, users.findById(created.getId()).orElseThrow().getUserPw())).isTrue();
    }
}
