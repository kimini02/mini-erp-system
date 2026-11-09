package com.minierp.backend.integration;

import com.minierp.backend.support.ApprovalApiTestSupport;
import com.minierp.backend.domain.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class M2NotificationTest extends ApprovalApiTestSupport {
    @Test
    void notificationsArePrivateAndReadOperationsAreIdempotent() throws Exception {
        long doc = submittedLeave();
        var count = data(getAs("/api/v1/notifications/unread-count", leaderToken));
        assertThat(count.path("count").asLong()).isEqualTo(1);
        var list = data(getAs("/api/v1/notifications?unreadOnly=true", leaderToken));
        var row = list.path("content").get(0);
        long id = row.path("id").asLong();
        assertThat(row.path("read").asBoolean()).isFalse();
        assertThat(row.path("link").asText()).isEqualTo("/approvals/" + doc);
        assertThat(row.path("createdAt").asText()).doesNotContain("Z", "+");
        assertThat(data(getAs("/api/v1/notifications", outsideToken)).path("content").size()).isZero();
        mvc.perform(patch("/api/v1/notifications/" + id + "/read").header("Authorization", "Bearer " + outsideToken))
                .andExpect(status().isForbidden());
        assertThat(data(getAs("/api/v1/notifications/unread-count", leaderToken)).path("count").asLong()).isEqualTo(1);
        for (int n = 0; n < 2; n++) {
            mvc.perform(patch("/api/v1/notifications/" + id + "/read").header("Authorization", "Bearer " + leaderToken))
                    .andExpect(status().isOk());
        }
        assertThat(data(getAs("/api/v1/notifications/unread-count", leaderToken)).path("count").asLong()).isZero();
        data(postAs("/api/v1/approval-documents/" + doc + "/approve", leaderToken, null));
        data(postAs("/api/v1/notifications/read-all", employeeToken, null));
        assertThat(data(getAs("/api/v1/notifications/unread-count", employeeToken)).path("count").asLong()).isZero();
    }
}
