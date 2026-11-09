package com.minierp.backend.domain.permission.dto;

import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record RoleMenuResponseDto(
        long id,
        String code,
        String name,
        String menuGroup,
        long sortOrder,
        String allowedActions,
        Boolean scopeApplicable) {
    public static RoleMenuResponseDto from(Map<String, Object> row) {
        return new RoleMenuResponseDto(number(row, "id"), (String) row.get("code"), (String) row.get("name"),
                (String) row.get("menuGroup"), number(row, "sortOrder"), (String) row.get("allowedActions"),
                (Boolean) row.get("scopeApplicable"));
    }
}
