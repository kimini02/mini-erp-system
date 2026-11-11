package com.minierp.backend.domain.permission.repository;

import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.map;

@Repository
@RequiredArgsConstructor
public class RoleQueries {
    private final SqlStore db;

    public Map<String, Object> findRole(long id) {
        return db.one("SELECT id,code,name,description,is_system AS system FROM roles WHERE id=:id", map("id", id),
                ErrorCode.ROLE_NOT_FOUND);
    }

    public List<Map<String, Object>> findMenus() {
        return db.rows(
                "SELECT id,code,name,menu_group AS menuGroup,sort_order AS sortOrder,allowed_actions AS allowedActions,scope_applicable AS scopeApplicable FROM menus ORDER BY sort_order,id",
                Map.of());
    }

    public List<Map<String, Object>> findRoles() {
        return db.rows(
                "SELECT r.id,r.code,r.name,r.description,r.is_system AS system,COUNT(ur.user_id) AS memberCount FROM roles r LEFT JOIN user_roles ur ON ur.role_id=r.id GROUP BY r.id,r.code,r.name,r.description,r.is_system ORDER BY r.id",
                Map.of());
    }

    public long countRoleMembers(long id) {
        return db.count("SELECT COUNT(*) FROM user_roles WHERE role_id=:id", map("id", id));
    }

    public long countRoleApprovalSteps(long id) {
        return db.count("SELECT COUNT(*) FROM approval_form_steps WHERE role_id=:id", map("id", id));
    }

    public List<Map<String, Object>> findPermissions(long id) {
        return db.rows(
                "SELECT m.code AS menu,COALESCE(p.can_view,0) AS can_view,COALESCE(p.can_create,0) AS can_create,COALESCE(p.can_update,0) AS can_update,COALESCE(p.can_delete,0) AS can_delete,p.data_scope FROM menus m LEFT JOIN role_permissions p ON p.menu_id=m.id AND p.role_id=:id ORDER BY m.sort_order,m.id",
                map("id", id));
    }

    public List<Map<String, Object>> findMemberIds(long id, int size, long offset) {
        return db.rows("SELECT user_id FROM user_roles WHERE role_id=:id ORDER BY user_id LIMIT :size OFFSET :offset",
                map("id", id, "size", size, "offset", offset));
    }

    public long countMembership(long user, long role) {
        return db.count("SELECT COUNT(*) FROM user_roles WHERE user_id=:user AND role_id=:role",
                map("user", user, "role", role));
    }

    public long countUserRoles(long id) {
        return db.count("SELECT COUNT(*) FROM user_roles WHERE user_id=:id", map("id", id));
    }
}
