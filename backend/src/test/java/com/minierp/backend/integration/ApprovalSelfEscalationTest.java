package com.minierp.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minierp.backend.domain.user.entity.User;
import com.minierp.backend.domain.user.entity.UserRole;
import com.minierp.backend.domain.user.repository.UserRepository;
import com.minierp.backend.global.security.JwtTokenProvider;
import com.minierp.backend.support.MariaDbIntegrationTest;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.*;
import static java.time.temporal.TemporalAdjusters.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@AutoConfigureMockMvc
class ApprovalSelfEscalationTest extends MariaDbIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider jwt;
    @Autowired Clock clock;
    User employee;
    User leader;
    User chief;
    LocalDate leaveDate;

    @BeforeEach
    void company() {
        employee = person("employee", UserRole.USER);
        leader = person("leader", UserRole.TEAM_LEADER);
        chief = person("chief", UserRole.ADMIN);
        jdbc.update("UPDATE users SET department_id=1 WHERE id=?", chief.getId());
        jdbc.update("UPDATE departments SET parent_id=NULL,head_user_id=? WHERE id=1", chief.getId());
        jdbc.update("UPDATE departments SET parent_id=1,head_user_id=? WHERE id=2", leader.getId());
        jdbc.update("UPDATE approval_forms SET skip_self=1,dedupe_approver=1,fallback_to_upper=1");
        // Keep the actual V3/V4 form steps, including both steps of LEAVE/GENERAL.
        leaveDate = LocalDate.now(clock).with(next(DayOfWeek.MONDAY));
        for (var person : List.of(employee, leader, chief)) {
            jdbc.update("INSERT INTO leave_ledger(user_id,leave_year,entry_type,days,memo,created_at) "
                            + "VALUES(?,?,'GRANT',10,'fixture',?)", person.getId(), leaveDate.getYear(),
                    LocalDateTime.now(clock));
        }
    }

    User person(String name, UserRole role) {
        String login = "test-" + UUID.randomUUID();
        var person = User.create(name, login, login + "@example.test", "unused-fixture-hash", "01", "사원");
        person.changeRole(role);
        return users.saveAndFlush(person);
    }

    String token(User person) {
        return jwt.generateAccessToken(person.getId().toString(), person.getTokenVersion());
    }

    ResultActions postAs(String path, User person, Object body) throws Exception {
        var request = post(path).header("Authorization", "Bearer " + token(person));
        if (body != null) request.contentType("application/json").content(mapper.writeValueAsString(body));
        return mvc.perform(request);
    }

    JsonNode data(ResultActions action) throws Exception {
        return mapper.readTree(action.andExpect(status().is2xxSuccessful()).andReturn().getResponse()
                .getContentAsString()).path("data");
    }

    Object body(String form) {
        return switch (form) {
            case "LEAVE" -> Map.of("leaveType", "ANNUAL", "startDate", leaveDate.toString(),
                    "endDate", leaveDate.toString(), "reason", "검증");
            case "OVERTIME" -> Map.of("overtimeDate", leaveDate.toString(), "startTime", "18:00",
                    "endTime", "20:00", "reason", "검증");
            case "ATTENDANCE_FIX" -> Map.of("workDate", LocalDate.now(clock).with(previous(DayOfWeek.MONDAY))
                    .toString(), "clockIn", "09:00", "clockOut", "18:00", "reason", "검증");
            default -> Map.of("content", "검증 품의");
        };
    }

    long draft(String form, User drafter) throws Exception {
        return data(postAs("/api/v1/approval-documents", drafter,
                Map.of("formCode", form, "title", "본인 결재선 검증", "body", body(form)))).path("id").asLong();
    }

    JsonNode preview(String form, User drafter) throws Exception {
        var documentPreview = data(postAs("/api/v1/approval-documents/line-preview", drafter,
                Map.of("formCode", form)));
        var formPreview = data(postAs("/api/v1/approval-forms/" + form + "/line-preview", chief,
                Map.of("drafterId", drafter.getId())));
        assertThat(documentPreview).isEqualTo(formPreview);
        return documentPreview;
    }

    List<Long> ids(JsonNode people) {
        var ids = new ArrayList<Long>();
        people.forEach(person -> ids.add(person.path("id").asLong()));
        return ids;
    }

    List<Long> pendingIds(JsonNode line) {
        var ids = new ArrayList<Long>();
        line.forEach(step -> {
            if (!step.path("status").asText().equals("SKIPPED")) {
                ids.add(step.path("approver").path("id").asLong());
            }
        });
        return ids;
    }

    static Stream<Arguments> matrix() {
        return Stream.of("OVERTIME", "ATTENDANCE_FIX", "LEAVE", "GENERAL")
                .flatMap(form -> Stream.of("employee", "leader", "chief").map(role -> Arguments.of(form, role)));
    }

    @ParameterizedTest(name = "{0}-{1}")
    @MethodSource("matrix")
    void previewAndSubmissionMatchActualCompanyHierarchy(String form, String role) throws Exception {
        User drafter = switch (role) {
            case "employee" -> employee;
            case "leader" -> leader;
            default -> chief;
        };
        boolean twoSteps = Set.of("LEAVE", "GENERAL").contains(form);
        List<Long> expected = switch (role) {
            case "employee" -> twoSteps ? List.of(leader.getId(), chief.getId()) : List.of(leader.getId());
            case "leader" -> List.of(chief.getId());
            default -> List.of();
        };
        var preview = preview(form, drafter);
        assertThat(preview.path("error").isNull()).isTrue();
        assertThat(ids(preview.path("finalLine"))).isEqualTo(expected);
        if (role.equals("leader")) {
            assertThat(preview.path("rows").get(0).path("status").asText()).isEqualTo("FALLBACK");
            assertThat(preview.path("rows").get(0).path("note").asText()).contains("본인", "상위");
            if (twoSteps) assertThat(preview.path("rows").get(1).path("status").asText()).isEqualTo("SKIPPED");
        }
        if (role.equals("chief") && twoSteps) {
            assertThat(preview.path("rows").size()).isEqualTo(2);
            assertThat(preview.path("rows").get(0).path("status").asText()).isEqualTo("SKIPPED");
            assertThat(preview.path("rows").get(0).path("note").asText()).contains("본인");
            assertThat(preview.path("rows").get(1).path("status").asText()).isEqualTo("SKIPPED");
            assertThat(preview.path("rows").get(1).path("result").isNull()).isTrue();
            assertThat(preview.path("rows").get(1).path("note").asText()).isEqualTo("상위 부서가 없어 건너뜁니다.");
        }
        long id = draft(form, drafter);
        var submitted = data(postAs("/api/v1/approval-documents/" + id + "/submit", drafter, null));
        assertThat(submitted.path("status").asText()).isEqualTo(expected.isEmpty() ? "APPROVED" : "IN_PROGRESS");
        assertThat(pendingIds(submitted.path("line"))).isEqualTo(expected);
        var detail = data(mvc.perform(get("/api/v1/approval-documents/" + id)
                .header("Authorization", "Bearer " + token(drafter))));
        assertThat(detail).isEqualTo(submitted);
        if (role.equals("chief")) {
            assertThat(detail.path("line").size()).isEqualTo(1);
            assertThat(detail.path("line").get(0).path("skipReason").asText()).isEqualTo("SELF");
            assertThat(detail.path("line").get(0).path("status").asText()).isEqualTo("SKIPPED");
            if (form.equals("LEAVE")) {
                assertThat(jdbc.queryForObject("SELECT days FROM leave_ledger WHERE document_id=? AND entry_type='USE'",
                        BigDecimal.class, id)).isEqualByComparingTo("-1");
                assertThat(jdbc.queryForObject("SELECT SUM(days) FROM leave_ledger WHERE user_id=? AND leave_year=?",
                        BigDecimal.class, chief.getId(), leaveDate.getYear())).isEqualByComparingTo("9");
            }
        } else if (role.equals("leader") && twoSteps) {
            assertThat(detail.path("line").get(1).path("skipReason").asText()).isEqualTo("DUPLICATE");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"OVERTIME", "GENERAL"})
    void selfEscalationWalksPastEmptyIntermediateDepartment(String form) throws Exception {
        jdbc.update("UPDATE departments SET parent_id=1,head_user_id=NULL WHERE id=3");
        jdbc.update("UPDATE departments SET parent_id=3 WHERE id=2");
        var preview = preview(form, leader);
        assertThat(preview.path("error").isNull()).isTrue();
        assertThat(ids(preview.path("finalLine"))).containsExactly(chief.getId());
        assertThat(preview.path("rows").get(0).path("note").asText()).contains("본인", "상위");
        var submitted = data(postAs("/api/v1/approval-documents/" + draft(form, leader) + "/submit", leader, null));
        assertThat(submitted.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(pendingIds(submitted.path("line"))).containsExactly(chief.getId());
    }

    @Test
    void existingButHeadlessUpperDepartmentStillRejectsPreviewAndSubmission() throws Exception {
        jdbc.update("UPDATE departments SET head_user_id=NULL WHERE id=1");
        var preview = preview("GENERAL", employee);
        assertThat(preview.path("error").isNull()).isFalse();
        assertThat(preview.path("rows").get(1).path("status").asText()).isEqualTo("ERROR");
        long id = draft("GENERAL", employee);
        postAs("/api/v1/approval-documents/" + id + "/submit", employee, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("error.code").value("APPROVER_NOT_FOUND"));
        assertThat(jdbc.queryForObject("SELECT status FROM approval_documents WHERE id=?", String.class, id))
                .isEqualTo("DRAFT");
    }

    @Test
    void selfWithNoActiveUpperHeadKeepsSelfSkip() throws Exception {
        var inactiveHead = person("inactive-head", UserRole.USER);
        jdbc.update("UPDATE users SET status='ON_LEAVE',department_id=1 WHERE id=?", inactiveHead.getId());
        jdbc.update("UPDATE departments SET head_user_id=? WHERE id=1", inactiveHead.getId());
        var preview = preview("OVERTIME", leader);
        assertThat(preview.path("error").isNull()).isTrue();
        assertThat(ids(preview.path("finalLine"))).isEmpty();
        var submitted = data(postAs("/api/v1/approval-documents/" + draft("OVERTIME", leader) + "/submit", leader, null));
        assertThat(submitted.path("status").asText()).isEqualTo("APPROVED");
        assertThat(submitted.path("line").get(0).path("skipReason").asText()).isEqualTo("SELF");
    }

    @Test
    void disablingSkipSelfKeepsTheOriginalApprover() throws Exception {
        jdbc.update("UPDATE approval_forms SET skip_self=0 WHERE code='OVERTIME'");
        assertThat(ids(preview("OVERTIME", leader).path("finalLine"))).containsExactly(leader.getId());
        var submitted = data(postAs("/api/v1/approval-documents/" + draft("OVERTIME", leader) + "/submit", leader, null));
        assertThat(submitted.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(pendingIds(submitted.path("line"))).containsExactly(leader.getId());
    }
}
