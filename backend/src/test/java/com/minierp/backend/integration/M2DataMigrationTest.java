package com.minierp.backend.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class M2DataMigrationTest {
    @Container static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:10.11");

    @Test void migratesLegacyRequestsAndPreservesEachEmployeesAvailableBalance() {
        var source = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        Flyway.configure().dataSource(source).target("2").load().migrate();
        var db = new JdbcTemplate(source);
        for (int id=1; id<=3; id++) db.update("""
            INSERT INTO users(id,created_at,login_id,user_name,user_email,department_id,position_id,status,
                must_change_pw,token_version,total_annual_leave,used_annual_leave,remaining_annual_leave)
            VALUES(?,'2025-01-01',?, ?, ?,2,1,'ACTIVE',0,0,15,2,13)
            """,id,"migration-"+id,"이관"+id,"migration-"+id+"@example.test");
        String[] states={"PENDING","APPROVED","REJECTED","CANCELLED"};
        for (int i=0; i<states.length; i++) {
            db.update("""
                INSERT INTO leave_requests(created_at,requester_id,approver_id,app_type,start_date,end_date,used_days,app_status,reject_reason,request_reason)
                VALUES(?,1,?,'HALF_MORNING','2025-02-03','2025-02-03',0.5,?,'기존 사유','이관 휴가')
                ""","2025-01-0"+(i+1)+" 09:00:00",i==0?null:2,states[i]);
            db.update("""
                INSERT INTO overtime_requests(created_at,requester_id,approver_id,overtime_date,start_time,end_time,reason,status)
                VALUES(?,3,2,'2025-02-08','09:00','12:00','이관 특근',?)
                ""","2025-01-0"+(i+1)+" 10:00:00",states[i]);
        }
        var before = db.queryForList("""
            SELECT u.id,u.remaining_annual_leave-COALESCE((SELECT SUM(l.used_days) FROM leave_requests l
            WHERE l.requester_id=u.id AND l.app_status='PENDING'),0) AS available FROM users u ORDER BY u.id
            """);
        var migration=Flyway.configure().dataSource(source).target("3").load();
        assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(migration.validateWithResult().validationSuccessful).isTrue();
        for (var row:before) {
            var after=db.queryForObject("SELECT SUM(days) FROM leave_ledger WHERE user_id=?",BigDecimal.class,row.get("id"));
            assertThat(after).isEqualByComparingTo((BigDecimal)row.get("available"));
        }
        assertThat(db.queryForObject("SELECT COUNT(*) FROM approval_documents",Integer.class)).isEqualTo(8);
        assertThat(db.queryForList("SELECT status FROM approval_documents WHERE form_id=1 ORDER BY doc_no",String.class))
                .containsExactly("IN_PROGRESS","APPROVED","REJECTED","WITHDRAWN");
        assertThat(db.queryForList("SELECT doc_no FROM approval_documents WHERE form_id=1 ORDER BY doc_no",String.class))
                .containsExactly("HR-2025-0001","HR-2025-0002","HR-2025-0003","HR-2025-0004");
        assertThat(db.queryForObject("SELECT last_seq FROM doc_no_sequences WHERE prefix='HR' AND doc_year=2025",Integer.class)).isEqualTo(4);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM approval_lines",Integer.class)).isEqualTo(7);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM approval_lines WHERE status='PENDING'",Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM leave_requests WHERE leave_type='HALF_AM' AND document_id IS NOT NULL",Integer.class)).isEqualTo(4);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM approval_histories WHERE action='REJECT' AND comment='기존 사유'",Integer.class)).isEqualTo(1);
        assertThat(db.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='users'",String.class))
                .doesNotContain("total_annual_leave","used_annual_leave","remaining_annual_leave");
        for (String table: java.util.List.of("leave_requests","overtime_requests"))
            assertThat(db.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=?",String.class,table))
                    .doesNotContain("requester_id","approver_id","app_status","status");
        assertThat(db.queryForObject("SELECT COUNT(*) FROM approval_forms",Integer.class)).isEqualTo(3);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM approval_form_steps",Integer.class)).isEqualTo(5);
    }
}
