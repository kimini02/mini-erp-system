package com.minierp.backend.domain.attendance.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record AttendanceBatchRunResponseDto(
        long id,
        String jobName,
        LocalDate targetDate,
        String status,
        long processedCount,
        long skippedCount,
        String message,
        LocalDateTime startedAt,
        LocalDateTime finishedAt) {
    public static AttendanceBatchRunResponseDto from(Map<String, Object> row) {
        return new AttendanceBatchRunResponseDto(number(row, "id"), (String) row.get("jobName"),
                (LocalDate) row.get("targetDate"), (String) row.get("status"), number(row, "processedCount"),
                number(row, "skippedCount"), (String) row.get("message"), (LocalDateTime) row.get("startedAt"),
                (LocalDateTime) row.get("finishedAt"));
    }
}
