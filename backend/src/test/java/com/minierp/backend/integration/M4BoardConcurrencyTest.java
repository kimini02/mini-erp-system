package com.minierp.backend.integration;

import com.minierp.backend.support.*;
import com.minierp.backend.domain.project.service.ProjectStateStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static com.minierp.backend.global.persistence.SqlStore.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(JdbcProbe.Configuration.class)
class M4BoardConcurrencyTest extends BoardTestSupport {
    @Autowired
    PlatformTransactionManager manager;
    @Autowired
    ProjectStateStore states;
    TransactionTemplate tx() {
        var t = new TransactionTemplate(manager);
        t.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return t;
    }

    void await(CyclicBarrier barrier) {
        try {
            barrier.await(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void projectLockPreventsWriteSkewInTenConcurrentCompletions() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        int broken = 0;
        try {
            for (int n = 0; n < 10; n++) {
                long p = project(leader, 2);
                var ids = taskIds(p);
                var gate = new CyclicBarrier(2);
                List<Future<?>> futures = new ArrayList<>();
                for (int i = 0; i < 2; i++) {
                    long id = ids.get(i);
                    long user = i == 0 ? employee.getId() : outsider.getId();
                    String token = i == 0 ? employeeToken : outsideToken;
                    jdbc.update("INSERT INTO task_assignments(task_id,user_id,created_at) VALUES(?,?,?)", id, user, date
                            .atStartOfDay());
                    futures.add(pool.submit(() -> {
                        await(gate);
                        try {
                            postAs("/api/v1/tasks/" + id + "/move", token,
                                    map("status", "DONE", "afterTaskId", null, "version", 0))
                                    .andExpect(status().isOk());
                        } catch (Exception e) {
                            throw new IllegalStateException(e);
                        }
                    }));
                }
                for (var f : futures) {
                    f.get(30, TimeUnit.SECONDS);
                }
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tasks WHERE project_id=? AND task_status='DONE'",
                        Integer.class, p)).isEqualTo(2);
                if (!"DONE".equals(jdbc.queryForObject("SELECT status FROM projects WHERE id=?", String.class, p))) {
                    broken++;
                }
            }
        } finally {
            pool.shutdownNow();
        }
        System.out.println("M4_AFTER_WRITE_SKEW trials=10 inconsistent=" + broken);
        assertThat(broken).isZero();
    }

    @Test
    void tenConcurrentCreatesHaveContiguousUniqueNumbers() throws Exception {
        long p = project(leader, 0);
        var gate = new CyclicBarrier(10);
        var pool = Executors.newFixedThreadPool(10);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 10; i++) {
                futures.add(pool.submit(() -> {
                    await(gate);
                    try {
                        postAs("/api/v1/projects/" + p + "/tasks", leaderToken, Map.of("title", "동시 등록"))
                                .andExpect(status().isCreated());
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }));
            }
            for (var f : futures) {
                f.get(45, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForList("SELECT task_no FROM tasks WHERE project_id=? ORDER BY task_no", Integer.class, p))
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10);
        assertThat(jdbc.queryForObject("SELECT last_task_seq FROM projects WHERE id=?", Integer.class, p))
                .isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT COUNT(*)-COUNT(DISTINCT position) FROM tasks WHERE project_id=?", Integer
                .class, p)).isZero();
        System.out.println("M4_SEQUENCE threads=10 created=10 duplicates=0 gaps=0");
    }

    @Test
    void aggregateCostComparedWithWholeListOnSameThousandRows() {
        long p = project(leader, 1000);
        long id = taskIds(p).get(0);
        double[][] times = new double[2][9];
        long[] queryCounts = new long[2];
        long[] returned = new long[2];
        // Both paths use the same dataset and identical task update/commit. After additionally locks the project.
        for (int n = -3; n < 9; n++) {
            for (int mode = 0; mode < 2; mode++) {
                final int variant = mode;
                long started = System.nanoTime();
                try (var trace = JdbcProbe.start()) {
                    tx().executeWithoutResult(s -> {
                        if (variant == 1) {
                            jdbc.queryForObject("SELECT id FROM projects WHERE id=? FOR UPDATE", Long.class, p);
                        }
                        jdbc.update("UPDATE tasks SET task_status='DONE' WHERE id=?", id);
                        if (variant == 0) {
                            var tasks = jdbc.queryForList("SELECT * FROM tasks WHERE project_id=?", p);
                            boolean done = tasks.stream().allMatch(t -> "DONE".equals(t.get("task_status")));
                            jdbc.update("UPDATE projects SET status=? WHERE id=?", done ? "DONE" : "PROGRESS", p);
                        } else {
                            states.recalculate(p);
                        }
                    });
                    if (n >= 0) {
                        times[mode][n] = (System.nanoTime() - started) / 1e6;
                    }
                    queryCounts[mode] = trace.queries.size();
                    returned[mode] = trace.queries.stream().filter(q -> q.sql.contains("from tasks"))
                            .mapToLong(q -> q.rows).sum();
                }
            }
        }
        for (var sample : times) {
            Arrays.sort(sample);
        }
        assertThat(queryCounts).containsExactly(3, 4);
        assertThat(returned).containsExactly(1000, 2);
        System.out.printf(Locale.ROOT,
                "M4_STATUS_PAIRED tasks=1000 warmups=3 samples=9 before_queries=%d after_queries=%d before_result_rows=%d after_result_rows=%d before_median_ms=%.3f after_median_ms=%.3f%n",
                queryCounts[0], queryCounts[1], returned[0], returned[1], times[0][4], times[1][4]);
        System.out.println("M4_STATUS_ANALYZE_BEFORE " + jdbc
                .queryForList("ANALYZE SELECT * FROM tasks WHERE project_id=?", p));
        System.out.println("M4_STATUS_ANALYZE_AFTER " + jdbc
                .queryForList("ANALYZE SELECT task_status,COUNT(*) FROM tasks WHERE project_id=? GROUP BY task_status",
                p));
    }

    @Test
    void gapsUpdateOneRowSerializeTwentyMovesAndRebalanceOnlyWhenExhausted() throws Exception {
        long p = project(leader, 100);
        var ids = taskIds(p);
        long updates;
        long normalRebalances = 0;
        var pool = Executors.newFixedThreadPool(2);
        try (var trace = JdbcProbe.start()) {
            postAs("/api/v1/tasks/" + ids.get(99) + "/move", leaderToken,
                    map("status", "DOING", "afterTaskId", null, "version", 0)).andExpect(status().isOk());
            updates = trace.taskUpdates();
            normalRebalances += trace.rebalances();
        }
        assertThat(updates).isEqualTo(1);
        long duplicateCount = 0;
        try {
            for (int trial = 0; trial < 20; trial++) {
                for (int i = 0; i < 100; i++) {
                    jdbc.update("UPDATE tasks SET position=? WHERE id=?", (i + 1) * 1024L, ids.get(i));
                }
                var gate = new CyclicBarrier(2);
                List<Future<Long>> futures = new ArrayList<>();
                for (long id : ids.subList(98, 100)) {
                    int version = jdbc.queryForObject("SELECT version FROM tasks WHERE id=?", Integer.class, id);
                    futures.add(pool.submit(() -> {
                        await(gate);
                        try (var trace = JdbcProbe.start()) {
                            postAs("/api/v1/tasks/" + id + "/move", leaderToken,
                                    map("status", "DOING", "afterTaskId", ids.get(0), "version", version))
                                    .andExpect(status().isOk());
                            return trace.rebalances();
                        }
                    }));
                }
                for (var f : futures) {
                    normalRebalances += f.get(30, TimeUnit.SECONDS);
                }
                duplicateCount += duplicates(p);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(duplicateCount).isZero();
        assertThat(normalRebalances).isZero();
        jdbc.update("UPDATE tasks SET position=1 WHERE id=?", ids.get(0));
        jdbc.update("UPDATE tasks SET position=2 WHERE id=?", ids.get(1));
        int version = jdbc.queryForObject("SELECT version FROM tasks WHERE id=?", Integer.class, ids.get(99));
        long forcedRebalances;
        long forcedUpdates;
        try (var trace = JdbcProbe.start()) {
            postAs("/api/v1/tasks/" + ids.get(99) + "/move", leaderToken,
                    map("status", "DOING", "afterTaskId", ids.get(0), "version", version)).andExpect(status().isOk());
            forcedRebalances = trace.rebalances();
            forcedUpdates = trace.taskUpdates();
        }
        assertThat(forcedRebalances).isEqualTo(1);
        assertThat(duplicates(p)).isZero();
        assertThat(jdbc.queryForList("SELECT id FROM tasks WHERE project_id=? ORDER BY position LIMIT 3", Long.class,
                p)).containsExactly(ids.get(0), ids.get(99), ids.get(1));
        System.out.printf(
                "M4_AFTER_ORDER cards=100 update_rows=%d concurrent_trials=20 duplicate_positions=%d normal_rebalances=%d forced_rebalances=%d forced_update_rows=%d%n",
                updates, duplicateCount, normalRebalances, forcedRebalances, forcedUpdates);
    }
}
