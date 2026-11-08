package com.minierp.backend.domain.overtime.dto;

import com.minierp.backend.domain.approval.dto.ApprovalFormBodyDto;
import java.time.LocalDate;
import java.util.Map;

public record OvertimeBodyResponseDto(
        LocalDate overtimeDate,
        String startTime,
        String endTime,
        String reason) implements ApprovalFormBodyDto {
    public static OvertimeBodyResponseDto from(Map<String, Object> row) {
        return new OvertimeBodyResponseDto((LocalDate) row.get("overtimeDate"), (String) row.get("startTime"),
                (String) row.get("endTime"), (String) row.get("reason"));
    }
}
