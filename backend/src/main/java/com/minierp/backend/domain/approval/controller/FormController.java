package com.minierp.backend.domain.approval.controller;

import com.minierp.backend.domain.approval.service.*;
import com.minierp.backend.domain.approval.dto.ApprovalFormResponseDto;
import com.minierp.backend.domain.approval.dto.ApprovalFormSummaryResponseDto;
import com.minierp.backend.domain.approval.dto.ApprovalLinePreviewResponseDto;
import com.minierp.backend.global.permission.*;
import com.minierp.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
import java.util.List;

@RestController
@RequestMapping("/api/v1/approval-forms")
@RequiredArgsConstructor
public class FormController {
    private final FormService service;
    private final ApprovalLineResolver resolver;
    private final Clock clock;
    public record Preview(@NotNull Long drafterId) {
    }

    private <T> ApiResponse<T> ok(T value) {
        return ApiResponse.success(clock, value);
    }

    @GetMapping
    public ApiResponse<List<ApprovalFormSummaryResponseDto>> getForms() {
        return ok(service.getForms());
    }

    @GetMapping("/{code}")
    @RequirePermission(menu = "APPROVAL_FORM", action = Action.VIEW)
    public ApiResponse<ApprovalFormResponseDto> getForm(@PathVariable String code) {
        return ok(service.getForm(code));
    }

    @PatchMapping("/{code}")
    @RequirePermission(menu = "APPROVAL_FORM", action = Action.UPDATE)
    public ApiResponse<ApprovalFormResponseDto> updateFormOptions(
            @PathVariable String code,
            @RequestBody FormService.Options body) {
        return ok(service.updateFormOptions(code, body));
    }

    @PutMapping("/{code}/steps")
    @RequirePermission(menu = "APPROVAL_FORM", action = Action.UPDATE)
    public ApiResponse<ApprovalFormResponseDto> updateFormSteps(
            @PathVariable String code,
            @RequestBody List<FormService.Step> body) {
        return ok(service.updateFormSteps(code, body));
    }

    @PostMapping("/{code}/line-preview")
    @RequirePermission(menu = "APPROVAL_FORM", action = Action.VIEW)
    public ApiResponse<ApprovalLinePreviewResponseDto> getFormLinePreview(
            @PathVariable String code,
            @Valid @RequestBody Preview body) {
        return ok(resolver.getLinePreview(code, body.drafterId(), null));
    }
}
