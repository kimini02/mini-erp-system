package com.minierp.backend.domain.project.repository;

import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.permission.*;
import com.minierp.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.map;

@Repository
@RequiredArgsConstructor
public class ProjectQueries {
    private final SqlStore db;

    public long countProject(long id) {
        return db.count("SELECT COUNT(*) FROM projects WHERE id=:id", map("id", id));
    }

    public List<Map<String, Object>> findMembers(long id) {
        return db.rows(
                "SELECT u.id,u.user_name AS name,pos.name AS title FROM project_members m JOIN users u ON u.id=m.user_id LEFT JOIN positions pos ON pos.id=u.position_id WHERE m.project_id=:id ORDER BY u.user_name,u.id",
                map("id", id));
    }

    public long countActiveUser(long id) {
        return db.count("SELECT COUNT(*) FROM users WHERE id=:id AND status='ACTIVE'", map("id", id));
    }

    public Map<String, Object> findSummary(long id, LocalDate today, long actor) {
        return db.one(ProjectSummaryQuery.sql("p.id=:id", false), map("id", id, "today", today, "actor", actor),
                ErrorCode.PROJECT_NOT_FOUND);
    }

    public long countUnfinishedForMembers(long id, List<Long> removed) {
        return db.count(
                "SELECT COUNT(*) FROM tasks t JOIN task_assignments a ON a.task_id=t.id WHERE t.project_id=:id AND t.task_status<>'DONE' AND a.user_id IN (:removed)",
                map("id", id, "removed", removed));
    }

    public List<Map<String, Object>> findMemberIds(long id) {
        return db.rows("SELECT user_id FROM project_members WHERE project_id=:id", map("id", id));
    }

    public List<Map<String, Object>> findVisible(
            long id,
            LocalDate today,
            ScopeFilter scope,
            long actor,
            boolean summary) {
        var args = map("id", id, "today", today);
        String where = visibility(args, scope, actor);
        String select = summary
                ? ProjectSummaryQuery.sql("p.id=:id AND " + where, false)
                : ProjectSummaryQuery.ROWS + " WHERE p.id=:id AND " + where;
        return db.rows(select, args);
    }

    public long countProjects(
            LocalDate today,
            long offset,
            int size,
            ScopeFilter scope,
            long actor,
            String status,
            String pattern) {
        var args = parameters(today, offset, size);
        String where = where(args, scope, actor, status, pattern);
        return db.count("SELECT COUNT(*) FROM projects p LEFT JOIN users u ON u.id=p.leader_id WHERE " + where, args);
    }

    public List<Map<String, Object>> findProjects(
            LocalDate today,
            long offset,
            int size,
            ScopeFilter scope,
            long actor,
            String status,
            String pattern) {
        var args = parameters(today, offset, size);
        String where = where(args, scope, actor, status, pattern);
        return db.rows(ProjectSummaryQuery.sql(where, true), args);
    }

    public String visibility(Map<String, Object> args, ScopeFilter scope, long actor) {
        args.put("actor", actor);
        String self =
                "(p.leader_id=:actor OR EXISTS(SELECT 1 FROM project_members pm WHERE pm.project_id=p.id AND pm.user_id=:actor))";
        if (scope.scope() == DataScope.ALL) {
            return "1=1";
        }
        if (scope.scope() == DataScope.SELF) {
            return self;
        }
        args.put("departments", scope.departmentIds());
        return "(" + self + " OR u.department_id IN (:departments))";
    }

    private Map<String, Object> parameters(LocalDate today, long offset, int size) {
        return map("today", today, "offset", offset, "size", size);
    }

    private String where(Map<String, Object> args, ScopeFilter scope, long actor, String status, String pattern) {
        String where = visibility(args, scope, actor);
        if (status != null) {
            where += " AND p.status=:status";
            args.put("status", status);
        }
        if (pattern != null) {
            where += " AND (p.title LIKE :q ESCAPE '!' OR p.project_key LIKE :q ESCAPE '!')";
            args.put("q", pattern);
        }
        return where;
    }

}
