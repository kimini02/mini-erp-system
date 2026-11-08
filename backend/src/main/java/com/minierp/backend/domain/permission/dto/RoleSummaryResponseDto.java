package com.minierp.backend.domain.permission.dto;

import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record RoleSummaryResponseDto(
        long id,
        String code,
        String name,
        String description,
        Boolean system,
        long memberCount) {
    public static RoleSummaryResponseDto from(Map<String, Object> row) {
        return new RoleSummaryResponseDto(number(row, "id"), (String) row.get("code"), (String) row.get("name"),
                (String) row.get("description"), (Boolean) row.get("system"), number(row, "memberCount"));
    }
}
