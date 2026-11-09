package com.minierp.backend.domain.user.controller;

import com.minierp.backend.domain.user.service.EmployeeService;
import com.minierp.backend.domain.user.dto.EmployeeRequests.*;
import com.minierp.backend.domain.user.dto.EmployeeDetailResponseDto;
import com.minierp.backend.domain.user.dto.EmployeeListResponseDto;
import com.minierp.backend.domain.user.dto.EmployeeLookupResponseDto;
import com.minierp.backend.domain.user.dto.EmployeeLoginIdPreviewResponseDto;
import com.minierp.backend.domain.user.dto.RegistrationResult;
import com.minierp.backend.global.permission.*;
import com.minierp.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.time.*;
import java.util.List;
import static com.minierp.backend.global.permission.Action.*;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final EmployeeService service;
    private final com.minierp.backend.domain.user.service.UserLookupService lookup;
    private final Clock clock;

    @GetMapping
    @RequirePermission(menu = "EMPLOYEE", action = VIEW)
    public ApiResponse<EmployeeListResponseDto> getEmployees(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "false") boolean includeSubDepartments,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "userName,asc") String sort) {
        return ApiResponse.success(clock,
                service.getEmployees(query, departmentId, includeSubDepartments, status, page, size, sort));
    }

    @GetMapping("/lookup")
    public ApiResponse<List<EmployeeLookupResponseDto>> getLookupUsers(
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(clock, lookup.getLookupUsers(query, departmentId, size));
    }

    @GetMapping("/login-id-preview")
    @RequirePermission(menu = "EMPLOYEE", action = CREATE)
    public ApiResponse<EmployeeLoginIdPreviewResponseDto> getLoginIdPreview(@RequestParam LocalDate hireDate) {
        return ApiResponse.success(clock, new EmployeeLoginIdPreviewResponseDto(service.getLoginIdPreview(hireDate)));
    }

    @GetMapping("/{id}")
    public ApiResponse<EmployeeDetailResponseDto> getEmployee(@PathVariable long id) {
        return ApiResponse.success(clock, service.getEmployee(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(menu = "EMPLOYEE", action = CREATE)
    public ApiResponse<RegistrationResult> createEmployee(@Valid @RequestBody Create request) {
        return ApiResponse.success(clock, service.register(request));
    }

    @PatchMapping("/{id}")
    @RequirePermission(menu = "EMPLOYEE", action = UPDATE)
    public ApiResponse<EmployeeDetailResponseDto> updateEmployee(
            @PathVariable long id,
            @Valid @RequestBody Patch request) {
        return ApiResponse.success(clock, service.patch(id, request));
    }

    @PatchMapping("/{id}/status")
    @RequirePermission(menu = "EMPLOYEE", action = UPDATE)
    public ApiResponse<EmployeeDetailResponseDto> updateEmployeeStatus(
            @PathVariable long id,
            @Valid @RequestBody Status request) {
        return ApiResponse.success(clock, service.status(id, request.status()));
    }

    @PutMapping("/{id}/roles")
    @RequirePermission(menu = "ROLE", action = UPDATE)
    public ApiResponse<Void> updateEmployeeRoles(@PathVariable long id, @Valid @RequestBody Roles request) {
        service.updateEmployeeRoles(id, request.roleCodes());
        return ApiResponse.success(clock, null);
    }

    @PostMapping("/{id}/invitation")
    @RequirePermission(menu = "EMPLOYEE", action = CREATE)
    public ApiResponse<RegistrationResult> createInvitation(@PathVariable long id) {
        return ApiResponse.success(clock, service.invitation(id));
    }
}
