package com.minierp.backend.domain.attendance.service;

import java.time.*;
import java.time.temporal.ChronoUnit;

public final class AttendanceRules {
    private AttendanceRules() {
    }

    public record Result(String inResult, String outResult, boolean absent) {
    }

    public static Result judge(
            LocalDate date,
            String type,
            LocalTime start,
            LocalTime end,
            int grace,
            LocalDateTime in,
            LocalDateTime out,
            boolean finalized) {
        if (type.equals("HOLIDAY") || type.equals("LEAVE")) {
            return new Result("NONE", "NONE", false);
        }
        LocalDateTime begin = date.atTime(type.equals("HALF_AM") ? start.plusHours(5) : start).plusMinutes(grace);
        LocalDateTime finish = date.atTime(type.equals("HALF_PM") ? start.plusHours(5) : end);
        String inResult = in == null ? "NONE" : in.truncatedTo(ChronoUnit.MINUTES)
                .isAfter(begin.truncatedTo(ChronoUnit.MINUTES)) ? "LATE" : "ON_TIME";
        String outResult = in == null ? "NONE" : out == null ? (finalized ? "MISSING" : null) : out.isBefore(finish)
                ? "EARLY" : "ON_TIME";
        return new Result(inResult, outResult, finalized && in == null);
    }

    public static String code(LocalDate date, LocalDate today, String type, Result result, LocalDateTime in) {
        if (result.absent()) {
            return "ABSENT";
        }
        if (type.equals("LEAVE")) {
            return "LEAVE";
        }
        if ("MISSING".equals(result.outResult())) {
            return "MISSING";
        }
        if ("LATE".equals(result.inResult())) {
            return "LATE";
        }
        if ("EARLY".equals(result.outResult())) {
            return "EARLY";
        }
        if (type.startsWith("HALF_")) {
            return type;
        }
        if ("ON_TIME".equals(result.inResult())) {
            return "ON_TIME";
        }
        if (type.equals("HOLIDAY")) {
            return in == null ? "HOLIDAY" : "HOLIDAY_WORK";
        }
        return date.isAfter(today) ? "FUTURE" : "NONE";
    }
}
