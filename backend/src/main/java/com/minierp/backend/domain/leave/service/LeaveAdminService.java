package com.minierp.backend.domain.leave.service;

import com.minierp.backend.domain.leave.repository.LeaveStore;
import com.minierp.backend.domain.leave.repository.LeaveQueries;
import com.minierp.backend.global.permission.*;
import com.minierp.backend.global.exception.*;
import com.minierp.backend.global.response.PageResponseDto;
import com.minierp.backend.domain.leave.dto.LeaveAdminBalanceResponseDto;
import com.minierp.backend.domain.leave.dto.LeaveLedgerEntryResponseDto;
import com.minierp.backend.domain.leave.dto.LeaveAdjustmentResponseDto;
import com.minierp.backend.domain.leave.dto.LeaveGrantRunResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class LeaveAdminService {
    private final LeaveStore store;
    private final LeaveQueries queries;
    private final PermissionAccess access;
    private final LeaveGrantService grants;
    private final LeaveQueryService query;
    private final Clock clock;
    private static final String MENU = "LEAVE_ADMIN";
    private int year(Integer requested) {
        int year = requested == null ? LocalDate.now(clock).getYear() : requested;
        if (year < 1 || year > 9999) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return year;
    }

    @Transactional(readOnly = true)
    public PageResponseDto<LeaveAdminBalanceResponseDto> getBalances(
            Integer requested,
            Long department,
            String search,
            int page,
            int size) {
        if (page < 0 || size < 1 || size > 200) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        int year = year(requested);
        long offset = (long) page * size;
        var scope = access.scope(MENU);
        if (department != null) {
            if (!scope.includesDepartment(department)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
        }
        String pattern = null;
        if (search != null && !search.isBlank()) {
            pattern = "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        }
        long total = queries.countBalances(year, size, offset, scope, department, pattern);
        var rows = queries.findBalances(year, size, offset, scope, department, pattern).stream()
                .map(LeaveAdminBalanceResponseDto::from).toList();
        return PageResponseDto.of(rows, page, size, total);
    }

    @Transactional(readOnly = true)
    public List<LeaveLedgerEntryResponseDto> getUserLedgerEntries(long user, Integer year) {
        access.assertUserInScope(MENU, user);
        return query.getUserLedgerEntries(user, year);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LeaveAdjustmentResponseDto createAdjustment(long user, int year, BigDecimal days, String memo) {
        access.assertUserInScope(MENU, user);
        year(year);
        validateAdjustment(days, memo);
        store.lockUser(user);
        var current = (BigDecimal) queries.findAdjustmentBalance(user, year).get("balance");
        if (current.add(days).signum() < 0) {
            throw new BusinessException(ErrorCode.LEAVE_BALANCE_NEGATIVE);
        }
        long id = store.insertAdjustment(user, year, days, memo, access.actor(), LocalDateTime.now(clock));
        return new LeaveAdjustmentResponseDto(id, user, year, current.add(days));
    }

    private void validateAdjustment(BigDecimal days, String memo) {
        if (days == null || days.signum() == 0 || days.remainder(new BigDecimal("0.5")).signum() != 0
                || days.abs().compareTo(new BigDecimal("9999999.5")) > 0
                || memo == null || memo.isBlank() || memo.length() > 500) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void all() {
        if (access.scope(MENU).scope() != DataScope.ALL) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    public LeaveGrantRunResponseDto createGrantRun(LocalDate date) {
        all();
        return LeaveGrantRunResponseDto.from(grants.run(date));
    }

    @Transactional(readOnly = true)
    public List<LeaveGrantRunResponseDto> getGrantRuns(int limit) {
        all();
        if (limit < 1 || limit > 200) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return queries.findGrantRuns(limit).stream().map(LeaveGrantRunResponseDto::from).toList();
    }
}
