package com.minierp.backend.integration;

import com.minierp.backend.support.AttendanceTestSupport;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class M3CsvLabelsTest extends AttendanceTestSupport {
    @Test
    void csvUsesKoreanLabelsAndEmptyNonAttendanceCellsWhileJsonKeepsCodes() throws Exception {
        var month = YearMonth.from(date);
        at(month.atDay(20), LocalTime.of(9, 20));
        String marker = "CSV-" + employee.getId();
        jdbc.update("UPDATE users SET user_name=? WHERE id=?", marker, employee.getId());
        String[] codes = {
            "ON_TIME", "LATE", "EARLY", "MISSING", "ABSENT", "LEAVE", "HALF_AM", "HALF_PM", "HOLIDAY_WORK", "HOLIDAY",
                    "NONE", "FUTURE"
        };
        String[] labels = {
            "정상", "지각", "조퇴", "퇴근 누락", "결근", "휴가", "오전 반차", "오후 반차", "휴일 근무", "", "", ""
        };
        for (int i = 0; i < codes.length; i++) {
            String code = codes[i];
            var day = month.atDay(i == 11 ? 25 : i + 1);
            String type = switch (code) {
                case "LEAVE", "HALF_AM", "HALF_PM" -> code;
                case "HOLIDAY", "HOLIDAY_WORK" -> "HOLIDAY";
                default -> "WORKDAY";
            };
            String in = Set.of("ON_TIME", "LATE").contains(code) ? code : "NONE";
            String out = Set.of("EARLY", "MISSING").contains(code) ? code : "NONE";
            jdbc.update(
                    "INSERT INTO attendances(user_id,work_date,day_type,clock_in_at,in_result,out_result,absent,created_at) VALUES(?,?,?,?,?,?,?,?)",
                    employee.getId(), day, type, i < 4 || code.equals("HOLIDAY_WORK") ? day.atTime(9, 0) : null, in,
                    out, code.equals("ABSENT"), LocalDateTime.now(clock));
        }
        var table = data(getAs("/api/v1/attendance/monthly?month=" + month, chiefToken));
        com.fasterxml.jackson.databind.JsonNode row = null;
        for (var r : table.path("rows")) {
            if (r.path("user").path("id").asLong() == employee.getId()) {
                row = r;
            }
        }
        assertThat(row).isNotNull();
        var bytes = getAs("/api/v1/attendance/monthly/export?month=" + month, chiefToken).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        String line = Arrays.stream(new String(bytes, StandardCharsets.UTF_8).split("\r\n"))
                .filter(v -> v.startsWith("\"" + marker + "\",")).findFirst().orElseThrow();
        String[] cells = line.split(",", -1);
        for (int i = 0; i < codes.length; i++) {
            int day = i == 11 ? 25 : i + 1;
            assertThat(row.path("cells").get(day - 1).path("code").asText()).isEqualTo(codes[i]);
            assertThat(cells[day + 2]).isEqualTo("\"" + labels[i] + "\"");
        }
    }
}
