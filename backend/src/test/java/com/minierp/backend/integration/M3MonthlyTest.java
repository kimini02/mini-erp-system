package com.minierp.backend.integration;

import com.minierp.backend.support.AttendanceTestSupport;
import com.minierp.backend.domain.attendance.service.AttendanceMonthlyQuery;
import com.minierp.backend.domain.user.entity.UserRole;
import com.minierp.backend.global.persistence.SqlStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
import static com.minierp.backend.global.persistence.SqlStore.map;

class M3MonthlyTest extends AttendanceTestSupport {
    @Autowired
    AttendanceMonthlyQuery query;
    @MockitoSpyBean
    SqlStore observed;
    JsonNode row(JsonNode table, long id) {
        for (var r : table.path("rows")) {
            if (r.path("user").path("id").asLong() == id) {
                return r;
            }
        }
        return mapper.nullNode();
    }

    @Test
    void retirementAndHireBoundariesAreVisibleInTableAndEmployeeList() throws Exception {
        var month = YearMonth.from(date);
        var retired = month.atDay(15);
        jdbc.update("UPDATE users SET status='RETIRED',retired_date=?,hire_date=? WHERE id=?", retired, month.atDay(3),
                employee.getId());
        var old = user("기존 퇴사자", UserRole.USER);
        jdbc.update("UPDATE users SET status='RETIRED',hire_date=? WHERE id=?", month.atDay(1), old.getId());
        var table = data(getAs("/api/v1/attendance/monthly?month=" + month, chiefToken));
        assertThat(row(table, old.getId()).isNull()).isTrue();
        var cells = row(table, employee.getId()).path("cells");
        assertThat(cells.get(0).path("code").asText()).isEqualTo("NONE");
        for (int n = 14; n < month.lengthOfMonth(); n++) {
            assertThat(cells.get(n).path("code").asText()).isEqualTo("NONE");
        }
        jdbc.update("INSERT INTO attendances(user_id,work_date,created_at) VALUES(?,?,?)", old.getId(), month.atDay(5),
                LocalDateTime.now(clock));
        assertThat(row(data(getAs("/api/v1/attendance/monthly?month=" + month, chiefToken)), old.getId()).isNull())
                .isFalse();
        assertThat(
                row(data(getAs("/api/v1/attendance/monthly?month=" + month.plusMonths(1), chiefToken)), old.getId())
                        .isNull())
                .isTrue();
        var list = data(getAs("/api/v1/users?size=200", chiefToken));
        assertThat(list.path("content").findValuesAsText("retiredDate")).contains(retired.toString());
    }

    @Test
    void scopeDetailCsvAndRejudgeEnforceSamePermissions() throws Exception {
        data(postAs("/api/v1/attendance/clock-in", employeeToken, null));
        var detail = data(getAs("/api/v1/attendance/records/" + employee.getId() + "/" + date, leaderToken));
        assertThat(detail.path("code").asText()).isEqualTo("LATE");
        assertThat(detail.path("reason").asText()).contains("09:20", "10분");
        getAs("/api/v1/attendance/monthly?month=" + YearMonth.from(date) + "&departmentId=3", leaderToken)
                .andExpect(status().isForbidden());
        jdbc.update("UPDATE users SET department_id=3 WHERE id=?", outsider.getId());
        getAs("/api/v1/attendance/records/" + outsider.getId() + "/" + date, leaderToken)
                .andExpect(status().isForbidden());
        var bytes = getAs("/api/v1/attendance/monthly/export?month=" + YearMonth.from(date), chiefToken)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        at(date.plusDays(1), LocalTime.of(0, 10));
        long before = jdbc.queryForObject("SELECT COUNT(*) FROM batch_runs", Long.class);
        jdbc.update("UPDATE role_permissions SET can_update=1 WHERE role_id=2 AND menu_id=5");
        try {
            postAs("/api/v1/attendance/rejudge", leaderToken, Map.of("date", date.toString()))
                    .andExpect(status().isForbidden());
        } finally {
            jdbc.update("UPDATE role_permissions SET can_update=0 WHERE role_id=2 AND menu_id=5");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM batch_runs", Long.class)).isEqualTo(before);
        data(postAs("/api/v1/attendance/rejudge", chiefToken, Map.of("date", date.toString())));
        assertThat(
                data(getAs("/api/v1/attendance/records/" + employee.getId() + "/" + date, chiefToken))
                        .path("code")
                        .asText())
                .isEqualTo("MISSING");
        data(postAs("/api/v1/attendance/clock-out", employeeToken, null));
        assertThat(
                data(getAs("/api/v1/attendance/records/" + employee.getId() + "/" + date, chiefToken))
                        .path("reason")
                        .asText())
                .doesNotContain("조퇴도");
    }

    double median(String sql, Map<String, Object> args) {
        for (int i = 0; i < 3; i++) {
            db.rows(sql, args);
        }
        double[] ms = new double[9];
        for (int i = 0; i < 9; i++) {
            long begin = System.nanoTime();
            assertThat(db.rows(sql, args)).hasSize(1550);
            ms[i] = (System.nanoTime() - begin) / 1e6;
        }
        Arrays.sort(ms);
        return ms[4];
    }

    @Test
    void fiftyByThirtyOneUsesOneDataQueryAndUniqueIndex() throws Exception {
        var month = YearMonth.from(date);
        while (month.lengthOfMonth() != 31) {
            month = month.plusMonths(1);
        }
        String code = "p-" + UUID.randomUUID();
        jdbc.update("INSERT INTO departments(name,code,parent_id,sort_order,active) VALUES('성능',?,2,0,1)", code);
        long dept = jdbc.queryForObject("SELECT id FROM departments WHERE code=?", Long.class, code);
        List<Object[]> values = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            var u = user("성능 " + i, UserRole.USER);
            jdbc.update("UPDATE users SET department_id=?,hire_date=? WHERE id=?", dept, month.atDay(1), u.getId());
            for (int n = 1; n <= 31; n++) {
                values.add(new Object[] { u.getId(), month.atDay(n), month.atDay(n).atTime(9, 0),
                        LocalDateTime.now(clock) });
            }
        }
        jdbc.batchUpdate(
                "INSERT INTO attendances(user_id,work_date,clock_in_at,in_result,created_at) VALUES(?,?,?,'ON_TIME',?)",
                values);
        jdbc.execute("ANALYZE TABLE attendances");
        SqlStore target = AopTestUtils.getUltimateTargetObject(observed);
        clearInvocations(target);
        var result = data(getAs("/api/v1/attendance/monthly?month=" + month + "&departmentId=" + dept, chiefToken));
        assertThat(result.path("rows").size()).isEqualTo(50);
        for (var r : result.path("rows")) {
            assertThat(r.path("cells").size()).isEqualTo(31);
        }
        long queries = mockingDetails(target)
                .getInvocations()
                .stream()
                .filter(i -> i.getMethod().getName().equals("rows")
                        && i.getArgument(0).toString().contains("WITH RECURSIVE dates"))
                .count();
        assertThat(queries).isEqualTo(1);
        long allSqlStoreReads = mockingDetails(target)
                .getInvocations()
                .stream()
                .filter(i -> Set.of("rows", "count").contains(i.getMethod().getName()))
                .count();
        assertThat(allSqlStoreReads).isEqualTo(2);
        // department existence + one table query; no per-cell reads
        var args = map("start", month.atDay(1), "end", month.atEndOfMonth(), "dept", dept);
        String sql = query.sql("u.department_id=:dept");
        String before = sql.replace("attendances a ON", "attendances a IGNORE INDEX (uk_attendances_user_date) ON");
        var plan = db.rows("EXPLAIN " + sql, args);
        assertThat(plan).anySatisfy(r -> {
            assertThat(r.get("table")).isEqualTo("a");
            assertThat(r.get("key")).isEqualTo("uk_attendances_user_date");
        });
        double beforeMs = median(before, args);
        double afterMs = median(sql, args);
        System.out.println("M3_MONTHLY_EXPLAIN=" + plan);
        System.out.printf(Locale.ROOT,
                "M3_MONTHLY_PERF users=50 days=31 data_queries=%d samples=9 warmups=3 median_before_ms=%.3f median_after_ms=%.3f%n",
                queries, beforeMs, afterMs);
    }
}
