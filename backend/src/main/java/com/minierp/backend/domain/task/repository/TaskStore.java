package com.minierp.backend.domain.task.repository;

import com.minierp.backend.global.persistence.SqlStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.map;

@Repository
@RequiredArgsConstructor
public class TaskStore {
    private final SqlStore db;

    public int insertHistory(long id, long actor, String action, String from, String to, LocalDateTime now) {
        return db.update(
                "INSERT INTO task_histories(task_id,actor_id,action,from_value,to_value,created_at) VALUES(:id,:actor,:action,:from,:to,:now)",
                map("id", id, "actor", actor, "action", action, "from", from, "to", to, "now", now));
    }

    public int deleteAssignment(long id, long user) {
        return db.update("DELETE FROM task_assignments WHERE task_id=:id AND user_id=:user",
                map("id", id, "user", user));
    }

    public int insertAssignment(long id, long user, LocalDateTime now) {
        return db.update("INSERT INTO task_assignments(task_id,user_id,created_at) VALUES(:id,:user,:now)",
                map("id", id, "user", user, "now", now));
    }

    public long insertTask(
            long projectId,
            long seq,
            long pos,
            long actor,
            String title,
            String content,
            LocalDate end,
            String priority,
            String status,
            LocalDateTime now) {
        return db.insert(
                "INSERT INTO tasks(project_id,task_no,position,version,created_by,task_title,task_content,end_date,priority,task_status,created_at) VALUES(:p,:seq,:pos,0,:actor,:title,:content,:end,:priority,:status,:now)",
                map("p", projectId, "seq", seq, "pos", pos, "actor", actor, "title", title, "content", content,
                        "end", end, "priority", priority, "status", status, "now", now));
    }

    public int updateLastSequence(long seq, long id) {
        return db.update("UPDATE projects SET last_task_seq=:seq WHERE id=:id", map("seq", seq, "id", id));
    }

    public int updateTask(long id, String title, String content, LocalDate end, String priority, LocalDateTime now) {
        return db.update(
                "UPDATE tasks SET task_title=COALESCE(:title,task_title),task_content=COALESCE(:content,task_content),end_date=COALESCE(:end,end_date),priority=COALESCE(:priority,priority),version=version+1,updated_at=:now WHERE id=:id",
                map("id", id, "title", title, "content", content, "end", end, "priority", priority, "now", now));
    }

    public int updatePosition(long id, String status, long pos, LocalDateTime now) {
        return db.update(
                "UPDATE tasks SET task_status=:status,position=:pos,version=version+1,updated_at=:now WHERE id=:id",
                map("id", id, "status", status, "pos", pos, "now", now));
    }

    public List<Map<String, Object>> findLockedProject(long id) {
        return db.rows("SELECT id FROM projects WHERE id=:id FOR UPDATE", map("id", id));
    }

    public int updateRebalancedPositions(long projectId, String status, long moving) {
        return db.update("""
    UPDATE tasks t JOIN (SELECT id,ROW_NUMBER() OVER(ORDER BY position,id)*1024 AS new_position FROM tasks
      WHERE project_id=:p AND task_status=:status AND id<>:moving) ranked ON ranked.id=t.id SET t.position=ranked.new_position
    """,
                map("p", projectId, "status", status, "moving", moving));
    }

    public int insertDueClaim(Object task, Object user, Object due, LocalDateTime now) {
        return db.update("""
      INSERT INTO task_due_alerts(task_id,user_id,due_date,created_at)
      SELECT :task,:user,:due,:now FROM DUAL
      WHERE NOT EXISTS (SELECT 1 FROM task_due_alerts WHERE task_id=:task AND user_id=:user AND due_date=:due)
      """,
                map("task", task, "user", user, "due", due, "now", now));
    }

    public int insertOverdueHistory(Object task, Object user, LocalDateTime now) {
        return db.update(
                "INSERT INTO task_histories(task_id,actor_id,action,to_value,created_at) VALUES(:task,NULL,'OVERDUE_NOTIFIED',:user,:now)",
                map("task", task, "user", user, "now", now));
    }
}
