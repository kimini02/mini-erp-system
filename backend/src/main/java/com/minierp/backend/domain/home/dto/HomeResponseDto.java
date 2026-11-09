package com.minierp.backend.domain.home.dto;

import com.minierp.backend.domain.attendance.dto.AttendanceTodayResponseDto;
import com.minierp.backend.domain.leave.dto.LeaveBalanceResponseDto;
import com.minierp.backend.domain.notification.dto.NotificationItemResponseDto;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record HomeResponseDto(AttendanceTodayResponseDto today, Approvals approvals, LeaveBalanceResponseDto leave,
        TeamWeek teamWeek, MyTasks myTasks, Notifications notifications) {
    public record Person(long id, String name, String title) {
    }

    public record Form(String code, String shortName) {
    }

    public record Approvals(long todoCount, List<Todo> todo, List<Mine> mine) {
    }

    public record Todo(long id, String docNo, Form form, String title, Person drafter,
            LocalDateTime submittedAt) {
        public static Todo from(Map<String, Object> row, Form form) {
            return new Todo(number(row, "id"), (String) row.get("doc_no"), form, (String) row.get("title"),
                    new Person(number(row, "drafter_id"), (String) row.get("user_name"),
                            (String) row.get("user_title")), (LocalDateTime) row.get("submitted_at"));
        }
    }

    public record Mine(long id, String docNo, Form form, String title, String status,
            Person currentApprover, LocalDateTime submittedAt) {
        public static Mine from(Map<String, Object> row, Form form) {
            return new Mine(number(row, "id"), (String) row.get("doc_no"), form, (String) row.get("title"),
                    (String) row.get("status"), row.get("approver_id") == null
                            ? null
                            : new Person(number(row, "approver_id"),
                                    (String) row.get("approver_name"),
                                    (String) row.get("approver_title")), (LocalDateTime) row.get("submitted_at"));
        }
    }

    public record Department(long id, String name) {
        public static Department from(Map<String, Object> row) {
            return new Department(number(row, "id"), (String) row.get("name"));
        }
    }

    public record TeamWeek(Department department, LocalDate from, LocalDate to, List<Day> days) {
    }

    public record Day(LocalDate date, List<WeekItem> items) {
    }

    public interface WeekItem {
    }

    public record TaskDue(String type, Task task, ProjectKey project) implements WeekItem {
        public static TaskDue from(Map<String, Object> row) {
            return new TaskDue("TASK_DUE",
                    new Task(number(row, "task_id"), row.get("project_key") + "-" + row.get("task_no"),
                            (String) row.get("task_title")),
                    new ProjectKey(number(row, "project_id"), (String) row.get("project_key")));
        }
    }

    public record Task(long id, String key, String title) {
    }

    public record ProjectKey(long id, String key) {
    }

    public record LeaveItem(String type, User user, String status, long documentId) implements WeekItem {
        public static LeaveItem from(Map<String, Object> row) {
            return new LeaveItem(row.get("type").toString().startsWith("HALF_") ? (String) row.get("type") : "LEAVE",
                    new User(number(row, "user_id"), (String) row.get("user_name")), (String) row.get("status"),
                    number(row, "document_id"));
        }
    }

    public record User(long id, String name) {
    }

    public record MyTasks(Project project, long count, List<TaskItem> items) {
    }

    public record Project(long id, String key, String title, long total, long done, BigDecimal overdue,
            long progressPct) {
        public static Project from(Map<String, Object> row) {
            long total = number(row, "task_count");
            long done = number(row, "done_count");
            return new Project(number(row, "id"), (String) row.get("project_key"), (String) row.get("title"),
                    total, done, (BigDecimal) row.get("overdue_count"), total == 0 ? 0 : done * 100 / total);
        }
    }

    public record TaskItem(long id, long projectId, String key, String title, String status, LocalDate endDate,
            long overdueDays) {
    }

    public record Notifications(long unread, List<NotificationItemResponseDto> items) {
    }
}
