package com.minierp.backend.domain.project.dto;

import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record ProjectPersonResponseDto(long id, String name, String title) {
    public static ProjectPersonResponseDto from(Map<String, Object> row) {
        return new ProjectPersonResponseDto(number(row, "id"), (String) row.get("name"), (String) row.get("title"));
    }

    public static ProjectPersonResponseDto leaderFrom(Map<String, Object> row) {
        return row.get("leader_id") == null
                ? null
                : new ProjectPersonResponseDto(number(row, "leader_id"),
                (String) row.get("leader_name"), (String) row.get("leader_title"));
    }
}
