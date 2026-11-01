package com.minierp.backend.domain.leave.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.minierp.backend.domain.approval.handler.*;
import com.minierp.backend.domain.approval.model.*;
import com.minierp.backend.domain.org.service.WorkingDays;
import com.minierp.backend.global.persistence.SqlStore;
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
    private final SqlStore db;
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
            db.one("SELECT id FROM users WHERE id=:id", map("id", handover), ErrorCode.USER_NOT_FOUND);
        }
        db.update("""
            INSERT INTO leave_requests(document_id,leave_type,start_date,end_date,used_days,handover_user_id,reason,created_at,updated_at)
            VALUES(:id,:type,:start,:end,0,:handover,:reason,:now,:now) ON DUPLICATE KEY UPDATE leave_type=VALUES(leave_type),start_date=VALUES(start_date),end_date=VALUES(end_date),used_days=0,handover_user_id=VALUES(handover_user_id),reason=VALUES(reason),updated_at=VALUES(updated_at)
            """, map("id", d.id(), "type", b.path("leaveType").textValue(), "start", BodyValues.date(b, "startDate"),
                "end", BodyValues.date(b, "endDate"), "handover", handover, "reason", b.path("reason").textValue(),
                "now", LocalDateTime.now(clock)));
    }

    public Map<String, Object> raw(long id) {
        return db.one("SELECT * FROM leave_requests WHERE document_id=:id", map("id", id), ErrorCode
                .DOCUMENT_NOT_FOUND);
    }

    public Map<String, Object> body(long id) {
        var r = raw(id);
        Object handover = null;
        if (r.get("handover_user_id") != null) {
            handover = db.one("SELECT id,user_name AS name FROM users WHERE id=:id",
                    map("id", r.get("handover_user_id")), ErrorCode.USER_NOT_FOUND);
        }
        return map("leaveType", r.get("leave_type"), "startDate", r.get("start_date"), "endDate", r.get("end_date"),
                "usedDays", r.get("used_days"), "handover", handover, "reason", r.get("reason"));
    }

    public void copyBody(long source, ApprovalDocument target) {
        db.update(
                "INSERT INTO leave_requests(document_id,leave_type,start_date,end_date,used_days,handover_user_id,reason,created_at,updated_at) SELECT :target,leave_type,start_date,end_date,0,handover_user_id,reason,:now,:now FROM leave_requests WHERE document_id=:source",
                map("target", target.id(), "source", source, "now", LocalDateTime.now(clock)));
    }

    public void deleteBody(long id) {
        db.update("DELETE FROM leave_requests WHERE document_id=:id", map("id", id));
    }

    public BigDecimal available(long user, int year) {
        return (BigDecimal) db
                .one(
                "SELECT COALESCE(SUM(days),0) AS available FROM leave_ledger WHERE user_id=:user AND leave_year=:year",
                map("user", user, "year", year), ErrorCode.USER_NOT_FOUND).get("available");
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
        return type.equals("SICK") || type.equals("FAMILY") ? BigDecimal.ZERO : half ? new BigDecimal("0.5")
                : BigDecimal.valueOf(working);
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
        if (db.count("""
            SELECT COUNT(*) FROM leave_requests l JOIN approval_documents d ON d.id=l.document_id
            WHERE d.drafter_id=:user AND d.status IN ('IN_PROGRESS','APPROVED') AND d.id<>:id AND l.start_date<=:end AND l.end_date>=:start
            """, map("user", doc.drafterId(), "id", doc.id(), "start", start, "end", end)) > 0) {
            throw new BusinessException(ErrorCode.LEAVE_PERIOD_OVERLAP);
        }
    }

    private void append(ApprovalDocument d, String type, BigDecimal days, int year, String memo) {
        if (days.signum() == 0) {
            return;
        }
        db.update(
                "INSERT INTO leave_ledger(user_id,leave_year,entry_type,days,document_id,memo,created_at) VALUES(:user,:year,:type,:days,:id,:memo,:now)",
                map("user", d.drafterId(), "year", year, "type", type, "days", days, "id", d.id(), "memo", memo, "now",
                LocalDateTime.now(clock)));
    }

    public void onSubmitted(ApprovalDocument d) {
        var r = raw(d.id());
        var start = (LocalDate) r.get("start_date");
        var used = days((String) r.get("leave_type"), start, (LocalDate) r.get("end_date"));
        db.update("UPDATE leave_requests SET used_days=:days WHERE document_id=:id", map("id", d.id(), "days", used));
        append(d, "RESERVE", used.negate(), start.getYear(), leaveMemo(d.id()));
    }

    private void release(ApprovalDocument d, boolean approved, String reason) {
        // Legacy reservations retain their migration year; never release them into another year.
        for (var r : db.rows(
                "SELECT leave_year,-SUM(days) AS days FROM leave_ledger WHERE document_id=:id AND entry_type='RESERVE' GROUP BY leave_year",
                map("id", d.id()))) {
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
        return label + " " + ((LocalDate) r.get("start_date")).format(format) + "~" + ((LocalDate) r.get("end_date"))
                .format(format);
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
