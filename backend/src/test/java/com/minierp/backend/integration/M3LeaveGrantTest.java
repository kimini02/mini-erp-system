package com.minierp.backend.integration;

import com.minierp.backend.support.AttendanceTestSupport;
import com.minierp.backend.domain.leave.service.*;
import com.minierp.backend.domain.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.*;
import java.util.*;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class M3LeaveGrantTest extends AttendanceTestSupport {
    @Autowired
    LeaveGrantService grants;
    long hired(LocalDate hire, String status) {
        var u = user("부여 검증", UserRole.USER);
        jdbc.update("UPDATE users SET hire_date=?,status=? WHERE id=?", hire, status, u.getId());
        return u.getId();
    }

    BigDecimal amount(String key) {
        return jdbc.queryForObject("SELECT days FROM leave_ledger WHERE grant_key=?", BigDecimal.class, key);
    }

    @Test
    void monthEndAndAllElevenMonthlyGrantsUseOriginalHireDay() {
        var hire = LocalDate.of(date.getYear(), 1, 31);
        long id = hired(hire, "ACTIVE");
        at(hire.plusMonths(1), LocalTime.of(0, 30));
        grants.runToday();
        assertThat(amount("M-" + id + "-1")).isEqualByComparingTo("1");
        at(hire.plusMonths(11), LocalTime.of(0, 30));
        grants.runToday();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM leave_ledger WHERE user_id=? AND grant_key LIKE 'M-%'",
                Integer.class, id)).isEqualTo(11);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM leave_ledger WHERE user_id=? AND grant_key LIKE 'A-%'",
                Integer.class, id)).isZero();
        System.out.println("M3_GRANT_MONTHLY january31_to=" + hire.plusMonths(1) + " monthly_count=11 days=11");
    }

    @Test
    void anniversaryProportionAndJanuaryOneAwardFifteenSixteenTwentyFive() {
        int year = date.getYear() + 1;
        var july = LocalDate.of(year - 1, 7, 1);
        long proportional = hired(july, "ON_LEAVE");
        var through = LocalDate.of(year, 7, 1);
        at(through, LocalTime.of(0, 30));
        grants.runToday();
        assertThat(amount("A-" + proportional)).isEqualByComparingTo("8");
        List<Long> ids = new ArrayList<>();
        for (int years : List.of(1, 3, 21)) {
            ids.add(hired(LocalDate.of(year - years, 1, 1), "ACTIVE"));
        }
        grants.run(LocalDate.of(year, 1, 1));
        assertThat(amount("A-" + ids.get(0))).isEqualByComparingTo("15");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM leave_ledger WHERE grant_key=?", Integer.class, "Y-" + ids
                .get(0) + "-" + year)).isZero();
        assertThat(amount("Y-" + ids.get(1) + "-" + year)).isEqualByComparingTo("16");
        assertThat(amount("Y-" + ids.get(2) + "-" + year)).isEqualByComparingTo("25");
        System.out
                .println(
                "M3_GRANT_RULES july_anniversary=8 january_anniversary=15 tenure3=16 tenure21=25 duplicate_jan1=0");
    }

    @Test
    void backfillOnlyCurrentYearSkipsUnknownHireAndIsIdempotent() throws Exception {
        var hire = LocalDate.of(date.getYear() - 1, 11, 1);
        long id = hired(hire, "ACTIVE");
        long missing = hired(null, "ACTIVE");
        long retired = hired(hire, "RETIRED");
        var run = LocalDate.of(date.getYear(), 1, 2);
        at(run, LocalTime.of(0, 30));
        data(postAs("/api/v1/leave/admin/grant-runs", chiefToken, Map.of("date", run.toString())));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM leave_ledger WHERE grant_key=?", Integer.class, "M-" + id
                + "-1")).isZero();
        assertThat(amount("M-" + id + "-2")).isEqualByComparingTo("1");
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM leave_ledger", Long.class);
        grants.runToday();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM leave_ledger", Long.class)).isEqualTo(count);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM leave_ledger WHERE user_id IN (?,?)", Integer.class,
                missing, retired)).isZero();
        var runs = data(getAs("/api/v1/leave/admin/grant-runs", chiefToken));
        assertThat(runs.get(0).path("message").asText()).contains(Long.toString(missing));
        assertThat(runs.get(0).path("processedCount").asInt()).isZero();
    }

    @Test
    void twoConcurrentBatchRunsIssueEachKeyOnlyOnceInTenTrials() throws Exception {
        jdbc.update("UPDATE users SET status='RETIRED'");
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            for (int n = 0; n < 10; n++) {
                long id = hired(date.minusMonths(1), "ACTIVE");
                var gate = new java.util.concurrent.CyclicBarrier(2);
                java.util.concurrent.Callable<Object> call = () -> {
                    gate.await(10, java.util.concurrent.TimeUnit.SECONDS);
                    return grants.run(date);
                };
                var a = pool.submit(call);
                var b = pool.submit(call);
                a.get(30, java.util.concurrent.TimeUnit.SECONDS);
                b.get(30, java.util.concurrent.TimeUnit.SECONDS);
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM leave_ledger WHERE grant_key=?", Integer.class,
                        "M-" + id + "-1")).isEqualTo(1);
            }
        } finally {
            pool.shutdownNow();
        }
        System.out.println("M3_GRANT_CONCURRENCY trials=10 duplicate_grants=0");
    }

    @Test
    void adminAdjustmentChecksScopeHalfDaysAndNegativeBalanceBeforeWriting() throws Exception {
        var request = new HashMap<String, Object>();
        request.put("userId", employee.getId());
        request.put("year", date.getYear());
        request.put("days", new BigDecimal("-0.5"));
        request.put("memo", "조정 검증");
        postAs("/api/v1/leave/admin/adjustments", employeeToken, request).andExpect(status().isForbidden());
        var changed = data(postAs("/api/v1/leave/admin/adjustments", chiefToken, request));
        assertThat(changed.path("available").decimalValue()).isEqualByComparingTo("9.5");
        assertThat(jdbc.queryForObject("SELECT created_by FROM leave_ledger WHERE id=?", Long.class, changed.path("id")
                .asLong())).isEqualTo(chief.getId());
        long count = jdbc.queryForObject("SELECT COUNT(*) FROM leave_ledger", Long.class);
        request.put("days", -10);
        postAs("/api/v1/leave/admin/adjustments", chiefToken, request).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code").value("LEAVE_BALANCE_NEGATIVE"));
        request.put("days", 0.3);
        postAs("/api/v1/leave/admin/adjustments", chiefToken, request).andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM leave_ledger", Long.class)).isEqualTo(count);
        jdbc.update("UPDATE users SET department_id=3 WHERE id=?", outsider.getId());
        getAs("/api/v1/leave/admin/ledger/" + outsider.getId(), leaderToken).andExpect(status().isForbidden());
        getAs("/api/v1/leave/admin/balances?departmentId=3", leaderToken).andExpect(status().isForbidden());
        jdbc.update("UPDATE role_permissions SET can_update=1,can_create=1 WHERE role_id=2 AND menu_id=6");
        try {
            postAs("/api/v1/leave/admin/grant-runs", leaderToken, Map.of("date", date.toString()))
                    .andExpect(status().isForbidden());
            request.put("userId", outsider.getId());
            request.put("days", 1);
            postAs("/api/v1/leave/admin/adjustments", leaderToken, request).andExpect(status().isForbidden());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM leave_ledger", Long.class)).isEqualTo(count);
        } finally {
            jdbc.update("UPDATE role_permissions SET can_update=0,can_create=0 WHERE role_id=2 AND menu_id=6");
        }
        var balances = data(getAs("/api/v1/leave/admin/balances?year=" + date.getYear() + "&q=employee", chiefToken));
        assertThat(balances.path("content")).anySatisfy(r -> {
            assertThat(r.path("user").path("id").asLong()).isEqualTo(employee.getId());
            assertThat(r.path("adjusted").decimalValue()).isEqualByComparingTo("-0.5");
        });
        assertThat(data(getAs("/api/v1/leave/admin/ledger/" + employee.getId() + "?year=" + date.getYear(), chiefToken))
                .get(0).path("type").asText()).isEqualTo("ADJUST");
    }

    @Test
    void legacyGrantSkipsWholeYearAndNextJanuaryResumesWithoutDeletingLedger() throws Exception {
        jdbc.update("UPDATE users SET status='RETIRED' WHERE id<>?", chief.getId());
        jdbc.update("UPDATE users SET hire_date=? WHERE id=?", date.minusYears(3), chief.getId());
        long id = hired(date.minusYears(3), "ACTIVE");
        long missing = hired(null, "ACTIVE");
        jdbc
                .update(
                "INSERT INTO leave_ledger(user_id,leave_year,entry_type,days,memo,created_at) VALUES(?,?,'GRANT',7,'v1 이관',?)",
                id, date.getYear(), LocalDateTime.now(clock));
        grants.run(date);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM leave_ledger WHERE user_id=? AND grant_key IS NOT NULL",
                Integer.class, id)).isZero();
        var run = data(getAs("/api/v1/leave/admin/grant-runs", chiefToken)).get(0);
        assertThat(run.path("skippedCount").asInt()).isEqualTo(2);
        assertThat(run.path("message").asText()).contains("입사일 없음: [" + missing + "]", "옛 부여 있음: [" + id + "]");
        var next = LocalDate.of(date.getYear() + 1, 1, 1);
        at(next, LocalTime.of(0, 30));
        grants.runToday();
        assertThat(amount("Y-" + id + "-" + next.getYear())).isEqualByComparingTo("16");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM leave_ledger WHERE user_id=? AND grant_key IS NULL AND days=7", Integer.class,
                id)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM leave_ledger WHERE user_id=? AND grant_key IS NOT NULL AND leave_year<?", Integer
                .class, id, next.getYear())).isZero();
    }
}
