package com.minierp.backend.domain.project.service;

import com.minierp.backend.domain.project.dto.BoardProjectRequests.*;
import com.minierp.backend.domain.project.entity.ProjectStatus;
import com.minierp.backend.global.response.PageResponseDto;
import com.minierp.backend.domain.project.dto.*;
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

    public Map<String, Object> getVisibleProject(long id) {
        return getVisibleProject(id, true);
    }

    private Map<String, Object> getVisibleProject(long id, boolean summary) {
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
    public Map<String, Object> lockVisibleProject(long id) {
        store.findLockedProject(id);
        return getVisibleProject(id, false);
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

    public ProjectSummaryResponseDto toSummary(Map<String, Object> project) {
        String myRole = project.get("leader_id") != null && number(project, "leader_id") == actor()
                ? "LEADER"
                : number(project, "is_member") > 0 ? "MEMBER" : null;
        return ProjectSummaryResponseDto.from(project, myRole);
    }

    public ProjectPermissionsResponseDto getPermissions(Map<String, Object> projectRow) {
        boolean manage = canManage(projectRow);
        return new ProjectPermissionsResponseDto(manage, manage);
    }

    public PageResponseDto<ProjectSummaryResponseDto> getProjects(ProjectStatus status, String query, int page,
            int size) {
        access.require("PROJECT", Action.VIEW);
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        var today = LocalDate.now(clock);
        long offset = (long) page * size;
        var scope = access.scope("PROJECT");
        long actor = actor();
        String statusValue = status == null ? null : status.name();
        String pattern = query == null || query.isBlank()
                ? null
                : "%" + query.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        long total = queries.countProjects(today, offset, size, scope, actor, statusValue, pattern);
        var rows = queries.findProjects(today, offset, size, scope, actor, statusValue, pattern);
        return PageResponseDto.of(rows.stream().map(this::toSummary).toList(), page, size, total);
    }

    public List<ProjectPersonResponseDto> getMembers(long id) {
        return queries.findMembers(id).stream().map(ProjectPersonResponseDto::from).toList();
    }

    public ProjectDetailResponseDto getProject(long id) {
        var projectRow = getVisibleProject(id);
        var summary = toSummary(projectRow);
        var members = getMembers(id);
        var permissions = getPermissions(projectRow);
        return ProjectDetailResponseDto.from(summary, members, permissions);
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
    public ProjectDetailResponseDto createProject(Create request) {
        access.require("PROJECT", Action.VIEW);
        access.require("PROJECT", Action.CREATE);
        period(request.startDate(), request.endDate());
        active(request.leaderId());
        long id;
        try {
            id = store.insertProject(request.key(), request.title(), request.content(), request.startDate(),
                    request.endDate(),
                    request.priority() == null ? "MEDIUM" : request.priority().name(), request.leaderId(),
                    LocalDateTime.now(clock));
        } catch (org.springframework.dao.DuplicateKeyException e) {
            throw new BusinessException(ErrorCode.PROJECT_KEY_DUPLICATE);
        }
        store.insertLeaderMember(id, request.leaderId(), LocalDateTime.now(clock));
        // Creation may designate an owner outside the creator's viewing scope. Return the created data, not an
        // unrestricted read endpoint.
        var projectRow = queries.findSummary(id, LocalDate.now(clock), actor());
        var summary = toSummary(projectRow);
        var members = getMembers(id);
        return ProjectDetailResponseDto.from(summary, members, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProjectDetailResponseDto updateProject(long id, Patch request) {
        var projectRow = lockVisibleProject(id);
        requireManage(projectRow);
        var start = request.startDate() == null ? (LocalDate) projectRow.get("start_date") : request.startDate();
        var end = request.endDate() == null ? (LocalDate) projectRow.get("end_date") : request.endDate();
        period(start, end);
        if (request.title() != null && request.title().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (request.leaderId() != null) {
            active(request.leaderId());
            store.insertLeaderIfMissing(id, request.leaderId(), LocalDateTime.now(clock));
        }
        store.updateProject(id, request.title(), request.content(), start, end,
                request.priority() == null ? null : request.priority().name(),
                request.leaderId(), LocalDateTime.now(clock));
        // A manager can transfer ownership out of their scope. Return this completed mutation without a second scope
        // denial/rollback.
        var changed = queries.findSummary(id, LocalDate.now(clock), actor());
        var summary = toSummary(changed);
        var members = getMembers(id);
        return ProjectDetailResponseDto.from(summary, members, null);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProjectDetailResponseDto updateProjectMembers(long id, List<Long> requested) {
        var projectRow = lockVisibleProject(id);
        requireManage(projectRow);
        var selected = validateMembers(projectRow, requested);
        applyMembers(id, selected);
        return getProject(id);
    }

    private Set<Long> validateMembers(Map<String, Object> projectRow, List<Long> requested) {
        long id = number(projectRow, "id");
        Set<Long> selected = new LinkedHashSet<>(requested);
        if (projectRow.get("leader_id") != null) {
            selected.add(number(projectRow, "leader_id"));
        }
        var existing = memberIds(id);
        for (long user : selected) {
            if (!existing.contains(user)) {
                active(user);
            }
        }
        var removed = existing.stream().filter(userId -> !selected.contains(userId)).toList();
        if (!removed.isEmpty() && queries.countUnfinishedForMembers(id, removed) > 0) {
            throw new BusinessException(ErrorCode.PROJECT_MEMBER_HAS_TASKS);
        }
        return selected;
    }

    private List<Long> memberIds(long id) {
        return queries.findMemberIds(id).stream().map(member -> number(member, "user_id")).toList();
    }

    private void applyMembers(long id, Set<Long> selected) {
        var old = memberIds(id);
        var removed = old.stream().filter(userId -> !selected.contains(userId)).toList();
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
