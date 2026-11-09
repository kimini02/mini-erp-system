package com.minierp.backend.domain.permission.repository;

import com.minierp.backend.global.persistence.SqlStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.map;

@Repository
@RequiredArgsConstructor
public class RoleStore {
    private final SqlStore db;

    public long insertRole(String code, String name, String description) {
        return db.insert("INSERT INTO roles(code,name,description,is_system) VALUES(:code,:name,:description,0)",
                map("code", code, "name", name, "description", description));
    }

    public int updateRoleCode(long id, String code) {
        return db.update("UPDATE roles SET code=:code WHERE id=:id", map("id", id, "code", code));
    }

    public int updateRole(long id, String name, String description) {
        return db.update(
                "UPDATE roles SET name=COALESCE(:name,name),description=COALESCE(:description,description) WHERE id=:id",
                map("id", id, "name", name, "description", description));
    }

    public List<Map<String, Object>> lockRole(long id) {
        return db.rows("SELECT id FROM roles WHERE id=:id FOR UPDATE", map("id", id));
    }

    public int deleteRolePermissions(long id) {
        return db.update("DELETE FROM role_permissions WHERE role_id=:id", map("id", id));
    }

    public int deleteRole(long id) {
        return db.update("DELETE FROM roles WHERE id=:id", map("id", id));
    }

    public int insertPermission(
            long role,
            Object menu,
            boolean canView,
            boolean canCreate,
            boolean canUpdate,
            boolean canDelete,
            String scope) {
        return db.update(
                "INSERT INTO role_permissions(role_id,menu_id,can_view,can_create,can_update,can_delete,data_scope) VALUES(:role,:menu,:v,:c,:u,:d,:scope)",
                map("role", role, "menu", menu, "v", canView, "c", canCreate,
                        "u", canUpdate, "d", canDelete, "scope", scope));
    }

    public int insertMembership(long user, long role) {
        return db.update("INSERT INTO user_roles(user_id,role_id) VALUES(:user,:role)",
                map("user", user, "role", role));
    }

    public int deleteMembership(long user, long role) {
        return db.update("DELETE FROM user_roles WHERE user_id=:user AND role_id=:role",
                map("user", user, "role", role));
    }
}
