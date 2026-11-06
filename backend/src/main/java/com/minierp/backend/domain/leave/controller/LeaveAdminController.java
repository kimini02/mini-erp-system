package com.minierp.backend.domain.leave.controller;

import com.minierp.backend.domain.leave.service.LeaveAdminService;
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
    public ApiResponse<?> balances(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Long departmentId,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(clock, service.balances(year, departmentId, q, page, size));
    }

    @GetMapping("/ledger/{userId}")
    @RequirePermission(menu = "LEAVE_ADMIN", action = Action.VIEW)
    public ApiResponse<?> ledger(@PathVariable long userId, @RequestParam(required = false) Integer year) {
        return ApiResponse.success(clock, service.ledger(userId, year));
    }

    public record Adjustment(
            @Positive long userId,
            @Min(1) @Max(9999) int year,
            @NotNull BigDecimal days,
            @NotBlank @Size(max = 500) String memo) {
    }

    @PostMapping("/adjustments")
    @RequirePermission(menu = "LEAVE_ADMIN", action = Action.CREATE)
    public ApiResponse<?> adjust(@Valid @RequestBody Adjustment b) {
        return ApiResponse.success(clock, service.adjust(b.userId(), b.year(), b.days(), b.memo()));
    }

    public record Run(@NotNull LocalDate date) {
    }

    @PostMapping("/grant-runs")
    @RequirePermission(menu = "LEAVE_ADMIN", action = Action.UPDATE)
    public ApiResponse<?> grant(@Valid @RequestBody Run b) {
        return ApiResponse.success(clock, service.grant(b.date()));
    }

    @GetMapping("/grant-runs")
    @RequirePermission(menu = "LEAVE_ADMIN", action = Action.UPDATE)
    public ApiResponse<?> runs(@RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.success(clock, service.runs(limit));
    }
}
