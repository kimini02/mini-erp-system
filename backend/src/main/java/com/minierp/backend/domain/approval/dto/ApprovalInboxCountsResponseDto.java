package com.minierp.backend.domain.approval.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record ApprovalInboxCountsResponseDto(
        long todo,
        @JsonProperty("in-progress") long inProgress,
        long done,
        long mine,
        long drafts,
        long reference) {
    public static ApprovalInboxCountsResponseDto from(Map<String, Object> row) {
        return new ApprovalInboxCountsResponseDto(number(row, "todo"), number(row, "in-progress"), number(row, "done"),
                number(row, "mine"), number(row, "drafts"), number(row, "reference"));
    }
}
