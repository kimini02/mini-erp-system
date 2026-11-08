package com.minierp.backend.domain.permission.dto;

import java.util.List;
import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.bool;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record RolePermissionsResponseDto(LockedRole role, List<Permission> permissions) {
    public record LockedRole(long id, String code, String name, String description, Boolean system, boolean locked) {
        public static LockedRole from(Map<String, Object> row, boolean locked) {
            return new LockedRole(number(row, "id"), (String) row.get("code"), (String) row.get("name"),
                    (String) row.get("description"), (Boolean) row.get("system"), locked);
        }
    }

    public record Permission(String menu, boolean view, boolean create, boolean update, boolean delete, String scope) {
        public static Permission from(Map<String, Object> row) {
            return new Permission((String) row.get("menu"), bool(row, "can_view"), bool(row, "can_create"),
                    bool(row, "can_update"), bool(row, "can_delete"), (String) row.get("data_scope"));
        }
    }
}
