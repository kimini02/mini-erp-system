package com.minierp.backend.domain.attendance.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AttendanceRecordResponseDto(
        User user,
        LocalDate date,
        String code,
        LocalDateTime clockInAt,
        LocalDateTime clockOutAt,
        AttendanceScheduleDetailResponseDto schedule,
        String reason,
        AttendanceDocumentResponseDto leaveDocument,
        AttendanceDocumentResponseDto fixDocument,
        LocalDateTime judgedAt) {
    public record User(long id, String name) {
    }
}
