package com.minierp.backend.integration;

import com.minierp.backend.support.AttendanceTestSupport;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class M3DashboardTest extends AttendanceTestSupport {
    @Test
    void dashboardIsRemovedForEveryRoleAfterM4() throws Exception {
        getAs("/api/v1/dashboard/admin-summary", leaderToken).andExpect(status().isNotFound());
        getAs("/api/v1/dashboard/admin-summary", employeeToken).andExpect(status().isNotFound());
        getAs("/api/v1/dashboard/admin-summary", chiefToken).andExpect(status().isNotFound());
    }
}
