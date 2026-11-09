package com.minierp.backend.domain.org.dto;

import java.time.LocalDate;
import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.number;

public record OrgHolidayResponseDto(long id, LocalDate date, String name, Boolean substitute) {
    public static OrgHolidayResponseDto from(Map<String, Object> row) {
        return new OrgHolidayResponseDto(number(row, "id"), (LocalDate) row.get("date"), (String) row.get("name"),
                (Boolean) row.get("substitute"));
    }
}
