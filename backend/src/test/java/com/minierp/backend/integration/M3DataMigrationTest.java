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
@Testcontainers(disabledWithoutDocker=true)
class M3DataMigrationTest {
    @Container static final MariaDBContainer<?> DATABASE=new MariaDBContainer<>("mariadb:10.11");
    @Test void v4PreservesDatesAndUsesCompanyScheduleForLegacyJudgments() {
        var source=new DriverManagerDataSource(DATABASE.getJdbcUrl(),DATABASE.getUsername(),DATABASE.getPassword());Flyway.configure().dataSource(source).target("3").load().migrate();var db=new JdbcTemplate(source);
        var date=LocalDate.now(TestClocks.fixed()).with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        db.update("INSERT INTO users(id,created_at,login_id,user_name,user_email,department_id,position_id,status,must_change_pw,token_version) VALUES(1,?,'migration','이관','migration@example.test',2,1,'ACTIVE',0,0)",date.atStartOfDay());
        db.update("UPDATE work_schedules SET start_time='09:00',end_time='18:00',late_grace_min=10 WHERE is_default=1");
        db.update("INSERT INTO work_schedules(name,start_time,end_time,late_grace_min,is_default) VALUES('부서 기준','10:00','19:00',0,0)");db.update("UPDATE departments SET work_schedule_id=(SELECT id FROM work_schedules WHERE name='부서 기준') WHERE id=2");
        String[] statuses={"LATE","NORMAL","ABSENT","LEAVE"};String[] ins={"09:10:59","09:11:00",null,null};
        for(int n=0;n<4;n++) db.update("INSERT INTO attendances(created_at,user_id,work_date,clock_in_time,clock_out_time,att_status) VALUES(?,1,?,?,'17:59:00',?)",date.atStartOfDay(),date.plusDays(n),ins[n],statuses[n]);
        var flyway=Flyway.configure().dataSource(source).target("4").load();assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
        var rows=db.queryForList("SELECT *,DATE_FORMAT(clock_in_at,'%Y-%m-%dT%H:%i:%s') AS local_in FROM attendances ORDER BY work_date");
        assertThat(rows).hasSize(4);assertThat(rows.get(0).get("local_in")).isEqualTo(date+"T09:10:59");assertThat(rows.get(0).get("in_result")).isEqualTo("ON_TIME");assertThat(rows.get(1).get("in_result")).isEqualTo("LATE");assertThat(rows.get(0).get("out_result")).isEqualTo("EARLY");assertThat(rows.get(2).get("absent")).isEqualTo(true);assertThat(rows.get(3).get("day_type")).isEqualTo("LEAVE");assertThat(rows.get(0).get("sched_source")).isEqualTo("COMPANY");
        assertThat(db.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='attendances'",String.class)).doesNotContain("clock_in_time","clock_out_time","att_status");
        assertThat(flyway.migrate().migrationsExecuted).isZero();assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
    }
}
