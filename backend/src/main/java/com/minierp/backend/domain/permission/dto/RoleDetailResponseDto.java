package com.minierp.backend.domain.permission.dto;

import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record RoleDetailResponseDto(long id, String code, String name, String description, Boolean system) {
    public static RoleDetailResponseDto from(Map<String, Object> row) {
        return new RoleDetailResponseDto(number(row, "id"), (String) row.get("code"), (String) row.get("name"),
                (String) row.get("description"), (Boolean) row.get("system"));
    }
}
