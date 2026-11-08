package com.minierp.backend.domain.project.service;

import com.minierp.backend.domain.project.dto.BoardProjectRequests.*;
import com.minierp.backend.domain.project.entity.ProjectStatus;
import com.minierp.backend.domain.user.service.EmployeeService;
import com.minierp.backend.domain.project.repository.ProjectQueries;
import com.minierp.backend.domain.project.repository.ProjectStore;
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
    private final ProjectQueries queries;
    private final ProjectStore store;
    private final PermissionAccess access;
    private final Clock clock;
    public long actor() {
        return access.actor();
    }

    public String visibilityPredicate(Map<String, Object> args) {
        var scope = access.scope("PROJECT");
        return queries.visibility(args, scope, actor());
    }

    public Map<String, Object> visible(long id) {
        return visible(id, true);
    }

    private Map<String, Object> visible(long id, boolean summary) {
        access.require("PROJECT", Action.VIEW);
        var today = LocalDate.now(clock);
        var scope = access.scope("PROJECT");
        long actor = actor();
        var rows = queries.findVisible(id, today, scope, actor, summary);
        if (rows.isEmpty()) {
            if (queries.countProject(id) == 0) {
                throw new BusinessException(ErrorCode.PROJECT_NOT_FOUND);
            }
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return rows.get(0);
    }

    /** Project is the first locked row. Recheck visibility after obtaining the lock. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<String, Object> lock(long id) {
        store.findLockedProject(id);
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
        var today = LocalDate.now(clock);
        long offset = (long) page * size;
        var scope = access.scope("PROJECT");
        long actor = actor();
        String statusValue = status == null ? null : status.name();
        String pattern = q == null || q.isBlank()
                ? null
                : "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        long total = queries.countProjects(today, offset, size, scope, actor, statusValue, pattern);
        var rows = queries.findProjects(today, offset, size, scope, actor, statusValue, pattern);
        return EmployeeService.page(rows.stream().map(this::view).toList(), page, size, total);
    }

    public List<Map<String, Object>> members(long id) {
        return queries.findMembers(id);
    }

    public Map<String, Object> detail(long id) {
        var p = visible(id);
        var result = view(p);
        result.put("members", members(id));
        result.put("permissions", permissions(p));
        return result;
    }

    private void active(long user) {
        if (queries.countActiveUser(user) == 0) {
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
            id = store.insertProject(r.key(), r.title(), r.content(), r.startDate(), r.endDate(),
                    r.priority() == null ? "MEDIUM" : r.priority().name(), r.leaderId(), LocalDateTime.now(clock));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.PROJECT_KEY_DUPLICATE);
        }
        store.insertLeaderMember(id, r.leaderId(), LocalDateTime.now(clock));
        // Creation may designate an owner outside the creator's viewing scope. Return the created data, not an
        // unrestricted read endpoint.
        var p = queries.findSummary(id, LocalDate.now(clock), actor());
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
            store.insertLeaderIfMissing(id, r.leaderId(), LocalDateTime.now(clock));
        }
        store.updateProject(id, r.title(), r.content(), start, end, r.priority() == null ? null : r.priority().name(),
                r.leaderId(), LocalDateTime.now(clock));
        // A manager can transfer ownership out of their scope. Return this completed mutation without a second scope
        // denial/rollback.
        var changed = queries.findSummary(id, LocalDate.now(clock), actor());
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
        if (!removed.isEmpty() && queries.countUnfinishedForMembers(id, removed) > 0) {
            throw new BusinessException(ErrorCode.PROJECT_MEMBER_HAS_TASKS);
        }
        return selected;
    }

    private List<Long> memberIds(long id) {
        return queries.findMemberIds(id).stream().map(r -> number(r, "user_id")).toList();
    }

    private void applyMembers(long id, Set<Long> selected) {
        var old = memberIds(id);
        var removed = old.stream().filter(u -> !selected.contains(u)).toList();
        if (!removed.isEmpty()) {
            store.deleteMembers(id, removed);
        }
        for (long user : selected) {
            if (!old.contains(user)) {
                store.insertMember(id, user, LocalDateTime.now(clock));
            }
        }
        store.updateChangedAt(id, LocalDateTime.now(clock));
    }
}
