package com.minierp.backend.integration;

import com.minierp.backend.support.BoardTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

class M4LegacyExperimentTest extends BoardTestSupport {
    @Autowired
    PlatformTransactionManager manager;
    TransactionTemplate tx() {
        var t = new TransactionTemplate(manager);
        t.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return t;
    }

    void await(CyclicBarrier gate) {
        try {
            gate.await(15, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void unlockedWholeListRecalculationReproducesWriteSkewTenTimes() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        int broken = 0;
        try {
            for (int n = 0; n < 10; n++) {
                long p = project(leader, 2);
                var ids = taskIds(p);
                var gate = new CyclicBarrier(2);
                List<Future<?>> futures = new ArrayList<>();
                for (long id : ids) {
                    futures.add(pool.submit(() -> tx().executeWithoutResult(s -> {
                        jdbc.update("UPDATE tasks SET task_status='DONE' WHERE id=?", id);
                        await(gate);
                        var states = jdbc.queryForList("SELECT task_status FROM tasks WHERE project_id=?", String.class,
                                p);
                        String state = states.stream().allMatch("DONE"::equals) ? "DONE" : "PROGRESS";
                        await(gate);
                        jdbc.update("UPDATE projects SET status=? WHERE id=?", state, p);
                    })));
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
        System.out.println("M4_BEFORE_WRITE_SKEW trials=10 inconsistent=" + broken);
        assertThat(broken).isEqualTo(10);
    }

    @Test
    void wholeListStatusCostWithOneThousandTasks() {
        long p = project(leader, 1000);
        long id = taskIds(p).get(0);
        double[] times = new double[9];
        for (int n = -3; n < 9; n++) {
            long started = System.nanoTime();
            tx().executeWithoutResult(s -> {
                jdbc.update("UPDATE tasks SET task_status='DONE' WHERE id=?", id);
                var tasks = jdbc.queryForList("SELECT * FROM tasks WHERE project_id=?", p);
                assertThat(tasks).hasSize(1000);
                boolean done = tasks.stream().allMatch(t -> "DONE".equals(t.get("task_status")));
                jdbc.update("UPDATE projects SET status=? WHERE id=?", done ? "DONE" : "PROGRESS", p);
            });
            if (n >= 0) {
                times[n] = (System.nanoTime() - started) / 1e6;
            }
        }
        Arrays.sort(times);
        System.out.printf(Locale.ROOT,
                "M4_BEFORE_STATUS tasks=1000 result_rows=1000 queries=3 select_queries=1 warmups=3 samples=9 median_ms=%.3f%n",
                times[4]);
    }

    @Test
    void fullRenumberUpdatesOneHundredRowsAndUnlockedMovesDuplicateTwentyTimes() throws Exception {
        long p = project(leader, 100);
        var ids = taskIds(p);
        List<Long> reordered = new ArrayList<>(ids);
        reordered.add(0, reordered.remove(99));
        int updated = tx().execute(s -> {
            int count = 0;
            for (int i = 0; i < 100; i++) {
                count += jdbc.update("UPDATE tasks SET position=? WHERE id=?", (i + 1) * 1024L, reordered.get(i));
            }
            return count;
        });
        assertThat(updated).isEqualTo(100);
        var pool = Executors.newFixedThreadPool(2);
        long duplicates = 0;
        try {
            for (int n = 0; n < 20; n++) {
                for (int i = 0; i < 100; i++) {
                    jdbc.update("UPDATE tasks SET position=? WHERE id=?", (i + 1) * 1024L, ids.get(i));
                }
                var gate = new CyclicBarrier(2);
                List<Future<?>> futures = new ArrayList<>();
                for (long id : ids.subList(98, 100)) {
                    futures.add(pool.submit(() -> tx().executeWithoutResult(s -> {
                        long before = jdbc.queryForObject("SELECT position FROM tasks WHERE id=?", Long.class, ids
                                .get(0));
                        long after = jdbc.queryForObject(
                                "SELECT MIN(position) FROM tasks WHERE project_id=? AND position>?", Long.class, p,
                                before);
                        await(gate);
                        jdbc.update("UPDATE tasks SET position=? WHERE id=?", before + (after - before) / 2, id);
                    })));
                }
                for (var f : futures) {
                    f.get(30, TimeUnit.SECONDS);
                }
                duplicates += duplicates(p);
            }
        } finally {
            pool.shutdownNow();
        }
        System.out.println("M4_BEFORE_ORDER cards=100 update_rows=" + updated
                + " concurrent_trials=20 duplicate_positions=" + duplicates);
        assertThat(duplicates).isEqualTo(20);
    }
}
