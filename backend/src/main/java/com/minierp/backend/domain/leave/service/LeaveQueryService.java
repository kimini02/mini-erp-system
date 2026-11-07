package com.minierp.backend.domain.leave.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.minierp.backend.domain.approval.handler.BodyValues;
import com.minierp.backend.domain.leave.handler.LeaveFormHandler;
import com.minierp.backend.domain.org.service.WorkingDays;
import com.minierp.backend.global.exception.*;
import com.minierp.backend.domain.leave.repository.LeaveQueries;
import com.minierp.backend.global.permission.PermissionAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveQueryService {
    private final LeaveQueries queries;
    private final PermissionAccess access;
    private final Clock clock;
    private final LeaveFormHandler handler;
    private final WorkingDays workingDays;
    private int year(Integer year) {
        int y = year == null ? LocalDate.now(clock).getYear() : year;
        if (y < 1 || y > 9999) {
            throw BodyValues.invalid();
        }
        return y;
    }

    public Map<String, Object> balance(Integer requestedYear) {
        int y = year(requestedYear);
        var result = queries.findBalance(access.actor(), y);
        result.put("year", y);
        return result;
    }

    public List<Map<String, Object>> ledger(Integer requestedYear, String type) {
        return ledger(access.actor(), requestedYear, type);
    }

    public List<Map<String, Object>> ledgerFor(long user, Integer requestedYear) {
        return ledger(user, requestedYear, null);
    }

    private List<Map<String, Object>> ledger(long user, Integer requestedYear, String type) {
        if (type != null && !type.isBlank()
                && !Set.of("GRANT", "USE", "RESERVE", "RELEASE", "EXPIRE", "ADJUST").contains(type)) {
            throw BodyValues.invalid();
        }
        // Calculate balance before filtering: a USE-only view must still include earlier GRANTs in its balance.
        int y = year(requestedYear);
        String entryType = null;
        if (type != null && !type.isBlank()) {
            entryType = type;
        }
        return queries.findLedger(user, y, entryType).stream()
                .map(l -> map("date", ((LocalDateTime) l.get("created_at")).toLocalDate(), "type", l.get("entry_type"),
                        "days", l.get("days"), "balance", l.get("balance"), "memo", l.get("memo"),
                        "document", l.get("document_id") == null
                                ? null
                                : map("id", l.get("document_id"), "docNo", l.get("doc_no"), "status", l.get("status"))))
                .toList();
    }

    public Map<String, Object> preview(JsonNode body) {
        String type = BodyValues.text(body, "leaveType", 20);
        if (!Set.of("ANNUAL", "HALF_AM", "HALF_PM", "SICK", "FAMILY").contains(type)) {
            throw BodyValues.invalid();
        }
        LocalDate start = BodyValues.date(body, "startDate");
        LocalDate end = BodyValues.date(body, "endDate");
        BigDecimal available = handler.available(access.actor(), start.getYear());
        BigDecimal days = BigDecimal.ZERO;
        long offDays = 0;
        String error = null;
        try {
            days = handler.days(type, start, end);
            offDays = ChronoUnit.DAYS.between(start, end) + 1 - workingDays.count(start, end);
            if (days.signum() > 0 && days.compareTo(available) > 0) {
                error = ErrorCode.LEAVE_BALANCE_EXCEEDED.getMessage();
            }
        } catch (BusinessException ex) {
            if (ex.getErrorCode() == ErrorCode.INVALID_INPUT_VALUE) {
                throw ex;
            }
            error = ex.getMessage();
            if (ex.getErrorCode() == ErrorCode.LEAVE_NO_WORKING_DAY) {
                offDays = ChronoUnit.DAYS.between(start, end) + 1;
            }
        }
        return map("days", days, "offDays", offDays, "available", available, "after", available.subtract(days),
                "error", error);
    }
}
