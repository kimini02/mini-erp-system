package com.minierp.backend.integration;
import com.minierp.backend.support.BoardTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
class M4RemovedRoutesTest extends BoardTestSupport {
 @Test void retiredReadsAndWritesHaveNoRouteAndLeaveNoTrace()throws Exception{
  long project=project(leader,1),task=taskIds(project).get(0);
  long history=jdbc.queryForObject("SELECT COUNT(*) FROM task_histories",Long.class),notifications=jdbc.queryForObject("SELECT COUNT(*) FROM notifications",Long.class);
  var reads=List.of("/dashboard/admin-summary","/dashboard/progress","/dashboard/projects","/tasks","/tasks/"+task+"/assignments","/tasks/recent-assignments","/projects/"+project+"/members","/projects/"+project+"/members/available","/projects/"+project+"/members/assignable","/projects/permissions/"+employee.getId(),"/projects/leaders","/projects/"+project+"/progress");
  for(String path:reads)getAs("/api/v1"+path,chiefToken).andExpect(status().isNotFound());
  var writes=List.of("PUT /projects/"+project,"PATCH /projects/"+project+"/leader","POST /projects/"+project+"/members","DELETE /projects/"+project+"/members/"+employee.getId(),"PUT /projects/permissions/"+employee.getId(),"POST /tasks","PUT /tasks/"+task,"PATCH /tasks/"+task+"/status","POST /tasks/"+task+"/assignments","DELETE /tasks/"+task+"/assignments/"+employee.getId());
  for(String route:writes){String[] parts=route.split(" ");mvc.perform(request(HttpMethod.valueOf(parts[0]),"/api/v1"+parts[1]).header("Authorization","Bearer "+chiefToken).contentType("application/json").content("{}")).andExpect(status().isNotFound());}
  assertThat(jdbc.queryForObject("SELECT version FROM tasks WHERE id=?",Integer.class,task)).isZero();
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_histories",Long.class)).isEqualTo(history);assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notifications",Long.class)).isEqualTo(notifications);
  getAs("/api/v1/projects/"+project+"/board",leaderToken).andExpect(status().isOk());getAs("/api/v1/tasks/"+task,leaderToken).andExpect(status().isOk());
 }
 @Test void oldJpaServiceClassesCannotBeLoaded(){
  for(String type:List.of("domain.project.service.ProjectService","domain.task.service.TaskService","domain.dashboard.service.DashboardService"))assertThatThrownBy(()->Class.forName("com.minierp.backend."+type)).isInstanceOf(ClassNotFoundException.class);
 }
}
