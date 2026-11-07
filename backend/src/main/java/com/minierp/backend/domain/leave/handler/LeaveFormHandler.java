package com.minierp.backend.domain.leave.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.minierp.backend.domain.approval.handler.*;
import com.minierp.backend.domain.approval.model.*;
import com.minierp.backend.domain.org.service.WorkingDays;
import com.minierp.backend.domain.leave.repository.LeaveQueries;
import com.minierp.backend.domain.leave.repository.LeaveStore;
import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Component
@RequiredArgsConstructor
public class LeaveFormHandler implements ApprovalFormHandler {
    private final LeaveQueries queries;
    private final LeaveStore store;
    private final WorkingDays workingDays;
    private final Clock clock;
    public String formCode() {
        return "LEAVE";
    }

    public void checkFormat(JsonNode b) {
        if (!Set.of("ANNUAL", "HALF_AM", "HALF_PM", "SICK", "FAMILY").contains(BodyValues.text(b, "leaveType", 20))) {
            throw BodyValues.invalid();
        }
        BodyValues.date(b, "startDate");
        BodyValues.date(b, "endDate");
        BodyValues.text(b, "reason", 500);
        BodyValues.optionalLong(b, "handoverUserId");
    }

    public void saveBody(ApprovalDocument d, JsonNode b) {
        checkFormat(b);
        Long handover = BodyValues.optionalLong(b, "handoverUserId");
        if (handover != null) {
            queries.findHandoverUser(handover);
        }
        store.upsertBody(d.id(), b.path("leaveType").textValue(), BodyValues.date(b, "startDate"),
                BodyValues.date(b, "endDate"), handover, b.path("reason").textValue(), LocalDateTime.now(clock));
    }

    public Map<String, Object> raw(long id) {
        return queries.findRequest(id);
    }

    public Map<String, Object> body(long id) {
        var r = raw(id);
        Object handover = null;
        if (r.get("handover_user_id") != null) {
            handover = queries.findHandoverName(r.get("handover_user_id"));
        }
        return map("leaveType", r.get("leave_type"), "startDate", r.get("start_date"), "endDate", r.get("end_date"),
                "usedDays", r.get("used_days"), "handover", handover, "reason", r.get("reason"));
    }

    public void copyBody(long source, ApprovalDocument target) {
        store.copyBody(target.id(), source, LocalDateTime.now(clock));
    }

    public void deleteBody(long id) {
        store.deleteBody(id);
    }

    public BigDecimal available(long user, int year) {
        return (BigDecimal) queries.findAvailable(user, year).get("available");
    }

    public BigDecimal days(String type, LocalDate start, LocalDate end) {
        if (start.getYear() != end.getYear()) {
            throw new BusinessException(ErrorCode.LEAVE_CROSS_YEAR);
        }
        if (end.isBefore(start)) {
            throw BodyValues.invalid();
        }
        boolean half = type.equals("HALF_AM") || type.equals("HALF_PM");
        if (half && !start.equals(end)) {
            throw new BusinessException(ErrorCode.INVALID_HALF_LEAVE_DATE);
        }
        long working = workingDays.count(start, end);
        if (working == 0) {
            throw new BusinessException(ErrorCode.LEAVE_NO_WORKING_DAY);
        }
        return type.equals("SICK") || type.equals("FAMILY")
                ? BigDecimal.ZERO
                : half ? new BigDecimal("0.5") : BigDecimal.valueOf(working);
    }

    public void validate(ApprovalDraft draft) {
        var doc = draft.document();
        var r = raw(doc.id());
        var start = (LocalDate) r.get("start_date");
        var end = (LocalDate) r.get("end_date");
        BigDecimal days = days((String) r.get("leave_type"), start, end);
        if (days.signum() > 0 && days.compareTo(available(doc.drafterId(), start.getYear())) > 0) {
            throw new BusinessException(ErrorCode.LEAVE_BALANCE_EXCEEDED);
        }
        if (queries.countOverlap(doc.drafterId(), doc.id(), start, end) > 0) {
            throw new BusinessException(ErrorCode.LEAVE_PERIOD_OVERLAP);
        }
    }

    private void append(ApprovalDocument d, String type, BigDecimal days, int year, String memo) {
        if (days.signum() == 0) {
            return;
        }
        store.insertLedger(d.drafterId(), year, type, days, d.id(), memo, LocalDateTime.now(clock));
    }

    public void onSubmitted(ApprovalDocument d) {
        var r = raw(d.id());
        var start = (LocalDate) r.get("start_date");
        var used = days((String) r.get("leave_type"), start, (LocalDate) r.get("end_date"));
        store.updateUsedDays(d.id(), used);
        append(d, "RESERVE", used.negate(), start.getYear(), leaveMemo(d.id()));
    }

    private void release(ApprovalDocument d, boolean approved, String reason) {
        // Legacy reservations retain their migration year; never release them into another year.
        for (var r : queries.findReservations(d.id())) {
            int year = ((Number) r.get("leave_year")).intValue();
            var days = (BigDecimal) r.get("days");
            if (approved) {
                append(d, "USE", days.negate(), year, leaveMemo(d.id()));
            }
            append(d, "RELEASE", days, year, "예약 해제 (" + reason + ")");
        }
    }

    private String leaveMemo(long id) {
        var r = raw(id);
        var format = java.time.format.DateTimeFormatter.ofPattern("M/d");
        String label = switch ((String) r.get("leave_type")) {
            case "HALF_AM" -> "오전 반차";
            case "HALF_PM" -> "오후 반차";
            default -> "연차";
        };
        return label + " " + ((LocalDate) r.get("start_date")).format(format)
                + "~" + ((LocalDate) r.get("end_date")).format(format);
    }

    public void onApproved(ApprovalDocument d) {
        release(d, true, "승인");
    }

    public void onRejected(ApprovalDocument d) {
        release(d, false, "반려");
    }

    public void onWithdrawn(ApprovalDocument d) {
        release(d, false, "회수");
    }
}
