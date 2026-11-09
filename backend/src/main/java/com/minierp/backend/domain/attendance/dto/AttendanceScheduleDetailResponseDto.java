package com.minierp.backend.domain.attendance.dto;

import com.minierp.backend.domain.attendance.repository.AttendanceStore.Schedule;

public record AttendanceScheduleDetailResponseDto(
        String startTime,
        String endTime,
        int lateGraceMin,
        String halfBoundary) {
    public static AttendanceScheduleDetailResponseDto from(Schedule schedule) {
        var full = AttendanceScheduleResponseDto.from(schedule);
        return new AttendanceScheduleDetailResponseDto(full.startTime(), full.endTime(), full.lateGraceMin(),
                full.halfBoundary());
    }
}
