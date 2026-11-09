package com.minierp.backend.domain.leave.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class LeavePolicyResponseDto {
    private String position;
    private int annualLeaveDays;
}
