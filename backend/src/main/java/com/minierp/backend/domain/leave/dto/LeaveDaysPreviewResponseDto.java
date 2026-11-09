package com.minierp.backend.domain.leave.dto;

import java.math.BigDecimal;

public record LeaveDaysPreviewResponseDto(
        BigDecimal days,
        long offDays,
        BigDecimal available,
        BigDecimal after,
        String error) {
}
