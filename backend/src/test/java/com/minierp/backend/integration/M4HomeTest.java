package com.minierp.backend.integration;
import com.minierp.backend.support.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@Import(JdbcProbe.Configuration.class)
class M4HomeTest extends BoardTestSupport {
 private void assign(long task,long user){jdbc.update("INSERT INTO task_assignments(task_id,user_id,created_at) VALUES(?,?,?)",task,user,LocalDateTime.now(clock));}
 private long document(long drafter,long approver){
  long d=db.insert("INSERT INTO approval_documents(form_id,drafter_id,drafter_dept_id,title,status,current_step,submitted_at,created_at) VALUES(3,:user,2,'홈 결재','IN_PROGRESS',1,:now,:now)",map("user",drafter,"now",LocalDateTime.now(clock)));
  jdbc.update("INSERT INTO approval_lines(document_id,step_order,line_kind,approver_id,approver_name,approver_title,approver_dept,source,status) VALUES(?,1,'APPROVER',?,'결재자','팀장','개발','DIRECT','PENDING')",d,approver);return d;
 }
 @Test void aggregatesBoundedWidgetsAndPreservesKoreanTimeAndScope()throws Exception{
  at(date,LocalTime.of(0,30));long p=project(leader,7),hidden=project(chief,1);jdbc.update("DELETE FROM project_members WHERE project_id=? AND user_id=?",hidden,employee.getId());
  var ids=taskIds(p);for(long id:ids)assign(id,employee.getId());assign(taskIds(hidden).get(0),employee.getId());
  jdbc.update("UPDATE tasks SET end_date=? WHERE project_id IN (?,?)",date.plusDays(2),p,hidden);jdbc.update("UPDATE tasks SET end_date=? WHERE id=?",date.minusDays(2),ids.get(0));
  for(int i=0;i<4;i++){document(leader.getId(),employee.getId());document(employee.getId(),leader.getId());}
  for(int i=0;i<6;i++)jdbc.update("INSERT INTO notifications(user_id,type,title,link,created_at) VALUES(?,'TASK_ASSIGNED','제목','/projects/1',?)",employee.getId(),LocalDateTime.now(clock));
  var home=data(getAs("/api/v1/home",employeeToken));
  assertThat(home.path("today")).isEqualTo(data(getAs("/api/v1/attendance/today",employeeToken)));
  assertThat(home.path("teamWeek").path("from").asText()).isEqualTo(date.toString());assertThat(home.path("teamWeek").path("days").size()).isEqualTo(5);
  assertThat(home.path("approvals").path("todoCount").asInt()).isEqualTo(4);assertThat(home.path("approvals").path("todo").size()).isEqualTo(3);assertThat(home.path("approvals").path("mine").size()).isEqualTo(3);
  assertThat(home.path("approvals").path("todo").get(0).path("submittedAt").asText()).isEqualTo(date+"T00:30:00");
  assertThat(home.path("myTasks").path("project").path("id").asLong()).isEqualTo(p);assertThat(home.path("myTasks").path("items").size()).isEqualTo(5);assertThat(home.path("myTasks").path("items").get(0).path("overdueDays").asInt()).isEqualTo(2);
  for(var day:home.path("teamWeek").path("days"))for(var item:day.path("items"))if(item.path("type").asText().equals("TASK_DUE"))assertThat(item.path("project").path("id").asLong()).isNotEqualTo(hidden);
  assertThat(home.path("notifications").path("unread").asInt()).isEqualTo(6);assertThat(home.path("notifications").path("items").size()).isEqualTo(5);
  assertThat(home.path("leave")).isEqualTo(data(getAs("/api/v1/leave/balance",employeeToken)));
  try(var trace=JdbcProbe.start()){data(getAs("/api/v1/home",employeeToken));System.out.printf("M4_HOME_FUNCTIONAL http_queries=%d%n",trace.queries.size());assertThat(trace.queries).hasSizeLessThanOrEqualTo(15);}
 }
 @Test void homeTodayMatchesClockedHolidayAndApprovedHalfDay()throws Exception{
  postAs("/api/v1/attendance/clock-in",employeeToken,null).andExpect(status().is2xxSuccessful());
  assertThat(data(getAs("/api/v1/home",employeeToken)).path("today")).isEqualTo(data(getAs("/api/v1/attendance/today",employeeToken)));
  jdbc.update("INSERT INTO holidays(holiday_date,name,substitute) VALUES(?,'검증 휴일',0)",date);
  assertThat(data(getAs("/api/v1/home",employeeToken)).path("today")).isEqualTo(data(getAs("/api/v1/attendance/today",employeeToken)));
  jdbc.update("DELETE FROM holidays WHERE holiday_date=?",date);jdbc.update("DELETE FROM attendances WHERE user_id=?",employee.getId());
  long doc=draft("LEAVE",leaveBody("HALF_PM",date,date));submit(doc);postAs("/api/v1/approval-documents/"+doc+"/approve",leaderToken,Map.of()).andExpect(status().isOk());
  assertThat(data(getAs("/api/v1/home",employeeToken)).path("today")).isEqualTo(data(getAs("/api/v1/attendance/today",employeeToken)));
  var team=data(getAs("/api/v1/home",employeeToken)).path("teamWeek");assertThat(team.toString()).contains("HALF_PM").doesNotContain("reason");
 }
 @Test void missingPermissionsHideWidgetsWithoutLeakingProjects()throws Exception{
  jdbc.update("DELETE FROM user_roles WHERE user_id=?",employee.getId());var home=data(getAs("/api/v1/home",employeeToken));
  for(String widget:List.of("today","approvals","myTasks","teamWeek"))assertThat(home.path(widget).isNull()).as(widget).isTrue();
  assertThat(home.path("leave").isObject()).isTrue();assertThat(home.path("notifications").path("items").size()).isZero();
 }
 @Test void teamWeekIncludesOnlyOwnDepartmentAndOmitsReasons()throws Exception{
  long own=draft("LEAVE",leaveBody("ANNUAL",date.plusDays(1),date.plusDays(1)));submit(own);
  long other=document(chief.getId(),leader.getId());jdbc.update("INSERT INTO leave_requests(document_id,leave_type,start_date,end_date,used_days,reason,created_at) VALUES(?,'ANNUAL',?,?,1,'보이면 안 됨',?)",other,date,date,LocalDateTime.now(clock));
  var team=data(getAs("/api/v1/home",employeeToken)).path("teamWeek");
  assertThat(team.toString()).contains("IN_PROGRESS").doesNotContain("reason","보이면 안 됨");
  for(var day:team.path("days"))for(var item:day.path("items"))assertThat(item.path("documentId").asLong()).isNotEqualTo(other);
 }
}
