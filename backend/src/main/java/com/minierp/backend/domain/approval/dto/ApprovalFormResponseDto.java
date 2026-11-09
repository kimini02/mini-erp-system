package com.minierp.backend.domain.approval.dto;

import java.util.List;
import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.bool;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record ApprovalFormResponseDto(
        String code,
        String name,
        String shortName,
        String prefix,
        String description,
        Options options,
        List<Step> steps) {
    public record Options(boolean dedupeApprover, boolean fallbackToUpper, boolean skipSelf, boolean allowLineEdit) {
        public static Options from(Map<String, Object> row) {
            return new Options(bool(row, "dedupe_approver"), bool(row, "fallback_to_upper"),
                    bool(row, "skip_self"), bool(row, "allow_line_edit"));
        }
    }

    public record Step(long order, String lineKind, String approverType, String roleCode, Long userId, String label) {
        public static Step from(Map<String, Object> row, String label) {
            return new Step(number(row, "step_order"), (String) row.get("line_kind"),
                    (String) row.get("approver_type"), (String) row.get("role_code"),
                    row.get("user_id") == null ? null : number(row, "user_id"), label);
        }
    }
}
