package com.minierp.backend.domain.notification.dto;

import java.time.LocalDateTime;
import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record NotificationItemResponseDto(
        long id,
        String type,
        String title,
        String link,
        boolean read,
        LocalDateTime createdAt) {
    public static NotificationItemResponseDto from(Map<String, Object> row) {
        return new NotificationItemResponseDto(number(row, "id"), (String) row.get("type"), (String) row.get("title"),
                (String) row.get("link"), row.get("read_at") != null, (LocalDateTime) row.get("created_at"));
    }
}
