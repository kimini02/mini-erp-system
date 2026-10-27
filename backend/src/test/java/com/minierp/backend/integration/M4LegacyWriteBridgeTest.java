package com.minierp.backend.integration;
import com.minierp.backend.support.BoardTestSupport;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class M4LegacyWriteBridgeTest extends BoardTestSupport {
 @Test void legacyCreateUsesSequenceAndHistoryInsteadOfNullTaskNumber()throws Exception{
  long p=project(leader,0);
  var result=data(postAs("/api/v1/tasks",leaderToken,Map.of("projectId",p,"taskTitle","기존 등록","taskContent","내용","endDate",date.plusDays(1),"priority","MEDIUM","assigneeIds",List.of(employee.getId()))).andExpect(status().isCreated()));
  long id=result.path("id").asLong();assertThat(jdbc.queryForObject("SELECT task_no FROM tasks WHERE id=?",Integer.class,id)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_histories WHERE task_id=?",Integer.class,id)).isEqualTo(2);
 }
 @Test void legacyStatusRequiresVersionAndRejectsStaleVersion()throws Exception{
  long p=project(leader,1),id=taskIds(p).get(0);String url="/api/v1/tasks/"+id+"/status";
  mvc.perform(patch(url).header("Authorization","Bearer "+leaderToken).contentType("application/json").content("{\"taskState\":\"DONE\"}")).andExpect(status().isBadRequest());
  mvc.perform(patch(url).header("Authorization","Bearer "+leaderToken).contentType("application/json").content("{\"taskState\":\"DONE\",\"version\":0}")).andExpect(status().isOk());
  mvc.perform(patch(url).header("Authorization","Bearer "+leaderToken).contentType("application/json").content("{\"taskState\":\"TODO\",\"version\":0}")).andExpect(status().isConflict());
  assertThat(jdbc.queryForObject("SELECT status FROM projects WHERE id=?",String.class,p)).isEqualTo("DONE");
 }
 @Test void legacyProjectUpdateCannotBypassDepartmentScope()throws Exception{
  long p=project(chief,1);
  mvc.perform(put("/api/v1/projects/"+p).header("Authorization","Bearer "+leaderToken).contentType("application/json").content(mapper.writeValueAsString(Map.of("title","권한 없음","content","내용","startDate",date,"endDate",date.plusDays(1),"priority","LOW")))).andExpect(status().isForbidden());
  assertThat(jdbc.queryForObject("SELECT title FROM projects WHERE id=?",String.class,p)).isEqualTo("측정");
 }
 @Test void legacyMembersAndAssignmentsFollowManagerAndUnfinishedTaskRules()throws Exception{
  long p=project(employee,1),id=taskIds(p).get(0);
  postAs("/api/v1/tasks/"+id+"/assignments",employeeToken,Map.of("userId",outsider.getId())).andExpect(status().isCreated());
  mvc.perform(delete("/api/v1/projects/"+p+"/members/"+outsider.getId()).header("Authorization","Bearer "+employeeToken)).andExpect(status().isConflict());
  mvc.perform(delete("/api/v1/tasks/"+id+"/assignments/"+outsider.getId()).header("Authorization","Bearer "+employeeToken)).andExpect(status().isOk());
  mvc.perform(delete("/api/v1/projects/"+p+"/members/"+outsider.getId()).header("Authorization","Bearer "+employeeToken)).andExpect(status().isOk());
  assertThat(jdbc.queryForObject("SELECT version FROM tasks WHERE id=?",Integer.class,id)).isEqualTo(2);
 }
}
