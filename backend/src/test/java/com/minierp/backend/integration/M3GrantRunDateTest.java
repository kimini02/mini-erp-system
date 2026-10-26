package com.minierp.backend.integration;
import com.minierp.backend.support.AttendanceTestSupport;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class M3GrantRunDateTest extends AttendanceTestSupport {
    @Test void futureGrantDateIsRejectedBeforeLedgerOrBatchWrites() throws Exception {
        long before=jdbc.queryForObject("SELECT COUNT(*) FROM leave_ledger",Long.class);
        for(var future:java.util.List.of(date.plusDays(1),date.plusYears(1).withDayOfYear(1))) {
            postAs("/api/v1/leave/admin/grant-runs",chiefToken,Map.of("date",future.toString())).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_INPUT_VALUE"));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM leave_ledger",Long.class)).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM batch_runs",Integer.class)).isZero();
    }
}
