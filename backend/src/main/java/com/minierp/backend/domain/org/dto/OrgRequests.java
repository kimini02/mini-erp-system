package com.minierp.backend.domain.org.dto;

import jakarta.validation.constraints.*;
import java.time.*;

public final class OrgRequests {
    public record DepartmentCreate(
            @NotBlank @Size(max = 100) String name,
            @NotNull Long parentId,
            @NotNull Integer sortOrder) {
    }

    public record DepartmentPatch(@Size(min = 1, max = 100) String name, Integer sortOrder) {
    }

    public record Parent(Long parentId) {
    }

    public record Head(@NotNull Long userId) {
    }

    public record Schedule(Boolean useDefault, LocalTime startTime, LocalTime endTime, Integer lateGraceMin) {
    }

    public record PositionName(@NotBlank @Size(max = 100) String name) {
    }

    public record PositionOrder(@NotNull Long id, @NotNull Integer rankOrder) {
    }

    public record HolidayCreate(
            @NotNull LocalDate date,
            @NotBlank @Size(max = 100) String name,
            @NotNull Boolean substitute) {
    }

    public record HolidayPatch(@Size(min = 1, max = 100) String name, Boolean substitute) {
    }
}
