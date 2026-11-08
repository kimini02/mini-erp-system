package com.minierp.backend.domain.leave.dto;

import java.math.BigDecimal;

public record LeaveAdjustmentResponseDto(long id, long userId, int year, BigDecimal available) {
}
