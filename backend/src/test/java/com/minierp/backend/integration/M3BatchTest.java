package com.minierp.backend.integration;
import com.minierp.backend.support.AttendanceTestSupport;
import com.minierp.backend.domain.attendance.service.*;
import com.minierp.backend.domain.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.AopTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
class M3BatchTest extends AttendanceTestSupport {
    @Autowired AbsenceBatchService batch;
    @MockitoSpyBean AttendanceStore store;
    @org.junit.jupiter.api.AfterEach void resetFailure() {reset((AttendanceStore)AopTestUtils.getUltimateTargetObject(store));}
    private Map<String,Object> row(long user,LocalDate date) {return jdbc.queryForMap("SELECT * FROM attendances WHERE user_id=? AND work_date=?",user,date);}
    @Test void batchFinalizesAbsenceLeaveMissingAndLateHalfApprovalIdempotently() throws Exception {
        at(date,LocalTime.of(9,0));data(postAs("/api/v1/attendance/clock-in",employeeToken,null));data(postAs("/api/v1/attendance/clock-in",outsideToken,null));
        at(date,LocalTime.of(14,0));assertThat(data(postAs("/api/v1/attendance/clock-out",employeeToken,null)).path("outResult").asText()).isEqualTo("EARLY");
        long half=draft("LEAVE",leaveBody("HALF_PM",date,date));submit(half);data(postAs("/api/v1/approval-documents/"+half+"/approve",leaderToken,null));
        long sick=data(postAs("/api/v1/approval-documents",leaderToken,Map.of("formCode","LEAVE","title","병가","body",leaveBody("SICK",date,date)))).path("id").asLong();data(postAs("/api/v1/approval-documents/"+sick+"/submit",leaderToken,null));
        at(date.plusDays(1),LocalTime.of(0,10));batch.runPreviousDay();
        assertThat(row(employee.getId(),date).get("out_result")).isEqualTo("ON_TIME");assertThat(row(employee.getId(),date).get("day_type")).isEqualTo("HALF_PM");
        assertThat(row(leader.getId(),date).get("day_type")).isEqualTo("LEAVE");assertThat(row(chief.getId(),date).get("absent")).isEqualTo(true);assertThat(row(outsider.getId(),date).get("out_result")).isEqualTo("MISSING");
        var before=jdbc.queryForList("SELECT * FROM attendances ORDER BY id");at(date.plusDays(1),LocalTime.of(1,0));batch.runPreviousDay();assertThat(jdbc.queryForList("SELECT * FROM attendances ORDER BY id")).isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM batch_runs WHERE status='SUCCESS'",Integer.class)).isEqualTo(2);
    }
    @Test void weekendHolidayAndEmploymentBoundariesDoNotCreateAbsence() {
        var future=user("입사 전",UserRole.USER);jdbc.update("UPDATE users SET hire_date=? WHERE id=?",date.plusDays(3),future.getId());
        var retired=user("퇴사",UserRole.USER);jdbc.update("UPDATE users SET status='RETIRED',hire_date=? WHERE id=?",date.minusYears(1),retired.getId());
        at(date.plusDays(1),LocalTime.of(0,5));batch.runPreviousDay();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendances WHERE user_id IN (?,?)",Integer.class,future.getId(),retired.getId())).isZero();
        at(date.plusWeeks(1),LocalTime.of(0,10));batch.runPreviousDay();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendances WHERE work_date=?",Integer.class,date.plusDays(6))).isZero();
        jdbc.update("INSERT INTO holidays(holiday_date,name,substitute) VALUES(?,'휴일',0)",date.plusDays(1));at(date.plusDays(2),LocalTime.of(0,10));batch.runPreviousDay();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendances WHERE work_date=?",Integer.class,date.plusDays(1))).isZero();
    }
    @Test void missingHireDateUsesCreationDateAndHalfDayWithoutClockInIsAbsent() throws Exception {
        jdbc.update("UPDATE users SET hire_date=NULL WHERE id=?",outsider.getId());
        long half=draft("LEAVE",leaveBody("HALF_AM",date,date));submit(half);data(postAs("/api/v1/approval-documents/"+half+"/approve",leaderToken,null));
        at(date.plusDays(1),LocalTime.of(0,10));batch.runPreviousDay();assertThat(row(employee.getId(),date).get("absent")).isEqualTo(true);assertThat(row(outsider.getId(),date).get("absent")).isEqualTo(true);
        batch.run(date.minusDays(1));assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendances WHERE user_id=? AND work_date=?",Integer.class,outsider.getId(),date.minusDays(1))).isZero();
    }
    @Test void failureRollsBackAttendanceButPersistsFailedRun() {
        at(date.plusDays(1),LocalTime.of(0,10));AttendanceStore target=AopTestUtils.getUltimateTargetObject(store);
        doAnswer(call->{call.callRealMethod();throw new IllegalStateException("injected");}).when(target).insert(org.mockito.ArgumentMatchers.anyLong(),org.mockito.ArgumentMatchers.eq(date),org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.anyString(),org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.isNull(),org.mockito.ArgumentMatchers.eq(true),org.mockito.ArgumentMatchers.any());
        assertThatThrownBy(()->batch.runPreviousDay()).isInstanceOf(org.springframework.dao.InvalidDataAccessApiUsageException.class).hasRootCauseInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendances WHERE work_date=?",Integer.class,date)).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM batch_runs WHERE status='FAILED'",Integer.class)).isEqualTo(1);
    }
}
