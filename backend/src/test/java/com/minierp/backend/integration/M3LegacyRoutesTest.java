package com.minierp.backend.integration;

import com.minierp.backend.support.AttendanceTestSupport;
import org.junit.jupiter.api.Test;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.*;

class M3LegacyRoutesTest extends AttendanceTestSupport {
    @Test
    void oldAttendanceRoutesAreGoneAndCannotWrite() throws Exception {
        for (String suffix : new String[] {
            "/check-in", "/check-out"
        }) {
            postAs("/api/v1/attendance" + suffix, employeeToken, java.util.Map
                    .of("workDate", date.toString(), "clockInTime", "08:50", "clockOutTime", "18:00"))
                    .andExpect(status().isNotFound());
        }
        getAs("/api/v1/attendance/summary", employeeToken).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/attendance").header("Authorization", "Bearer " + employeeToken)
                .contentType("application/json").content("{\"clockInTime\":\"08:50\"}"))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendances WHERE user_id=?", Integer.class, employee
                .getId())).isZero();
    }
}
