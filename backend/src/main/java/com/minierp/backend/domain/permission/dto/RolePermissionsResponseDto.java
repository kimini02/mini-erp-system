package com.minierp.backend.domain.permission.dto;

import java.util.List;
import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.bool;

public record RolePermissionsResponseDto(LockedRole role, List<Permission> permissions) {
    public record LockedRole(long id, String code, String name, String description, Boolean system, boolean locked) {
    }

    public record Permission(String menu, boolean view, boolean create, boolean update, boolean delete, String scope) {
        public static Permission from(Map<String, Object> row) {
            return new Permission((String) row.get("menu"), bool(row, "can_view"), bool(row, "can_create"),
                    bool(row, "can_update"), bool(row, "can_delete"), (String) row.get("data_scope"));
        }
    }
}
