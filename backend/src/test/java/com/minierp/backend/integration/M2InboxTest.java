package com.minierp.backend.integration;

import com.minierp.backend.support.ApprovalApiTestSupport;
import com.minierp.backend.domain.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class M2InboxTest extends ApprovalApiTestSupport {
    @Test
    void allTabsAndCountsFollowDocumentMembershipAndState() throws Exception {
        jdbc.update(
                "INSERT INTO approval_form_steps(form_id,step_order,line_kind,approver_type,user_id) VALUES(1,2,'APPROVER','USER',?),(1,3,'REFERENCE','USER',?)",
                chief.getId(), outsider.getId());
        long id = submittedLeave();
        var todo = data(getAs("/api/v1/approval-inbox?box=todo", leaderToken));
        var row = todo.path("content").get(0);
        assertThat(row.path("id").asLong()).isEqualTo(id);
        assertThat(row.path("formCode").asText()).isEqualTo("LEAVE");
        assertThat(row.path("formName").asText()).isEqualTo("휴가");
        assertThat(row.path("updatedAt").asText()).doesNotContain("Z", "+");
        assertThat(row.path("steps").toString()).isEqualTo("[\"DONE\",\"CURRENT\",\"TODO\"]");
        assertThat(data(getAs("/api/v1/approval-inbox?box=todo", outsideToken)).path("content").size()).isZero();
        assertThat(data(getAs("/api/v1/approval-inbox?box=reference", outsideToken)).path("content").size())
                .isEqualTo(1);
        assertThat(data(getAs("/api/v1/approval-inbox/counts", employeeToken)).path("mine").asLong()).isEqualTo(1);
        data(postAs("/api/v1/approval-documents/" + id + "/approve", leaderToken, null));
        assertThat(data(getAs("/api/v1/approval-inbox/counts", leaderToken)).path("in-progress").asLong()).isEqualTo(1);
        data(postAs("/api/v1/approval-documents/" + id + "/reject", chiefToken, Map.of("comment", "반려")));
        assertThat(data(getAs("/api/v1/approval-inbox/counts", leaderToken)).path("done").asLong()).isEqualTo(1);
        assertThat(data(getAs("/api/v1/approval-inbox/counts", outsideToken)).path("done").asLong()).isZero();
        long older = draft("GENERAL", Map.of("content", "예전"));
        long newer = draft("GENERAL", Map.of("content", "최근"));
        jdbc.update("UPDATE approval_documents SET updated_at=DATE_ADD(updated_at,INTERVAL 1 HOUR) WHERE id=?", older);
        var drafts = data(getAs("/api/v1/approval-inbox?box=drafts", employeeToken));
        assertThat(drafts.path("content").get(0).path("id").asLong()).isEqualTo(older);
        assertThat(drafts.path("page").path("totalElements").asInt()).isEqualTo(2);
        assertThat(
                data(getAs("/api/v1/approval-inbox?box=mine&formCode=OVERTIME", employeeToken)).path("content").size())
                .isZero();
        getAs("/api/v1/approval-inbox?box=unknown", employeeToken).andExpect(status().isBadRequest());
    }
}
