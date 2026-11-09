package com.minierp.backend.domain.overtime.entity;

import com.minierp.backend.domain.user.entity.User;
import com.minierp.backend.global.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalTime;

@Entity
@Table(name = "overtime_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
public class OvertimeRequest extends BaseEntity {
    @Column(name = "document_id", nullable = false, unique = true)
    private Long documentId;
    @ManyToOne(fetch = FetchType.LAZY)
    @org.hibernate.annotations.JoinFormula("(select d.drafter_id from approval_documents d where d.id=document_id)")
    private User requester;
    @ManyToOne(fetch = FetchType.LAZY)
    @org.hibernate.annotations.JoinFormula(
            "(select l.approver_id from approval_lines l where l.document_id=document_id and l.line_kind='APPROVER' order by l.step_order desc limit 1)")
    private User approver;
    @Column(name = "overtime_date", nullable = false)
    private LocalDate overtimeDate;
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
    @Column(name = "reason", length = 500)
    private String reason;
    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.Formula(
            "(select case d.status when 'IN_PROGRESS' then 'PENDING' when 'WITHDRAWN' then 'CANCELLED' else d.status end from approval_documents d where d.id=document_id)")
    private OvertimeStatus status = OvertimeStatus.PENDING;
    @Builder
    public OvertimeRequest(
            User requester,
            LocalDate overtimeDate,
            LocalTime startTime,
            LocalTime endTime,
            String reason) {
        this.requester = requester;
        this.overtimeDate = overtimeDate;
        this.startTime = startTime;
        this.endTime = endTime;
        this.reason = reason;
    }

    public void approve(User approver) {
        if (this.status != OvertimeStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 요청입니다.");
        }
        this.approver = approver;
        this.status = OvertimeStatus.APPROVED;
    }

    public void reject(User approver) {
        if (this.status != OvertimeStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 요청입니다.");
        }
        this.approver = approver;
        this.status = OvertimeStatus.REJECTED;
    }

    public void cancel(User requester) {
        if (this.status != OvertimeStatus.PENDING) {
            throw new IllegalStateException("이미 처리된 요청입니다.");
        }
        if (requester == null || !this.requester.getId().equals(requester.getId())) {
            throw new IllegalArgumentException("본인 신청 건만 취소할 수 있습니다.");
        }
        this.status = OvertimeStatus.CANCELLED;
    }
}
