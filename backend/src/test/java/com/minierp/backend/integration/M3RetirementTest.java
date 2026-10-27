package com.minierp.backend.integration;
import com.minierp.backend.support.AttendanceTestSupport;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
class M3RetirementTest extends AttendanceTestSupport {
    @org.springframework.beans.factory.annotation.Autowired com.minierp.backend.domain.attendance.service.AbsenceBatchService batch;
    @Test void absenceUsesHistoricalEmploymentWindowAndCurrentLeaveStatus() {
        jdbc.update("UPDATE users SET status='RETIRED',retired_date=? WHERE id=?",date.plusDays(1),employee.getId());
        jdbc.update("UPDATE users SET status='ON_LEAVE' WHERE id=?",leader.getId());
        jdbc.update("UPDATE users SET hire_date=? WHERE id=?",date.plusDays(2),outsider.getId());
        at(date.plusDays(3),java.time.LocalTime.of(0,10));batch.run(date);batch.run(date.plusDays(1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendances WHERE user_id=?",Integer.class,employee.getId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendances WHERE user_id IN (?,?)",Integer.class,leader.getId(),outsider.getId())).isZero();
        jdbc.update("UPDATE users SET status='ACTIVE',retired_date=NULL WHERE id=?",employee.getId());batch.run(date.plusDays(1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendances WHERE user_id=?",Integer.class,employee.getId())).isEqualTo(2);
    }

    @Test void legacyRetireeAndInvitationPendingHaveNoNewAbsenceRows() {
        jdbc.update("UPDATE users SET status='RETIRED',retired_date=NULL WHERE id=?",employee.getId());
        jdbc.update("UPDATE users SET user_pw=NULL WHERE id=?",outsider.getId());
        at(date.plusDays(2),java.time.LocalTime.of(0,10));batch.run(date);batch.run(date.plusDays(1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendances WHERE user_id=?",Integer.class,employee.getId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendances WHERE user_id=?",Integer.class,outsider.getId())).isZero();
    }
    @Test void existingRowsAreRejudgedEvenWhenNewRowEligibilityIsLost() {
        jdbc.update("INSERT INTO attendances(user_id,work_date,clock_in_at,created_at) VALUES(?,?,?,?)",employee.getId(),date,date.atTime(9,0),date.atTime(9,0));
        jdbc.update("UPDATE users SET status='RETIRED',retired_date=NULL,user_pw=NULL WHERE id=?",employee.getId());
        at(date.plusDays(1),java.time.LocalTime.of(0,10));batch.run(date);
        assertThat(jdbc.queryForObject("SELECT out_result FROM attendances WHERE user_id=?",String.class,employee.getId())).isEqualTo("MISSING");
    }

    @Test void retirementRecordsKoreanDateAndReactivationClearsIt() throws Exception {
        var retired=data(mvc.perform(patch("/api/v1/users/"+employee.getId()+"/status").header("Authorization","Bearer "+chiefToken).contentType("application/json").content("{\"status\":\"RETIRED\"}")));
        assertThat(retired.path("retiredDate").asText()).isEqualTo(date.toString());
        assertThat(data(getAs("/api/v1/users/"+employee.getId(),chiefToken)).path("retiredDate").asText()).isEqualTo(date.toString());
        var active=data(mvc.perform(patch("/api/v1/users/"+employee.getId()+"/status").header("Authorization","Bearer "+chiefToken).contentType("application/json").content("{\"status\":\"ACTIVE\"}")));
        assertThat(active.path("retiredDate").isNull()).isTrue();
    }

    @Test void retirementThroughLeaveToActiveClearsDateAndShowsAbsenceInMonthlyTable() throws Exception {
        String url="/api/v1/users/"+employee.getId()+"/status";
        var retired=data(mvc.perform(patch(url).header("Authorization","Bearer "+chiefToken).contentType("application/json").content("{\"status\":\"RETIRED\"}")));
        assertThat(retired.path("retiredDate").asText()).isEqualTo(date.toString());
        var onLeave=data(mvc.perform(patch(url).header("Authorization","Bearer "+chiefToken).contentType("application/json").content("{\"status\":\"ON_LEAVE\"}")));
        var workDate=date.plusDays(1);at(workDate,java.time.LocalTime.of(9,0));
        var active=data(mvc.perform(patch(url).header("Authorization","Bearer "+chiefToken).contentType("application/json").content("{\"status\":\"ACTIVE\"}")));
        at(workDate.plusDays(1),java.time.LocalTime.of(0,10));batch.run(workDate);
        assertThat(jdbc.queryForObject("SELECT absent FROM attendances WHERE user_id=? AND work_date=?",Boolean.class,employee.getId(),workDate)).isTrue();
        var table=data(getAs("/api/v1/attendance/monthly?month="+java.time.YearMonth.from(workDate),chiefToken));
        var row=java.util.stream.StreamSupport.stream(table.path("rows").spliterator(),false)
            .filter(r->r.path("user").path("id").asLong()==employee.getId()).findFirst().orElseThrow();
        org.junit.jupiter.api.Assertions.assertAll(
            ()->assertThat(onLeave.path("retiredDate").isNull()).as("RETIRED to ON_LEAVE clears retirement date").isTrue(),
            ()->assertThat(active.path("retiredDate").isNull()).as("ACTIVE response has no retirement date").isTrue(),
            ()->assertThat(jdbc.queryForObject("SELECT retired_date FROM users WHERE id=?",java.sql.Date.class,employee.getId())).isNull(),
            ()->assertThat(row.path("cells").get(workDate.getDayOfMonth()-1).path("code").asText()).isEqualTo("ABSENT"),
            ()->assertThat(row.path("totals").path("absent").asInt()).isEqualTo(1)
        );
    }
}
