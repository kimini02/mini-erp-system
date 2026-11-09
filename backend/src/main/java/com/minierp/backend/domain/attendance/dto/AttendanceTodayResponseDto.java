package com.minierp.backend.domain.attendance.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record AttendanceTodayResponseDto(
        LocalDate workDate,
        String dayType,
        String holidayName,
        AttendanceScheduleResponseDto schedule,
        LocalDateTime clockInAt,
        LocalDateTime clockOutAt,
        String inResult,
        String outResult,
        Leave leave,
        boolean canClockIn,
        boolean canClockOut,
        boolean earlyIfNow,
        long monthLateCount,
        long monthAbsentCount) {
    public record Leave(String type, long documentId, String docNo) {
        public static Leave from(Map<String, Object> row) {
            return row == null
                    ? null
                    : new Leave((String) row.get("type"), number(row, "documentId"), (String) row.get("docNo"));
        }
    }
}
