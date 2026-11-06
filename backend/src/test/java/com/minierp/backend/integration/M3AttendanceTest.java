package com.minierp.backend.integration;

import com.minierp.backend.support.AttendanceTestSupport;
import com.minierp.backend.domain.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class M3AttendanceTest extends AttendanceTestSupport {
    private void approved(String type, LocalDate day) throws Exception {
        long id = draft("LEAVE", leaveBody(type, day, day));
        submit(id);
        data(postAs("/api/v1/approval-documents/" + id + "/approve", leaderToken, null));
    }

    @Test
    void minuteBoundarySnapshotAndRepeatedCheckout() throws Exception {
        at(date, LocalTime.of(9, 10, 59));
        var in = data(postAs("/api/v1/attendance/clock-in", employeeToken, null));
        assertThat(in.path("inResult").asText()).isEqualTo("ON_TIME");
        jdbc.update(
                "UPDATE work_schedules SET start_time='10:00',end_time='19:00',late_grace_min=0 WHERE is_default=1");
        at(date, LocalTime.of(17, 59));
        var early = data(postAs("/api/v1/attendance/clock-out", employeeToken, null));
        assertThat(early.path("outResult").asText()).isEqualTo("EARLY");
        assertThat(early.path("schedule").path("startTime").asText()).isEqualTo("09:00");
        at(date, LocalTime.of(18, 0));
        assertThat(data(postAs("/api/v1/attendance/clock-out", employeeToken, null)).path("outResult").asText())
                .isEqualTo("ON_TIME");
        jdbc.update(
                "UPDATE work_schedules SET start_time='09:00',end_time='18:00',late_grace_min=10 WHERE is_default=1");
        at(date, LocalTime.of(9, 11));
        assertThat(data(postAs("/api/v1/attendance/clock-in", outsideToken, null)).path("inResult").asText())
                .isEqualTo("LATE");
    }

    @Test
    void morningAndAfternoonHalfLeaveBoundariesAndFullLeave() throws Exception {
        approved("HALF_AM", date);
        at(date, LocalTime.of(14, 10, 59));
        assertThat(data(postAs("/api/v1/attendance/clock-in", employeeToken, null)).path("inResult").asText())
                .isEqualTo("ON_TIME");
        at(date.plusDays(1), LocalTime.of(9, 0));
        approved("HALF_PM", date.plusDays(1));
        data(postAs("/api/v1/attendance/clock-in", employeeToken, null));
        at(date.plusDays(1), LocalTime.of(13, 59));
        assertThat(data(postAs("/api/v1/attendance/clock-out", employeeToken, null)).path("outResult").asText())
                .isEqualTo("EARLY");
        at(date.plusDays(1), LocalTime.of(14, 0));
        assertThat(data(postAs("/api/v1/attendance/clock-out", employeeToken, null)).path("outResult").asText())
                .isEqualTo("ON_TIME");
        at(date.plusDays(2), LocalTime.of(9, 0));
        approved("SICK", date.plusDays(2));
        postAs("/api/v1/attendance/clock-in", employeeToken, null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ATTENDANCE_ON_LEAVE"));
    }

    @Test
    void seoulMidnightAndNextDayCheckoutUseLocalDate() throws Exception {
        at(date, LocalTime.of(0, 5));
        var in = data(postAs("/api/v1/attendance/clock-in", employeeToken, null));
        assertThat(in.path("workDate").asText()).isEqualTo(date.toString());
        assertThat(in.path("clockInAt").asText()).isEqualTo(date + "T00:05:00");
        at(date.plusDays(1), LocalTime.of(0, 5));
        var out = data(postAs("/api/v1/attendance/clock-out", employeeToken, null));
        assertThat(out.path("workDate").asText()).isEqualTo(date.toString());
        assertThat(out.path("clockOutAt").asText()).isEqualTo(date.plusDays(1) + "T00:05:00");
        at(date.plusDays(1), LocalTime.of(0, 10));
        assertThat(data(postAs("/api/v1/attendance/clock-out", employeeToken, null)).path("clockOutAt").asText())
                .isEqualTo(date.plusDays(1) + "T00:10:00");
    }

    @Test
    void concurrentClockInHasExactlyOneWinnerInTenTrials() throws Exception {
        int violations = 0;
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            for (int n = 0; n < 10; n++) {
                var u = user("동시 출근", UserRole.USER);
                String token = token(u);
                var gate = new java.util.concurrent.CyclicBarrier(2);
                java.util.concurrent.Callable<Integer> call = () -> {
                    gate.await(10, java.util.concurrent.TimeUnit.SECONDS);
                    return postAs("/api/v1/attendance/clock-in", token, null).andReturn().getResponse().getStatus();
                };
                var first = pool.submit(call);
                var second = pool.submit(call);
                var status = List.of(first.get(20, java.util.concurrent.TimeUnit.SECONDS),
                        second.get(20, java.util.concurrent.TimeUnit.SECONDS));
                assertThat(status).containsExactlyInAnyOrder(200, 409);
                if (jdbc.queryForObject("SELECT COUNT(*) FROM attendances WHERE user_id=?", Integer.class,
                        u.getId()) != 1) {
                    violations++;
                }
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(violations).isZero();
        System.out.println("M3_CLOCK_IN_RACE trials=10 duplicate_rows=0 conflicts=10");
    }

    @Test
    void holidayWorkAndMonthIncludeAllDatesAndPermissionDenialWritesNothing() throws Exception {
        at(date.plusDays(5), LocalTime.of(10, 0));
        var holiday = data(postAs("/api/v1/attendance/clock-in", employeeToken, null));
        assertThat(holiday.path("dayType").asText()).isEqualTo("HOLIDAY");
        assertThat(holiday.path("inResult").asText()).isEqualTo("NONE");
        var month = YearMonth.from(date.plusDays(5));
        assertThat(data(getAs("/api/v1/attendance/me?month=" + month, employeeToken)).path("days").size())
                .isEqualTo(month.lengthOfMonth());
        jdbc.update(
                "UPDATE role_permissions SET can_create=0 WHERE role_id=3 AND menu_id=(SELECT id FROM menus WHERE code='MY_ATTENDANCE')");
        try {
            postAs("/api/v1/attendance/clock-in", outsideToken, null).andExpect(status().isForbidden());
            assertThat(
                    jdbc.queryForObject("SELECT COUNT(*) FROM attendances WHERE user_id=?", Integer.class,
                            outsider.getId()))
                    .isZero();
        } finally {
            jdbc.update(
                    "UPDATE role_permissions SET can_create=1 WHERE role_id=3 AND menu_id=(SELECT id FROM menus WHERE code='MY_ATTENDANCE')");
        }
    }
}
