package com.minierp.backend.integration;
import com.minierp.backend.support.AttendanceTestSupport;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class M3DashboardTest extends AttendanceTestSupport {
    @Test void dashboardOnlyChiefCanReadSummary() throws Exception {
        getAs("/api/v1/dashboard/admin-summary",leaderToken).andExpect(status().isForbidden());
        getAs("/api/v1/dashboard/admin-summary",employeeToken).andExpect(status().isForbidden());
        getAs("/api/v1/dashboard/admin-summary",chiefToken).andExpect(status().isOk());
    }
}
