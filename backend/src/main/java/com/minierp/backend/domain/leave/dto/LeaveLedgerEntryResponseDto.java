package com.minierp.backend.domain.leave.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record LeaveLedgerEntryResponseDto(
        LocalDate date,
        String type,
        BigDecimal days,
        BigDecimal balance,
        String memo,
        Document document) {
    public static LeaveLedgerEntryResponseDto from(Map<String, Object> row) {
        return new LeaveLedgerEntryResponseDto(((LocalDateTime) row.get("created_at")).toLocalDate(),
                (String) row.get("entry_type"), (BigDecimal) row.get("days"), (BigDecimal) row.get("balance"),
                (String) row.get("memo"), row.get("document_id") == null ? null : Document.from(row));
    }

    public record Document(long id, String docNo, String status) {
        public static Document from(Map<String, Object> row) {
            return new Document(number(row, "document_id"), (String) row.get("doc_no"), (String) row.get("status"));
        }
    }
}
