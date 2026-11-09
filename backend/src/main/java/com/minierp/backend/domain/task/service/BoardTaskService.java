package com.minierp.backend.domain.task.service;

import com.minierp.backend.domain.project.repository.ProjectStateStore;
import com.minierp.backend.domain.task.dto.BoardTaskRequests.*;
import com.minierp.backend.domain.task.dto.*;
import com.minierp.backend.domain.project.dto.ProjectPersonResponseDto;
import com.minierp.backend.domain.project.service.*;
import com.minierp.backend.domain.notification.service.NotificationService;
import com.minierp.backend.domain.task.repository.TaskQueries;
import com.minierp.backend.domain.task.repository.TaskStore;
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
    private final TaskQueries queries;
    private final TaskStore store;
    private final BoardProjectService projects;
    private final ProjectStateStore states;
    private final NotificationService notifications;
    private final Clock clock;
    private Map<String, Object> raw(long id) {
        return queries.findTask(id);
    }

    private long projectId(long task) {
        return number(queries.findTaskProject(task), "project_id");
    }

    private List<ProjectPersonResponseDto> assignees(long id) {
        return queries.findAssignees(id).stream().map(ProjectPersonResponseDto::from).toList();
    }

    private boolean assigned(List<ProjectPersonResponseDto> people) {
        return people.stream().anyMatch(person -> person.id() == projects.actor());
    }

    private TaskCardResponseDto card(
            Map<String, Object> task,
            Map<String, Object> project,
            List<ProjectPersonResponseDto> people) {
        var end = (LocalDate) task.get("end_date");
        long overdue = "DONE".equals(task.get("task_status"))
                ? 0
                : Math.max(0, ChronoUnit.DAYS.between(end, LocalDate.now(clock)));
        return new TaskCardResponseDto(number(task, "id"), project.get("project_key") + "-" + task.get("task_no"),
                (String) task.get("task_title"), (String) task.get("task_status"), (String) task.get("priority"),
                end, overdue, people, number(task, "position"), number(task, "version"),
                projects.canManage(project) || assigned(people));
    }

    public TaskBoardResponseDto getBoard(long id) {
        var projectRow = projects.getVisibleProject(id);
        var tasks = queries.findBoardTasks(id);
        // Members and all assignments in one query: no per-card lookups, including empty boards.
        var people = queries.findBoardPeople(id);
        List<ProjectPersonResponseDto> members = new ArrayList<>();
        Map<Long, List<ProjectPersonResponseDto>> assignments = new HashMap<>();
        for (var row : people) {
            var user = ProjectPersonResponseDto.from(row);
            if (row.get("task_id") == null) {
                members.add(user);
            } else {
                assignments.computeIfAbsent(number(row, "task_id"), taskId -> new ArrayList<>()).add(user);
            }
        }
        List<TaskBoardResponseDto.Column> columns = new ArrayList<>();
        for (String status : List.of("TODO", "DOING", "DONE")) {
            columns.add(new TaskBoardResponseDto.Column(status, tasks.stream()
                            .filter(taskRow -> status.equals(taskRow.get("task_status")))
                            .map(taskRow -> card(taskRow, projectRow,
                                    assignments.getOrDefault(number(taskRow, "id"), List.of())))
                            .toList()));
        }
        return new TaskBoardResponseDto(projects.toSummary(projectRow), members, projects.getPermissions(projectRow),
                columns);
    }

    public TaskDetailResponseDto getTask(long id) {
        var projectRow = projects.getVisibleProject(projectId(id));
        return taskDetail(id, projectRow);
    }

    private TaskDetailResponseDto taskDetail(long id, Map<String, Object> projectRow) {
        var task = raw(id);
        var card = card(task, projectRow, assignees(id));
        long projectId = number(task, "project_id");
        String content = (String) task.get("task_content");
        boolean canManage = projects.canManage(projectRow);
        var history = queries.findHistory(id);
        return TaskDetailResponseDto.from(card, projectId, content, canManage,
                history.stream().map(TaskDetailResponseDto::historyFrom).toList());
    }

    private void history(long id, String action, String from, String to) {
        store.insertHistory(id, projects.actor(), action, from, to, LocalDateTime.now(clock));
    }

    private Set<Long> validateAssignees(long project, List<Long> ids) {
        Set<Long> unique = new LinkedHashSet<>(ids == null ? List.of() : ids);
        if (!unique.isEmpty() && queries.countMembers(project, unique) != unique.size()) {
            throw new BusinessException(ErrorCode.TASK_ASSIGNEE_NOT_MEMBER);
        }
        return unique;
    }

    private void assign(long task, long project, String title, Set<Long> selected) {
        var old = queries.findAssigneeIds(task).stream().map(assignment -> number(assignment, "user_id")).toList();
        for (long user : old) {
            if (!selected.contains(user)) {
                store.deleteAssignment(task, user);
                history(task, "UNASSIGNED", Long.toString(user), null);
            }
        }
        for (long user : selected) {
            if (!old.contains(user)) {
                store.insertAssignment(task, user, LocalDateTime.now(clock));
                history(task, "ASSIGNED", null, Long.toString(user));
                notifications.storeTask(user, task, title, "/projects/" + project + "/board?taskId=" + task);
            }
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskDetailResponseDto createTask(long project, Create request) {
        var projectRow = projects.lockVisibleProject(project);
        projects.requireManage(projectRow);
        var assigned = validateAssignees(project, request.assigneeIds());
        var end = request.endDate() == null ? (LocalDate) projectRow.get("end_date") : request.endDate();
        String status = request.status() == null ? "TODO" : request.status().name();
        long seq = number(projectRow, "last_task_seq") + 1;
        if (seq > Integer.MAX_VALUE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        long position = position(project, status, 0, null, true);
        long id = store.insertTask(project, seq, position, projects.actor(), request.title(),
                request.content() == null ? "" : request.content(), end,
                request.priority() == null ? "MEDIUM" : request.priority().name(), status, LocalDateTime.now(clock));
        store.updateLastSequence(seq, project);
        history(id, "CREATED", null, request.title());
        assign(id, project, projectRow.get("project_key") + "-" + seq + " " + request.title(), assigned);
        states.recalculate(project);
        return taskDetail(id, projectRow);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskDetailResponseDto updateTask(long id, Patch request) {
        long project = projectId(id);
        var projectRow = projects.lockVisibleProject(project);
        projects.requireManage(projectRow);
        var task = raw(id);
        Set<Long> selected = request.assigneeIds() == null ? null : validateAssignees(project, request.assigneeIds());
        if (request.title() != null && request.title().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        store.updateTask(id, request.title(), request.content(), request.endDate(),
                request.priority() == null ? null : request.priority().name(),
                LocalDateTime.now(clock));
        if (selected != null) {
            assign(id, project,
                    projectRow.get("project_key") + "-" + task.get("task_no") + " "
                            + (request.title() == null ? task.get("task_title") : request.title()), selected);
        }
        List<String> changed = new ArrayList<>();
        if (request.title() != null) {
            changed.add("제목");
        }
        if (request.content() != null) {
            changed.add("설명");
        }
        if (request.endDate() != null) {
            changed.add("마감일");
        }
        if (request.priority() != null) {
            changed.add("우선순위");
        }
        if (!changed.isEmpty()) {
            history(id, "UPDATED", null, String.join(", ", changed));
        }
        states.recalculate(project);
        return taskDetail(id, projectRow);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TaskMoveResponseDto moveTask(long id, Move request) {
        long project = projectId(id);
        var projectRow = projects.lockVisibleProject(project);
        var task = raw(id);
        var people = assignees(id);
        if (!projects.canManage(projectRow) && !assigned(people)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (number(task, "version") != request.version()) {
            throw new BusinessException(ErrorCode.TASK_VERSION_CONFLICT,
                    map("currentTask", card(task, projectRow, people)));
        }
        String status = request.status().name();
        long pos = position(project, status, id, request.afterTaskId(), false);
        store.updatePosition(id, status, pos, LocalDateTime.now(clock));
        boolean changed = !status.equals(task.get("task_status"));
        history(id, changed ? "STATUS" : "MOVED",
                changed ? (String) task.get("task_status") : task.get("position").toString(),
                changed ? status : Long.toString(pos));
        var summary = states.recalculate(project);
        return new TaskMoveResponseDto(card(raw(id), projectRow, people), TaskMoveResponseDto.Project.from(summary));
    }

    /** Requires the project lock. Only the destination column is rebalanced, excluding the moving card. */
    private long position(long project, String status, long moving, Long after, boolean append) {
        if (after != null) {
            if (after == moving || queries.countValidPredecessor(project, status, after) == 0) {
                throw new BusinessException(ErrorCode.INVALID_TASK_POSITION);
            }
        }
        for (int attempt = 0; attempt < 2; attempt++) {
            long lower = 0;
            if (append) {
                lower = queries.findMaxPosition(project, status, moving);
            } else if (after != null) {
                lower = number(queries.findPredecessorPosition(after), "position");
            }
            var next = append
                    ? List.< Map<String, Object>> of()
                    : queries.findNextPosition(project, status, moving, lower);
            if (next.isEmpty() && lower <= Long.MAX_VALUE - 1024) {
                return lower + 1024;
            }
            if (!next.isEmpty()) {
                long upper = number(next.get(0), "position");
                if (upper - lower > 1) {
                    return lower + (upper - lower) / 2;
                }
            }
            store.updateRebalancedPositions(project, status, moving);
        }
        throw new IllegalStateException("Cannot allocate board position after rebalance");
    }
}
