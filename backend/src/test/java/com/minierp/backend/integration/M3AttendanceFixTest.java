package com.minierp.backend.integration;
import com.minierp.backend.support.AttendanceTestSupport;
import com.minierp.backend.domain.notification.service.NotificationService;
import com.minierp.backend.domain.attendance.service.AbsenceBatchService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;
class M3AttendanceFixTest extends AttendanceTestSupport {
    @Autowired AbsenceBatchService batch;
    @MockitoSpyBean NotificationService notifications;
    Map<String,Object> body(LocalDate day,String in,String out) {var b=new HashMap<String,Object>();b.put("workDate",day.toString());b.put("clockIn",in);b.put("clockOut",out);b.put("reason","카드 오류");return b;}
    void old(LocalDate day) {jdbc.update("INSERT INTO attendances(user_id,work_date,clock_in_at,sched_grace_min,in_result,created_at) VALUES(?,?,?,10,'LATE',?)",employee.getId(),day,day.atTime(9,14),LocalDateTime.now(clock));}
    Map<String,Object> attendance(LocalDate day) {return jdbc.queryForMap("SELECT * FROM attendances WHERE user_id=? AND work_date=?",employee.getId(),day);}
    @Test void approvalUpdatesSnapshotVerdictAndDocumentWhileRejectionAndWithdrawalDoNot() throws Exception {
        var day=date.minusWeeks(1);old(day);long id=draft("ATTENDANCE_FIX",body(day,"09:02","18:10"));var submitted=submit(id);
        assertThat(submitted.path("body").path("before").path("clockIn").asText()).isEqualTo("09:14");assertThat(submitted.path("docNo").asText()).startsWith("AT-");
        data(postAs("/api/v1/approval-documents/"+id+"/approve",leaderToken,null));assertThat(attendance(day).get("in_result")).isEqualTo("ON_TIME");assertThat(((Number)attendance(day).get("fix_document_id")).longValue()).isEqualTo(id);
        var unchanged=attendance(day);long rejected=draft("ATTENDANCE_FIX",body(day,"10:00","18:00"));submit(rejected);data(postAs("/api/v1/approval-documents/"+rejected+"/reject",leaderToken,Map.of("comment","사유 확인")));assertThat(attendance(day)).isEqualTo(unchanged);
        long withdrawn=draft("ATTENDANCE_FIX",body(day,"10:00","18:00"));submit(withdrawn);data(postAs("/api/v1/approval-documents/"+withdrawn+"/withdraw",employeeToken,null));assertThat(attendance(day)).isEqualTo(unchanged);
    }
    @Test void rangeHolidayFormatAndDuplicateAreRejectedWithoutSideEffects() throws Exception {
        for(var day:List.of(date,date.plusDays(1),date.minusDays(31))) {long id=draft("ATTENDANCE_FIX",body(day,"09:00",null));postAs("/api/v1/approval-documents/"+id+"/submit",employeeToken,null).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("ATTENDANCE_FIX_OUT_OF_RANGE"));assertThat(count("notifications",id)).isZero();}
        long holiday=draft("ATTENDANCE_FIX",body(date.minusDays(1),"09:00",null));postAs("/api/v1/approval-documents/"+holiday+"/submit",employeeToken,null).andExpect(status().isBadRequest());
        postAs("/api/v1/approval-documents",employeeToken,Map.of("formCode","ATTENDANCE_FIX","title","잘못된 시각","body",body(date.minusWeeks(1),"18:00","09:00"))).andExpect(status().isBadRequest());
        var day=date.minusWeeks(1);long a=draft("ATTENDANCE_FIX",body(day,"09:00",null));submit(a);long b=draft("ATTENDANCE_FIX",body(day,"09:00",null));postAs("/api/v1/approval-documents/"+b+"/submit",employeeToken,null).andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("ATTENDANCE_FIX_DUPLICATE"));assertThat(count("notifications",b)).isZero();
    }
    @Test void notificationFailureRollsBackCorrectionAndRepeatApprovalCreatesNothing() throws Exception {
        var day=date.minusWeeks(1);old(day);long id=draft("ATTENDANCE_FIX",body(day,"09:02","18:00"));submit(id);var before=attendance(day);long alerts=count("notifications",id);
        NotificationService target=org.springframework.test.util.AopTestUtils.getUltimateTargetObject(notifications);doThrow(new IllegalStateException("injected")).when(target).store(anyLong(),eq(id),eq("APPROVED"),anyString());
        try {postAs("/api/v1/approval-documents/"+id+"/approve",leaderToken,null).andExpect(status().isInternalServerError());assertThat(attendance(day)).isEqualTo(before);assertThat(jdbc.queryForObject("SELECT status FROM approval_documents WHERE id=?",String.class,id)).isEqualTo("IN_PROGRESS");assertThat(count("notifications",id)).isEqualTo(alerts);}finally{reset(target);}
        data(postAs("/api/v1/approval-documents/"+id+"/approve",leaderToken,null));long done=count("notifications",id);postAs("/api/v1/approval-documents/"+id+"/approve",leaderToken,null).andExpect(status().isConflict());assertThat(count("notifications",id)).isEqualTo(done);
    }
    @Test void checkoutOnlyCorrectionUsesExistingClockInAndSurvivesRejudging() throws Exception {
        var day=date.minusWeeks(1);old(day);long id=draft("ATTENDANCE_FIX",body(day,null,"18:00"));submit(id);data(postAs("/api/v1/approval-documents/"+id+"/approve",leaderToken,null));assertThat(attendance(day).get("absent")).isEqualTo(false);batch.run(day);assertThat(attendance(day).get("absent")).isEqualTo(false);
    }
    @Test void absentDayCheckoutOnlyIsRejectedAtSubmission() throws Exception {
        var day=date.minusWeeks(1);batch.run(day);var before=attendance(day);
        long id=draft("ATTENDANCE_FIX",body(day,null,"18:00"));
        postAs("/api/v1/approval-documents/"+id+"/submit",employeeToken,null).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("ATTENDANCE_FIX_CLOCK_IN_REQUIRED"));
        assertThat(attendance(day)).isEqualTo(before);assertThat(count("notifications",id)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM approval_documents WHERE id=?",String.class,id)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT before_json FROM attendance_fix_requests WHERE document_id=?",String.class,id)).isNull();
    }
    @Test void clockInAfterExistingClockOutIsRejectedAtSubmission() throws Exception {
        var day=date.minusWeeks(1);old(day);jdbc.update("UPDATE attendances SET clock_out_at=? WHERE user_id=?",day.atTime(18,0),employee.getId());var before=attendance(day);
        long id=draft("ATTENDANCE_FIX",body(day,"18:01",null));
        postAs("/api/v1/approval-documents/"+id+"/submit",employeeToken,null).andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_ATTENDANCE_TIME"));
        assertThat(attendance(day)).isEqualTo(before);assertThat(count("notifications",id)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM approval_documents WHERE id=?",String.class,id)).isEqualTo("DRAFT");
    }
    @Test void approvalRechecksMergedTimesWhenAttendanceChangedSinceSubmission() throws Exception {
        var day=date.minusWeeks(1);old(day);long id=draft("ATTENDANCE_FIX",body(day,null,"18:00"));submit(id);
        jdbc.update("UPDATE attendances SET clock_in_at=NULL WHERE user_id=?",employee.getId());long alerts=count("notifications",id);
        postAs("/api/v1/approval-documents/"+id+"/approve",leaderToken,null).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.error.code").value("ATTENDANCE_FIX_CLOCK_IN_REQUIRED"));
        assertThat(jdbc.queryForObject("SELECT status FROM approval_documents WHERE id=?",String.class,id)).isEqualTo("IN_PROGRESS");assertThat(count("notifications",id)).isEqualTo(alerts);
    }
}
