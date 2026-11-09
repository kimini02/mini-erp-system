package com.minierp.backend.domain.user.dto;

import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record EmployeeDetailResponseDto(
        long id,
        String loginId,
        String name,
        String email,
        Department department,
        long positionId,
        String position,
        String departmentCode,
        String departmentName,
        String role,
        List<Role> roles,
        LocalDate hireDate,
        LocalDate retiredDate,
        String status,
        boolean invitationPending,
        BigDecimal remainingAnnualLeave,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
    public record Department(long id, String name) {
        public static Department from(Map<String, Object> row) {
            return new Department(number(row, "department_id"), (String) row.get("department_name"));
        }
    }

    public record Role(String code, String name) {
        public static Role from(Map<String, Object> row) {
            return new Role((String) row.get("code"), (String) row.get("name"));
        }
    }
}
