package com.minierp.backend.domain.permission.service;

import com.minierp.backend.domain.permission.dto.RoleRequests.*;
import com.minierp.backend.domain.user.service.EmployeeService;
import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleService {
    private final SqlStore db;
    private final EmployeeService employees;
    private Map<String, Object> role(long id) {
        return db.one("SELECT id,code,name,description,is_system AS system FROM roles WHERE id=:id", map("id", id),
                ErrorCode.ROLE_NOT_FOUND);
    }

    public List<Map<String, Object>> menus() {
        return db.rows(
                "SELECT id,code,name,menu_group AS menuGroup,sort_order AS sortOrder,allowed_actions AS allowedActions,scope_applicable AS scopeApplicable FROM menus ORDER BY sort_order,id",
                Map.of());
    }

    public List<Map<String, Object>> roles() {
        return db.rows(
                "SELECT r.id,r.code,r.name,r.description,r.is_system AS system,COUNT(ur.user_id) AS memberCount FROM roles r LEFT JOIN user_roles ur ON ur.role_id=r.id GROUP BY r.id,r.code,r.name,r.description,r.is_system ORDER BY r.id",
                Map.of());
    }

    @Transactional
    public Map<String, Object> create(Create r) {
        long id = db.insert("INSERT INTO roles(code,name,description,is_system) VALUES(:code,:name,:description,0)",
                map("code", UUID.randomUUID().toString(), "name", r.name(), "description", r.description()));
        db.update("UPDATE roles SET code=:code WHERE id=:id", map("id", id, "code", String.format("R-%04d", id)));
        return role(id);
    }

    @Transactional
    public Map<String, Object> patch(long id, Patch r) {
        var role = role(id);
        if (bool(role, "system")) {
            throw new BusinessException(ErrorCode.SYSTEM_ROLE_LOCKED);
        }
        if (r.name() != null && r.name().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        db.update(
                "UPDATE roles SET name=COALESCE(:name,name),description=COALESCE(:description,description) WHERE id=:id",
                map("id", id, "name", r.name(), "description", r.description()));
        return role(id);
    }

    @Transactional
    public void delete(long id) {
        var role = role(id);
        if (bool(role, "system")) {
            throw new BusinessException(ErrorCode.SYSTEM_ROLE_LOCKED);
        }
        db.rows("SELECT id FROM roles WHERE id=:id FOR UPDATE", map("id", id));
        if (db.count("SELECT COUNT(*) FROM user_roles WHERE role_id=:id", map("id", id)) > 0 || db.count(
                "SELECT COUNT(*) FROM approval_form_steps WHERE role_id=:id", map("id", id)) > 0) {
            throw new BusinessException(ErrorCode.ROLE_IN_USE);
        }
        db.update("DELETE FROM role_permissions WHERE role_id=:id", map("id", id));
        db.update("DELETE FROM roles WHERE id=:id", map("id", id));
    }

    public Map<String, Object> permissions(long id) {
        var role = role(id);
        role.put("locked", "CHIEF".equals(role.get("code")));
        var rows = db.rows(
                "SELECT m.code AS menu,COALESCE(p.can_view,0) AS can_view,COALESCE(p.can_create,0) AS can_create,COALESCE(p.can_update,0) AS can_update,COALESCE(p.can_delete,0) AS can_delete,p.data_scope FROM menus m LEFT JOIN role_permissions p ON p.menu_id=m.id AND p.role_id=:id ORDER BY m.sort_order,m.id",
                map("id", id));
        return map("role", role,
                "permissions", rows.stream()
                        .map(
                                r -> map("menu", r.get("menu"), "view", bool(r, "can_view"),
                                        "create", bool(r, "can_create"), "update", bool(r, "can_update"),
                                        "delete", bool(r, "can_delete"), "scope", r.get("data_scope")))
                        .toList());
    }

    @Transactional
    public Map<String, Object> permissions(long id, List<Permission> permissions) {
        var role = role(id);
        if ("CHIEF".equals(role.get("code"))) {
            throw new BusinessException(ErrorCode.SYSTEM_ROLE_LOCKED);
        }
        Map<String, Map<String, Object>> menus = new HashMap<>();
        for (var m : menus()) {
            menus.put((String) m.get("code"), m);
        }
        Set<String> seen = new HashSet<>();
        for (var p : permissions) {
            var m = menus.get(p.menu());
            if (m == null || !seen.add(p.menu())) {
                throw new BusinessException(ErrorCode.INVALID_PERMISSION);
            }
            String allowed = (String) m.get("allowedActions");
            boolean any = p.view() || p.create() || p.update() || p.deleteAction();
            if (p.view() && !allowed.contains("V") || p.create() && !allowed.contains("C") || p.update()
                    && !allowed.contains(
                    "U") || p.deleteAction() && !allowed.contains("D") || !bool(m, "scopeApplicable")
                            && p.scope() != null || bool(m, "scopeApplicable") && any && p.scope() == null) {
                throw new BusinessException(ErrorCode.INVALID_PERMISSION);
            }
        }
        db.update("DELETE FROM role_permissions WHERE role_id=:id", map("id", id));
        for (var p : permissions) {
            db.update(
                    "INSERT INTO role_permissions(role_id,menu_id,can_view,can_create,can_update,can_delete,data_scope) VALUES(:role,:menu,:v,:c,:u,:d,:scope)",
                    map("role", id, "menu", menus.get(p.menu()).get("id"), "v", p.view(), "c", p.create(),
                            "u", p.update(), "d", p.deleteAction(),
                            "scope", p.scope() == null ? null : p.scope().name()));
        }
        return permissions(id);
    }

    public Map<String, Object> members(long id, int page, int size) {
        role(id);
        if (page < 0 || size < 1 || size > 200) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        var ids = db.rows(
                "SELECT user_id FROM user_roles WHERE role_id=:id ORDER BY user_id LIMIT :size OFFSET :offset",
                map("id", id, "size", size, "offset", page * size))
                .stream()
                .map(r -> number(r, "user_id"))
                .toList();
        return EmployeeService.page(employees.views(ids), page, size,
                db.count("SELECT COUNT(*) FROM user_roles WHERE role_id=:id", map("id", id)));
    }

    @Transactional
    public void addMembers(long id, List<Long> users) {
        employees.lockChief();
        role(id);
        for (long user : users) {
            employees.raw(user);
            if (db.count("SELECT COUNT(*) FROM user_roles WHERE user_id=:user AND role_id=:role",
                    map("user", user, "role", id)) == 0) {
                db.update("INSERT INTO user_roles(user_id,role_id) VALUES(:user,:role)", map("user", user, "role", id));
            }
        }
    }

    @Transactional
    public void removeMember(long id, long userId) {
        employees.lockChief();
        var role = role(id);
        employees.raw(userId);
        if (db.count("SELECT COUNT(*) FROM user_roles WHERE user_id=:user AND role_id=:role",
                map("user", userId, "role", id)) == 0) {
            return;
        }
        if ("CHIEF".equals(role.get("code"))) {
            employees.protectLastChief(userId);
        }
        if (db.count("SELECT COUNT(*) FROM user_roles WHERE user_id=:id", map("id", userId)) <= 1) {
            throw new BusinessException(ErrorCode.ROLE_REQUIRED);
        }
        db.update("DELETE FROM user_roles WHERE user_id=:user AND role_id=:role", map("user", userId, "role", id));
    }
}
