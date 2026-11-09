package com.minierp.backend.domain.project.dto;

public record ProjectPermissionsResponseDto(boolean canManage, boolean canCreateTask) {
}
