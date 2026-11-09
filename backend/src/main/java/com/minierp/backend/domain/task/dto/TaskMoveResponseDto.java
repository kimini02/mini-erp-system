package com.minierp.backend.domain.task.dto;

import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record TaskMoveResponseDto(TaskCardResponseDto task, Project project) {
    public record Project(long id, String status, long taskCount, long doneCount, long progressPct) {
        public static Project from(Map<String, Object> row) {
            return new Project(number(row, "id"), (String) row.get("status"), number(row, "taskCount"),
                    number(row, "doneCount"), number(row, "progressPct"));
        }
    }
}
