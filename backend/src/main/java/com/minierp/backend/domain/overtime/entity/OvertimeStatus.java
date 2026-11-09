package com.minierp.backend.domain.overtime.entity;

import lombok.Getter;

@Getter
public enum OvertimeStatus {
    DRAFT("임시 저장"),
    PENDING("결재 대기"),
    APPROVED("승인"),
    REJECTED("반려"),
    CANCELLED("취소");
    private final String displayName;
    OvertimeStatus(String displayName) {
        this.displayName = displayName;
    }
}
