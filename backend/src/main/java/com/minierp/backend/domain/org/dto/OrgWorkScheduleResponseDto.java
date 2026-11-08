package com.minierp.backend.domain.org.dto;

import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record OrgWorkScheduleResponseDto(long id, String name, String startTime, String endTime, long lateGraceMin) {
    public static OrgWorkScheduleResponseDto from(Map<String, Object> row) {
        return new OrgWorkScheduleResponseDto(number(row, "id"), (String) row.get("name"),
                (String) row.get("startTime"), (String) row.get("endTime"), number(row, "lateGraceMin"));
    }
}
