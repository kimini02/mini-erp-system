package com.minierp.backend.integration;
import com.minierp.backend.support.*;
import com.minierp.backend.domain.task.service.*;
import com.minierp.backend.domain.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import java.time.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
@org.springframework.context.annotation.Import(JdbcProbe.Configuration.class)
class M4OverdueTest extends BoardTestSupport {
 @Autowired TaskOverdueService service;
 @MockitoSpyBean NotificationService notifications;
 private long dueTask(){long p=project(leader,1),id=taskIds(p).get(0);jdbc.update("UPDATE tasks SET end_date=? WHERE id=?",date.minusDays(1),id);jdbc.update("INSERT INTO task_assignments(task_id,user_id,created_at) VALUES(?,?,?)",id,employee.getId(),LocalDateTime.now(clock));return id;}
 private long alerts(long id){return jdbc.queryForObject("SELECT COUNT(*) FROM task_due_alerts WHERE task_id=?",Long.class,id);}
 private long sent(){return jdbc.queryForObject("SELECT COUNT(*) FROM notifications WHERE user_id=? AND type='TASK_OVERDUE'",Long.class,employee.getId());}
 @Test void repeatAndChangedDeadlineAndDoneFollowTheUniqueContract(){
  long task=dueTask();service.runToday();service.runToday();assertThat(alerts(task)).isEqualTo(1);assertThat(sent()).isEqualTo(1);
  String key=jdbc.queryForObject("SELECT CONCAT(p.project_key,'-',t.task_no,' ',t.task_title) FROM tasks t JOIN projects p ON p.id=t.project_id WHERE t.id=?",String.class,task);
  assertThat(jdbc.queryForObject("SELECT title FROM notifications WHERE user_id=?",String.class,employee.getId())).isEqualTo(key);
  jdbc.update("UPDATE tasks SET end_date=? WHERE id=?",date.minusDays(2),task);service.runToday();assertThat(alerts(task)).isEqualTo(2);assertThat(sent()).isEqualTo(2);
  jdbc.update("UPDATE tasks SET task_status='DONE',end_date=? WHERE id=?",date.minusDays(3),task);service.runToday();assertThat(sent()).isEqualTo(2);
  assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM batch_runs WHERE job_name='TASK_OVERDUE' AND status='SUCCESS'",Long.class)).isEqualTo(4);
  System.out.println("M4_OVERDUE repeat_notifications=1 changed_deadline_total=2 done_additional=0 run_records=4");
 }
 @Test void claimAffectedRowsDistinguishFirstInsertFromRepeat(){
  jdbc.update("UPDATE tasks SET task_status='DONE'");
  long task=dueTask();
  try(var trace=JdbcProbe.start()){
   service.runToday();service.runToday();
   var rows=trace.queries.stream().filter(q->q.sql.startsWith("insert")&&q.sql.contains("into task_due_alerts")).map(q->q.updates).toList();
   assertThat(rows).containsExactly(1L,0L);
  }
  assertThat(alerts(task)).isEqualTo(1);assertThat(sent()).isEqualTo(1);
 }
 @Test void invalidClaimFailsBatchRatherThanSilentlySkipping(){
  long task=dueTask();
  // Fault injection is confined to this disposable Testcontainers database.
  jdbc.execute("CREATE TRIGGER m4_invalid_claim BEFORE INSERT ON task_due_alerts FOR EACH ROW SET NEW.user_id=9223372036854775807");
  try{
   assertThatThrownBy(()->service.runToday()).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
   assertThat(alerts(task)).isZero();assertThat(sent()).isZero();
   assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_histories WHERE task_id=?",Long.class,task)).isZero();
   assertThat(jdbc.queryForObject("SELECT status FROM batch_runs",String.class)).isEqualTo("FAILED");
  }finally{jdbc.execute("DROP TRIGGER m4_invalid_claim");}
  service.runToday();assertThat(alerts(task)).isEqualTo(1);assertThat(sent()).isEqualTo(1);
 }
 @Test void concurrentRunsSendOnlyOnce()throws Exception{
  long task=dueTask();var barrier=new CyclicBarrier(2);var pool=Executors.newFixedThreadPool(2);
  try{Callable<Object> run=()->{barrier.await(10,TimeUnit.SECONDS);return service.runToday();};var a=pool.submit(run);var b=pool.submit(run);a.get(20,TimeUnit.SECONDS);b.get(20,TimeUnit.SECONDS);}finally{pool.shutdownNow();}
  assertThat(alerts(task)).isEqualTo(1);assertThat(sent()).isEqualTo(1);System.out.println("M4_OVERDUE concurrent_runs=2 claims=1 notifications=1");
 }
 @Test void notificationFailureRollsBackClaimAndHistoryAndAllowsRetry(){
  long task=dueTask();doThrow(new IllegalStateException("test failure")).when((NotificationService)org.springframework.test.util.AopTestUtils.getUltimateTargetObject(notifications)).storeTaskOverdue(eq(employee.getId()),anyString(),anyString());
  assertThatThrownBy(()->service.runToday()).isInstanceOf(IllegalStateException.class);assertThat(alerts(task)).isZero();assertThat(sent()).isZero();assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_histories WHERE task_id=?",Long.class,task)).isZero();
  assertThat(jdbc.queryForObject("SELECT status FROM batch_runs",String.class)).isEqualTo("FAILED");
  doCallRealMethod().when((NotificationService)org.springframework.test.util.AopTestUtils.getUltimateTargetObject(notifications)).storeTaskOverdue(eq(employee.getId()),anyString(),anyString());service.runToday();assertThat(sent()).isEqualTo(1);
 }
}
