package com.minierp.backend.domain.leave.controller;

import com.minierp.backend.domain.leave.service.LeaveAdminService;
import com.minierp.backend.domain.leave.dto.LeaveAdminBalanceResponseDto;
import com.minierp.backend.domain.leave.dto.LeaveLedgerEntryResponseDto;
import com.minierp.backend.domain.leave.dto.LeaveAdjustmentResponseDto;
import com.minierp.backend.domain.leave.dto.LeaveGrantRunResponseDto;
import com.minierp.backend.global.response.PageResponseDto;
import java.util.List;
import com.minierp.backend.global.permission.*;
import com.minierp.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.*;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/leave/admin")
@RequiredArgsConstructor
public class LeaveAdminController {
    private final LeaveAdminService service;
    private final Clock clock;
    @GetMapping("/balances")
    @RequirePermission(menu = "LEAVE_ADMIN", action = Action.VIEW)
    public ApiResponse<PageResponseDto<LeaveAdminBalanceResponseDto>> getBalances(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(name = "q", required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(clock, service.getBalances(year, departmentId, search, page, size));
    }

    @GetMapping("/ledger/{userId}")
    @RequirePermission(menu = "LEAVE_ADMIN", action = Action.VIEW)
    public ApiResponse<List<LeaveLedgerEntryResponseDto>> getUserLedgerEntries(
            @PathVariable long userId,
            @RequestParam(required = false) Integer year) {
        return ApiResponse.success(clock, service.getUserLedgerEntries(userId, year));
    }

    public record Adjustment(
            @Positive long userId,
            @Min(1) @Max(9999) int year,
            @NotNull BigDecimal days,
            @NotBlank @Size(max = 500) String memo) {
    }

    @PostMapping("/adjustments")
    @RequirePermission(menu = "LEAVE_ADMIN", action = Action.CREATE)
    public ApiResponse<LeaveAdjustmentResponseDto> createAdjustment(@Valid @RequestBody Adjustment request) {
        return ApiResponse.success(clock,
                service.createAdjustment(request.userId(), request.year(), request.days(), request.memo()));
    }

    public record Run(@NotNull LocalDate date) {
    }

    @PostMapping("/grant-runs")
    @RequirePermission(menu = "LEAVE_ADMIN", action = Action.UPDATE)
    public ApiResponse<LeaveGrantRunResponseDto> createGrantRun(@Valid @RequestBody Run request) {
        return ApiResponse.success(clock, service.createGrantRun(request.date()));
    }

    @GetMapping("/grant-runs")
    @RequirePermission(menu = "LEAVE_ADMIN", action = Action.UPDATE)
    public ApiResponse<List<LeaveGrantRunResponseDto>> getGrantRuns(@RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(clock, service.getGrantRuns(limit));
    }
}
