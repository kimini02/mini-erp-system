package com.minierp.backend.integration;

import com.minierp.backend.support.ApprovalApiTestSupport;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class R04Group2Probe extends ApprovalApiTestSupport {
    @Test
    void documentUpdateDeleteAmountHandoverAndLinkedAdminLedger() throws Exception {
        long general = draft("GENERAL", Map.of("content", "probe", "amount", 10000L));
        var updated = data(mvc.perform(put("/api/v1/approval-documents/" + general)
                .header("Authorization", "Bearer " + employeeToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(Map.of("formCode", "GENERAL", "title", "updated probe",
                        "body", Map.of("content", "updated content", "amount", 11000L))))));
        assertThat(updated.path("body").path("amount").asLong()).isEqualTo(11000L);
        data(mvc.perform(delete("/api/v1/approval-documents/" + general)
                .header("Authorization", "Bearer " + employeeToken)));
        long leave = draft("LEAVE", Map.of("leaveType", "ANNUAL", "startDate", start.toString(),
                "endDate", start.toString(), "reason", "probe handover", "handoverUserId", outsider.getId()));
        var submitted = submit(leave);
        assertThat(submitted.path("body").path("handover").path("id").asLong()).isEqualTo(outsider.getId());
        assertThat(submitted.path("body").path("handover").path("name").asText()).isEqualTo("outside");
        var ledger = data(getAs("/api/v1/leave/admin/ledger/" + employee.getId(), chiefToken));
        assertThat(ledger.toString()).contains("\"document\":{");
        assertThat(ledger.findValues("document").stream()
                .filter(value -> !value.isNull()).map(value -> value.path("id").asLong()).toList()).contains(leave);
    }

    @Test
    void emptyInboxSkipsLineQueryAndPreservesPage() throws Exception {
        var inbox = data(getAs("/api/v1/approval-inbox?box=todo", outsideToken));
        assertThat(inbox.path("content").isArray()).isTrue();
        assertThat(inbox.path("content").size()).isZero();
        assertThat(inbox.path("page").path("totalElements").asLong()).isZero();
    }
}
