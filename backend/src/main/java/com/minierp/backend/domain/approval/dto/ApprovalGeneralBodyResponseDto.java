package com.minierp.backend.domain.approval.dto;

import java.util.Map;

public record ApprovalGeneralBodyResponseDto(String content, Long amount) implements ApprovalFormBodyDto {
    public static ApprovalGeneralBodyResponseDto from(Map<String, Object> row) {
        return new ApprovalGeneralBodyResponseDto((String) row.get("content"),
                row.get("amount") == null ? null : ((Number) row.get("amount")).longValue());
    }
}
