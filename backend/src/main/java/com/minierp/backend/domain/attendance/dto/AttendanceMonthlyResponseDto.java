package com.minierp.backend.domain.attendance.dto;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record AttendanceMonthlyResponseDto(String month, Collection<Day> days, List<Row> rows, List<String> legend) {
    public record Day(LocalDate date, String weekday, String dayType, String holidayName) {
    }

    public record User(long id, String name, String title, String department) {
        public static User from(Map<String, Object> row) {
            return new User(number(row, "user_id"), (String) row.get("user_name"), (String) row.get("title"),
                    (String) row.get("department"));
        }
    }

    public record Cell(LocalDate date, String code) {
    }

    public record Totals(int late, int early, int absent, int missing, double leaveDays) {
    }

    public record Row(User user, List<Cell> cells, Totals totals) {
    }
}
