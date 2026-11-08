package com.minierp.backend.domain.leave.dto;

import com.minierp.backend.domain.approval.dto.ApprovalFormBodyDto;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record LeaveBodyResponseDto(
        String leaveType,
        LocalDate startDate,
        LocalDate endDate,
        BigDecimal usedDays,
        Handover handover,
        String reason) implements ApprovalFormBodyDto {
    public static LeaveBodyResponseDto from(Map<String, Object> row, Handover handover) {
        return new LeaveBodyResponseDto((String) row.get("leave_type"), (LocalDate) row.get("start_date"),
                (LocalDate) row.get("end_date"), (BigDecimal) row.get("used_days"),
                handover, (String) row.get("reason"));
    }

    public record Handover(long id, String name) {
        public static Handover from(Map<String, Object> row) {
            return new Handover(number(row, "id"), (String) row.get("name"));
        }
    }
}
