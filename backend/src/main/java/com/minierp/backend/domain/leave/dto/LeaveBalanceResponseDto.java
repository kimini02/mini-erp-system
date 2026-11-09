package com.minierp.backend.domain.leave.dto;

import java.math.BigDecimal;
import java.util.Map;

public record LeaveBalanceResponseDto(
        BigDecimal granted,
        BigDecimal used,
        BigDecimal reserved,
        BigDecimal available,
        int year) {
    public static LeaveBalanceResponseDto from(Map<String, Object> row, int year) {
        return new LeaveBalanceResponseDto((BigDecimal) row.get("granted"), (BigDecimal) row.get("used"),
                (BigDecimal) row.get("reserved"), (BigDecimal) row.get("available"), year);
    }
}
