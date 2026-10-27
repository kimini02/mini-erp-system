package com.minierp.backend.integration;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.*;
import com.minierp.backend.support.TestClocks;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.sql.*;
import static org.assertj.core.api.Assertions.*;
/** Preserves develop 5f85a6f's read-then-insert shape against V3, not the production implementation. */
@Testcontainers(disabledWithoutDocker=true)
class M3LegacyAttendanceTest {
    @Container static final MariaDBContainer<?> DATABASE=new MariaDBContainer<>("mariadb:10.11");
    static JdbcTemplate db;
    @BeforeAll static void setup() {
        var source=new DriverManagerDataSource(DATABASE.getJdbcUrl(),DATABASE.getUsername(),DATABASE.getPassword());Flyway.configure().dataSource(source).target("3").load().migrate();db=new JdbcTemplate(source);
    }
    long user() {
        String id=UUID.randomUUID().toString();db.update("INSERT INTO users(created_at,login_id,user_name,user_email,department_id,position_id,status,must_change_pw,token_version) VALUES(?,?,'legacy',?,2,1,'ACTIVE',0,0)",LocalDateTime.now(TestClocks.fixed()),id,id+"@example.test");return db.queryForObject("SELECT id FROM users WHERE login_id=?",Long.class,id);
    }
    @Test void legacyRequestSuppliedValuesAreStoredInTenAttempts() {
        var fake=LocalDate.now(TestClocks.fixed()).minusWeeks(2);int forged=0;
        for(int n=0;n<10;n++) {long user=user();db.update("INSERT INTO attendances(user_id,work_date,clock_in_time,att_status,created_at) VALUES(?,?,'08:50','NORMAL',?)",user,fake,LocalDateTime.now(TestClocks.fixed()));if(db.queryForObject("SELECT COUNT(*) FROM attendances WHERE user_id=? AND work_date=? AND clock_in_time='08:50'",Integer.class,user,fake)==1) forged++;}
        assertThat(forged).isEqualTo(10);System.out.println("M3_KI04_LEGACY_MODEL attempts=10 forged=10");
    }
    @Test void legacyConcurrentReadThenInsertLeaksUniqueConstraintInTenTrials() throws Exception {
        int constraintErrors=0;var pool=Executors.newFixedThreadPool(2);var date=LocalDate.now(TestClocks.fixed());
        try {for(int n=0;n<10;n++) {
            long user=user();var gate=new CyclicBarrier(2);
            Callable<String> call=()->{try(var c=DriverManager.getConnection(DATABASE.getJdbcUrl(),DATABASE.getUsername(),DATABASE.getPassword())) {
                c.setAutoCommit(false);c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
                try(var read=c.prepareStatement("SELECT id FROM attendances WHERE user_id=? AND work_date=?")) {read.setLong(1,user);read.setObject(2,date);assertThat(read.executeQuery().next()).isFalse();}
                gate.await(10,TimeUnit.SECONDS);
                try(var insert=c.prepareStatement("INSERT INTO attendances(user_id,work_date,clock_in_time,att_status,created_at) VALUES(?,?,'09:00','NORMAL',?)")) {
                    insert.setLong(1,user);insert.setObject(2,date);insert.setObject(3,LocalDateTime.now(TestClocks.fixed()));insert.executeUpdate();c.commit();return "SUCCESS";
                } catch(SQLException e) {c.rollback();if(e.getErrorCode()==1062) return "UNHANDLED_UNIQUE";throw e;}
            }};
            var a=pool.submit(call);var b=pool.submit(call);var results=List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));assertThat(results).containsExactlyInAnyOrder("SUCCESS","UNHANDLED_UNIQUE");constraintErrors++;
        }} finally {pool.shutdownNow();}
        assertThat(constraintErrors).isEqualTo(10);System.out.println("M3_LEGACY_CLOCK_IN_RACE trials=10 unhandled_unique_errors=10");
    }
}
