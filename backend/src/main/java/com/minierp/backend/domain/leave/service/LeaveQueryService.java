package com.minierp.backend.domain.leave.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.minierp.backend.domain.leave.dto.LeaveBalanceResponseDto;
import com.minierp.backend.domain.leave.dto.LeaveLedgerEntryResponseDto;
import com.minierp.backend.domain.leave.dto.LeaveDaysPreviewResponseDto;
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
        int selectedYear = year == null ? LocalDate.now(clock).getYear() : year;
        if (selectedYear < 1 || selectedYear > 9999) {
            throw BodyValues.invalid();
        }
        return selectedYear;
    }

    public LeaveBalanceResponseDto getBalance(Integer requestedYear) {
        int selectedYear = year(requestedYear);
        var result = queries.findBalance(access.actor(), selectedYear);
        return LeaveBalanceResponseDto.from(result, selectedYear);
    }

    public List<LeaveLedgerEntryResponseDto> getLedgerEntries(Integer requestedYear, String type) {
        return ledger(access.actor(), requestedYear, type);
    }

    public List<LeaveLedgerEntryResponseDto> getUserLedgerEntries(long user, Integer requestedYear) {
        return ledger(user, requestedYear, null);
    }

    private List<LeaveLedgerEntryResponseDto> ledger(long user, Integer requestedYear, String type) {
        if (type != null && !type.isBlank()
                && !Set.of("GRANT", "USE", "RESERVE", "RELEASE", "EXPIRE", "ADJUST").contains(type)) {
            throw BodyValues.invalid();
        }
        // Calculate balance before filtering: a USE-only view must still include earlier GRANTs in its balance.
        int selectedYear = year(requestedYear);
        String entryType = null;
        if (type != null && !type.isBlank()) {
            entryType = type;
        }
        return queries.findLedger(user, selectedYear, entryType).stream()
                .map(LeaveLedgerEntryResponseDto::from).toList();
    }

    public LeaveDaysPreviewResponseDto getDaysPreview(JsonNode body) {
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
        return new LeaveDaysPreviewResponseDto(days, offDays, available, available.subtract(days), error);
    }
}
