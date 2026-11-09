package com.minierp.backend.domain.approval.dto;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record ApprovalDocumentResponseDto(
        long id,
        String docNo,
        Form form,
        String title,
        String status,
        ApprovalPersonResponseDto drafter,
        LocalDateTime submittedAt,
        LocalDateTime completedAt,
        ApprovalFormBodyDto body,
        List<Line> line,
        List<ApprovalPersonResponseDto> references,
        List<History> history,
        MyActions myActions,
        Long resubmittedFromId) {
    public record Form(String code, String name) {
        public static Form from(Map<String, Object> row) {
            return new Form((String) row.get("form_code"), (String) row.get("form_name"));
        }
    }

    public record Line(
            long order,
            String kind,
            ApprovalPersonResponseDto approver,
            String source,
            String rule,
            String status,
            String skipReason,
            LocalDateTime actedAt,
            String comment) {
        public static Line from(Map<String, Object> row) {
            return new Line(number(row, "step_order"), (String) row.get("line_kind"),
                    ApprovalPersonResponseDto.fromSnapshot(row), (String) row.get("source"),
                    (String) row.get("rule_label"), (String) row.get("status"), (String) row.get("skip_reason"),
                    (LocalDateTime) row.get("acted_at"), (String) row.get("comment"));
        }
    }

    public record History(String action, Actor actor, String comment, LocalDateTime at) {
        public static History from(Map<String, Object> row) {
            return new History((String) row.get("action"),
                    row.get("actor_id") == null ? null : Actor.from(row),
                    (String) row.get("comment"), (LocalDateTime) row.get("created_at"));
        }
    }

    public record Actor(long id, String name) {
        public static Actor from(Map<String, Object> row) {
            return new Actor(number(row, "actor_id"), (String) row.get("user_name"));
        }
    }

    public record MyActions(boolean canApprove, boolean canReject, boolean canWithdraw, boolean canResubmit) {
    }
}
