package com.minierp.backend.integration;
import com.minierp.backend.support.AttendanceTestSupport;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
class M3GrantRegistrationTest extends AttendanceTestSupport {
    @Test void newHireDoesNotReceiveLegacyPositionGrantBeforeFirstMonth() throws Exception {
        long id=data(postAs("/api/v1/users",chiefToken,Map.of("name","새 사원","email",UUID.randomUUID()+"@example.test","hireDate",date.toString(),"departmentId",2,"positionId",1,"roleCodes",List.of("EMPLOYEE")))).path("id").asLong();
        var granted=jdbc.queryForObject("SELECT COALESCE(SUM(days),0) FROM leave_ledger WHERE user_id=?",java.math.BigDecimal.class,id);
        System.out.println("M3_NEW_HIRE_GRANT="+granted);assertThat(granted).isZero();
    }
}
