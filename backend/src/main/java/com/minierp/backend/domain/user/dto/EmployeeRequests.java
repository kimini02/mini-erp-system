package com.minierp.backend.domain.user.dto;

import jakarta.validation.constraints.*;
import com.minierp.backend.domain.user.entity.UserStatus;
import java.time.LocalDate;
import java.util.List;

public final class EmployeeRequests {
    public record Create(
            @NotBlank @Size(max = 50) String name,
            @NotBlank @Email @Size(max = 100) String email,
            @NotNull LocalDate hireDate,
            @NotNull Long departmentId,
            @NotNull Long positionId,
            @NotNull List<String> roleCodes) {
    }

    public record Patch(
            @Size(min = 1, max = 50) String name,
            @Email @Size(max = 100) String email,
            Long departmentId,
            Long positionId,
            @com.fasterxml.jackson.annotation.JsonFormat(
                    shape = com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING, pattern = "uuuu-MM-dd",
                    lenient = com.fasterxml.jackson.annotation.OptBoolean.FALSE) LocalDate hireDate) {
    }

    public record Status(@NotNull UserStatus status) {
    }

    public record Roles(@NotNull List<String> roleCodes) {
    }
}
