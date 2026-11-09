package com.minierp.backend.domain.permission.controller;

import com.minierp.backend.domain.permission.service.RoleService;
import com.minierp.backend.domain.permission.dto.RoleRequests.*;
import com.minierp.backend.domain.permission.dto.*;
import com.minierp.backend.domain.user.dto.EmployeeDetailResponseDto;
import com.minierp.backend.global.response.PageResponseDto;
import com.minierp.backend.global.permission.*;
import com.minierp.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.time.Clock;
import java.util.List;
import static com.minierp.backend.global.permission.Action.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RoleController {
    private final RoleService service;
    private final Clock clock;

    @GetMapping("/menus")
    @RequirePermission(menu = "ROLE", action = VIEW)
    public ApiResponse<List<RoleMenuResponseDto>> getMenus() {
        return ApiResponse.success(clock, service.getMenus());
    }

    @GetMapping("/roles")
    @RequirePermission(menu = "ROLE", action = VIEW)
    public ApiResponse<List<RoleSummaryResponseDto>> getRoles() {
        return ApiResponse.success(clock, service.getRoles());
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(menu = "ROLE", action = CREATE)
    public ApiResponse<RoleDetailResponseDto> createRole(@Valid @RequestBody Create request) {
        return ApiResponse.success(clock, service.createRole(request));
    }

    @PatchMapping("/roles/{id}")
    @RequirePermission(menu = "ROLE", action = UPDATE)
    public ApiResponse<RoleDetailResponseDto> updateRole(@PathVariable long id, @Valid @RequestBody Patch request) {
        return ApiResponse.success(clock, service.updateRole(id, request));
    }

    @DeleteMapping("/roles/{id}")
    @RequirePermission(menu = "ROLE", action = DELETE)
    public ApiResponse<Void> deleteRole(@PathVariable long id) {
        service.deleteRole(id);
        return ApiResponse.success(clock, null);
    }

    @GetMapping("/roles/{id}/permissions")
    @RequirePermission(menu = "ROLE", action = VIEW)
    public ApiResponse<RolePermissionsResponseDto> getRolePermissions(@PathVariable long id) {
        return ApiResponse.success(clock, service.getRolePermissions(id));
    }

    @PutMapping("/roles/{id}/permissions")
    @RequirePermission(menu = "ROLE", action = UPDATE)
    public ApiResponse<RolePermissionsResponseDto> updateRolePermissions(
            @PathVariable long id,
            @Valid @RequestBody List<@Valid Permission> request) {
        return ApiResponse.success(clock, service.updateRolePermissions(id, request));
    }

    @GetMapping("/roles/{id}/members")
    @RequirePermission(menu = "ROLE", action = VIEW)
    public ApiResponse<PageResponseDto<EmployeeDetailResponseDto>> getRoleMembers(
            @PathVariable long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(clock, service.getRoleMembers(id, page, size));
    }

    @PostMapping("/roles/{id}/members")
    @RequirePermission(menu = "ROLE", action = UPDATE)
    public ApiResponse<Void> createRoleMembers(@PathVariable long id, @Valid @RequestBody Members request) {
        service.createRoleMembers(id, request.userIds());
        return ApiResponse.success(clock, null);
    }

    @DeleteMapping("/roles/{id}/members/{userId}")
    @RequirePermission(menu = "ROLE", action = UPDATE)
    public ApiResponse<Void> deleteRoleMember(@PathVariable long id, @PathVariable long userId) {
        service.deleteRoleMember(id, userId);
        return ApiResponse.success(clock, null);
    }
}
