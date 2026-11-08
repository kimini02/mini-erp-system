package com.minierp.backend.domain.leave.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.minierp.backend.domain.leave.dto.LeaveBodyResponseDto;
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

    public void checkFormat(JsonNode body) {
        if (!Set.of("ANNUAL", "HALF_AM", "HALF_PM", "SICK", "FAMILY")
                .contains(BodyValues.text(body, "leaveType", 20))) {
            throw BodyValues.invalid();
        }
        BodyValues.date(body, "startDate");
        BodyValues.date(body, "endDate");
        BodyValues.text(body, "reason", 500);
        BodyValues.optionalLong(body, "handoverUserId");
    }

    public void saveBody(ApprovalDocument document, JsonNode body) {
        checkFormat(body);
        Long handover = BodyValues.optionalLong(body, "handoverUserId");
        if (handover != null) {
            queries.findHandoverUser(handover);
        }
        store.upsertBody(document.id(), body.path("leaveType").textValue(), BodyValues.date(body, "startDate"),
                BodyValues.date(body, "endDate"), handover, body.path("reason").textValue(), LocalDateTime.now(clock));
    }

    public Map<String, Object> raw(long id) {
        return queries.findRequest(id);
    }

    public LeaveBodyResponseDto body(long id) {
        var request = raw(id);
        LeaveBodyResponseDto.Handover handover = null;
        if (request.get("handover_user_id") != null) {
            handover = LeaveBodyResponseDto.Handover.from(queries.findHandoverName(request.get("handover_user_id")));
        }
        return LeaveBodyResponseDto.from(request, handover);
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
        var request = raw(doc.id());
        var start = (LocalDate) request.get("start_date");
        var end = (LocalDate) request.get("end_date");
        BigDecimal days = days((String) request.get("leave_type"), start, end);
        if (days.signum() > 0 && days.compareTo(available(doc.drafterId(), start.getYear())) > 0) {
            throw new BusinessException(ErrorCode.LEAVE_BALANCE_EXCEEDED);
        }
        if (queries.countOverlap(doc.drafterId(), doc.id(), start, end) > 0) {
            throw new BusinessException(ErrorCode.LEAVE_PERIOD_OVERLAP);
        }
    }

    private void append(ApprovalDocument document, String type, BigDecimal days, int year, String memo) {
        if (days.signum() == 0) {
            return;
        }
        store.insertLedger(document.drafterId(), year, type, days, document.id(), memo, LocalDateTime.now(clock));
    }

    public void onSubmitted(ApprovalDocument document) {
        var request = raw(document.id());
        var start = (LocalDate) request.get("start_date");
        var used = days((String) request.get("leave_type"), start, (LocalDate) request.get("end_date"));
        store.updateUsedDays(document.id(), used);
        append(document, "RESERVE", used.negate(), start.getYear(), leaveMemo(document.id()));
    }

    private void release(ApprovalDocument document, boolean approved, String reason) {
        // Legacy reservations retain their migration year; never release them into another year.
        for (var request : queries.findReservations(document.id())) {
            int year = ((Number) request.get("leave_year")).intValue();
            var days = (BigDecimal) request.get("days");
            if (approved) {
                append(document, "USE", days.negate(), year, leaveMemo(document.id()));
            }
            append(document, "RELEASE", days, year, "예약 해제 (" + reason + ")");
        }
    }

    private String leaveMemo(long id) {
        var request = raw(id);
        var format = java.time.format.DateTimeFormatter.ofPattern("M/d");
        String label = switch ((String) request.get("leave_type")) {
            case "HALF_AM" -> "오전 반차";
            case "HALF_PM" -> "오후 반차";
            default -> "연차";
        };
        return label + " " + ((LocalDate) request.get("start_date")).format(format)
                + "~" + ((LocalDate) request.get("end_date")).format(format);
    }

    public void onApproved(ApprovalDocument document) {
        release(document, true, "승인");
    }

    public void onRejected(ApprovalDocument document) {
        release(document, false, "반려");
    }

    public void onWithdrawn(ApprovalDocument document) {
        release(document, false, "회수");
    }
}
