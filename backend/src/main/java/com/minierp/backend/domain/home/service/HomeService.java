package com.minierp.backend.domain.home.service;

import com.minierp.backend.domain.home.repository.HomeQueries;
import com.minierp.backend.domain.home.dto.HomeResponseDto;
import com.minierp.backend.domain.home.dto.HomeResponseDto.*;
import com.minierp.backend.domain.notification.dto.NotificationItemResponseDto;
import com.minierp.backend.domain.attendance.service.AttendanceService;
import com.minierp.backend.domain.approval.service.FormService;
import com.minierp.backend.domain.leave.service.LeaveQueryService;
import com.minierp.backend.domain.project.service.*;
import com.minierp.backend.global.permission.PermissionAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.time.temporal.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class HomeService {
    private final HomeQueries queries;
    private final Clock clock;
    private final PermissionAccess access;
    private final AttendanceService attendance;
    private final LeaveQueryService leave;
    private final BoardProjectService projects;
    public HomeResponseDto getHome() {
        long actor = access.actor();
        var grants = access.grants(actor);
        boolean attendanceView = view(grants, "MY_ATTENDANCE");
        boolean approvalView = view(grants, "APPROVAL_INBOX");
        boolean projectView = view(grants, "PROJECT");
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate to = from.plusDays(4);
        var args = map("actor", actor, "today", today, "from", from, "to", to);
        String scope = projectView ? projects.visibilityPredicate(args) : HomeQueries.NO_PROJECTS;
        return new HomeResponseDto(attendanceView ? attendance.getHomeAttendance() : null,
                approvalView ? approvals(args) : null, leave.getBalance(null),
                attendanceView || projectView ? teamWeek(args, attendanceView, projectView, scope) : null,
                projectView ? myTasks(args, scope, today) : null, notifications(args));
    }

    private static boolean view(Map<String, PermissionAccess.Grant> grants, String menu) {
        var grant = grants.get(menu);
        return grant != null && grant.actions().contains("V");
    }

    private Form form(Map<String, Object> row) {
        String code = (String) row.get("code");
        return new Form(code, FormService.shortName(code));
    }

    private Approvals approvals(Map<String, Object> args) {
        long count = queries.countTodo(args);
        var todo = queries.findTodo(args).stream().map(row -> Todo.from(row, form(row))).toList();
        var mine = queries.findMine(args).stream().map(row -> Mine.from(row, form(row))).toList();
        return new Approvals(count, todo, mine);
    }

    private TeamWeek teamWeek(Map<String, Object> args, boolean leave, boolean tasks, String scope) {
        var department = queries.findDepartment(args);
        args.put("department", department.get("id"));
        LocalDate from = (LocalDate) args.get("from");
        LocalDate to = (LocalDate) args.get("to");
        Map<LocalDate, List<WeekItem>> days = new LinkedHashMap<>();
        for (int i = 0; i < 5; i++) {
            days.put(from.plusDays(i), new ArrayList<>());
        }
        for (var row : queries.findTeamWeek(args, leave, tasks, scope)) {
            WeekItem item;
            if ("TASK_DUE".equals(row.get("type"))) {
                item = TaskDue.from(row);
            } else {
                item = LeaveItem.from(row);
            }
            LocalDate begin = (LocalDate) row.get("start_date");
            LocalDate end = (LocalDate) row.get("end_date");
            if (begin.isBefore(from)) {
                begin = from;
            }
            if (end.isAfter(to)) {
                end = to;
            }
            for (LocalDate day = begin; !day.isAfter(end); day = day.plusDays(1)) {
                days.get(day).add(item);
            }
        }
        return new TeamWeek(Department.from(department), from, to,
                days.entrySet().stream().map(entry -> new Day(entry.getKey(), entry.getValue())).toList());
    }

    private MyTasks myTasks(Map<String, Object> args, String scope, LocalDate today) {
        var summaries = queries.findMySummary(args, scope);
        Project project = null;
        if (!summaries.isEmpty()) {
            project = Project.from(summaries.get(0));
        }
        var rows = queries.findMyItems(args, scope);
        var items = rows.stream()
                .map(row -> new TaskItem(number(row, "id"), number(row, "project_id"),
                        row.get("project_key") + "-" + row.get("task_no"), (String) row.get("task_title"),
                        (String) row.get("task_status"), (LocalDate) row.get("end_date"),
                        Math.max(0, ChronoUnit.DAYS.between((LocalDate) row.get("end_date"), today))))
                .toList();
        return new MyTasks(project, rows.isEmpty() ? 0 : number(rows.get(0), "matching_count"), items);
    }

    private Notifications notifications(Map<String, Object> args) {
        var rows = queries.findNotifications(args);
        return new Notifications(number(rows.get(0), "unread"),
                rows.stream().filter(row -> row.get("id") != null).map(NotificationItemResponseDto::from).toList());
    }
}
