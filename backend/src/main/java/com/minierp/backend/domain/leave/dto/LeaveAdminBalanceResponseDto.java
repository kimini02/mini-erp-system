package com.minierp.backend.domain.leave.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record LeaveAdminBalanceResponseDto(
        User user,
        LocalDate hireDate,
        boolean hireDateMissing,
        BigDecimal granted,
        BigDecimal used,
        BigDecimal reserved,
        BigDecimal adjusted,
        BigDecimal available) {
    public static LeaveAdminBalanceResponseDto from(Map<String, Object> row) {
        return new LeaveAdminBalanceResponseDto(User.from(row), (LocalDate) row.get("hire_date"),
                row.get("hire_date") == null, (BigDecimal) row.get("granted"), (BigDecimal) row.get("used"),
                (BigDecimal) row.get("reserved"), (BigDecimal) row.get("adjusted"), (BigDecimal) row.get("available"));
    }

    public record User(long id, String name, String title, String department) {
        public static User from(Map<String, Object> row) {
            return new User(number(row, "id"), (String) row.get("user_name"), (String) row.get("title"),
                    (String) row.get("department"));
        }
    }
}
