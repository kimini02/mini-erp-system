package com.minierp.backend.domain.project.service;

import com.minierp.backend.domain.project.dto.BoardProjectRequests.*;
import com.minierp.backend.domain.project.entity.ProjectStatus;
import com.minierp.backend.domain.user.service.EmployeeService;
import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.permission.*;
import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class BoardProjectService {
    private final SqlStore db;
    private final PermissionAccess access;
    private final Clock clock;
    public long actor() {
        return access.actor();
    }

    public String visibilityPredicate(Map<String, Object> args) {
        var scope = access.scope("PROJECT");
        args.put("actor", actor());
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

    public Map<String, Object> visible(long id) {
        return visible(id, true);
    }

    private Map<String, Object> visible(long id, boolean summary) {
        access.require("PROJECT", Action.VIEW);
        var args = map("id", id, "today", LocalDate.now(clock));
        String where = visibilityPredicate(args);
        String select = summary
                ? ProjectSummaryQuery.sql("p.id=:id AND " + where, false)
                : ProjectSummaryQuery.ROWS + " WHERE p.id=:id AND " + where;
        var rows = db.rows(select, args);
        if (rows.isEmpty()) {
            if (db.count("SELECT COUNT(*) FROM projects WHERE id=:id", map("id", id)) == 0) {
                throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
            }
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return rows.get(0);
    }

    /** Project is the first locked row. Recheck visibility after obtaining the lock. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<String, Object> lock(long id) {
        db.one("SELECT id FROM projects WHERE id=:id FOR UPDATE", map("id", id), ErrorCode.PROJECT_NOT_FOUND);
        return visible(id, false);
    }

    public boolean canManage(Map<String, Object> row) {
        if (row.get("leader_id") != null && number(row, "leader_id") == actor()) {
            return true;
        }
        var grant = access.grants(actor()).get("PROJECT");
        return grant != null && grant.actions().contains("U");
    }

    public void requireManage(Map<String, Object> row) {
        if (!canManage(row)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    public Map<String, Object> view(Map<String, Object> p) {
        long total = number(p, "task_count");
        long done = number(p, "done_count");
        return map("id", p.get("id"), "key", p.get("project_key"), "title", p.get("title"), "content", p.get("content"),
                "status", p.get("status"), "priority", p.get("priority"), "startDate", p.get("start_date"),
                "endDate", p.get("end_date"),
                "leader", p.get("leader_id") == null
                        ? null
                        : map("id", p.get("leader_id"), "name", p.get("leader_name"), "title", p.get("leader_title")),
                "memberCount", p.get("member_count"), "taskCount", total, "doneCount", done,
                "overdueCount", p.get("overdue_count"), "progressPct", total == 0 ? 0 : done * 100 / total,
                "myRole", p.get("leader_id") != null && number(p, "leader_id") == actor()
                        ? "LEADER"
                        : number(p, "is_member") > 0 ? "MEMBER" : null);
    }

    public Map<String, Object> permissions(Map<String, Object> p) {
        boolean manage = canManage(p);
        return map("canManage", manage, "canCreateTask", manage);
    }

    public Map<String, Object> list(ProjectStatus status, String q, int page, int size) {
        access.require("PROJECT", Action.VIEW);
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        var args = map("today", LocalDate.now(clock), "offset", (long) page * size, "size", size);
        String where = visibilityPredicate(args);
        if (status != null) {
            where += " AND p.status=:status";
            args.put("status", status.name());
        }
        if (q != null && !q.isBlank()) {
            where += " AND (p.title LIKE :q ESCAPE '!' OR p.project_key LIKE :q ESCAPE '!')";
            args.put("q", "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
        }
        long total = db.count("SELECT COUNT(*) FROM projects p LEFT JOIN users u ON u.id=p.leader_id WHERE " + where,
                args);
        var rows = db.rows(ProjectSummaryQuery.sql(where, true), args);
        return EmployeeService.page(rows.stream().map(this::view).toList(), page, size, total);
    }

    public List<Map<String, Object>> members(long id) {
        return db.rows(
                "SELECT u.id,u.user_name AS name,pos.name AS title FROM project_members m JOIN users u ON u.id=m.user_id LEFT JOIN positions pos ON pos.id=u.position_id WHERE m.project_id=:id ORDER BY u.user_name,u.id",
                map("id", id));
    }

    public Map<String, Object> detail(long id) {
        var p = visible(id);
        var result = view(p);
        result.put("members", members(id));
        result.put("permissions", permissions(p));
        return result;
    }

    private void active(long user) {
        if (db.count("SELECT COUNT(*) FROM users WHERE id=:id AND status='ACTIVE'", map("id", user)) == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE, "재직 중인 사원을 지정해 주세요.");
        }
    }

    private void period(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw new BusinessException(ErrorCode.INVALID_PROJECT_PERIOD);
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> create(Create r) {
        access.require("PROJECT", Action.VIEW);
        access.require("PROJECT", Action.CREATE);
        period(r.startDate(), r.endDate());
        active(r.leaderId());
        long id;
        try {
            id = db.insert(
                    "INSERT INTO projects(project_key,title,content,start_date,end_date,priority,status,leader_id,created_at) VALUES(:key,:title,:content,:start,:end,:priority,'READY',:leader,:now)",
                    map("key", r.key(), "title", r.title(), "content", r.content(), "start", r.startDate(),
                            "end", r.endDate(), "priority", r.priority() == null ? "MEDIUM" : r.priority().name(),
                            "leader", r.leaderId(), "now", LocalDateTime.now(clock)));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.PROJECT_KEY_DUPLICATE);
        }
        db.update("INSERT INTO project_members(project_id,user_id,created_at) VALUES(:p,:u,:now)",
                map("p", id, "u", r.leaderId(), "now", LocalDateTime.now(clock)));
        // Creation may designate an owner outside the creator's viewing scope. Return the created data, not an
        // unrestricted read endpoint.
        var p = db.one(ProjectSummaryQuery.sql("p.id=:id", false),
                map("id", id, "today", LocalDate.now(clock), "actor", actor()), ErrorCode.PROJECT_NOT_FOUND);
        var result = view(p);
        result.put("members", members(id));
        return result;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> patch(long id, Patch r) {
        var p = lock(id);
        requireManage(p);
        var start = r.startDate() == null ? (LocalDate) p.get("start_date") : r.startDate();
        var end = r.endDate() == null ? (LocalDate) p.get("end_date") : r.endDate();
        period(start, end);
        if (r.title() != null && r.title().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (r.leaderId() != null) {
            active(r.leaderId());
            db.update("INSERT IGNORE INTO project_members(project_id,user_id,created_at) VALUES(:p,:u,:now)",
                    map("p", id, "u", r.leaderId(), "now", LocalDateTime.now(clock)));
        }
        db.update(
                "UPDATE projects SET title=COALESCE(:title,title),content=COALESCE(:content,content),start_date=:start,end_date=:end,priority=COALESCE(:priority,priority),leader_id=COALESCE(:leader,leader_id),updated_at=:now WHERE id=:id",
                map("id", id, "title", r.title(), "content", r.content(), "start", start, "end", end,
                        "priority", r.priority() == null ? null : r.priority().name(), "leader", r.leaderId(),
                        "now", LocalDateTime.now(clock)));
        // A manager can transfer ownership out of their scope. Return this completed mutation without a second scope
        // denial/rollback.
        var changed = db.one(ProjectSummaryQuery.sql("p.id=:id", false),
                map("id", id, "today", LocalDate.now(clock), "actor", actor()), ErrorCode.PROJECT_NOT_FOUND);
        var result = view(changed);
        result.put("members", members(id));
        return result;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> replaceMembers(long id, List<Long> requested) {
        var p = lock(id);
        requireManage(p);
        var selected = validateMembers(p, requested);
        applyMembers(id, selected);
        return detail(id);
    }

    private Set<Long> validateMembers(Map<String, Object> p, List<Long> requested) {
        long id = number(p, "id");
        Set<Long> selected = new LinkedHashSet<>(requested);
        if (p.get("leader_id") != null) {
            selected.add(number(p, "leader_id"));
        }
        var existing = memberIds(id);
        for (long user : selected) {
            if (!existing.contains(user)) {
                active(user);
            }
        }
        var removed = existing.stream().filter(u -> !selected.contains(u)).toList();
        if (!removed.isEmpty()
                && db.count(
                        "SELECT COUNT(*) FROM tasks t JOIN task_assignments a ON a.task_id=t.id WHERE t.project_id=:id AND t.task_status<>'DONE' AND a.user_id IN (:removed)",
                        map("id", id, "removed", removed)) > 0) {
            throw new BusinessException(ErrorCode.PROJECT_MEMBER_HAS_TASKS);
        }
        return selected;
    }

    private List<Long> memberIds(long id) {
        return db.rows("SELECT user_id FROM project_members WHERE project_id=:id", map("id", id))
                .stream()
                .map(r -> number(r, "user_id"))
                .toList();
    }

    private void applyMembers(long id, Set<Long> selected) {
        var old = memberIds(id);
        var removed = old.stream().filter(u -> !selected.contains(u)).toList();
        if (!removed.isEmpty()) {
            db.update("DELETE FROM project_members WHERE project_id=:id AND user_id IN (:removed)",
                    map("id", id, "removed", removed));
        }
        for (long user : selected) {
            if (!old.contains(user)) {
                db.update("INSERT INTO project_members(project_id,user_id,created_at) VALUES(:p,:u,:now)",
                        map("p", id, "u", user, "now", LocalDateTime.now(clock)));
            }
        }
        db.update("UPDATE projects SET updated_at=:now WHERE id=:id", map("id", id, "now", LocalDateTime.now(clock)));
    }
}
