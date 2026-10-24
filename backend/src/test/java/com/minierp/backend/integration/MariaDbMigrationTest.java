package com.minierp.backend.integration;

import com.minierp.backend.support.MariaDbIntegrationTest;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

class MariaDbMigrationTest extends MariaDbIntegrationTest {

    @Autowired Flyway flyway;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManagerFactory entityManagerFactory;
    @Autowired Environment environment;

    @Test
    void emptyDatabaseRunsFlywayAndStartsValidatedContext() {
        assertThat(jdbc.queryForObject("SELECT VERSION()", String.class)).startsWith("10.11.");
        assertThat(environment.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(entityManagerFactory.isOpen()).isTrue();
        assertThat(flyway.info().applied()).hasSize(3);
        assertThat(flyway.info().current().getScript()).isEqualTo("V3__approval_and_leave_ledger.sql");
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(jdbc.queryForList("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name <> 'flyway_schema_history'
                """, String.class)).containsExactlyInAnyOrder(
                "users", "projects", "project_members", "tasks", "task_assignments",
                "attendances", "leave_requests", "overtime_requests",
                "departments", "work_schedules", "positions", "roles", "user_roles",
                "menus", "role_permissions", "login_id_sequences", "holidays", "approval_forms", "approval_form_steps", "approval_documents", "approval_lines", "approval_histories", "doc_no_sequences", "general_requests", "notifications", "leave_ledger");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isZero();
    }

    @Test
    void schemaHasAllForeignKeysAndUniqueConstraints() {
        // Hibernate validate does not verify foreign keys or uniqueness.
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND constraint_type = 'FOREIGN KEY'
                """, Long.class)).isEqualTo(36);
        assertThat(jdbc.queryForList("""
                SELECT constraint_name FROM information_schema.table_constraints
                WHERE constraint_schema = DATABASE() AND constraint_type = 'UNIQUE'
                """, String.class)).containsExactlyInAnyOrder(
                "uk_users_email", "uk_users_login_id", "uk_project_members_project_user",
                "uk_task_assignments_task_user", "uk_attendances_user_date",
                "uk_work_schedule_default", "uk_positions_name", "uk_departments_code",
                "uk_departments_root", "uk_roles_code", "uk_menus_code", "uk_holidays_date", "uk_approval_forms_code", "uk_form_steps_order", "uk_approval_documents_no", "uk_approval_lines_order", "uk_general_document", "uk_leave_ledger_grant", "uk_leave_requests_document", "uk_overtime_requests_document");
        assertThat(jdbc.queryForObject("""
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'users' AND column_name = 'department_id'
                """, String.class)).isEqualTo("NO");
    }

    @Test
    void repeatedMigrationDoesNotRecreateSchema() {
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(flyway.info().applied()).hasSize(3);
    }
}
