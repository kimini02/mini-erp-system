package com.minierp.backend.domain.approval.dto;

import java.util.List;

public record ApprovalLinePreviewResponseDto(
        List<Row> rows,
        List<ApprovalPersonResponseDto> finalLine,
        List<ApprovalPersonResponseDto> references,
        String error) {
    public record Row(
            int order,
            String kind,
            String rule,
            ApprovalPersonResponseDto result,
            String status,
            String note) {
    }
}
