package com.minierp.backend.integration;

import com.minierp.backend.support.ApprovalApiTestSupport;
import org.junit.jupiter.api.Test;
import java.time.LocalDate;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class M2ReviewTest extends ApprovalApiTestSupport {
    @Test
    void missingReferenceDoesNotBlockSubmission() throws Exception {
        jdbc.update(
                "INSERT INTO approval_form_steps(form_id,step_order,line_kind,approver_type,role_id) VALUES(1,2,'REFERENCE','ROLE',2)");
        jdbc.update("DELETE FROM user_roles WHERE role_id=2");
        var preview = data(
                postAs("/api/v1/approval-documents/line-preview", employeeToken, Map.of("formCode", "LEAVE")));
        assertThat(preview.path("rows").get(1).path("status").asText()).isEqualTo("SKIPPED");
        assertThat(preview.path("rows").get(1).path("note").asText()).isEqualTo("참조할 사람이 없어 건너뜁니다.");
        long id = draft("LEAVE", leaveBody("ANNUAL", start, start));
        assertThat(submit(id).path("references").size()).isZero();
        assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM approval_lines WHERE document_id=? AND line_kind='REFERENCE'",
                        Integer.class, id))
                .isZero();
    }

    @Test
    void referenceSelfIsOmittedAndDetailSeparatesReferences() throws Exception {
        jdbc.update(
                "INSERT INTO approval_form_steps(form_id,step_order,line_kind,approver_type,user_id) VALUES(1,2,'REFERENCE','USER',?),(1,3,'REFERENCE','USER',?)",
                employee.getId(), chief.getId());
        var preview = data(
                postAs("/api/v1/approval-documents/line-preview", employeeToken, Map.of("formCode", "LEAVE")));
        assertThat(preview.path("rows").get(0).path("kind").asText()).isEqualTo("APPROVER");
        assertThat(preview.path("rows").get(0).path("result").path("department").asText()).isNotBlank();
        assertThat(preview.path("references").size()).isEqualTo(1);
        long id = draft("LEAVE", leaveBody("ANNUAL", start, start));
        var result = submit(id);
        assertThat(result.path("line").size()).isEqualTo(1);
        assertThat(result.path("references").size()).isEqualTo(1);
        assertThat(result.path("references").get(0).path("id").asLong()).isEqualTo(chief.getId());
    }

    @Test
    void fallbackExplainsSourceAndDestinationAndSkippedLineHasReason() throws Exception {
        jdbc.update("UPDATE departments SET head_user_id=NULL WHERE id=2");
        var preview = data(
                postAs("/api/v1/approval-documents/line-preview", employeeToken, Map.of("formCode", "LEAVE")));
        assertThat(preview.path("rows").get(0).path("note").asText()).contains("부서장이 비어 있어", "부서장에게 넘어갑니다.");
        jdbc.update("UPDATE departments SET head_user_id=? WHERE id=2", employee.getId());
        long id = draft("LEAVE", leaveBody("ANNUAL", start, start));
        // KI-15: a superior exists, so this self step is now pending for that superior.
        var submitted = submit(id);
        assertThat(submitted.path("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(submitted.path("line").get(0).path("status").asText()).isEqualTo("PENDING");
        assertThat(submitted.path("line").get(0).path("skipReason").isNull()).isTrue();
        assertThat(submitted.path("line").get(0).path("approver").path("id").asLong()).isEqualTo(chief.getId());
    }

    @Test
    void crossYearIsRejectedBeforeReservation() throws Exception {
        LocalDate from = LocalDate.of(start.getYear(), 12, 31);
        LocalDate to = from.plusDays(1);
        long id = draft("LEAVE", leaveBody("ANNUAL", from, to));
        postAs("/api/v1/approval-documents/" + id + "/submit", employeeToken, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("LEAVE_CROSS_YEAR"));
        assertThat(count("leave_ledger", id)).isZero();
        assertThat(count("notifications", id)).isZero();
    }

    @Test
    void zeroDayLeaveNeverCreatesLedgerAndReleaseMemoIsReadable() throws Exception {
        for (String type : java.util.List.of("SICK", "FAMILY")) {
            long id = draft("LEAVE", leaveBody(type, start, start));
            submit(id);
            assertThat(count("leave_ledger", id)).isZero();
            data(postAs("/api/v1/approval-documents/" + id + "/withdraw", employeeToken, null));
            assertThat(count("leave_ledger", id)).isZero();
        }
        long id = submittedLeave();
        assertThat(
                jdbc.queryForObject("SELECT memo FROM leave_ledger WHERE document_id=? AND entry_type='RESERVE'",
                        String.class, id))
                .startsWith("연차 ");
        data(postAs("/api/v1/approval-documents/" + id + "/reject", leaderToken, Map.of("comment", "검증")));
        assertThat(
                jdbc.queryForObject("SELECT memo FROM leave_ledger WHERE document_id=? AND entry_type='RELEASE'",
                        String.class, id))
                .isEqualTo("예약 해제 (반려)");
    }
}
