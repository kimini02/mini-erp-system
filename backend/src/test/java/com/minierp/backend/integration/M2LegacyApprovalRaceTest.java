package com.minierp.backend.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
/** Frozen M1 write pattern: both requests read PENDING before either unconditionally saves. */
@Testcontainers(disabledWithoutDocker = true)
class M2LegacyApprovalRaceTest {
    @Container
    static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:10.11");
    @Test
    void unconditionalLegacyApprovalAcceptsBothRequestsInTenControlledRaces() throws Exception {
        var source = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        Flyway.configure().dataSource(source).target("2").load().migrate();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("""
            INSERT INTO users(id,created_at,login_id,user_name,user_email,department_id,position_id,status,
                              must_change_pw,token_version,total_annual_leave,used_annual_leave,remaining_annual_leave)
            VALUES(1,'2026-01-01','race','race','race@example.test',2,1,'ACTIVE',0,0,10,0,10)
            """);
        jdbc.update("""
            INSERT INTO leave_requests(id,created_at,requester_id,app_type,start_date,end_date,used_days,app_status)
            VALUES(1,'2026-01-01',1,'ANNUAL','2026-01-05','2026-01-05',1,'PENDING')
            """);
        int violations = 0;
        var pool = Executors.newFixedThreadPool(2);
        try {
            for (int trial = 0; trial < 10; trial++) {
                jdbc.update("UPDATE leave_requests SET app_status='PENDING' WHERE id=1");
                jdbc.update("UPDATE users SET used_annual_leave=0,remaining_annual_leave=10 WHERE id=1");
                var readBarrier = new CyclicBarrier(2);
                Callable<Boolean> approve = () -> new TransactionTemplate(new DataSourceTransactionManager(source))
                        .execute(status -> {
                            String state = jdbc.queryForObject("SELECT app_status FROM leave_requests WHERE id=1",
                                    String.class);
                            var remaining = jdbc.queryForObject("SELECT remaining_annual_leave FROM users WHERE id=1",
                                    java.math.BigDecimal.class);
                            try {
                                readBarrier.await(10, TimeUnit.SECONDS);
                            } catch (Exception ex) {
                                throw new IllegalStateException(ex);
                            }
                            if (!"PENDING".equals(state)) {
                                return false;
                            }
                            jdbc.update("UPDATE leave_requests SET app_status='APPROVED' WHERE id=1");
                            jdbc.update("UPDATE users SET used_annual_leave=1,remaining_annual_leave=? WHERE id=1",
                                    remaining.subtract(java.math.BigDecimal.ONE));
                            return true;
                        });
                var first = pool.submit(approve);
                var second = pool.submit(approve);
                if (first.get(15, TimeUnit.SECONDS) && second.get(15, TimeUnit.SECONDS)) {
                    violations++;
                }
            }
        } finally {
            pool.shutdownNow();
        }
        System.out.println("M2_LEGACY_APPROVAL_RACE trials=10 double_success=" + violations);
        assertThat(violations).isEqualTo(10);
    }
}
