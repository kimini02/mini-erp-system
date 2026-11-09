package com.minierp.backend.domain.task.dto;

import com.minierp.backend.domain.project.dto.*;
import java.util.*;

public record TaskBoardResponseDto(ProjectSummaryResponseDto project, List<ProjectPersonResponseDto> members,
        ProjectPermissionsResponseDto permissions, List<Column> columns) {
    public record Column(String status, List<TaskCardResponseDto> tasks) {
    }
}
