package com.minierp.backend.domain.permission.service;

import com.minierp.backend.domain.permission.dto.RoleRequests.*;
import com.minierp.backend.domain.user.service.EmployeeService;
import com.minierp.backend.domain.permission.repository.RoleQueries;
import com.minierp.backend.global.exception.*;
import com.minierp.backend.domain.permission.repository.RoleStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RoleService {
    private final RoleQueries queries;
    private final RoleStore store;
    private final EmployeeService employees;
    private Map<String, Object> role(long id) {
        return queries.findRole(id);
    }

    public List<Map<String, Object>> menus() {
        return queries.findMenus();
    }

    public List<Map<String, Object>> roles() {
        return queries.findRoles();
    }

    @Transactional
    public Map<String, Object> create(Create r) {
        long id = store.insertRole(UUID.randomUUID().toString(), r.name(), r.description());
        store.updateRoleCode(id, String.format("R-%04d", id));
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
        store.updateRole(id, r.name(), r.description());
        return role(id);
    }

    @Transactional
    public void delete(long id) {
        var role = role(id);
        if (bool(role, "system")) {
            throw new BusinessException(ErrorCode.SYSTEM_ROLE_LOCKED);
        }
        store.lockRole(id);
        if (queries.countRoleMembers(id) > 0 || queries.countRoleApprovalSteps(id) > 0) {
            throw new BusinessException(ErrorCode.ROLE_IN_USE);
        }
        store.deleteRolePermissions(id);
        store.deleteRole(id);
    }

    public Map<String, Object> permissions(long id) {
        var role = role(id);
        role.put("locked", "CHIEF".equals(role.get("code")));
        var rows = queries.findPermissions(id);
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
            if (p.view() && !allowed.contains("V") || p.create() && !allowed.contains("C")
                    || p.update() && !allowed.contains("U") || p.deleteAction() && !allowed.contains("D")
                    || !bool(m, "scopeApplicable") && p.scope() != null
                    || bool(m, "scopeApplicable") && any && p.scope() == null) {
                throw new BusinessException(ErrorCode.INVALID_PERMISSION);
            }
        }
        store.deleteRolePermissions(id);
        for (var p : permissions) {
            store.insertPermission(id, menus.get(p.menu()).get("id"), p.view(), p.create(), p.update(),
                    p.deleteAction(), p.scope() == null ? null : p.scope().name());
        }
        return permissions(id);
    }

    public Map<String, Object> members(long id, int page, int size) {
        role(id);
        if (page < 0 || size < 1 || size > 200) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        var ids = queries.findMemberIds(id, size, page * size).stream().map(r -> number(r, "user_id")).toList();
        return EmployeeService.page(employees.getEmployeeViews(ids), page, size, queries.countRoleMembers(id));
    }

    @Transactional
    public void addMembers(long id, List<Long> users) {
        employees.lockChief();
        role(id);
        for (long user : users) {
            employees.raw(user);
            if (queries.countMembership(user, id) == 0) {
                store.insertMembership(user, id);
            }
        }
    }

    @Transactional
    public void removeMember(long id, long userId) {
        employees.lockChief();
        var role = role(id);
        employees.raw(userId);
        if (queries.countMembership(userId, id) == 0) {
            return;
        }
        if ("CHIEF".equals(role.get("code"))) {
            employees.protectLastChief(userId);
        }
        if (queries.countUserRoles(userId) <= 1) {
            throw new BusinessException(ErrorCode.ROLE_REQUIRED);
        }
        store.deleteMembership(userId, id);
    }
}
