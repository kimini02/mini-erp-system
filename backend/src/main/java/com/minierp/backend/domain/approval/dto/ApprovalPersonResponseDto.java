package com.minierp.backend.domain.approval.dto;

import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record ApprovalPersonResponseDto(long id, String name, String title, String department) {
    public static ApprovalPersonResponseDto from(Map<String, Object> row) {
        return new ApprovalPersonResponseDto(number(row, "id"), (String) row.get("name"),
                (String) row.get("title"), (String) row.get("department"));
    }

    public static ApprovalPersonResponseDto fromSnapshot(Map<String, Object> row) {
        return new ApprovalPersonResponseDto(number(row, "approver_id"), (String) row.get("approver_name"),
                (String) row.get("approver_title"), (String) row.get("approver_dept"));
    }
}
