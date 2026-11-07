package com.minierp.backend.domain.task.repository;

import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.map;

@Repository
@RequiredArgsConstructor
public class TaskQueries {
    private final SqlStore db;

    public Map<String, Object> findTask(long id) {
        return db.one("SELECT * FROM tasks WHERE id=:id", map("id", id), ErrorCode.TASK_NOT_FOUND);
    }

    public Map<String, Object> findTaskProject(long id) {
        return db.one("SELECT project_id FROM tasks WHERE id=:id", map("id", id), ErrorCode.TASK_NOT_FOUND);
    }

    public List<Map<String, Object>> findAssignees(long id) {
        return db.rows(
                "SELECT u.id,u.user_name AS name,p.name AS title FROM task_assignments a JOIN users u ON u.id=a.user_id LEFT JOIN positions p ON p.id=u.position_id WHERE a.task_id=:id ORDER BY u.id",
                map("id", id));
    }

    public List<Map<String, Object>> findBoardTasks(long id) {
        return db.rows("SELECT * FROM tasks WHERE project_id=:id ORDER BY position,id", map("id", id));
    }

    public List<Map<String, Object>> findBoardPeople(long id) {
        return db.rows("""
   SELECT NULL AS task_id,u.id,u.user_name AS name,pos.name AS title FROM project_members m JOIN users u ON u.id=m.user_id LEFT JOIN positions pos ON pos.id=u.position_id WHERE m.project_id=:id
   UNION ALL
   SELECT a.task_id,u.id,u.user_name AS name,pos.name AS title FROM task_assignments a JOIN tasks t ON t.id=a.task_id JOIN users u ON u.id=a.user_id LEFT JOIN positions pos ON pos.id=u.position_id WHERE t.project_id=:id
   """, map("id", id));
    }

    public List<Map<String, Object>> findHistory(long id) {
        return db.rows("""
   SELECT h.*,u.user_name AS actor_name,target.user_name AS target_name,
     CASE h.action WHEN 'ASSIGNED' THEN CAST(h.to_value AS UNSIGNED) WHEN 'UNASSIGNED' THEN CAST(h.from_value AS UNSIGNED) WHEN 'OVERDUE_NOTIFIED' THEN CAST(h.to_value AS UNSIGNED) END AS target_id
   FROM task_histories h LEFT JOIN users u ON u.id=h.actor_id
   LEFT JOIN users target ON target.id=CASE h.action WHEN 'ASSIGNED' THEN CAST(h.to_value AS UNSIGNED) WHEN 'UNASSIGNED' THEN CAST(h.from_value AS UNSIGNED) WHEN 'OVERDUE_NOTIFIED' THEN CAST(h.to_value AS UNSIGNED) END
   WHERE task_id=:id ORDER BY h.id DESC LIMIT 50
   """, map("id", id));
    }

    public long countMembers(long p, Set<Long> ids) {
        return db.count("SELECT COUNT(*) FROM project_members WHERE project_id=:p AND user_id IN (:ids)",
                map("p", p, "ids", ids));
    }

    public List<Map<String, Object>> findAssigneeIds(long id) {
        return db.rows("SELECT user_id FROM task_assignments WHERE task_id=:id", map("id", id));
    }

    public List<Map<String, Object>> findOverdueProjects(LocalDate today) {
        return db.rows(
                "SELECT DISTINCT project_id FROM tasks WHERE end_date<:today AND task_status<>'DONE' ORDER BY project_id",
                map("today", today));
    }

    public List<Map<String, Object>> findOverdueTasks(long project, LocalDate today) {
        return db.rows("""
     SELECT t.id,t.task_no,t.task_title,t.end_date,p.project_key,a.user_id
     FROM tasks t JOIN projects p ON p.id=t.project_id JOIN task_assignments a ON a.task_id=t.id
     WHERE t.project_id=:project AND t.end_date<:today AND t.task_status<>'DONE' ORDER BY t.id,a.user_id
     """, map("project", project, "today", today));
    }

    public long countValidPredecessor(long p, String status, Long after) {
        return db.count("SELECT COUNT(*) FROM tasks WHERE id=:after AND project_id=:p AND task_status=:status",
                map("p", p, "status", status, "after", after));
    }

    public long countMaxPosition(long p, String status, long moving) {
        return db.count(
                "SELECT COALESCE(MAX(position),0) FROM tasks WHERE project_id=:p AND task_status=:status AND id<>:moving",
                map("p", p, "status", status, "moving", moving));
    }

    public Map<String, Object> findPredecessorPosition(Long after) {
        return db.one("SELECT position FROM tasks WHERE id=:after", map("after", after),
                ErrorCode.INVALID_TASK_POSITION);
    }

    public List<Map<String, Object>> findNextPosition(long p, String status, long moving, long lower) {
        return db.rows(
                "SELECT position FROM tasks WHERE project_id=:p AND task_status=:status AND id<>:moving AND position>:lower ORDER BY position,id LIMIT 1",
                map("p", p, "status", status, "moving", moving, "lower", lower));
    }
}
