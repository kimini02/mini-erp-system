package com.minierp.backend.domain.task.service;

import com.minierp.backend.domain.task.dto.BoardTaskRequests.*;
import com.minierp.backend.domain.project.service.*;
import com.minierp.backend.domain.notification.service.NotificationService;
import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class BoardTaskService {
    private final SqlStore db;
    private final BoardProjectService projects;
    private final ProjectStateStore states;
    private final NotificationService notifications;
    private final Clock clock;
    private Map<String, Object> raw(long id) {
        return db.one("SELECT * FROM tasks WHERE id=:id", map("id", id), ErrorCode.TASK_NOT_FOUND);
    }

    private long projectId(long task) {
        return number(db.one("SELECT project_id FROM tasks WHERE id=:id", map("id", task), ErrorCode.TASK_NOT_FOUND),
                "project_id");
    }

    private List<Map<String, Object>> assignees(long id) {
        return db.rows(
                "SELECT u.id,u.user_name AS name,p.name AS title FROM task_assignments a JOIN users u ON u.id=a.user_id LEFT JOIN positions p ON p.id=u.position_id WHERE a.task_id=:id ORDER BY u.id",
                map("id", id));
    }

    private boolean assigned(List<Map<String, Object>> people) {
        return people.stream().anyMatch(u -> number(u, "id") == projects.actor());
    }

    private Map<String, Object> card(
            Map<String, Object> task,
            Map<String, Object> project,
            List<Map<String, Object>> people) {
        var end = (LocalDate) task.get("end_date");
        long overdue = "DONE".equals(task.get("task_status"))
                ? 0
                : Math.max(0, ChronoUnit.DAYS.between(end, LocalDate.now(clock)));
        return map("id", task.get("id"), "key", project.get("project_key") + "-" + task.get("task_no"),
                "title", task.get("task_title"), "status", task.get("task_status"), "priority", task.get("priority"),
                "endDate", end, "overdueDays", overdue, "assignees", people, "position", task.get("position"),
                "version", task.get("version"), "canMove", projects.canManage(project) || assigned(people));
    }

    public Map<String, Object> board(long id) {
        var p = projects.visible(id);
        var tasks = db.rows("SELECT * FROM tasks WHERE project_id=:id ORDER BY position,id", map("id", id));
        // Members and all assignments in one query: no per-card lookups, including empty boards.
        var people = db.rows("""
   SELECT NULL AS task_id,u.id,u.user_name AS name,pos.name AS title FROM project_members m JOIN users u ON u.id=m.user_id LEFT JOIN positions pos ON pos.id=u.position_id WHERE m.project_id=:id
   UNION ALL
   SELECT a.task_id,u.id,u.user_name AS name,pos.name AS title FROM task_assignments a JOIN tasks t ON t.id=a.task_id JOIN users u ON u.id=a.user_id LEFT JOIN positions pos ON pos.id=u.position_id WHERE t.project_id=:id
   """, map("id", id));
        List<Map<String, Object>> members = new ArrayList<>();
        Map<Long, List<Map<String, Object>>> assignments = new HashMap<>();
        for (var row : people) {
            var user = map("id", row.get("id"), "name", row.get("name"), "title", row.get("title"));
            if (row.get("task_id") == null) {
                members.add(user);
            } else {
                assignments.computeIfAbsent(number(row, "task_id"), k -> new ArrayList<>()).add(user);
            }
        }
        List<Map<String, Object>> columns = new ArrayList<>();
        for (String status : List.of("TODO", "DOING", "DONE")) {
            columns.add(map("status", status,
                    "tasks", tasks.stream()
                            .filter(t -> status.equals(t.get("task_status")))
                            .map(t -> card(t, p, assignments.getOrDefault(number(t, "id"), List.of())))
                            .toList()));
        }
        return map("project", projects.view(p), "members", members, "permissions", projects.permissions(p),
                "columns", columns);
    }

    public Map<String, Object> detail(long id) {
        var p = projects.visible(projectId(id));
        return detail(id, p);
    }

    private Map<String, Object> detail(long id, Map<String, Object> p) {
        var task = raw(id);
        var result = card(task, p, assignees(id));
        result.put("projectId", task.get("project_id"));
        result.put("content", task.get("task_content"));
        result.put("canManage", projects.canManage(p));
        var history = db.rows("""
   SELECT h.*,u.user_name AS actor_name,target.user_name AS target_name,
     CASE h.action WHEN 'ASSIGNED' THEN CAST(h.to_value AS UNSIGNED) WHEN 'UNASSIGNED' THEN CAST(h.from_value AS UNSIGNED) WHEN 'OVERDUE_NOTIFIED' THEN CAST(h.to_value AS UNSIGNED) END AS target_id
   FROM task_histories h LEFT JOIN users u ON u.id=h.actor_id
   LEFT JOIN users target ON target.id=CASE h.action WHEN 'ASSIGNED' THEN CAST(h.to_value AS UNSIGNED) WHEN 'UNASSIGNED' THEN CAST(h.from_value AS UNSIGNED) WHEN 'OVERDUE_NOTIFIED' THEN CAST(h.to_value AS UNSIGNED) END
   WHERE task_id=:id ORDER BY h.id DESC LIMIT 50
   """, map("id", id));
        result.put("history", history.stream().map(h -> {
            var event = map("action", h.get("action"),
                    "actor", h.get("actor_id") == null
                            ? null
                            : map("id", h.get("actor_id"), "name", h.get("actor_name")),
                    "at", h.get("created_at"));
            if (!"MOVED".equals(h.get("action"))) {
                event.put("from", h.get("from_value"));
                event.put("to", h.get("to_value"));
            }
            if (h.get("target_id") != null) {
                event.put("target", map("id", h.get("target_id"), "name", h.get("target_name")));
            }
            return event;
        }).toList());
        return result;
    }

    private void history(long id, String action, String from, String to) {
        db.update(
                "INSERT INTO task_histories(task_id,actor_id,action,from_value,to_value,created_at) VALUES(:id,:actor,:action,:from,:to,:now)",
                map("id", id, "actor", projects.actor(), "action", action, "from", from, "to", to,
                        "now", LocalDateTime.now(clock)));
    }

    private Set<Long> validateAssignees(long project, List<Long> ids) {
        Set<Long> unique = new LinkedHashSet<>(ids == null ? List.of() : ids);
        if (!unique.isEmpty()
                && db.count("SELECT COUNT(*) FROM project_members WHERE project_id=:p AND user_id IN (:ids)",
                        map("p", project, "ids", unique)) != unique.size()) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNEE_NOT_MEMBER);
        }
        return unique;
    }

    private void assign(long task, long project, String title, Set<Long> selected) {
        var old = db.rows("SELECT user_id FROM task_assignments WHERE task_id=:id", map("id", task))
                .stream()
                .map(r -> number(r, "user_id"))
                .toList();
        for (long user : old) {
            if (!selected.contains(user)) {
                db.update("DELETE FROM task_assignments WHERE task_id=:id AND user_id=:user",
                        map("id", task, "user", user));
                history(task, "UNASSIGNED", Long.toString(user), null);
            }
        }
        for (long user : selected) {
            if (!old.contains(user)) {
                db.update("INSERT INTO task_assignments(task_id,user_id,created_at) VALUES(:id,:user,:now)",
                        map("id", task, "user", user, "now", LocalDateTime.now(clock)));
                history(task, "ASSIGNED", null, Long.toString(user));
                notifications.storeTask(user, task, title, "/projects/" + project + "/board?taskId=" + task);
            }
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> create(long project, Create r) {
        var p = projects.lock(project);
        projects.requireManage(p);
        var assigned = validateAssignees(project, r.assigneeIds());
        var end = r.endDate() == null ? (LocalDate) p.get("end_date") : r.endDate();
        String status = r.status() == null ? "TODO" : r.status().name();
        long seq = number(p, "last_task_seq") + 1;
        if (seq > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        long position = position(project, status, 0, null, true);
        long id = db.insert(
                "INSERT INTO tasks(project_id,task_no,position,version,created_by,task_title,task_content,end_date,priority,task_status,created_at) VALUES(:p,:seq,:pos,0,:actor,:title,:content,:end,:priority,:status,:now)",
                map("p", project, "seq", seq, "pos", position, "actor", projects.actor(), "title", r.title(),
                        "content", r.content() == null ? "" : r.content(), "end", end,
                        "priority", r.priority() == null ? "MEDIUM" : r.priority().name(), "status", status,
                        "now", LocalDateTime.now(clock)));
        db.update("UPDATE projects SET last_task_seq=:seq WHERE id=:id", map("seq", seq, "id", project));
        history(id, "CREATED", null, r.title());
        assign(id, project, p.get("project_key") + "-" + seq + " " + r.title(), assigned);
        states.recalculate(project);
        return detail(id, p);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> patch(long id, Patch r) {
        long project = projectId(id);
        var p = projects.lock(project);
        projects.requireManage(p);
        var task = raw(id);
        Set<Long> selected = r.assigneeIds() == null ? null : validateAssignees(project, r.assigneeIds());
        if (r.title() != null && r.title().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        db.update(
                "UPDATE tasks SET task_title=COALESCE(:title,task_title),task_content=COALESCE(:content,task_content),end_date=COALESCE(:end,end_date),priority=COALESCE(:priority,priority),version=version+1,updated_at=:now WHERE id=:id",
                map("id", id, "title", r.title(), "content", r.content(), "end", r.endDate(),
                        "priority", r.priority() == null ? null : r.priority().name(),
                        "now", LocalDateTime.now(clock)));
        if (selected != null) {
            assign(id, project,
                    p.get("project_key") + "-" + task.get("task_no") + " "
                            + (r.title() == null ? task.get("task_title") : r.title()), selected);
        }
        List<String> changed = new ArrayList<>();
        if (r.title() != null) {
            changed.add("제목");
        }
        if (r.content() != null) {
            changed.add("설명");
        }
        if (r.endDate() != null) {
            changed.add("마감일");
        }
        if (r.priority() != null) {
            changed.add("우선순위");
        }
        if (!changed.isEmpty()) {
            history(id, "UPDATED", null, String.join(", ", changed));
        }
        states.recalculate(project);
        return detail(id, p);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> move(long id, Move r) {
        long project = projectId(id);
        var p = projects.lock(project);
        var task = raw(id);
        var people = assignees(id);
        if (!projects.canManage(p) && !assigned(people)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (number(task, "version") != r.version()) {
            throw new BusinessException(ErrorCode.TASK_VERSION_CONFLICT, map("currentTask", card(task, p, people)));
        }
        String status = r.status().name();
        long pos = position(project, status, id, r.afterTaskId(), false);
        db.update("UPDATE tasks SET task_status=:status,position=:pos,version=version+1,updated_at=:now WHERE id=:id",
                map("id", id, "status", status, "pos", pos, "now", LocalDateTime.now(clock)));
        boolean changed = !status.equals(task.get("task_status"));
        history(id, changed ? "STATUS" : "MOVED",
                changed ? (String) task.get("task_status") : task.get("position").toString(),
                changed ? status : Long.toString(pos));
        var summary = states.recalculate(project);
        return map("task", card(raw(id), p, people), "project", summary);
    }

    /** Requires the project lock. Only the destination column is rebalanced, excluding the moving card. */
    private long position(long project, String status, long moving, Long after, boolean append) {
        var args = map("p", project, "status", status, "moving", moving);
        if (after != null) {
            args.put("after", after);
            if (after == moving
                    || db.count("SELECT COUNT(*) FROM tasks WHERE id=:after AND project_id=:p AND task_status=:status",
                            args) == 0) {
                throw new BusinessException(ErrorCode.INVALID_TASK_POSITION);
            }
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            long lower = 0;
            if (append) {
                lower = db.count(
                        "SELECT COALESCE(MAX(position),0) FROM tasks WHERE project_id=:p AND task_status=:status AND id<>:moving",
                        args);
            } else if (after != null) {
                lower = number(
                        db.one("SELECT position FROM tasks WHERE id=:after", args, ErrorCode.INVALID_TASK_POSITION),
                        "position");
            }
            args.put("lower", lower);
            var next = append
                    ? List.< Map<String, Object>> of()
                    : db.rows(
                            "SELECT position FROM tasks WHERE project_id=:p AND task_status=:status AND id<>:moving AND position>:lower ORDER BY position,id LIMIT 1",
                            args);
            if (next.isEmpty() && lower <= Long.MAX_VALUE - 1024) {
                return lower + 1024;
            }
            if (!next.isEmpty()) {
                long upper = number(next.get(0), "position");
                if (upper - lower > 1) {
                    return lower + (upper - lower) / 2;
                }
            }
            db.update("""
    UPDATE tasks t JOIN (SELECT id,ROW_NUMBER() OVER(ORDER BY position,id)*1024 AS new_position FROM tasks
      WHERE project_id=:p AND task_status=:status AND id<>:moving) ranked ON ranked.id=t.id SET t.position=ranked.new_position
    """, args);
        }
        throw new IllegalStateException("Cannot allocate board position after rebalance");
    }
}
