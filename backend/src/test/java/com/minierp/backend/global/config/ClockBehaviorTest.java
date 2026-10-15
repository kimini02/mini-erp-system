package com.minierp.backend.global.config;

import com.minierp.backend.domain.approval.dto.LeaveRequestCreateDto;
import com.minierp.backend.domain.approval.entity.LeaveType;
import com.minierp.backend.domain.approval.repository.LeaveRequestRepository;
import com.minierp.backend.domain.approval.service.ApprovalService;
import com.minierp.backend.domain.overtime.dto.OvertimeRequestDto;
import com.minierp.backend.domain.overtime.repository.OvertimeRequestRepository;
import com.minierp.backend.domain.overtime.service.OvertimeService;
import com.minierp.backend.domain.project.entity.Project;
import com.minierp.backend.domain.task.entity.Task;
import com.minierp.backend.domain.task.entity.TaskStatus;
import com.minierp.backend.domain.user.entity.User;
import com.minierp.backend.domain.user.repository.UserRepository;
import com.minierp.backend.global.entity.Priority;
import com.minierp.backend.global.exception.BusinessException;
import com.minierp.backend.global.exception.ErrorCode;
import com.minierp.backend.global.service.AccessPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ClockBehaviorTest {
    @Test
    void productionClockUsesSeoulEvenAtUtcDateBoundary() {
        Clock production = new ClockConfig().clock();
        assertThat(production.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
        Clock midnight = Clock.fixed(Instant.parse("2026-12-31T15:00:00Z"), production.getZone());
        assertThat(LocalDate.now(midnight))
                .isEqualTo(LocalDate.now(midnight.withZone(ZoneOffset.UTC)).plusDays(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-04-06T00:00:00Z", "2026-12-31T15:00:00Z"})
    void changingFixedDatePreservesPastRequestAndOverdueRules(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), new ClockConfig().clock().getZone());
        LocalDate today = LocalDate.now(clock);
        var users = mock(UserRepository.class);
        var leaves = mock(LeaveRequestRepository.class);
        var overtimes = mock(OvertimeRequestRepository.class);
        // The entity is only an authenticated requester fixture; no password is needed here.
        when(users.findById(1L)).thenReturn(Optional.of(mock(User.class)));
        var approvalService = new ApprovalService(leaves, users, new AccessPolicy(mock(com.minierp.backend.global.permission.PermissionAccess.class)), clock, mock(com.minierp.backend.global.permission.LegacyRequestScopeRepository.class));
        var overtimeService = new OvertimeService(overtimes, users, new AccessPolicy(mock(com.minierp.backend.global.permission.PermissionAccess.class)), clock, mock(com.minierp.backend.global.permission.LegacyRequestScopeRepository.class));

        var leave = new LeaveRequestCreateDto();
        leave.setAppType(LeaveType.ANNUAL);
        leave.setStartDate(today.minusDays(1));
        leave.setEndDate(today.minusDays(1));
        assertThatThrownBy(() -> approvalService.createLeaveRequest(leave, 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(leaves, never()).save(any());

        var overtime = new OvertimeRequestDto();
        overtime.setStartDate(today.minusDays(1));
        overtime.setEndDate(today.minusDays(1));
        overtime.setStartTime(LocalTime.of(9, 0));
        overtime.setEndTime(LocalTime.of(12, 0));
        assertThatThrownBy(() -> overtimeService.requestOvertime(overtime, 1L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE));
        verify(overtimes, never()).save(any());

        var project = Project.create("시계 검증", "", today.minusDays(7), today.plusDays(7), Priority.MEDIUM);
        var task = Task.create("지연 판정", "", today.minusDays(1), TaskStatus.DOING, Priority.MEDIUM, project);
        assertThat(task.isOverdue(today)).isTrue();
        assertThat(task.isOverdue(today.minusDays(1))).isFalse();
        task.changeStatus(TaskStatus.DONE);
        assertThat(task.isOverdue(today)).isFalse();
    }
}
