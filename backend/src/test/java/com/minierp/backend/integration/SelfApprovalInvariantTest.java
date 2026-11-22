package com.minierp.backend.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.minierp.backend.domain.user.entity.User;
import com.minierp.backend.domain.user.entity.UserRole;
import com.minierp.backend.domain.user.repository.UserRepository;
import com.minierp.backend.global.security.JwtTokenProvider;
import com.minierp.backend.support.MariaDbIntegrationTest;
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
class SelfApprovalInvariantTest extends MariaDbIntegrationTest {
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
        jdbc.update("UPDATE approval_forms SET allow_line_edit=1 WHERE code='GENERAL'");
        jdbc.update("DELETE FROM approval_form_steps WHERE form_id=3");
        jdbc.update("INSERT INTO approval_form_steps(form_id,step_order,line_kind,approver_type) "
                + "VALUES(3,1,'APPROVER','DEPT_HEAD'),(3,2,'APPROVER','UPPER_DEPT_HEAD')");
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
        if (body != null) {
            request.contentType("application/json").content(mapper.writeValueAsString(body));
        }
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

    Map<String, Object> request(List<Map<String, Object>> line) {
        var request = new LinkedHashMap<String, Object>();
        request.put("formCode", "GENERAL");
        request.put("title", "자기 승인 검사");
        request.put("body", body("GENERAL"));
        request.put("line", line);
        return request;
    }

    Map<String, Object> step(User person, String kind) {
        return Map.of("userId", person.getId(), "lineKind", kind);
    }

    long manualDraft(boolean mixed) throws Exception {
        var line = mixed ? List.of(step(chief, "APPROVER"), step(leader, "APPROVER"))
                : List.of(step(leader, "APPROVER"));
        return data(postAs("/api/v1/approval-documents", employee, request(line))).path("id").asLong();
    }

    long rejected(boolean mixed) throws Exception {
        long id = manualDraft(mixed);
        data(postAs("/api/v1/approval-documents/" + id + "/submit", employee, null));
        data(postAs("/api/v1/approval-documents/" + id + "/reject", mixed ? chief : leader,
                Map.of("comment", "다시 확인")));
        return id;
    }

    void legacySelfLine(long id) {
        // Old drafts/rejected documents may already contain the forbidden explicit self entry.
        jdbc.update("UPDATE approval_lines SET approver_id=?,approver_name=? WHERE document_id=? AND step_order=1",
                employee.getId(), employee.getUserName(), id);
    }

    List<Long> mutationCounts() {
        return Stream.of("approval_documents", "approval_lines", "approval_histories", "notifications",
                        "leave_ledger")
                .map(table -> jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Long.class)).toList();
    }

    static Stream<Arguments> manualEntrances() {
        return Stream.of("CREATE", "UPDATE", "PREVIEW", "SUBMIT", "RESUBMIT", "REVISED_UPDATE", "REVISED_SUBMIT")
                .flatMap(entry -> Stream.of(false, true).map(mixed -> Arguments.of(entry, mixed)));
    }

    @ParameterizedTest(name = "A-{0}-selfAndOther={1}")
    @MethodSource("manualEntrances")
    void everyManualEntranceRejectsSelfApprover(String entry, boolean mixed) throws Exception {
        var badLine = mixed ? List.of(step(employee, "APPROVER"), step(leader, "APPROVER"))
                : List.of(step(employee, "APPROVER"));
        long id = 0;
        String expectedState = "DRAFT";
        if (entry.equals("UPDATE") || entry.equals("SUBMIT")) {
            id = manualDraft(mixed);
        } else if (entry.equals("RESUBMIT") || entry.startsWith("REVISED_")) {
            id = rejected(mixed);
            if (entry.startsWith("REVISED_")) {
                id = data(postAs("/api/v1/approval-documents/" + id + "/resubmit", employee, null))
                        .path("id").asLong();
            } else {
                expectedState = "REJECTED";
            }
        }
        if (Set.of("SUBMIT", "RESUBMIT", "REVISED_SUBMIT").contains(entry)) {
            legacySelfLine(id);
        }
        var beforeCounts = mutationCounts();
        List<Map<String, Object>> beforeLines = jdbc.queryForList(
                "SELECT * FROM approval_lines WHERE document_id=? ORDER BY id", id);
        ResultActions result = switch (entry) {
            case "CREATE" -> postAs("/api/v1/approval-documents", employee, request(badLine));
            case "PREVIEW" -> postAs("/api/v1/approval-documents/line-preview", employee,
                    Map.of("formCode", "GENERAL", "line", badLine));
            case "UPDATE", "REVISED_UPDATE" -> mvc.perform(put("/api/v1/approval-documents/" + id)
                    .header("Authorization", "Bearer " + token(employee)).contentType("application/json")
                    .content(mapper.writeValueAsString(request(badLine))));
            case "RESUBMIT" -> postAs("/api/v1/approval-documents/" + id + "/resubmit", employee, null);
            default -> postAs("/api/v1/approval-documents/" + id + "/submit", employee, null);
        };
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("error.code").value("INVALID_APPROVAL_STEPS"))
                .andExpect(jsonPath("error.message").value("본인은 결재자로 지정할 수 없습니다."));
        assertThat(mutationCounts()).isEqualTo(beforeCounts);
        assertThat(jdbc.queryForList("SELECT * FROM approval_lines WHERE document_id=? ORDER BY id", id))
                .isEqualTo(beforeLines);
        if (id != 0) {
            assertThat(jdbc.queryForObject("SELECT status FROM approval_documents WHERE id=?", String.class, id))
                    .isEqualTo(expectedState);
        }
    }

    void selfRule(String type, User drafter) {
        jdbc.update("DELETE FROM approval_form_steps WHERE form_id=3");
        if (type.equals("USER")) {
            jdbc.update("INSERT INTO approval_form_steps(form_id,step_order,line_kind,approver_type,user_id) "
                    + "VALUES(3,1,'APPROVER','USER',?)", drafter.getId());
        } else {
            String code = "T" + UUID.randomUUID().toString().replace("-", "");
            jdbc.update("INSERT INTO roles(code,name,is_system) VALUES(?, '검증 전용 역할',0)", code);
            long role = jdbc.queryForObject("SELECT id FROM roles WHERE code=?", Long.class, code);
            jdbc.update("INSERT INTO user_roles(user_id,role_id) VALUES(?,?)", drafter.getId(), role);
            jdbc.update("INSERT INTO approval_form_steps(form_id,step_order,line_kind,approver_type,role_id) "
                    + "VALUES(3,1,'APPROVER','ROLE',?)", role);
        }
    }

    static Stream<Arguments> zeroApproverPaths() {
        return Stream.of("USER", "ROLE").flatMap(type -> Stream.of("NEW", "RESUBMIT", "REVISED_SUBMIT")
                .map(entry -> Arguments.of(type, entry)));
    }

    @ParameterizedTest(name = "B-{0}-{1}")
    @MethodSource("zeroApproverPaths")
    void ruleSelfWithoutPendingIsRejectedAtPreviewAndSubmission(String type, String entry) throws Exception {
        long id;
        if (entry.equals("NEW")) {
            id = draft("GENERAL", employee);
        } else {
            id = draft("GENERAL", employee);
            data(postAs("/api/v1/approval-documents/" + id + "/submit", employee, null));
            data(postAs("/api/v1/approval-documents/" + id + "/reject", leader, Map.of("comment", "반려")));
            if (entry.equals("REVISED_SUBMIT")) {
                id = data(postAs("/api/v1/approval-documents/" + id + "/resubmit", employee, null))
                        .path("id").asLong();
            }
        }
        selfRule(type, employee);
        if (entry.equals("RESUBMIT")) {
            long originalId = id;
            var copy = data(postAs("/api/v1/approval-documents/" + id + "/resubmit", employee, null)
                    .andExpect(status().isCreated()));
            assertThat(copy.path("status").asText()).isEqualTo("DRAFT");
            id = copy.path("id").asLong();
            assertThat(id).isNotEqualTo(originalId);
            assertThat(jdbc.queryForObject("SELECT status FROM approval_documents WHERE id=?",
                    String.class, originalId)).isEqualTo("REJECTED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_lines WHERE document_id=?",
                    Integer.class, id)).isZero();
        }
        var beforeCounts = mutationCounts();
        var preview = preview("GENERAL", employee);
        var result = postAs("/api/v1/approval-documents/" + id + "/submit", employee, null)
                .andReturn().getResponse();
        long documentId = id;
        Assertions.assertAll(
                () -> assertThat(preview.path("error").isNull()).isFalse(),
                () -> assertThat(preview.path("rows").findValuesAsText("status")).contains("ERROR"),
                () -> assertThat(ids(preview.path("finalLine"))).isEmpty(),
                () -> assertThat(result.getStatus()).isEqualTo(422),
                () -> assertThat(mapper.readTree(result.getContentAsString()).path("error").path("code").asText())
                        .isEqualTo("APPROVER_NOT_FOUND"),
                () -> assertThat(mutationCounts()).isEqualTo(beforeCounts),
                () -> assertThat(jdbc.queryForObject("SELECT status FROM approval_documents WHERE id=?",
                        String.class, documentId)).isEqualTo("DRAFT"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_lines WHERE document_id=?",
                Integer.class, id)).isZero();
    }

    @Test
    void rejectedRuleDraftCanBeCopiedWhileUpperDepartmentHeadIsVacant() throws Exception {
        long original = draft("LEAVE", employee);
        data(postAs("/api/v1/approval-documents/" + original + "/submit", employee, null));
        data(postAs("/api/v1/approval-documents/" + original + "/reject", leader, Map.of("comment", "반려")));
        var originalLines = jdbc.queryForList("SELECT * FROM approval_lines WHERE document_id=? ORDER BY id",
                original);
        jdbc.update("UPDATE departments SET head_user_id=NULL WHERE id=1");
        // A copied rule draft, just like a new draft, resolves its line only at submission.
        postAs("/api/v1/approval-documents", employee,
                Map.of("formCode", "LEAVE", "title", "새 임시 문서", "body", body("LEAVE")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("data.status").value("DRAFT"));
        var copied = data(postAs("/api/v1/approval-documents/" + original + "/resubmit", employee, null)
                .andExpect(status().isCreated()));
        long copyId = copied.path("id").asLong();
        assertThat(copyId).isNotEqualTo(original);
        assertThat(copied.path("status").asText()).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_lines WHERE document_id=?",
                Integer.class, copyId)).isZero();
        assertThat(jdbc.queryForObject("SELECT resubmitted_from_id FROM approval_documents WHERE id=?",
                Long.class, copyId)).isEqualTo(original);
        var beforeCounts = mutationCounts();
        postAs("/api/v1/approval-documents/" + copyId + "/submit", employee, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("error.code").value("APPROVER_NOT_FOUND"));
        assertThat(mutationCounts()).isEqualTo(beforeCounts);
        assertThat(jdbc.queryForObject("SELECT status FROM approval_documents WHERE id=?", String.class, copyId))
                .isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_lines WHERE document_id=?",
                Integer.class, copyId)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM approval_documents WHERE id=?", String.class, original))
                .isEqualTo("REJECTED");
        assertThat(jdbc.queryForList("SELECT * FROM approval_lines WHERE document_id=? ORDER BY id", original))
                .isEqualTo(originalLines);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "ROLE"})
    void disabledSkipSelfKeepsRuleDrafterPending(String type) throws Exception {
        selfRule(type, employee);
        jdbc.update("UPDATE approval_forms SET skip_self=0 WHERE code='GENERAL'");
        assertThat(ids(preview("GENERAL", employee).path("finalLine"))).containsExactly(employee.getId());
        var submitted = data(postAs("/api/v1/approval-documents/" + draft("GENERAL", employee) + "/submit",
                employee, null));
        assertThat(submitted.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(submitted.path("line").get(0).path("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void manualSelfIsRejectedEvenWhenSkipSelfIsDisabled() throws Exception {
        jdbc.update("UPDATE approval_forms SET skip_self=0 WHERE code='GENERAL'");
        postAs("/api/v1/approval-documents", employee, request(List.of(step(employee, "APPROVER"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("error.message").value("본인은 결재자로 지정할 수 없습니다."));
    }

    @Test
    void ownReferenceIsSilentlyOmitted() throws Exception {
        var request = request(List.of(step(leader, "APPROVER"), step(employee, "REFERENCE")));
        long id = data(postAs("/api/v1/approval-documents", employee, request)).path("id").asLong();
        var submitted = data(postAs("/api/v1/approval-documents/" + id + "/submit", employee, null));
        assertThat(submitted.path("references")).isEmpty();
        assertThat(submitted.path("status").asText()).isEqualTo("IN_PROGRESS");
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "ROLE"})
    void representativeRuleSelfMayAutoApprove(String type) throws Exception {
        selfRule(type, chief);
        assertThat(preview("GENERAL", chief).path("error").isNull()).isTrue();
        var submitted = data(postAs("/api/v1/approval-documents/" + draft("GENERAL", chief) + "/submit", chief, null));
        assertThat(submitted.path("status").asText()).isEqualTo("APPROVED");
    }

    @ParameterizedTest
    @ValueSource(strings = {"VACANT", "ON_LEAVE", "OTHER_ACTIVE", "CYCLE"})
    void zeroApproverChecksActualHierarchyDespiteUserRule(String upper) throws Exception {
        selfRule("USER", employee);
        jdbc.update("UPDATE departments SET head_user_id=? WHERE id=2", employee.getId());
        if (upper.equals("VACANT")) {
            jdbc.update("UPDATE departments SET head_user_id=NULL WHERE id=1");
        }
        if (upper.equals("ON_LEAVE")) {
            var inactive = person("inactive", UserRole.USER);
            jdbc.update("UPDATE users SET status='ON_LEAVE' WHERE id=?", inactive.getId());
            jdbc.update("UPDATE departments SET head_user_id=? WHERE id=1", inactive.getId());
        }
        if (upper.equals("CYCLE")) {
            jdbc.update("UPDATE departments SET head_user_id=?,parent_id=2 WHERE id=1", employee.getId());
        }
        assertThat(preview("GENERAL", employee).path("error").isNull()).isFalse();
        long id = draft("GENERAL", employee);
        postAs("/api/v1/approval-documents/" + id + "/submit", employee, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("error.code").value("APPROVER_NOT_FOUND"));
        assertThat(jdbc.queryForObject("SELECT status FROM approval_documents WHERE id=?", String.class, id))
                .isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_lines WHERE document_id=?", Integer.class, id))
                .isZero();
    }

    @Test
    void finalCheckUsesCurrentDepartmentRatherThanDraftSnapshot() throws Exception {
        selfRule("USER", chief);
        long id = draft("GENERAL", chief);
        jdbc.update("UPDATE users SET department_id=2 WHERE id=?", chief.getId());
        assertThat(preview("GENERAL", chief).path("error").isNull()).isFalse();
        postAs("/api/v1/approval-documents/" + id + "/submit", chief, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("error.code").value("APPROVER_NOT_FOUND"));
        assertThat(jdbc.queryForObject("SELECT status FROM approval_documents WHERE id=?", String.class, id))
                .isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM approval_lines WHERE document_id=?", Integer.class, id))
                .isZero();
    }

    @Test
    void allActiveHeadsInChainMayBeDrafterWithoutCheckingRoleOrRootDepartmentId() throws Exception {
        selfRule("USER", employee);
        jdbc.update("UPDATE departments SET head_user_id=? WHERE id IN (1,2)", employee.getId());
        assertThat(preview("GENERAL", employee).path("error").isNull()).isTrue();
        var submitted = data(postAs("/api/v1/approval-documents/" + draft("GENERAL", employee)
                + "/submit", employee, null));
        assertThat(submitted.path("status").asText()).isEqualTo("APPROVED");
    }

    @Test
    void auditSelectFindsLegacyUnsafeSnapshotAndExcludesReviewedAndRepresentativeDocuments() throws Exception {
        long unsafe = manualDraft(false);
        // Persist only the old auto-approval shape; the pre-fix API reproduction is recorded in KI-17.
        jdbc.update("UPDATE approval_lines SET approver_id=?,status='SKIPPED',skip_reason='SELF',acted_at=NULL "
                + "WHERE document_id=?", employee.getId(), unsafe);
        jdbc.update("UPDATE approval_documents SET status='APPROVED' WHERE id=?", unsafe);
        long reviewed = manualDraft(false);
        data(postAs("/api/v1/approval-documents/" + reviewed + "/submit", employee, null));
        assertThat(data(postAs("/api/v1/approval-documents/" + reviewed + "/approve", leader, Map.of()))
                .path("status").asText()).isEqualTo("APPROVED");
        long representative = draft("OVERTIME", chief);
        assertThat(data(postAs("/api/v1/approval-documents/" + representative + "/submit", chief, null))
                .path("status").asText()).isEqualTo("APPROVED");
        String sql;
        try (var input = getClass().getResourceAsStream("/approval/self-approval-audit.sql")) {
            sql = new String(input.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        }
        var found = jdbc.queryForList(sql).stream().map(row -> ((Number) row.get("id")).longValue()).toList();
        assertThat(found).contains(unsafe).doesNotContain(reviewed, representative);
    }
}
