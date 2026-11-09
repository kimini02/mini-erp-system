package com.minierp.backend.domain.approval.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record ApprovalInboxItemResponseDto(
        long id,
        String docNo,
        String formCode,
        String formName,
        String title,
        Drafter drafter,
        LocalDateTime submittedAt,
        LocalDateTime updatedAt,
        String status,
        List<String> steps,
        CurrentApprover currentApprover) {
    public record Drafter(String name, String title, String department) {
        public static Drafter from(Map<String, Object> row) {
            return new Drafter((String) row.get("user_name"), (String) row.get("position_name"),
                    (String) row.get("department_name"));
        }
    }

    public record CurrentApprover(String name, String title) {
        public static CurrentApprover from(Map<String, Object> row) {
            return new CurrentApprover((String) row.get("approver_name"), (String) row.get("approver_title"));
        }
    }
}
