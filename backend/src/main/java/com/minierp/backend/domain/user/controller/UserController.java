package com.minierp.backend.domain.user.controller;

import com.minierp.backend.domain.user.service.EmployeeService;
import com.minierp.backend.domain.user.dto.EmployeeRequests.*;
import com.minierp.backend.global.permission.*;
import com.minierp.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.Map;
import static com.minierp.backend.global.permission.Action.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final EmployeeService service;
    private final com.minierp.backend.domain.user.service.UserLookupService lookup;
    private final Clock clock;
    private ApiResponse<?> ok(Object value) {
        return ApiResponse.success(clock, value);
    }

    @GetMapping
    @RequirePermission(menu = "EMPLOYEE", action = VIEW)
    public ApiResponse<?> list(
            @RequestParam(required = false)
            String q,
            @RequestParam(required = false)
            Long departmentId,
            @RequestParam(defaultValue = "false")
            boolean includeSubDepartments,
            @RequestParam(required = false)
            String status,
            @RequestParam(defaultValue = "0")
            int page,
            @RequestParam(defaultValue = "20")
            int size,
            @RequestParam(defaultValue = "userName,asc")
            String sort) {
        return ok(service.list(q, departmentId, includeSubDepartments, status, page, size, sort));
    }

    @GetMapping("/lookup")
    public ApiResponse<?> lookup(
            @RequestParam(required = false)
            String q,
            @RequestParam(required = false)
            Long departmentId,
            @RequestParam(defaultValue = "20")
            int size) {
        return ok(lookup.lookup(q, departmentId, size));
    }

    @GetMapping("/login-id-preview")
    @RequirePermission(menu = "EMPLOYEE", action = CREATE)
    public ApiResponse<?> preview(
            @RequestParam
            LocalDate hireDate) {
        return ok(Map.of("loginId", service.preview(hireDate)));
    }

    @GetMapping("/{id}")
    public ApiResponse<?> detail(
            @PathVariable
            long id) {
        return ok(service.detail(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(menu = "EMPLOYEE", action = CREATE)
    public ApiResponse<?> create(
            @Valid
            @RequestBody
            Create r) {
        return ok(service.register(r));
    }

    @PatchMapping("/{id}")
    @RequirePermission(menu = "EMPLOYEE", action = UPDATE)
    public ApiResponse<?> patch(
            @PathVariable
            long id,
            @Valid
            @RequestBody
            Patch r) {
        return ok(service.patch(id, r));
    }

    @PatchMapping("/{id}/status")
    @RequirePermission(menu = "EMPLOYEE", action = UPDATE)
    public ApiResponse<?> status(
            @PathVariable
            long id,
            @Valid
            @RequestBody
            Status r) {
        return ok(service.status(id, r.status()));
    }

    @PutMapping("/{id}/roles")
    @RequirePermission(menu = "ROLE", action = UPDATE)
    public ApiResponse<?> roles(
            @PathVariable
            long id,
            @Valid
            @RequestBody
            Roles r) {
        service.replaceRoles(id, r.roleCodes());
        return ok(null);
    }

    @PostMapping("/{id}/invitation")
    @RequirePermission(menu = "EMPLOYEE", action = CREATE)
    public ApiResponse<?> invitation(
            @PathVariable
            long id) {
        return ok(service.invitation(id));
    }
}
