package com.minierp.backend.domain.task.dto;

import com.minierp.backend.domain.project.dto.*;
import java.time.*;
import java.util.*;

public record TaskCardResponseDto(
        long id,
        String key,
        String title,
        String status,
        String priority,
        LocalDate endDate,
        long overdueDays,
        List<ProjectPersonResponseDto> assignees,
        long position,
        long version,
        boolean canMove) {
}
