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
        jdbc.update("UPDATE users SET retired_date=NULL WHERE id=?",employee.getId());batch.run(date.plusDays(1));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM attendances WHERE user_id=?",Integer.class,employee.getId())).isEqualTo(2);
    }

    @Test void retirementRecordsKoreanDateAndReactivationClearsIt() throws Exception {
        var retired=data(mvc.perform(patch("/api/v1/users/"+employee.getId()+"/status").header("Authorization","Bearer "+chiefToken).contentType("application/json").content("{\"status\":\"RETIRED\"}")));
        assertThat(retired.path("retiredDate").asText()).isEqualTo(date.toString());
        assertThat(data(getAs("/api/v1/users/"+employee.getId(),chiefToken)).path("retiredDate").asText()).isEqualTo(date.toString());
        var active=data(mvc.perform(patch("/api/v1/users/"+employee.getId()+"/status").header("Authorization","Bearer "+chiefToken).contentType("application/json").content("{\"status\":\"ACTIVE\"}")));
        assertThat(active.path("retiredDate").isNull()).isTrue();
    }
}
