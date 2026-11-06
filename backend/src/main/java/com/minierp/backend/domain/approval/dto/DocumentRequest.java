package com.minierp.backend.domain.approval.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record DocumentRequest(
        @NotBlank String formCode,
        @NotBlank @Size(max = 255) String title,
        @NotNull JsonNode body,
        List<@Valid ManualStep> line) {
    public record ManualStep(@NotNull @Positive Long userId, @NotBlank String lineKind) {
    }
}
