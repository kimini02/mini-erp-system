package com.minierp.backend.domain.approval.model;

public record ApprovalDocument(long id, long drafterId, long departmentId, String formCode) {
}
