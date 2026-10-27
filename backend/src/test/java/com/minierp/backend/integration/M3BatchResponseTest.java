package com.minierp.backend.integration;
import com.minierp.backend.support.AttendanceTestSupport;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class M3BatchResponseTest extends AttendanceTestSupport {
    @Test void grantReturnsItsPersistedRunWithLocalTimestamps() throws Exception { assertResponse("/leave/admin/grant-runs"); }
    @Test void rejudgeReturnsItsPersistedRunWithLocalTimestamps() throws Exception { assertResponse("/attendance/rejudge"); }
    private void assertResponse(String endpoint) throws Exception {
            var day=endpoint.contains("grant")?date:date.minusDays(1);
            var response=data(postAs("/api/v1"+endpoint,chiefToken,Map.of("date",day.toString())));
            List<String> names=new ArrayList<>();response.fieldNames().forEachRemaining(names::add);
            assertThat(names).containsExactlyInAnyOrder("id","jobName","targetDate","status","processedCount","skippedCount","message","startedAt","finishedAt");
            var stored=db.rows("SELECT id,job_name AS jobName,target_date AS targetDate,status,processed_count AS processedCount,skipped_count AS skippedCount,message,started_at AS startedAt,finished_at AS finishedAt FROM batch_runs WHERE id=:id",Map.of("id",response.path("id").asLong())).get(0);
            assertThat(response).isEqualTo(mapper.readTree(mapper.writeValueAsString(stored)));
            assertThat(response.path("targetDate").asText()).isEqualTo(day.toString());
            assertThat(response.path("startedAt").asText()).isEqualTo(date+"T09:20:00");
            if(endpoint.contains("grant"))assertThat(data(getAs("/api/v1/leave/admin/grant-runs",chiefToken)).get(0)).isEqualTo(response);
            var again=data(postAs("/api/v1"+endpoint,chiefToken,Map.of("date",day.toString())));
            assertThat(again.path("id").asLong()).isGreaterThan(response.path("id").asLong());
    }
}
