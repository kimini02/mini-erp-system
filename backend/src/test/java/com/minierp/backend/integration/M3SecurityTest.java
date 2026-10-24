package com.minierp.backend.integration;
import com.minierp.backend.support.AttendanceTestSupport;
import com.minierp.backend.domain.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
class M3SecurityTest extends AttendanceTestSupport {
    @Test void clientCannotForgeDateAndTimeInTenAttempts() throws Exception {
        int forged=0;LocalDate fake=date.minusWeeks(2);
        for(int n=0;n<10;n++) {
            var u=user("조작 검증",UserRole.USER);
            var result=data(postAs("/api/v1/attendance/clock-in",token(u),Map.of("workDate",fake.toString(),"clockInTime","08:50:00")));
            assertThat(result.path("clockInAt").asText()).isEqualTo(date+"T09:20:00");assertThat(result.path("inResult").asText()).isEqualTo("LATE");
            if(jdbc.queryForObject("SELECT COUNT(*) FROM attendances WHERE user_id=? AND work_date=?",Integer.class,u.getId(),fake)==1) forged++;
        }
        assertThat(forged).isZero();System.out.println("M3_KI04_AFTER attempts=10 forged=0");
        mvc.perform(put("/api/v1/attendance").param("workDate",fake.toString()).header("Authorization","Bearer "+employeeToken).contentType("application/json").content("{\"clockInTime\":\"08:50\",\"clockOutTime\":\"18:00\"}")).andExpect(status().isNotFound());
    }
}
