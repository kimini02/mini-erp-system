package com.minierp.backend.domain.project.dto;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record ProjectSummaryResponseDto(
        long id,
        String key,
        String title,
        String content,
        String status,
        String priority,
        LocalDate startDate,
        LocalDate endDate,
        ProjectPersonResponseDto leader,
        long memberCount,
        long taskCount,
        long doneCount,
        BigDecimal overdueCount,
        long progressPct,
        String myRole) {
    public static ProjectSummaryResponseDto from(Map<String, Object> row, String myRole) {
        long total = number(row, "task_count");
        long done = number(row, "done_count");
        return new ProjectSummaryResponseDto(number(row, "id"), (String) row.get("project_key"),
                (String) row.get("title"), (String) row.get("content"), (String) row.get("status"),
                (String) row.get("priority"), (LocalDate) row.get("start_date"), (LocalDate) row.get("end_date"),
                ProjectPersonResponseDto.leaderFrom(row), number(row, "member_count"), total, done,
                (BigDecimal) row.get("overdue_count"), total == 0 ? 0 : done * 100 / total, myRole);
    }
}
