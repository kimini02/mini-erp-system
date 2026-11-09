package com.minierp.backend.domain.attendance.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AttendanceMyMonthResponseDto(String month, Summary summary, List<Day> days) {
    public record Summary(
            int workDays,
            int present,
            int late,
            int early,
            int absent,
            int missing,
            double leaveDays) {
    }

    public interface Day {
    }

    public record EmptyDay(LocalDate date, String dayType) implements Day {
    }

    public record RecordedDay(
            LocalDate date,
            String dayType,
            LocalDateTime clockInAt,
            LocalDateTime clockOutAt,
            String inResult,
            String outResult,
            boolean absent,
            AttendanceDocumentResponseDto fixDocument) implements Day {
    }
}
