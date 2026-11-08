package com.minierp.backend.domain.org.controller;

import com.minierp.backend.domain.org.service.OrgService;
import com.minierp.backend.domain.org.dto.OrgRequests.*;
import com.minierp.backend.domain.org.dto.*;
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
public class OrgController {
    private final OrgService service;
    private final Clock clock;

    @GetMapping("/departments/tree")
    public ApiResponse<OrgDepartmentTreeResponseDto> getDepartmentTree() {
        return ApiResponse.success(clock, service.tree());
    }

    @GetMapping("/departments/{id}")
    public ApiResponse<OrgDepartmentResponseDto> getDepartment(@PathVariable long id) {
        return ApiResponse.success(clock, service.getDepartment(id));
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(menu = "DEPARTMENT", action = CREATE)
    public ApiResponse<OrgDepartmentResponseDto> createDepartment(@Valid @RequestBody DepartmentCreate request) {
        return ApiResponse.success(clock, service.createDepartment(request));
    }

    @PatchMapping("/departments/{id}")
    @RequirePermission(menu = "DEPARTMENT", action = UPDATE)
    public ApiResponse<OrgDepartmentResponseDto> updateDepartment(
            @PathVariable long id,
            @Valid @RequestBody DepartmentPatch request) {
        return ApiResponse.success(clock, service.updateDepartment(id, request));
    }

    @PatchMapping("/departments/{id}/parent")
    @RequirePermission(menu = "DEPARTMENT", action = UPDATE)
    public ApiResponse<OrgDepartmentResponseDto> updateDepartmentParent(
            @PathVariable long id,
            @RequestBody Parent request) {
        return ApiResponse.success(clock, service.updateDepartmentParent(id, request.parentId()));
    }

    @PutMapping("/departments/{id}/head")
    @RequirePermission(menu = "DEPARTMENT", action = UPDATE)
    public ApiResponse<OrgDepartmentResponseDto> updateDepartmentHead(
            @PathVariable long id,
            @Valid @RequestBody Head request) {
        return ApiResponse.success(clock, service.setHead(id, request.userId()));
    }

    @DeleteMapping("/departments/{id}/head")
    @RequirePermission(menu = "DEPARTMENT", action = UPDATE)
    public ApiResponse<OrgDepartmentResponseDto> deleteDepartmentHead(@PathVariable long id) {
        return ApiResponse.success(clock, service.setHead(id, null));
    }

    @PutMapping("/departments/{id}/work-schedule")
    @RequirePermission(menu = "DEPARTMENT", action = UPDATE)
    public ApiResponse<OrgDepartmentResponseDto> updateDepartmentWorkSchedule(
            @PathVariable long id,
            @RequestBody Schedule request) {
        return ApiResponse.success(clock, service.updateDepartmentSchedule(id, request));
    }

    @DeleteMapping("/departments/{id}")
    @RequirePermission(menu = "DEPARTMENT", action = DELETE)
    public ApiResponse<Void> deleteDepartment(@PathVariable long id) {
        service.deleteDepartment(id);
        return ApiResponse.success(clock, null);
    }

    @GetMapping("/work-schedules/default")
    public ApiResponse<OrgWorkScheduleResponseDto> getDefaultWorkSchedule() {
        return ApiResponse.success(clock, service.getDefaultWorkSchedule());
    }

    @PutMapping("/work-schedules/default")
    @RequirePermission(menu = "DEPARTMENT", action = UPDATE)
    public ApiResponse<OrgWorkScheduleResponseDto> updateDefaultWorkSchedule(@RequestBody Schedule request) {
        return ApiResponse.success(clock, service.updateDefaultWorkSchedule(request));
    }

    @GetMapping("/positions")
    public ApiResponse<List<OrgPositionResponseDto>> getPositions() {
        return ApiResponse.success(clock, service.getPositions());
    }

    @PostMapping("/positions")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(menu = "DEPARTMENT", action = CREATE)
    public ApiResponse<OrgPositionCreateResponseDto> createPosition(@Valid @RequestBody PositionName request) {
        return ApiResponse.success(clock, service.createPosition(request.name()));
    }

    @PatchMapping("/positions/{id}")
    @RequirePermission(menu = "DEPARTMENT", action = UPDATE)
    public ApiResponse<OrgPositionUpdateResponseDto> updatePosition(
            @PathVariable long id,
            @Valid @RequestBody PositionName request) {
        return ApiResponse.success(clock, service.updatePosition(id, request.name()));
    }

    @PutMapping("/positions/order")
    @RequirePermission(menu = "DEPARTMENT", action = UPDATE)
    public ApiResponse<List<OrgPositionResponseDto>> updatePositionOrder(
            @Valid @RequestBody List<@Valid PositionOrder> request) {
        return ApiResponse.success(clock, service.updatePositionOrder(request));
    }

    @DeleteMapping("/positions/{id}")
    @RequirePermission(menu = "DEPARTMENT", action = DELETE)
    public ApiResponse<Void> deletePosition(@PathVariable long id) {
        service.deletePosition(id);
        return ApiResponse.success(clock, null);
    }

    @GetMapping("/holidays")
    public ApiResponse<List<OrgHolidayResponseDto>> getHolidays(@RequestParam int year) {
        return ApiResponse.success(clock, service.getHolidays(year));
    }

    @PostMapping("/holidays")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(menu = "HOLIDAY", action = CREATE)
    public ApiResponse<OrgHolidayResponseDto> createHoliday(@Valid @RequestBody HolidayCreate request) {
        return ApiResponse.success(clock, service.createHoliday(request));
    }

    @PatchMapping("/holidays/{id}")
    @RequirePermission(menu = "HOLIDAY", action = UPDATE)
    public ApiResponse<OrgHolidayResponseDto> updateHoliday(
            @PathVariable long id,
            @Valid @RequestBody HolidayPatch request) {
        return ApiResponse.success(clock, service.updateHoliday(id, request));
    }

    @DeleteMapping("/holidays/{id}")
    @RequirePermission(menu = "HOLIDAY", action = DELETE)
    public ApiResponse<Void> deleteHoliday(@PathVariable long id) {
        service.deleteHoliday(id);
        return ApiResponse.success(clock, null);
    }
}
