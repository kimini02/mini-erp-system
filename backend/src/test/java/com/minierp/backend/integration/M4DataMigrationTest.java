package com.minierp.backend.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.*;
import com.minierp.backend.support.TestClocks;
import java.time.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class M4DataMigrationTest {
    @Container
    static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:10.11");
    @Test
    void v6KeepsLegacyDataAndNumbersEachProjectAndColumn() {
        var source = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(), DATABASE.getPassword());
        Flyway.configure().dataSource(source).target("5").load().migrate();
        var db = new JdbcTemplate(source);
        var today = LocalDate.now(TestClocks.fixed());
        db.update(
                "INSERT INTO users(id,created_at,login_id,user_name,user_email,department_id,position_id,status,must_change_pw,token_version) VALUES(1,?,'m4','이관','m4@example.test',2,1,'ACTIVE',0,0)",
                today.atStartOfDay());
        for (int p = 1; p <= 2; p++) {
            db.update(
                    "INSERT INTO projects(id,created_at,title,start_date,end_date,status,priority,leader_id) VALUES(?,?,?, ?,?,'PROGRESS','MEDIUM',1)",
                    p, today.atStartOfDay(), "이관 " + p, today, today.plusMonths(1));
        }
        for (int i = 1; i <= 5; i++) {
            db.update(
                    "INSERT INTO tasks(id,created_at,task_title,task_content,end_date,task_status,priority,project_id) VALUES(?,?,?,'내용',?,?,'MEDIUM',?)",
                    i, today.atStartOfDay(), "업무 " + i, today.plusDays(1), i % 2 == 0 ? "DOING" : "TODO", i < 4 ? 1
                    : 2);
        }
        var old = db
                .queryForList(
                "SELECT id,task_title,task_content,end_date,task_status,project_id FROM tasks ORDER BY id");
        var flyway = Flyway.configure().dataSource(source).load();
        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(db
                .queryForList(
                "SELECT id,task_title,task_content,end_date,task_status,project_id FROM tasks ORDER BY id"))
                .isEqualTo(old);
        assertThat(db.queryForList("SELECT project_key FROM projects ORDER BY id", String.class))
                .containsExactly("P1", "P2");
        assertThat(db.queryForList("SELECT task_no FROM tasks ORDER BY id", Integer.class))
                .containsExactly(1, 2, 3, 1, 2);
        assertThat(db.queryForList("SELECT position FROM tasks ORDER BY id", Long.class))
                .containsExactly(1024L, 1024L, 2048L, 1024L, 1024L);
        assertThat(db.queryForList("SELECT last_task_seq FROM projects ORDER BY id", Integer.class))
                .containsExactly(3, 2);
        assertThat(db.queryForObject("SELECT COUNT(*) FROM project_members WHERE user_id=1", Integer.class))
                .isEqualTo(2);
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }
}
