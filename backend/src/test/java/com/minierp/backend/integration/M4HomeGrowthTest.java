package com.minierp.backend.integration;

import com.minierp.backend.support.*;
import com.minierp.backend.domain.home.repository.HomeQueries;
import com.minierp.backend.domain.project.repository.ProjectSummaryQuery;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;
import static org.assertj.core.api.Assertions.*;

@Import(JdbcProbe.Configuration.class)
class M4HomeGrowthTest extends BoardTestSupport {
    @Autowired
    NamedParameterJdbcTemplate named;
    @Test
    void homeSqlCountStaysFixedAndAnalyzesScopedWorkAtOneTenAndHundredTimes() throws Exception {
        long mine = project(employee, 0);
        long noise = project(chief, 0);
        jdbc.update("DELETE FROM project_members WHERE project_id=? AND user_id=?", noise, employee.getId());
        jdbc.update("UPDATE users SET department_id=1 WHERE id=?", outsider.getId());
        String scope =
                "(p.leader_id=:actor OR EXISTS(SELECT 1 FROM project_members pm WHERE pm.project_id=p.id AND pm.user_id=:actor))";
        var args = map("actor", employee.getId(), "department", 2, "today", date, "from", date, "to", date.plusDays(4));
        int previous = 0;
        Integer queryCount = null;
        for (int scale : new int[] { 1, 10, 100 }) {
            for (var pair : List.of(new long[] { mine, 20, employee.getId() },
                    new long[] { noise, 980, outsider.getId() })) {
                List<Object[]> rows = new ArrayList<>();
                for (int n = previous * (int) pair[1] + 1; n <= scale * pair[1]; n++) {
                    rows.add(new Object[] { pair[0], n, n * 1024L, date.plusDays(2), LocalDateTime.now(clock) });
                }
                for (int offset = 0; offset < rows.size(); offset += 1000) {
                    jdbc.batchUpdate(
                            "INSERT INTO tasks(project_id,task_no,position,end_date,created_at,task_title,task_content,task_status,priority) VALUES(?,?,?,?,?,'홈 부하','','DOING','MEDIUM')",
                            rows.subList(offset, Math.min(rows.size(), offset + 1000)));
                }
                jdbc.update(
                        "INSERT INTO task_assignments(task_id,user_id,created_at) SELECT id,?,? FROM tasks WHERE project_id=? AND task_no>?",
                        pair[2], LocalDateTime.now(clock), pair[0], previous * pair[1]);
            }
            long lastDocument = jdbc.queryForObject("SELECT COALESCE(MAX(id),0) FROM approval_documents", Long.class);
            List<Object[]> docs = new ArrayList<>();
            List<Object[]> notes = new ArrayList<>();
            for (int n = previous * 100; n < scale * 100; n++) {
                docs.add(new Object[] { n % 10 == 0 ? employee.getId() : chief.getId(), LocalDateTime.now(clock),
                        LocalDateTime.now(clock) });
                notes.add(new Object[] { employee.getId(), LocalDateTime.now(clock) });
            }
            jdbc.batchUpdate(
                    "INSERT INTO approval_documents(form_id,drafter_id,drafter_dept_id,title,status,current_step,submitted_at,created_at) VALUES(1,?,2,'부하','IN_PROGRESS',1,?,?)",
                    docs);
            jdbc.update(
                    "INSERT INTO leave_requests(document_id,leave_type,start_date,end_date,used_days,reason,created_at) SELECT id,'ANNUAL',DATE_SUB(?,INTERVAL id DAY),DATE_SUB(?,INTERVAL id DAY),1,'비공개',? FROM approval_documents WHERE id>?",
                    date, date, LocalDateTime.now(clock), lastDocument);
            jdbc.batchUpdate(
                    "INSERT INTO notifications(user_id,type,title,link,created_at) VALUES(?,'TASK_ASSIGNED','부하','/projects/1',?)",
                    notes);
            previous = scale;
            long queries;
            try (var trace = JdbcProbe.start()) {
                var home = data(getAs("/api/v1/home", employeeToken));
                assertThat(home.path("myTasks").path("items").size()).isEqualTo(5);
                queries = trace.queries.size();
            }
            if (queryCount == null) {
                queryCount = (int) queries;
            }
            assertThat(queries).isEqualTo(queryCount.longValue()).isLessThanOrEqualTo(15);
            for (int n = 0; n < 3; n++) {
                data(getAs("/api/v1/home", employeeToken));
            }
            long[] elapsed = new long[9];
            for (int n = 0; n < 9; n++) {
                long begin = System.nanoTime();
                data(getAs("/api/v1/home", employeeToken));
                elapsed[n] = System.nanoTime() - begin;
            }
            Arrays.sort(elapsed);
            System.out.printf(Locale.ROOT,
                    "M4_HOME_GROWTH scale=%d tasks=%d documents=%d notifications=%d queries=%d median_ms=%.3f%n", scale,
                    1000 * scale, 100 * scale, 100 * scale, queries, elapsed[4] / 1_000_000.0);
            var statements = map("my_items", HomeQueries.myItems(scope), "my_project", HomeQueries.mySummary(scope),
                    "team_week", HomeQueries.teamWeek(true, true, scope));
            String beforeTeam = LegacyHomeQueries.teamWeek(true, true, scope);
            System.out.printf(Locale.ROOT, "M4_HOME_TEAM_BEFORE scale=%d median_ms=%.3f reads=%s%n", scale,
                    MariaDbPlan.median(named, beforeTeam, args), MariaDbPlan.analyze(named, beforeTeam, args));
            assertThat(named.queryForList(HomeQueries.teamWeek(true, true, scope), args))
                    .containsExactlyInAnyOrderElementsOf(named.queryForList(beforeTeam, args));
            var teamPlan = MariaDbPlan.analyze(named, HomeQueries.teamWeek(true, true, scope), args);
            assertThat(teamPlan.stream().filter(r -> r.table().equals("l")).mapToDouble(MariaDbPlan.Read::total).sum())
                    .isLessThanOrEqualTo(10 * scale);
            assertThat(teamPlan.stream().filter(r -> r.table().equals("t")).mapToDouble(MariaDbPlan.Read::total).sum())
                    .isLessThanOrEqualTo(20 * scale);
            for (var entry : statements.entrySet()) {
                String sql = (String) entry.getValue();
                var reads = MariaDbPlan.analyze(named, sql, args);
                System.out.printf(Locale.ROOT, "M4_HOME_ANALYZE kind=%s scale=%d median_ms=%.3f reads=%s%n",
                        entry.getKey(), scale, MariaDbPlan.median(named, sql, args), reads);
            }
        }
    }
}
