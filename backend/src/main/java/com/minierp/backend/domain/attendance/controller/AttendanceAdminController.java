package com.minierp.backend.domain.attendance.controller;

import com.minierp.backend.domain.attendance.service.AttendanceAdminService;
import com.minierp.backend.global.permission.*;
import com.minierp.backend.global.response.ApiResponse;
import com.minierp.backend.domain.attendance.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.*;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceAdminController {
    private final AttendanceAdminService service;
    private final Clock clock;
    @GetMapping("/monthly")
    @RequirePermission(menu = "ATTENDANCE_MONTHLY", action = Action.VIEW)
    public ApiResponse<AttendanceMonthlyResponseDto> getMonthlyAttendance(
            @RequestParam String month,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "false") boolean includeSubDepartments) {
        return ApiResponse.success(clock, service.getMonthlyAttendance(month, departmentId, includeSubDepartments));
    }

    @GetMapping("/records/{userId}/{date}")
    @RequirePermission(menu = "ATTENDANCE_MONTHLY", action = Action.VIEW)
    public ApiResponse<AttendanceRecordResponseDto> getAttendanceRecord(
            @PathVariable long userId,
            @PathVariable @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(clock, service.getAttendanceRecord(userId, date));
    }

    @GetMapping("/monthly/export")
    @RequirePermission(menu = "ATTENDANCE_MONTHLY", action = Action.VIEW)
    public ResponseEntity<byte[]> exportMonthlyAttendance(
            @RequestParam String month,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(defaultValue = "false") boolean includeSubDepartments) {
        var bytes = service.exportMonthlyAttendance(month, departmentId, includeSubDepartments);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=attendance-" + month + ".csv")
                .body(bytes);
    }

    public record Rejudge(@NotNull LocalDate date) {
    }

    @PostMapping("/rejudge")
    @RequirePermission(menu = "ATTENDANCE_MONTHLY", action = Action.UPDATE)
    public ApiResponse<AttendanceBatchRunResponseDto> rejudgeAttendance(@Valid @RequestBody Rejudge body) {
        return ApiResponse.success(clock, service.rejudgeAttendance(body.date()));
    }
}
