package com.minierp.backend.integration;

import com.minierp.backend.support.MariaDbIntegrationTest;
import com.minierp.backend.domain.user.entity.*;
import com.minierp.backend.domain.user.repository.UserRepository;
import com.minierp.backend.domain.approval.entity.*;
import com.minierp.backend.domain.approval.repository.LeaveRequestRepository;
import com.minierp.backend.domain.approval.service.ApprovalService;
import com.minierp.backend.global.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class M1RegressionTest extends MariaDbIntegrationTest {
    @Autowired UserRepository users;
    @Autowired LeaveRequestRepository leaves;
    @Autowired ApprovalService approval;
    @Autowired Clock clock;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired com.minierp.backend.domain.overtime.repository.OvertimeRequestRepository overtimes;
    @Autowired com.minierp.backend.domain.overtime.service.OvertimeService overtime;

    private User user(String department, UserRole role) {
        String id = UUID.randomUUID().toString();
        User user = User.create("범위 검증", id, id + "@example.test", "unused-encoded", department, "사원");
        user.changeRole(role);
        return users.saveAndFlush(user);
    }
    private LeaveRequest leave(User user) {
        LocalDate date = LocalDate.now(clock).with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.WEDNESDAY));
        return leaves.saveAndFlush(LeaveRequest.builder().requester(user).appType(LeaveType.ANNUAL)
                .startDate(date).endDate(date).build());
    }
    private com.minierp.backend.domain.overtime.entity.OvertimeRequest overtime(User user) {
        return overtimes.saveAndFlush(com.minierp.backend.domain.overtime.entity.OvertimeRequest.builder()
                .requester(user).overtimeDate(LocalDate.now(clock).plusDays(5))
                .startTime(LocalTime.of(9,0)).endTime(LocalTime.of(18,0)).reason("검증").build());
    }
    @Test void outsideDepartmentCannotBeRejectedForLeaveOrHandledForOvertime() {
        User leader = user("01", UserRole.TEAM_LEADER);
        User outside = user("02", UserRole.USER);
        var l = leave(outside); var o = overtime(outside);
        var reason = new com.minierp.backend.domain.approval.dto.RejectRequestDto();reason.setRejectReason("검증");
        assertDenied(() -> approval.rejectLeaveRequest(l.getId(),leader.getId(),reason));
        assertDenied(() -> overtime.approveOvertime(o.getId(),leader.getId()));
        assertDenied(() -> overtime.rejectOvertime(o.getId(),leader.getId()));
        assertThat(overtime.getAllOvertimeRequestList(leader.getId(),true)).noneMatch(row -> row.getId().equals(o.getId()));
        assertThat(leaves.findById(l.getId()).orElseThrow().getAppStatus()).isEqualTo(LeaveStatus.PENDING);
    }
    private void assertDenied(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class,
                ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }
    @Test void descendantRequestsAreVisibleAndApprovableAndChiefSeesAll() {
        User leader=user("01",UserRole.TEAM_LEADER), worker=user("03",UserRole.USER), chief=user("02",UserRole.ADMIN);
        jdbc.update("UPDATE departments SET parent_id=2 WHERE id=4");
        try {
            var l=leave(worker);var o=overtime(worker);
            assertThat(approval.getAllLeaveRequestList(leader.getId(),true)).anyMatch(row->row.getAppId().equals(l.getId()));
            assertThat(overtime.getAllOvertimeRequestList(leader.getId(),true)).anyMatch(row->row.getId().equals(o.getId()));
            assertThat(approval.getAllLeaveRequestList(chief.getId(),true)).anyMatch(row->row.getAppId().equals(l.getId()));
            assertThat(overtime.getAllOvertimeRequestList(chief.getId(),true)).anyMatch(row->row.getId().equals(o.getId()));
            approval.approveLeaveRequest(l.getId(),leader.getId());overtime.approveOvertime(o.getId(),leader.getId());
            assertDenied(()->approval.getAllLeaveRequestList(worker.getId(),true));
            assertDenied(()->overtime.getAllOvertimeRequestList(worker.getId(),true));
        } finally { jdbc.update("UPDATE departments SET parent_id=1 WHERE id=4"); }
    }
    @Test
    void leaderCannotReadAnotherDepartmentsLeave() {
        User leader = user("01", UserRole.TEAM_LEADER);
        LeaveRequest outside = leave(user("02", UserRole.USER));
        assertThat(approval.getAllLeaveRequestList(leader.getId(), true))
                .noneMatch(row -> row.getAppId().equals(outside.getId()));
    }
    @Test
    void leaderCannotApproveAnotherDepartmentsLeave() {
        User leader = user("01", UserRole.TEAM_LEADER);
        LeaveRequest outside = leave(user("02", UserRole.USER));
        assertThatThrownBy(() -> approval.approveLeaveRequest(outside.getId(), leader.getId()))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
    }
}
