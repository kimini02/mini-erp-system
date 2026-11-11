package com.minierp.backend.domain.permission.service;

import com.minierp.backend.domain.permission.dto.RoleRequests.*;
import com.minierp.backend.domain.permission.dto.*;
import com.minierp.backend.domain.user.dto.EmployeeDetailResponseDto;
import com.minierp.backend.global.response.PageResponseDto;
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

    public List<RoleMenuResponseDto> getMenus() {
        return queries.findMenus().stream().map(RoleMenuResponseDto::from).toList();
    }

    public List<RoleSummaryResponseDto> getRoles() {
        return queries.findRoles().stream().map(RoleSummaryResponseDto::from).toList();
    }

    @Transactional
    public RoleDetailResponseDto createRole(Create request) {
        long id = store.insertRole(UUID.randomUUID().toString(), request.name(), request.description());
        store.updateRoleCode(id, String.format("R-%04d", id));
        return RoleDetailResponseDto.from(role(id));
    }

    @Transactional
    public RoleDetailResponseDto updateRole(long id, Patch request) {
        var role = role(id);
        if (bool(role, "system")) {
            throw new BusinessException(ErrorCode.SYSTEM_ROLE_LOCKED);
        }
        if (request.name() != null && request.name().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        store.updateRole(id, request.name(), request.description());
        return RoleDetailResponseDto.from(role(id));
    }

    @Transactional
    public void deleteRole(long id) {
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

    public RolePermissionsResponseDto getRolePermissions(long id) {
        var role = role(id);
        boolean locked = "CHIEF".equals(role.get("code"));
        var rows = queries.findPermissions(id);
        return new RolePermissionsResponseDto(
                RolePermissionsResponseDto.LockedRole.from(role, locked),
                rows.stream().map(RolePermissionsResponseDto.Permission::from).toList());
    }

    @Transactional
    public RolePermissionsResponseDto updateRolePermissions(long id, List<Permission> permissions) {
        var role = role(id);
        if ("CHIEF".equals(role.get("code"))) {
            throw new BusinessException(ErrorCode.SYSTEM_ROLE_LOCKED);
        }
        var menus = permissionMenus();
        validatePermissions(menus, permissions);
        store.deleteRolePermissions(id);
        for (var permission : permissions) {
            store.insertPermission(id, menus.get(permission.menu()).get("id"),
                    permission.view(), permission.create(), permission.update(), permission.deleteAction(),
                    permission.scope() == null ? null : permission.scope().name());
        }
        return getRolePermissions(id);
    }

    private Map<String, Map<String, Object>> permissionMenus() {
        Map<String, Map<String, Object>> menus = new HashMap<>();
        for (var menu : queries.findMenus()) {
            menus.put((String) menu.get("code"), menu);
        }
        return menus;
    }

    private void validatePermissions(Map<String, Map<String, Object>> menus, List<Permission> permissions) {
        Set<String> seen = new HashSet<>();
        for (var permission : permissions) {
            var menu = menus.get(permission.menu());
            if (menu == null || !seen.add(permission.menu())) {
                throw new BusinessException(ErrorCode.INVALID_PERMISSION);
            }
            String allowed = (String) menu.get("allowedActions");
            boolean any = permission.view() || permission.create() || permission.update() || permission.deleteAction();
            if (permission.view() && !allowed.contains("V") || permission.create() && !allowed.contains("C")
                    || permission.update() && !allowed.contains("U")
                    || permission.deleteAction() && !allowed.contains("D")
                    || !bool(menu, "scopeApplicable") && permission.scope() != null
                    || bool(menu, "scopeApplicable") && any && permission.scope() == null) {
                throw new BusinessException(ErrorCode.INVALID_PERMISSION);
            }
        }
    }

    public PageResponseDto<EmployeeDetailResponseDto> getRoleMembers(long id, int page, int size) {
        role(id);
        if (page < 0 || size < 1 || size > 200) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        var ids = queries.findMemberIds(id, size, (long) page * size).stream()
                .map(row -> number(row, "user_id")).toList();
        return PageResponseDto.of(employees.getEmployeeViews(ids), page, size, queries.countRoleMembers(id));
    }

    @Transactional
    public void createRoleMembers(long id, List<Long> users) {
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
    public void deleteRoleMember(long id, long userId) {
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
