package com.minierp.backend.domain.attendance.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.minierp.backend.domain.approval.dto.ApprovalFormBodyDto;
import java.time.LocalDate;

public record AttendanceFixBodyResponseDto(
        LocalDate workDate,
        String clockIn,
        String clockOut,
        String reason,
        JsonNode before) implements ApprovalFormBodyDto {
}
