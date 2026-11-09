package com.minierp.backend.domain.org.dto;

import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record OrgPositionResponseDto(long id, String name, long rankOrder) {
    public static OrgPositionResponseDto from(Map<String, Object> row) {
        return new OrgPositionResponseDto(number(row, "id"), (String) row.get("name"), number(row, "rankOrder"));
    }
}
