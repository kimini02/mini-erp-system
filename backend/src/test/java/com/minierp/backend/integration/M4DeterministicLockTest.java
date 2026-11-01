package com.minierp.backend.integration;

import com.minierp.backend.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static com.minierp.backend.global.persistence.SqlStore.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(JdbcProbe.Configuration.class)
class M4DeterministicLockTest extends BoardTestSupport {
    void waitFor(CountDownLatch latch) throws Exception {
        assertThat(latch.await(15, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void secondCompletionWaitsAtProjectLockUntilFirstCommit() throws Exception {
        experiment(true);
    }

    @Test
    void secondReorderWaitsAndReadsPositionsAfterFirstCommit() throws Exception {
        experiment(false);
    }

    void experiment(boolean completing) throws Exception {
        long p = project(leader, completing ? 2 : 100);
        var ids = taskIds(p);
        long first = ids.get(ids.size() - 2);
        long second = ids.get(ids.size() - 1);
        var firstUpdated = new CountDownLatch(1);
        var releaseFirst = new CountDownLatch(1);
        var secondAttempt = new CountDownLatch(1);
        var secondLocked = new CountDownLatch(1);
        var secondConnection = new AtomicLong();
        var waitStarted = new AtomicLong();
        var waitFinished = new AtomicLong();
        var firstVisible = new AtomicBoolean();
        var events = new CopyOnWriteArrayList<String>();
        var pool = Executors.newFixedThreadPool(2);
        var monitor = new JdbcTemplate(new DriverManagerDataSource(DATABASE.getJdbcUrl(), "root", DATABASE
                .getPassword()));
        String status = completing ? "DONE" : "DOING";
        Long after = completing ? null : ids.get(0);
        try {
            var a = pool.submit(() -> {
                try (var hook = JdbcProbe.observe(new JdbcProbe.Observer() {
                    @Override
                            public void after(Statement st, String sql) throws Exception {
                        if (sql.startsWith("update tasks set task_status=")) {
                            events.add("A_TASK_UPDATED_UNCOMMITTED");
                            firstUpdated.countDown();
                            waitFor(releaseFirst);
                            events.add("A_ALLOWED_TO_COMMIT");
                        }
                    }
                })) {
                    postAs("/api/v1/tasks/" + first + "/move", leaderToken,
                            map("status", status, "afterTaskId", after, "version", 0)).andExpect(status().isOk());
                }
                return null;
            });
            waitFor(firstUpdated);
            assertThat(jdbc.queryForObject("SELECT version FROM tasks WHERE id=?", Integer.class, first)).isZero();
            var b = pool.submit(() -> {
                try (var hook = JdbcProbe.observe(new JdbcProbe.Observer() {
                    @Override
                            public void before(Statement st, String sql) throws Exception {
                        if (sql.startsWith("select id from projects") && sql.contains("for update")) {
                            try (var q = st.getConnection().createStatement();
                                    var rs = q.executeQuery("SELECT CONNECTION_ID()")) {
                                rs.next();
                                secondConnection.set(rs.getLong(1));
                            }
                            waitStarted.set(System.nanoTime());
                            events.add("B_ATTEMPTS_PROJECT_LOCK");
                            secondAttempt.countDown();
                        }
                    }
                    @Override
                            public void after(Statement st, String sql) throws Exception {
                        if (sql.startsWith("select id from projects") && sql.contains("for update")) {
                            waitFinished.set(System.nanoTime());
                            try (var q = st.getConnection().prepareStatement("SELECT version FROM tasks WHERE id=?")) {
                                q.setLong(1, first);
                                try (var rs = q.executeQuery()) {
                                    rs.next();
                                    firstVisible.set(rs.getInt(1) == 1);
                                }
                            }
                            events.add("B_LOCK_ACQUIRED_A_COMMIT_VISIBLE");
                            secondLocked.countDown();
                        }
                    }
                })) {
                    postAs("/api/v1/tasks/" + second + "/move", leaderToken,
                            map("status", status, "afterTaskId", after, "version", 0)).andExpect(status().isOk());
                }
                return null;
            });
            waitFor(secondAttempt);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            long waits = 0;
            while (System.nanoTime() < deadline) {
                waits = monitor.queryForObject(
                        "SELECT COUNT(*) FROM information_schema.INNODB_LOCK_WAITS w JOIN information_schema.INNODB_TRX t ON t.trx_id=w.requesting_trx_id WHERE t.trx_mysql_thread_id=?",
                        Long.class, secondConnection.get());
                if (waits > 0) {
                    break;
                }
                Thread.sleep(10);
            }
            assertThat(waits).isPositive();
            events.add("DATABASE_CONFIRMS_B_LOCK_WAIT");
            assertThat(secondLocked.getCount()).isEqualTo(1);
            assertThat(secondLocked.await(150, TimeUnit.MILLISECONDS)).isFalse();
            releaseFirst.countDown();
            a.get(30, TimeUnit.SECONDS);
            b.get(30, TimeUnit.SECONDS);
            assertThat(firstVisible).isTrue();
            assertThat(waitFinished.get() - waitStarted.get())
                    .isGreaterThanOrEqualTo(TimeUnit.MILLISECONDS.toNanos(150));
            assertThat(events.indexOf("B_LOCK_ACQUIRED_A_COMMIT_VISIBLE"))
                    .isGreaterThan(events.indexOf("A_ALLOWED_TO_COMMIT"));
            if (completing) {
                assertThat(jdbc.queryForObject("SELECT status FROM projects WHERE id=?", String.class, p))
                        .isEqualTo("DONE");
            } else {
                assertThat(duplicates(p)).isZero();
            }
            System.out.printf(Locale.ROOT,
                    "M4_DETERMINISTIC kind=%s database_waits=%d wait_ms=%.3f first_commit_visible=%s events=%s%n",
                    completing ? "completion" : "reorder", waits, (waitFinished.get() - waitStarted.get()) / 1e6,
                    firstVisible.get(), events);
        } finally {
            releaseFirst.countDown();
            pool.shutdownNow();
        }
    }
}
