package com.minierp.backend.integration;

import com.fasterxml.jackson.databind.*;
import com.minierp.backend.domain.notification.service.NotificationService;
import com.minierp.backend.domain.user.entity.*;
import com.minierp.backend.domain.user.repository.UserRepository;
import com.minierp.backend.global.security.JwtTokenProvider;
import com.minierp.backend.support.MariaDbIntegrationTest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.http.MediaType;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class M2DocumentIntegrationTest extends MariaDbIntegrationTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper mapper;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    UserRepository users;
    @Autowired
    JwtTokenProvider jwt;
    @Autowired
    Clock clock;
    @MockitoSpyBean
    NotificationService notifications;
    private User employee;
    private User leader;
    private User chief;
    private String employeeToken;
    private String leaderToken;
    private String chiefToken;
    private LocalDate start;
    @BeforeEach
    void prepare() {
        reset((NotificationService) org.springframework.test.util.AopTestUtils.getUltimateTargetObject(notifications));
        employee = user("employee", UserRole.USER, "01");
        leader = user("leader", UserRole.TEAM_LEADER, "01");
        chief = user("chief", UserRole.ADMIN, "02");
        employeeToken = token(employee);
        leaderToken = token(leader);
        chiefToken = token(chief);
        jdbc.update("UPDATE departments SET head_user_id=? WHERE id=2", leader.getId());
        jdbc.update("UPDATE departments SET head_user_id=? WHERE id=1", chief.getId());
        jdbc.update("DELETE FROM approval_form_steps WHERE form_id=1");
        jdbc.update(
                "INSERT INTO approval_form_steps(form_id,step_order,line_kind,approver_type) VALUES(1,1,'APPROVER','DEPT_HEAD')");
        jdbc.update(
                "INSERT INTO leave_ledger(user_id,leave_year,entry_type,days,memo,created_at) VALUES(?,?,'GRANT',10,'fixture',?)",
                employee.getId(), LocalDate.now(clock).getYear(), LocalDateTime.now(clock));
        start = LocalDate.now(clock).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
    }

    private User user(String prefix, UserRole role, String department) {
        String login = prefix + UUID.randomUUID();
        User u = User.create(prefix, login, login + "@example.test", "unused-encoded", department, "사원");
        u.changeRole(role);
        return users.saveAndFlush(u);
    }

    private String token(User u) {
        return jwt.generateAccessToken(u.getId().toString(), u.getTokenVersion());
    }

    private JsonNode result(ResultActions request) throws Exception {
        return mapper.readTree(
                request.andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString())
                .path("data");
    }

    private ResultActions postAs(String path, String token, Object body) throws Exception {
        var request = post(path).header("Authorization", "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        }
        return mvc.perform(request);
    }

    private long draft(String form, Object body) throws Exception {
        return result(
                postAs("/api/v1/approval-documents", employeeToken,
                        Map.of("formCode", form, "title", "M2 fixture", "body", body)))
                .path("id")
                .asLong();
    }

    private long leave(LocalDate from, LocalDate to) throws Exception {
        return draft("LEAVE",
                Map.of("leaveType", "ANNUAL", "startDate", from.toString(), "endDate", to.toString(),
                        "reason", "fixture"));
    }

    private long submittedLeave() throws Exception {
        long id = leave(start, start);
        result(postAs("/api/v1/approval-documents/" + id + "/submit", employeeToken, null));
        return id;
    }

    private long count(String table, long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE document_id=?", Long.class, id);
    }

    private List<Integer> race(Callable<Integer> first, Callable<Integer> second) throws Exception {
        var start = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return first.call();
            });
            var b = pool.submit(() -> {
                start.await(10, TimeUnit.SECONDS);
                return second.call();
            });
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private int approve(long id) throws Exception {
        var response = postAs("/api/v1/approval-documents/" + id + "/approve", leaderToken, null)
                .andReturn()
                .getResponse();
        if (response.getStatus() == 409) {
            assertThat(mapper.readTree(response.getContentAsString()).path("error").path("code").asText())
                    .isEqualTo("DOCUMENT_STATE_CONFLICT");
        }
        return response.getStatus();
    }

    @Test
    void notificationInsertFailureRollsBackDocumentLineHistoryAndLeaveLedger() throws Exception {
        long id = submittedLeave();
        var beforeLedger = jdbc.queryForList("SELECT * FROM leave_ledger WHERE document_id=? ORDER BY id", id);
        var beforeDocument = jdbc.queryForMap("SELECT * FROM approval_documents WHERE id=?", id);
        var beforeLine = jdbc.queryForList("SELECT * FROM approval_lines WHERE document_id=? ORDER BY id", id);
        long beforeHistory = count("approval_histories", id);
        long beforeNotifications = count("notifications", id);
        doAnswer(invocation -> {
            invocation.callRealMethod();
            throw new IllegalStateException("injected notification storage failure");
        }).when(
                (NotificationService) org.springframework.test.util.AopTestUtils.getUltimateTargetObject(notifications))
                .store(eq(employee.getId()), eq(id), eq("APPROVED"), anyString());
        postAs("/api/v1/approval-documents/" + id + "/approve", leaderToken, null)
                .andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForMap("SELECT * FROM approval_documents WHERE id=?", id)).isEqualTo(beforeDocument);
        assertThat(jdbc.queryForList("SELECT * FROM approval_lines WHERE document_id=? ORDER BY id", id))
                .isEqualTo(beforeLine);
        assertThat(jdbc.queryForList("SELECT * FROM leave_ledger WHERE document_id=? ORDER BY id", id))
                .isEqualTo(beforeLedger);
        assertThat(count("approval_histories", id)).isEqualTo(beforeHistory);
        assertThat(count("notifications", id)).isEqualTo(beforeNotifications);
    }

    @Test
    void concurrentApprovalHasOne409AndNoNotificationFromLosingRequestInTenTrials() throws Exception {
        int violations = 0;
        for (int trial = 0; trial < 10; trial++) {
            long id = leave(start.plusWeeks(trial), start.plusWeeks(trial));
            result(postAs("/api/v1/approval-documents/" + id + "/submit", employeeToken, null));
            var results = race(() -> approve(id), () -> approve(id));
            if (!results.containsAll(List.of(200, 409))) {
                violations++;
            }
            assertThat(results).containsExactlyInAnyOrder(200, 409);
            assertThat(
                    jdbc.queryForObject("SELECT COUNT(*) FROM leave_ledger WHERE document_id=? AND entry_type='USE'",
                            Integer.class, id))
                    .isEqualTo(1);
            assertThat(
                    jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE document_id=? AND type='APPROVED'",
                            Integer.class, id))
                    .isEqualTo(1);
            assertThat(count("notifications", id)).isEqualTo(2);
            // one submit + one successful approval
            assertThat(
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM approval_histories WHERE document_id=? AND action='APPROVE'",
                            Integer.class, id))
                    .isEqualTo(1);
        }
        System.out.println(
                "M2_FIXED_APPROVAL_RACE trials=10 double_success=" + violations + " losing_request_notifications=0");
    }

    @Test
    void concurrentApprovalAndWithdrawalHaveOnlyOneWinner() throws Exception {
        long id = submittedLeave();
        assertThat(race(() -> approve(id),
                () -> postAs("/api/v1/approval-documents/" + id + "/withdraw", employeeToken, null)
                        .andReturn()
                        .getResponse()
                        .getStatus()))
                .containsExactlyInAnyOrder(200, 409);
        assertThat(count("notifications", id)).isEqualTo(2);
    }

    @Test
    void concurrentSubmissionsCannotSpendSameRemainingDays() throws Exception {
        jdbc.update("UPDATE leave_ledger SET days=2 WHERE user_id=? AND entry_type='GRANT'", employee.getId());
        long first = leave(start, start.plusDays(1));
        long second = leave(start.plusWeeks(1), start.plusWeeks(1).plusDays(1));
        var outcomes = race(() -> postAs("/api/v1/approval-documents/" + first + "/submit", employeeToken, null)
                .andReturn()
                .getResponse()
                .getStatus(),
                () -> postAs("/api/v1/approval-documents/" + second + "/submit", employeeToken, null)
                        .andReturn()
                        .getResponse()
                        .getStatus());
        assertThat(outcomes).containsExactlyInAnyOrder(200, 422);
        assertThat(
                jdbc.queryForObject("SELECT SUM(days) FROM leave_ledger WHERE user_id=?", BigDecimal.class,
                        employee.getId()))
                .isEqualByComparingTo("0");
    }

    @Test
    void missingHeadDoesNotAutoApproveAndSnapshotSurvivesHeadReplacement() throws Exception {
        jdbc.update("UPDATE departments SET head_user_id=NULL WHERE id IN (1,2)");
        long blocked = leave(start, start);
        postAs("/api/v1/approval-documents/" + blocked + "/submit", employeeToken, null)
                .andExpect(status().isUnprocessableEntity());
        assertThat(count("notifications", blocked)).isZero();
        assertThat(count("leave_ledger", blocked)).isZero();
        jdbc.update("UPDATE departments SET head_user_id=? WHERE id=2", leader.getId());
        long id = submittedLeave();
        jdbc.update("UPDATE departments SET head_user_id=? WHERE id=2", employee.getId());
        var detail = result(
                mvc.perform(
                        get("/api/v1/approval-documents/" + id).header("Authorization", "Bearer " + employeeToken)));
        assertThat(detail.path("line").get(0).path("approver").path("id").asLong()).isEqualTo(leader.getId());
        assertThat(detail.path("submittedAt").asText())
                .isEqualTo(LocalDateTime.now(clock).format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        assertThat(approve(id)).isEqualTo(200);
    }

    @Test
    void overtimeUsesOneDateAndTimesAndApproves() throws Exception {
        long id = draft("OVERTIME",
                Map.of("overtimeDate", start.toString(), "startTime", "18:00", "endTime", "20:00",
                        "reason", "fixture"));
        result(postAs("/api/v1/approval-documents/" + id + "/submit", employeeToken, null));
        assertThat(
                result(postAs("/api/v1/approval-documents/" + id + "/approve", leaderToken, null))
                        .path("status")
                        .asText())
                .isEqualTo("APPROVED");
    }

    @Test
    void lineRulesHandleNormalSelfFallbackAndDuplicateApprovers() throws Exception {
        var preview = Map.of("formCode", "LEAVE");
        var normal = result(postAs("/api/v1/approval-documents/line-preview", employeeToken, preview));
        assertThat(normal.path("rows").get(0).path("status").asText()).isEqualTo("OK");
        jdbc.update("UPDATE departments SET head_user_id=NULL WHERE id=2");
        var fallback = result(postAs("/api/v1/approval-documents/line-preview", employeeToken, preview));
        assertThat(fallback.path("rows").get(0).path("status").asText()).isEqualTo("FALLBACK");
        assertThat(fallback.path("finalLine").get(0).path("id").asLong()).isEqualTo(chief.getId());
        jdbc.update("UPDATE departments SET head_user_id=? WHERE id=2", employee.getId());
        var self = result(postAs("/api/v1/approval-documents/line-preview", employeeToken, preview));
        assertThat(self.path("rows").get(0).path("status").asText()).isEqualTo("SKIPPED");
        long auto = leave(start, start);
        assertThat(
                result(postAs("/api/v1/approval-documents/" + auto + "/submit", employeeToken, null))
                        .path("status")
                        .asText())
                .isEqualTo("APPROVED");
        assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM leave_ledger WHERE document_id=? AND entry_type='USE'",
                        Integer.class, auto))
                .isEqualTo(1);
        jdbc.update("UPDATE departments SET head_user_id=? WHERE id IN (1,2)", leader.getId());
        jdbc.update(
                "INSERT INTO approval_form_steps(form_id,step_order,line_kind,approver_type) VALUES(1,2,'APPROVER','UPPER_DEPT_HEAD')");
        var duplicate = result(postAs("/api/v1/approval-documents/line-preview", employeeToken, preview));
        assertThat(duplicate.path("rows").get(1).path("status").asText()).isEqualTo("SKIPPED");
        assertThat(duplicate.path("finalLine").size()).isEqualTo(1);
    }

    @Test
    void documentNumbersAreUniqueForEightDifferentDraftersSubmittingConcurrently() throws Exception {
        var ids = new ArrayList<Long>();
        var tokens = new ArrayList<String>();
        for (int i = 0; i < 8; i++) {
            var drafter = user("number", UserRole.USER, "01");
            String token = token(drafter);
            tokens.add(token);
            ids.add(
                    result(
                            postAs("/api/v1/approval-documents", token,
                                    Map.of("formCode", "GENERAL", "title", "번호 검증",
                                            "body", Map.of("content", "fixture"))))
                            .path("id")
                            .asLong());
        }
        var gate = new CyclicBarrier(8);
        var pool = Executors.newFixedThreadPool(8);
        try {
            var futures = new ArrayList<Future<String>>();
            for (int i = 0; i < 8; i++) {
                final int index = i;
                futures.add(pool.submit(() -> {
                    gate.await(10, TimeUnit.SECONDS);
                    return result(
                            postAs("/api/v1/approval-documents/" + ids.get(index) + "/submit", tokens.get(index), null))
                            .path("docNo")
                            .asText();
                }));
            }
            var numbers = new ArrayList<String>();
            for (var future : futures) {
                numbers.add(future.get(20, TimeUnit.SECONDS));
            }
            assertThat(new HashSet<>(numbers)).hasSize(8);
            assertThat(numbers).allMatch(n -> n.startsWith("GN-" + LocalDate.now(clock).getYear() + "-"));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void rejectWithdrawAndResubmitKeepLedgerInvariantAndOriginalDocument() throws Exception {
        long rejected = submittedLeave();
        postAs("/api/v1/approval-documents/" + rejected + "/reject", leaderToken, Map.of())
                .andExpect(status().isBadRequest());
        assertThat(
                result(
                        postAs("/api/v1/approval-documents/" + rejected + "/reject", leaderToken,
                                Map.of("comment", "사유")))
                        .path("status")
                        .asText())
                .isEqualTo("REJECTED");
        long copied = result(postAs("/api/v1/approval-documents/" + rejected + "/resubmit", employeeToken, null))
                .path("id")
                .asLong();
        assertThat(copied).isNotEqualTo(rejected);
        var copy = result(
                mvc.perform(
                        get("/api/v1/approval-documents/" + copied)
                                .header("Authorization", "Bearer " + employeeToken)));
        assertThat(copy.path("resubmittedFromId").asLong()).isEqualTo(rejected);
        result(postAs("/api/v1/approval-documents/" + copied + "/submit", employeeToken, null));
        result(postAs("/api/v1/approval-documents/" + copied + "/withdraw", employeeToken, null));
        long approved = submittedLeave();
        assertThat(approve(approved)).isEqualTo(200);
        long pending = leave(start.plusWeeks(1), start.plusWeeks(1));
        result(postAs("/api/v1/approval-documents/" + pending + "/submit", employeeToken, null));
        assertThat(
                jdbc.queryForObject("SELECT SUM(days) FROM leave_ledger WHERE user_id=?", BigDecimal.class,
                        employee.getId()))
                .isEqualByComparingTo("8");
        assertThat(jdbc.queryForObject("SELECT status FROM approval_documents WHERE id=?", String.class, rejected))
                .isEqualTo("REJECTED");
    }

    @Test
    void outsiderCannotReadEditOrApproveAndDeniedRequestsLeaveNoChanges() throws Exception {
        long id = submittedLeave();
        var outsider = user("outsider", UserRole.TEAM_LEADER, "03");
        String token = token(outsider);
        var before = jdbc.queryForMap("SELECT * FROM approval_documents WHERE id=?", id);
        long notes = count("notifications", id);
        long ledger = count("leave_ledger", id);
        long history = count("approval_histories", id);
        mvc.perform(get("/api/v1/approval-documents/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        postAs("/api/v1/approval-documents/" + id + "/approve", token, null).andExpect(status().isForbidden());
        postAs("/api/v1/approval-documents/" + id + "/withdraw", token, null).andExpect(status().isForbidden());
        mvc.perform(delete("/api/v1/approval-documents/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForMap("SELECT * FROM approval_documents WHERE id=?", id)).isEqualTo(before);
        assertThat(count("notifications", id)).isEqualTo(notes);
        assertThat(count("leave_ledger", id)).isEqualTo(ledger);
        assertThat(count("approval_histories", id)).isEqualTo(history);
    }

    @Test
    void previewShowsActualFailingStepWithoutSubmittingAnyPartialLine() throws Exception {
        jdbc.update(
                "INSERT INTO approval_form_steps(form_id,step_order,line_kind,approver_type) VALUES(1,2,'APPROVER','UPPER_DEPT_HEAD')");
        jdbc.update("UPDATE departments SET head_user_id=NULL WHERE id=1");
        var preview = result(
                postAs("/api/v1/approval-documents/line-preview", employeeToken, Map.of("formCode", "LEAVE")));
        assertThat(preview.path("rows").size()).isEqualTo(2);
        assertThat(preview.path("rows").get(0).path("status").asText()).isEqualTo("OK");
        assertThat(preview.path("rows").get(1).path("order").asInt()).isEqualTo(2);
        assertThat(preview.path("rows").get(1).path("status").asText()).isEqualTo("ERROR");
        assertThat(preview.path("finalLine").isEmpty()).isTrue();
        long id = leave(start, start);
        postAs("/api/v1/approval-documents/" + id + "/submit", employeeToken, null)
                .andExpect(status().isUnprocessableEntity());
        assertThat(count("approval_lines", id)).isZero();
        assertThat(count("notifications", id)).isZero();
    }
}
