package com.minierp.backend.domain.project.dto;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;
import com.fasterxml.jackson.annotation.JsonInclude;

public record ProjectDetailResponseDto(
        long id,
        String key,
        String title,
        String content,
        String status,
        String priority,
        LocalDate startDate,
        LocalDate endDate,
        ProjectPersonResponseDto leader,
        long memberCount,
        long taskCount,
        long doneCount,
        BigDecimal overdueCount,
        long progressPct,
        String myRole,
        List<ProjectPersonResponseDto> members,
        @JsonInclude(JsonInclude.Include.NON_NULL) ProjectPermissionsResponseDto permissions) {
    public static ProjectDetailResponseDto from(
            ProjectSummaryResponseDto summary,
            List<ProjectPersonResponseDto> members,
            ProjectPermissionsResponseDto permissions) {
        return new ProjectDetailResponseDto(summary.id(), summary.key(), summary.title(), summary.content(),
                summary.status(), summary.priority(), summary.startDate(), summary.endDate(), summary.leader(),
                summary.memberCount(), summary.taskCount(), summary.doneCount(), summary.overdueCount(),
                summary.progressPct(), summary.myRole(), members, permissions);
    }
}
