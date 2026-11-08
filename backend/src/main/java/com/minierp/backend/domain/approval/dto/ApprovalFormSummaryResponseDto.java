package com.minierp.backend.domain.approval.dto;

public record ApprovalFormSummaryResponseDto(
        String code,
        String name,
        String shortName,
        String prefix,
        String description,
        boolean allowLineEdit,
        long approverStepCount,
        long referenceStepCount) {
}
