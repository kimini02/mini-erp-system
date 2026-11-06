package com.minierp.backend.integration;

import com.minierp.backend.support.*;
import com.minierp.backend.domain.project.service.ProjectSummaryQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(JdbcProbe.Configuration.class)
@org.junit.jupiter.api.TestMethodOrder(org.junit.jupiter.api.MethodOrderer.OrderAnnotation.class)
class M4QueryGrowthTest extends BoardTestSupport {
    @Autowired
    NamedParameterJdbcTemplate named;
    @org.junit.jupiter.api.Order(1)
    @Test
    void summaryCostWhenUnrelatedTasksGrowTenAndOneHundredTimes() throws Exception {
        List<Long> visible = new ArrayList<>();
        List<Long> noise = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            visible.add(project(employee, 20));
        }
        for (int i = 0; i < 9; i++) {
            long p = project(chief, 0);
            jdbc.update("DELETE FROM project_members WHERE project_id=? AND user_id=?", p, employee.getId());
            noise.add(p);
        }
        String where =
                "(p.leader_id=:actor OR EXISTS(SELECT 1 FROM project_members pm WHERE pm.project_id=p.id AND pm.user_id=:actor))";
        String list = LegacyProjectSummary.SELECT + " WHERE " + where
                + " ORDER BY COALESCE(p.updated_at,p.created_at) DESC,p.id DESC LIMIT :size OFFSET :offset";
        String board = LegacyProjectSummary.SELECT + " WHERE p.id=:id AND " + where;
        var args = map("actor", employee.getId(), "today", date, "id", visible.get(0), "size", 2, "offset", 0);
        int previous = 0;
        for (int scale : new int[] {
            1, 10, 100
        }) {
            int count = (1000 * scale - 100) / 9;
            for (long p : noise) {
                List<Object[]> rows = new ArrayList<>();
                for (int n = previous + 1; n <= count; n++) {
                    rows.add(new Object[] {
                        p, n, n * 1024L, date.plusDays(1), LocalDateTime.now(clock)
                    });
                }
                jdbc.batchUpdate(
                        "INSERT INTO tasks(project_id,task_no,position,end_date,created_at,task_title,task_content,task_status,priority) VALUES(?,?,?,?,?,'부하','','DOING','MEDIUM')",
                        rows);
            }
            previous = count;
            for (String kind : List.of("list", "board")) {
                String sql = kind.equals("list") ? list : board;
                var reads = MariaDbPlan.analyze(named, sql, args);
                double ms = MariaDbPlan.median(named, sql, args);
                System.out.printf(Locale.ROOT,
                        "M4_SUMMARY_BEFORE kind=%s scale=%d total_tasks=%d median_ms=%.3f reads=%s%n", kind, scale,
                        1000 * scale, ms, reads);
                String optimized = ProjectSummaryQuery.sql(kind.equals("list") ? where : "p.id=:id AND " + where,
                        kind.equals("list"));
                var after = MariaDbPlan.analyze(named, optimized, args);
                double afterMs = MariaDbPlan.median(named, optimized, args);
                double taskReads = after.stream()
                        .filter(r -> r.table().equals("t"))
                        .mapToDouble(MariaDbPlan.Read::total)
                        .sum();
                assertThat(taskReads).isLessThanOrEqualTo(kind.equals("list") ? 40 : 20);
                assertThat(named.queryForList(optimized, args)).isEqualTo(named.queryForList(sql, args));
                System.out.printf(Locale.ROOT,
                        "M4_SUMMARY_AFTER kind=%s scale=%d total_tasks=%d task_reads=%.0f median_ms=%.3f reads=%s%n",
                        kind, scale, 1000 * scale, taskReads, afterMs, after);
            }
            try (var trace = JdbcProbe.start()) {
                getAs("/api/v1/projects?size=2", employeeToken).andExpect(status().isOk());
                System.out.printf("M4_SUMMARY_HTTP_AFTER scale=%d list_queries=%d%n", scale, trace.queries.size());
            }
            try (var trace = JdbcProbe.start()) {
                getAs("/api/v1/projects/" + visible.get(0) + "/board", employeeToken).andExpect(status().isOk());
                System.out.printf("M4_SUMMARY_HTTP_AFTER scale=%d board_queries=%d%n", scale, trace.queries.size());
            }
        }
    }

    /** Actual old HTTP path was measured before removal (13 SQL). Keep its 1+2N core comparison runnable. */
    @org.junit.jupiter.api.Order(2)
    @Test
    void legacyDashboardComparisonStillShowsOnePlusTwoQueriesPerProject() {
        for (int n = 0; n < 5; n++) {
            project(leader, 20);
        }
        try (var trace = JdbcProbe.start()) {
            var projects = db.rows("SELECT id,status,end_date FROM projects", Map.of());
            projects.stream()
                    .sorted(Comparator.comparing((Map<String, Object> p) -> !"PROGRESS".equals(p.get("status")))
                            .thenComparing(p -> (LocalDate) p.get("end_date")))
                    .limit(5)
                    .forEach(p -> {
                        db.count("SELECT COUNT(*) FROM tasks WHERE project_id=:id", p);
                        db.count("SELECT COUNT(*) FROM tasks WHERE project_id=:id AND task_status='DONE'", p);
                    });
            assertThat(trace.queries).hasSize(11);
            System.out.println(
                    "M4_DASHBOARD_COMPARISON projects=5 task_count_queries=10 core_queries=11; actual_removed_HTTP_before=13");
        }
    }
}
