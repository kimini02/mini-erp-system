package com.minierp.backend.domain.task.service;
import com.minierp.backend.domain.notification.service.NotificationService;
import com.minierp.backend.global.persistence.SqlStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import static com.minierp.backend.global.persistence.SqlStore.*;
@Service @RequiredArgsConstructor
public class TaskOverdueWork {
 private final SqlStore db;private final NotificationService notifications;private final Clock clock;
 public record Result(int processed,int skipped) {}
 @Transactional(isolation=Isolation.READ_COMMITTED)
 public Result run(LocalDate today){
  int processed=0,skipped=0;
  // Same first lock as all board writes; id ordering prevents cross-project lock inversion.
  var projects=db.rows("SELECT DISTINCT project_id FROM tasks WHERE end_date<:today AND task_status<>'DONE' ORDER BY project_id",map("today",today));
  for(var p:projects){
   long project=number(p,"project_id");var locked=db.rows("SELECT id FROM projects WHERE id=:id FOR UPDATE",map("id",project));if(locked.isEmpty())continue;
   var tasks=db.rows("""
     SELECT t.id,t.task_no,t.task_title,t.end_date,p.project_key,a.user_id
     FROM tasks t JOIN projects p ON p.id=t.project_id JOIN task_assignments a ON a.task_id=t.id
     WHERE t.project_id=:project AND t.end_date<:today AND t.task_status<>'DONE' ORDER BY t.id,a.user_id
     """,map("project",project,"today",today));
   for(var t:tasks){
    var args=map("task",t.get("id"),"user",t.get("user_id"),"due",t.get("end_date"),"now",LocalDateTime.now(clock));
    // All claim writers hold the project lock. Exclude an existing key so JDBC's default
    // CLIENT_FOUND_ROWS cannot report a no-op duplicate update as a fresh insert (1).
    // The unique key remains the final guard; non-duplicate errors must roll back the batch.
    int inserted=db.update("""
      INSERT INTO task_due_alerts(task_id,user_id,due_date,created_at)
      SELECT :task,:user,:due,:now FROM DUAL
      WHERE NOT EXISTS (SELECT 1 FROM task_due_alerts WHERE task_id=:task AND user_id=:user AND due_date=:due)
      """,args);
    if(inserted!=1){skipped++;continue;}
    long task=number(t,"id");String title=t.get("project_key")+"-"+t.get("task_no")+" "+t.get("task_title");
    notifications.storeTaskOverdue(number(t,"user_id"),title,"/projects/"+project+"/board?taskId="+task);
    db.update("INSERT INTO task_histories(task_id,actor_id,action,to_value,created_at) VALUES(:task,NULL,'OVERDUE_NOTIFIED',:user,:now)",args);processed++;
   }
  }
  return new Result(processed,skipped);
 }
}
