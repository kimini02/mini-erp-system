package com.minierp.backend.support;
import com.fasterxml.jackson.databind.*;
import com.minierp.backend.domain.user.entity.*;
import com.minierp.backend.domain.user.repository.UserRepository;
import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.*;
import java.time.*;
import java.util.*;
import static java.time.temporal.TemporalAdjusters.next;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
@AutoConfigureMockMvc
public abstract class AttendanceTestSupport extends MariaDbIntegrationTest {
    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper mapper;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected SqlStore db;
    @Autowired protected UserRepository users;
    @Autowired protected JwtTokenProvider jwt;
    @Autowired protected Clock clock;
    protected User employee,leader,chief,outsider;
    protected String employeeToken,leaderToken,chiefToken,outsideToken;
    protected LocalDate start;
    @org.springframework.test.context.bean.override.mockito.MockitoBean(name="testClock") Clock adjustable;
    protected LocalDate date;
    protected void at(LocalDate date,LocalTime time) {
        org.mockito.Mockito.when(adjustable.getZone()).thenReturn(ZoneId.of("Asia/Seoul"));
        org.mockito.Mockito.when(adjustable.instant()).thenReturn(date.atTime(time).atZone(ZoneId.of("Asia/Seoul")).toInstant());
    }
    @BeforeEach void fixture() {
        date=LocalDate.now(TestClocks.fixed()).with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));at(date,LocalTime.of(9,20));
        jdbc.update("DELETE FROM attendances");jdbc.update("DELETE FROM batch_runs");
        employee=user("employee",UserRole.USER);leader=user("leader",UserRole.TEAM_LEADER);
        chief=user("chief",UserRole.ADMIN);outsider=user("outside",UserRole.USER);
        jdbc.update("UPDATE users SET department_id=1 WHERE id=?",chief.getId());
        jdbc.update("UPDATE departments SET head_user_id=? WHERE id=2",leader.getId());
        jdbc.update("UPDATE departments SET head_user_id=? WHERE id=1",chief.getId());
        jdbc.update("UPDATE approval_forms SET allow_line_edit=(code='GENERAL'),skip_self=1,dedupe_approver=1,fallback_to_upper=1");
        jdbc.update("DELETE FROM approval_form_steps");
        jdbc.update("INSERT INTO approval_form_steps(form_id,step_order,line_kind,approver_type) VALUES(1,1,'APPROVER','DEPT_HEAD'),(2,1,'APPROVER','DEPT_HEAD'),(3,1,'APPROVER','DEPT_HEAD'),(4,1,'APPROVER','DEPT_HEAD')");
        jdbc.update("UPDATE users SET hire_date=? WHERE id IN (?,?,?,?)",date.minusYears(1),employee.getId(),leader.getId(),chief.getId(),outsider.getId());
        jdbc.update("UPDATE work_schedules SET start_time='09:00',end_time='18:00',late_grace_min=10 WHERE is_default=1");
        employeeToken=token(employee);leaderToken=token(leader);chiefToken=token(chief);outsideToken=token(outsider);
        start=LocalDate.now(clock).with(next(DayOfWeek.MONDAY));
        jdbc.update("INSERT INTO leave_ledger(user_id,leave_year,entry_type,days,memo,created_at) VALUES(?,?,'GRANT',10,'fixture',?)",employee.getId(),start.getYear(),LocalDateTime.now(clock));
    }
    protected User user(String name,UserRole role) {
        String id="test-"+UUID.randomUUID();var u=User.create(name,id,id+"@example.test","unused-encoded","01","사원");u.changeRole(role);return users.saveAndFlush(u);
    }
    protected String token(User user) { return jwt.generateAccessToken(user.getId().toString(),user.getTokenVersion()); }
    protected JsonNode data(ResultActions action) throws Exception { return mapper.readTree(action.andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString()).path("data"); }
    protected ResultActions postAs(String url,String token,Object body) throws Exception {
        var request=post(url).header("Authorization","Bearer "+token);if(body!=null) request.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body));return mvc.perform(request);
    }
    protected ResultActions getAs(String url,String token) throws Exception { return mvc.perform(get(url).header("Authorization","Bearer "+token)); }
    protected Map<String,Object> leaveBody(String type,LocalDate begin,LocalDate end) { return Map.of("leaveType",type,"startDate",begin.toString(),"endDate",end.toString(),"reason","검증"); }
    protected long draft(String form,Object body) throws Exception { return data(postAs("/api/v1/approval-documents",employeeToken,Map.of("formCode",form,"title","검증 문서","body",body))).path("id").asLong(); }
    protected JsonNode submit(long id) throws Exception { return data(postAs("/api/v1/approval-documents/"+id+"/submit",employeeToken,null)); }
    protected long submittedLeave() throws Exception { long id=draft("LEAVE",leaveBody("ANNUAL",start,start));submit(id);return id; }
    protected long count(String table,long doc) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE document_id=?",Long.class,doc); }
}
