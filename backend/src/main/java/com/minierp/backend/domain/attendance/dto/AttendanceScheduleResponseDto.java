package com.minierp.backend.domain.attendance.dto;

import com.minierp.backend.domain.attendance.repository.AttendanceStore.Schedule;
import java.time.format.DateTimeFormatter;

public record AttendanceScheduleResponseDto(
        String startTime,
        String endTime,
        int lateGraceMin,
        String halfBoundary,
        String source,
        String departmentName) {
    public static AttendanceScheduleResponseDto from(Schedule schedule) {
        var format = DateTimeFormatter.ofPattern("HH:mm");
        return new AttendanceScheduleResponseDto(schedule.start().format(format), schedule.end().format(format),
                schedule.grace(), schedule.start().plusHours(5).format(format),
                schedule.source(), schedule.department());
    }
}
