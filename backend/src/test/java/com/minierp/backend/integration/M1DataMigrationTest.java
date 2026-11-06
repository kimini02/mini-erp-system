package com.minierp.backend.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
@Testcontainers(disabledWithoutDocker = true)
class M1DataMigrationTest {
    @Container
    static final MariaDBContainer<?> DATABASE = new MariaDBContainer<>("mariadb:10.11");
    @Test
    void migratesPopulatedM0DatabaseWithoutLosingLegacyValues(
            org.springframework.boot.test.system.CapturedOutput output) {
        var dataSource = new DriverManagerDataSource(DATABASE.getJdbcUrl(), DATABASE.getUsername(),
                DATABASE.getPassword());
        String location = "classpath:db/migration";
        Flyway.configure().dataSource(dataSource).locations(location).target("1").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        String hash = "encoded-fixture-" + UUID.randomUUID();
        String[] roles = {
            "ADMIN", "TEAM_LEADER", "USER", "USER"
        };
        for (int i = 0; i < 4; i++) {
            jdbc.update("""
            INSERT INTO users(created_at,user_name,user_email,user_pw,department_code,position_name,
                user_role,is_active,total_annual_leave,used_annual_leave,remaining_annual_leave,login_id)
            VALUES('2020-01-01 09:00:00',?,?,?,?,'기존직급',?,?,15.5,2,13.5,?)
            """, "이관" + i, "migration" + i + "@example.test", hash, "0" + (i + 1), roles[i], i != 2, i == 2 ? "2026012"
                    : "legacy" + i);
        }
        var migration = Flyway.configure().dataSource(dataSource).locations(location).target("2").load();
        assertThat(migration.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(migration.validateWithResult().validationSuccessful).isTrue();
        assertThat(output.getAll())
                .contains("M1: migrated_inactive_to_retired=1")
                .contains("M1: migrated_unknown_department=1");
        assertThat(
                jdbc.queryForObject(
                        "SELECT is_nullable FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='users' AND column_name='hire_date'",
                        String.class))
                .isEqualTo("YES");
        assertThat(jdbc.queryForList("SELECT login_id FROM users ORDER BY id", String.class))
                .containsExactly("legacy0", "legacy1", "2026012", "legacy3");
        assertThat(
                jdbc.queryForList(
                        "SELECT r.code FROM user_roles ur JOIN roles r ON r.id=ur.role_id ORDER BY ur.user_id",
                        String.class))
                .containsExactly("CHIEF", "LEADER", "EMPLOYEE", "EMPLOYEE");
        assertThat(jdbc.queryForList("SELECT department_id FROM users ORDER BY id", Long.class))
                .containsExactly(2L, 3L, 4L, 1L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM positions WHERE name='기존직급'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(DISTINCT position_id) FROM users", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT last_seq FROM login_id_sequences WHERE hire_year=2026", Integer.class))
                .isEqualTo(12);
        for (var row : jdbc.queryForList("SELECT * FROM users")) {
            assertThat(row.get("user_pw")).isEqualTo(hash);
            assertThat(row.get("status")).isEqualTo("2026012".equals(row.get("login_id")) ? "RETIRED" : "ACTIVE");
            assertThat(row.get("hire_date")).isNull();
            assertThat((BigDecimal) row.get("remaining_annual_leave")).isEqualByComparingTo("13.5");
            assertThat((BigDecimal) row.get("total_annual_leave")).isEqualByComparingTo("15.5");
        }
        assertThat(
                jdbc.queryForList(
                        "SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='users'",
                        String.class))
                .doesNotContain("department_code", "position_name", "user_role", "is_active");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM menus", Integer.class)).isEqualTo(11);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM departments WHERE parent_id=1", Integer.class))
                .isEqualTo(3);
    }
}
