package com.minierp.backend.integration;

import com.minierp.backend.domain.attendance.service.AttendanceStore;
import com.minierp.backend.support.BoardTestSupport;
import com.minierp.backend.support.JdbcProbe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import java.time.*;
import static org.assertj.core.api.Assertions.assertThat;

@Import(JdbcProbe.Configuration.class)
class M5HomeCountersTest extends BoardTestSupport {
    @Autowired AttendanceStore attendance;

    private void assign(long task, long user) {
        jdbc.update("INSERT INTO task_assignments(task_id,user_id,created_at) VALUES(?,?,?)", task, user, LocalDateTime.now(clock));
    }

    @Test void monthAbsentCountExcludesOtherMonthsAndUsersOnEveryTodayResponse() throws Exception {
        date = date.withDayOfMonth(15);
        at(date, LocalTime.of(0,30)); // Still the previous UTC day.
        for (LocalDate day : new LocalDate[]{date.minusDays(1), date.minusDays(2), date.withDayOfMonth(1).minusDays(1)}) {
            attendance.insert(employee.getId(), day, attendance.schedule(employee.getId()), "WORKDAY", null, null, true, LocalDateTime.now(clock));
        }
        attendance.insert(outsider.getId(), date.minusDays(1), attendance.schedule(outsider.getId()), "WORKDAY", null, null, true, LocalDateTime.now(clock));
        assertThat(data(getAs("/api/v1/attendance/today", employeeToken)).path("monthAbsentCount").asInt(-1)).isEqualTo(2);
        assertThat(data(getAs("/api/v1/home", employeeToken)).path("today").path("monthAbsentCount").asInt(-1)).isEqualTo(2);
        assertThat(data(postAs("/api/v1/attendance/clock-in", employeeToken, null)).path("monthAbsentCount").asInt(-1)).isEqualTo(2);
        assertThat(data(postAs("/api/v1/attendance/clock-out", employeeToken, null)).path("monthAbsentCount").asInt(-1)).isEqualTo(2);
    }

    @Test void taskCountIncludesAllSevenVisibleAssignedOpenTasksWhileItemsStayAtFive() throws Exception {
        long visible = project(leader, 9), hidden = project(chief, 1);
        var tasks = taskIds(visible);
        for (int i=0;i<8;i++) assign(tasks.get(i), employee.getId());
        jdbc.update("UPDATE tasks SET task_status='DONE' WHERE id=?", tasks.get(7));
        assign(tasks.get(8), outsider.getId());
        assign(taskIds(hidden).get(0), employee.getId());
        jdbc.update("DELETE FROM project_members WHERE project_id=? AND user_id=?", hidden, employee.getId());
        try (var trace = JdbcProbe.start()) {
            var home = data(getAs("/api/v1/home", employeeToken));
            assertThat(home.path("myTasks").path("count").asInt(-1)).isEqualTo(7);
            assertThat(home.path("myTasks").path("items").size()).isEqualTo(5);
            assertThat(trace.queries).hasSize(14);
            System.out.printf("M5_HOME_COUNTERS http_queries=%d count=7 items=5%n", trace.queries.size());
        }
    }

    @Test void emptyWidgetsReturnZeroCounts() throws Exception {
        var home = data(getAs("/api/v1/home", employeeToken));
        assertThat(home.path("today").path("monthAbsentCount").asInt(-1)).isZero();
        assertThat(home.path("myTasks").path("count").asInt(-1)).isZero();
        assertThat(home.path("myTasks").path("items").isEmpty()).isTrue();
    }
}
