package com.minierp.backend.integration;

import com.fasterxml.jackson.databind.*;
import com.minierp.backend.support.MariaDbIntegrationTest;
import com.minierp.backend.domain.user.entity.*;
import com.minierp.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import java.time.*;
import java.util.*;
import static java.time.temporal.TemporalAdjusters.next;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Existing end-to-end coverage now uses the M2 contract and real MariaDB. */
@AutoConfigureMockMvc
class CoreApiIntegrationTest extends MariaDbIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired Clock clock;
    private User employee,leader,chief;
    private String employeeToken,leaderToken,chiefToken,password;
    private LocalDate workDate,overtimeDate;
    @BeforeEach void setUp() throws Exception {
        password="Aa1!"+UUID.randomUUID();
        employee=user("employee",UserRole.USER);leader=user("leader",UserRole.TEAM_LEADER);chief=user("chief",UserRole.ADMIN);
        employeeToken=login(employee);leaderToken=login(leader);chiefToken=login(chief);
        jdbc.update("UPDATE departments SET head_user_id=? WHERE id=2",leader.getId());
        jdbc.update("DELETE FROM approval_form_steps WHERE form_id=1");
        jdbc.update("INSERT INTO approval_form_steps(form_id,step_order,line_kind,approver_type) VALUES(1,1,'APPROVER','DEPT_HEAD')");
        workDate=LocalDate.now(clock).with(next(DayOfWeek.WEDNESDAY));overtimeDate=workDate.with(next(DayOfWeek.SATURDAY));
        jdbc.update("INSERT INTO leave_ledger(user_id,leave_year,entry_type,days,memo,created_at) VALUES(?,?,'GRANT',14,'fixture',?)",employee.getId(),workDate.getYear(),LocalDateTime.now(clock));
    }
    private User user(String prefix,UserRole role) {
        String id=prefix+UUID.randomUUID();var user=User.create(prefix,id,id+"@example.test",encoder.encode(password),"01","사원");user.changeRole(role);return users.saveAndFlush(user);
    }
    private JsonNode body(ResultActions action) throws Exception { return mapper.readTree(action.andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString()).path("data"); }
    private ResultActions postAs(String path,String token,Object body) throws Exception {
        var request=post(path).header("Authorization","Bearer "+token);
        if(body!=null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));
        return mvc.perform(request);
    }
    private String login(User user) throws Exception {
        return body(mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content(mapper.writeValueAsString(Map.of("id",user.getLoginId(),"password",password))))).path("accessToken").asText();
    }
    private long draft(String code,Object request) throws Exception { return body(postAs("/api/v1/approval-documents",employeeToken,Map.of("formCode",code,"title","통합 검증","body",request))).path("id").asLong(); }
    private Map<String,Object> leaveBody(String type,LocalDate end) { return Map.of("leaveType",type,"startDate",workDate.toString(),"endDate",end.toString(),"reason","병원 진료"); }
    private long leave() throws Exception { long id=draft("LEAVE",leaveBody("ANNUAL",workDate));body(postAs("/api/v1/approval-documents/"+id+"/submit",employeeToken,null));return id; }
    private long overtime() throws Exception { long id=draft("OVERTIME",Map.of("overtimeDate",overtimeDate.toString(),"startTime","09:00:00","endTime","12:00:00","reason","주말 긴급 작업"));body(postAs("/api/v1/approval-documents/"+id+"/submit",employeeToken,null));return id; }
    private JsonNode approve(long id) throws Exception { return body(postAs("/api/v1/approval-documents/"+id+"/approve",leaderToken,null)); }

    @Test void login_shouldIssueAccessToken() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(Map.of("id",employee.getLoginId(),"password",password))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").isNotEmpty()).andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andExpect(r->assertThat(LocalDateTime.parse(mapper.readTree(r.getResponse().getContentAsString()).path("timestamp").asText())).isEqualTo(LocalDateTime.now(clock)));
    }
    @Test void getUsers_adminShouldSeeList() throws Exception {
        mvc.perform(get("/api/v1/users").header("Authorization","Bearer "+chiefToken).param("q",employee.getLoginId()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].id").value(employee.getId()));
    }
    @Test void approveLeave_shouldReturnApprovedStatus() throws Exception {
        var document=approve(leave());assertThat(document.path("status").asText()).isEqualTo("APPROVED");
        assertThat(document.path("body").path("reason").asText()).isEqualTo("병원 진료");
        assertThat(document.path("line").get(0).path("approver").path("id").asLong()).isEqualTo(leader.getId());
    }
    @Test void approveOvertime_shouldReturnApprovedStatus() throws Exception {
        var document=approve(overtime());assertThat(document.path("status").asText()).isEqualTo("APPROVED");
        assertThat(document.path("body").path("overtimeDate").asText()).isEqualTo(overtimeDate.toString());
        assertThat(document.path("line").get(0).path("approver").path("id").asLong()).isEqualTo(leader.getId());
    }
    @Test void calendar_shouldContainLeaveAndOvertimeEvents() throws Exception {
        approve(leave());approve(overtime());
        for(var expected:Map.of(workDate,"LEAVE",overtimeDate,"OVERTIME").entrySet())
            mvc.perform(get("/api/v1/calendar/events").header("Authorization","Bearer "+employeeToken)
                .param("year",""+expected.getKey().getYear()).param("month",""+expected.getKey().getMonthValue()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[*].type",hasItems(expected.getValue())));
    }
    @Test void attendance_checkInAndCheckOut_shouldSucceed() throws Exception {
        postAs("/api/v1/attendance/check-in",employeeToken,Map.of("workDate",workDate.toString(),"clockInTime","08:55:00"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.attStatus").value("NORMAL"));
        mvc.perform(patch("/api/v1/attendance/check-out").header("Authorization","Bearer "+employeeToken).param("workDate",workDate.toString())
            .contentType(MediaType.APPLICATION_JSON).content("{\"clockOutTime\":\"18:10:00\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.clockOutTime").value("18:10:00"));
    }
    @Test void createLeave_sameDateDuplicateShouldFail() throws Exception {
        leave();long duplicate=draft("LEAVE",leaveBody("ANNUAL",workDate));
        postAs("/api/v1/approval-documents/"+duplicate+"/submit",employeeToken,null)
            .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code").value("LEAVE_PERIOD_OVERLAP"));
    }
    @Test void halfDayRequiresSameDateAndChargesHalfDay() throws Exception {
        long invalid=draft("LEAVE",leaveBody("HALF_AM",workDate.plusDays(2)));
        postAs("/api/v1/approval-documents/"+invalid+"/submit",employeeToken,null).andExpect(status().isBadRequest());
        long valid=draft("LEAVE",leaveBody("HALF_AM",workDate));
        var result=body(postAs("/api/v1/approval-documents/"+valid+"/submit",employeeToken,null));
        assertThat(result.path("body").path("usedDays").decimalValue()).isEqualByComparingTo("0.5");
    }
    @Test void cancelLeave_shouldSucceedForOwnPendingRequest() throws Exception {
        long id=leave();assertThat(body(postAs("/api/v1/approval-documents/"+id+"/withdraw",employeeToken,null)).path("status").asText()).isEqualTo("WITHDRAWN");
    }
}
