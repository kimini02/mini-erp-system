package com.minierp.backend.integration;

import com.minierp.backend.support.ApprovalApiTestSupport;
import com.minierp.backend.domain.user.entity.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

public class M2LeaveQueryTest extends ApprovalApiTestSupport {
    @Test
    void previewUsesWorkingDaysAndCrossYearReturnsErrorWithoutWrites() throws Exception {
        jdbc.update(
                "INSERT INTO holidays(holiday_date,name,substitute) VALUES(?,'휴일',0) ON DUPLICATE KEY UPDATE name=name",
                start.plusDays(1));
        var preview = data(
                postAs("/api/v1/leave/days-preview", employeeToken,
                        Map.of("leaveType", "ANNUAL", "startDate", start.toString(),
                                "endDate", start.plusDays(6).toString())));
        assertThat(preview.path("days").asDouble()).isEqualTo(4);
        assertThat(preview.path("offDays").asInt()).isEqualTo(3);
        assertThat(preview.path("after").asDouble()).isEqualTo(6);
        assertThat(
                data(
                        postAs("/api/v1/leave/days-preview", employeeToken,
                                Map.of("leaveType", "HALF_AM", "startDate", start.toString(),
                                        "endDate", start.toString())))
                        .path("days")
                        .asDouble())
                .isEqualTo(.5);
        var cross = data(
                postAs("/api/v1/leave/days-preview", employeeToken,
                        Map.of("leaveType", "ANNUAL", "startDate", start.withMonth(12).withDayOfMonth(31).toString(),
                                "endDate", start.plusYears(1).withDayOfYear(1).toString())));
        assertThat(cross.path("error").asText()).isEqualTo("연도가 바뀌는 휴가는 연도별로 나눠 신청해 주세요.");
        assertThat(
                jdbc.queryForObject("SELECT COUNT(*) FROM leave_ledger WHERE user_id=?", Long.class, employee.getId()))
                .isEqualTo(1);
    }

    @Test
    void balanceAndFilteredLedgerRetainCumulativeBalanceAndOwnership() throws Exception {
        long id = submittedLeave();
        data(postAs("/api/v1/approval-documents/" + id + "/approve", leaderToken, null));
        var balance = data(getAs("/api/v1/leave/balance?year=" + start.getYear(), employeeToken));
        assertThat(balance.path("granted").asDouble()).isEqualTo(10);
        assertThat(balance.path("used").asDouble()).isEqualTo(1);
        assertThat(balance.path("reserved").asDouble()).isZero();
        assertThat(balance.path("available").asDouble()).isEqualTo(9);
        var full = data(getAs("/api/v1/leave/ledger?year=" + start.getYear(), employeeToken));
        assertThat(full.size()).isEqualTo(4);
        assertThat(full.get(0).path("balance").asDouble()).isEqualTo(9);
        var filtered = data(getAs("/api/v1/leave/ledger?year=" + start.getYear() + "&type=USE", employeeToken));
        assertThat(filtered.size()).isEqualTo(1);
        assertThat(filtered.get(0).path("balance").asDouble()).isEqualTo(8);
        assertThat(filtered.get(0).path("document").path("id").asLong()).isEqualTo(id);
        assertThat(data(getAs("/api/v1/leave/ledger?year=" + start.getYear(), outsideToken)).size()).isZero();
        assertThat(
                data(getAs("/api/v1/leave/balance?year=" + (start.getYear() + 1), employeeToken))
                        .path("available")
                        .asDouble())
                .isZero();
    }
}
