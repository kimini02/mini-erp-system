package com.minierp.backend.domain.attendance.controller;

import com.minierp.backend.domain.attendance.service.AttendanceService;
import com.minierp.backend.global.permission.*;
import com.minierp.backend.global.response.ApiResponse;
import com.minierp.backend.domain.attendance.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {
    private final AttendanceService service;
    private final Clock clock;
    @GetMapping("/today")
    @RequirePermission(menu = "MY_ATTENDANCE", action = Action.VIEW)
    public ApiResponse<AttendanceTodayResponseDto> getTodayAttendance() {
        return ApiResponse.success(clock, service.getTodayAttendance());
    }

    @PostMapping("/clock-in")
    @RequirePermission(menu = "MY_ATTENDANCE", action = Action.CREATE)
    public ApiResponse<AttendanceTodayResponseDto> clockIn() {
        return ApiResponse.success(clock, service.clockIn());
    }

    @PostMapping("/clock-out")
    @RequirePermission(menu = "MY_ATTENDANCE", action = Action.CREATE)
    public ApiResponse<AttendanceTodayResponseDto> clockOut() {
        return ApiResponse.success(clock, service.clockOut());
    }

    @GetMapping("/me")
    @RequirePermission(menu = "MY_ATTENDANCE", action = Action.VIEW)
    public ApiResponse<AttendanceMyMonthResponseDto> getMyMonthlyAttendance(@RequestParam String month) {
        return ApiResponse.success(clock, service.getMyMonthlyAttendance(month));
    }
}
