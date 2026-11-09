package com.minierp.backend.domain.task.dto;

import com.minierp.backend.domain.project.dto.*;
import java.time.*;
import java.util.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record TaskDetailResponseDto(
        long id,
        String key,
        String title,
        String status,
        String priority,
        LocalDate endDate,
        long overdueDays,
        List<ProjectPersonResponseDto> assignees,
        long position,
        long version,
        boolean canMove,
        long projectId,
        String content,
        boolean canManage,
        List<History> history) {
    public static TaskDetailResponseDto from(TaskCardResponseDto card, long projectId, String content,
            boolean canManage, List<History> history) {
        return new TaskDetailResponseDto(card.id(), card.key(), card.title(), card.status(), card.priority(),
                card.endDate(), card.overdueDays(), card.assignees(), card.position(), card.version(), card.canMove(),
                projectId, content, canManage, history);
    }

    public interface History {
    }

    public record Person(long id, String name) {
    }

    public record Moved(String action, Person actor, LocalDateTime at) implements History {
    }

    public record Event(String action, Person actor, LocalDateTime at, String from, String to,
            @JsonInclude(JsonInclude.Include.NON_NULL) Person target) implements History {
    }

    public static History historyFrom(Map<String, Object> row) {
        var actor = row.get("actor_id") == null
                ? null
                : new Person(number(row, "actor_id"), (String) row.get("actor_name"));
        String action = (String) row.get("action");
        var at = (LocalDateTime) row.get("created_at");
        if ("MOVED".equals(action)) {
            return new Moved(action, actor, at);
        }
        return new Event(action, actor, at, (String) row.get("from_value"), (String) row.get("to_value"),
                row.get("target_id") == null
                        ? null
                        : new Person(number(row, "target_id"), (String) row.get("target_name")));
    }
}
