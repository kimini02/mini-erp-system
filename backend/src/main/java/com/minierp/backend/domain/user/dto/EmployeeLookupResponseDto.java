package com.minierp.backend.domain.user.dto;

import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record EmployeeLookupResponseDto(long id, String name, String title, String department, long departmentId) {
    public static EmployeeLookupResponseDto from(Map<String, Object> row) {
        return new EmployeeLookupResponseDto(number(row, "id"), (String) row.get("name"), (String) row.get("title"),
                (String) row.get("department"), number(row, "departmentId"));
    }
}
