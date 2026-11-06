package com.minierp.backend.integration;

import com.minierp.backend.support.ApprovalApiTestSupport;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
/** M1 department checks are superseded by M2 document membership; the old paths must disappear. */
class M1RegressionTest extends ApprovalApiTestSupport {
    @Test
    void legacyReadPathsAreRemovedAndPolicyIsPreserved() throws Exception {
        for (String path : List.of("/leave/all", "/leave/my", "/overtime/all", "/overtime/my", "/overtime/1")) {
            getAs("/api/v1" + path, chiefToken).andExpect(status().isNotFound());
        }
        assertThat(data(getAs("/api/v1/leave/policy", employeeToken)).size()).isEqualTo(5);
    }

    @Test
    void legacyWritePathsAreRemoved() throws Exception {
        for (String path : List.of("/leave", "/overtime")) {
            postAs("/api/v1" + path, employeeToken, Map.of()).andExpect(status().isNotFound());
        }
        for (String path : List.of("/leave/1/approve", "/leave/1/reject", "/leave/1/cancel", "/overtime/1/approve",
                "/overtime/1/reject", "/overtime/1/cancel")) {
            mvc.perform(patch("/api/v1" + path).header("Authorization", "Bearer " + chiefToken))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void newDocumentReadAndDecisionRequireMembershipEvenForAnotherLeader() throws Exception {
        long id = submittedLeave();
        jdbc.update("UPDATE users SET department_id=3 WHERE id=?", outsider.getId());
        jdbc.update("UPDATE user_roles SET role_id=2 WHERE user_id=?", outsider.getId());
        getAs("/api/v1/approval-documents/" + id, outsideToken).andExpect(status().isForbidden());
        for (String action : List.of("approve", "reject")) {
            postAs("/api/v1/approval-documents/" + id + "/" + action, outsideToken, Map.of("comment", "검증"))
                    .andExpect(status().isForbidden());
        }
        assertThat(count("leave_ledger", id)).isEqualTo(1);
        assertThat(count("notifications", id)).isEqualTo(1);
        getAs("/api/v1/approval-documents/" + id, chiefToken).andExpect(status().isOk());
        data(postAs("/api/v1/approval-documents/" + id + "/approve", leaderToken, null));
    }

    @Test
    void homeAndCalendarReadNewDocumentsWithoutDuplicateEvents() throws Exception {
        long leave = submittedLeave();
        var pending = data(getAs("/api/v1/home", leaderToken));
        assertThat(pending.path("approvals").path("todoCount").asLong()).isPositive();
        for (String path : List.of("progress", "projects")) {
            getAs("/api/v1/dashboard/" + path, chiefToken).andExpect(status().isNotFound());
        }
        data(postAs("/api/v1/approval-documents/" + leave + "/approve", leaderToken, null));
        long overtime = draft("OVERTIME",
                Map.of("overtimeDate", start.toString(), "startTime", "09:00", "endTime", "12:00", "reason", "호환 검증"));
        submit(overtime);
        data(postAs("/api/v1/approval-documents/" + overtime + "/approve", leaderToken, null));
        var events = data(
                getAs("/api/v1/calendar/events?year=" + start.getYear() + "&month=" + start.getMonthValue(),
                        employeeToken));
        assertThat(events.size()).isEqualTo(2);
        assertThat(events.findValuesAsText("type")).containsExactlyInAnyOrder("LEAVE", "OVERTIME");
        assertThat(
                data(
                        getAs("/api/v1/calendar/events?year=" + start.getYear() + "&month=" + start.getMonthValue(),
                                outsideToken))
                        .size())
                .isZero();
    }
}
