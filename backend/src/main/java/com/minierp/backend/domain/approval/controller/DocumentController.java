package com.minierp.backend.domain.approval.controller;

import com.minierp.backend.domain.approval.dto.DocumentRequest;
import com.minierp.backend.domain.approval.dto.ApprovalDraftResponseDto;
import com.minierp.backend.domain.approval.dto.ApprovalDocumentResponseDto;
import com.minierp.backend.domain.approval.dto.ApprovalLinePreviewResponseDto;
import com.minierp.backend.domain.approval.dto.DocumentRequest.ManualStep;
import com.minierp.backend.domain.approval.service.*;
import com.minierp.backend.global.permission.*;
import com.minierp.backend.global.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;
import java.util.List;

@RestController
@RequestMapping("/api/v1/approval-documents")
@RequiredArgsConstructor
public class DocumentController {
    private final DocumentService service;
    private final ApprovalLineResolver resolver;
    private final PermissionAccess access;
    private final Clock clock;
    public record Preview(@NotBlank String formCode, List<@Valid ManualStep> line) {
    }

    public record Comment(String comment) {
    }

    private <T> ApiResponse<T> ok(T data) {
        return ApiResponse.success(clock, data);
    }

    @PostMapping("/line-preview")
    @RequirePermission(menu = "APPROVAL_DRAFT", action = Action.CREATE)
    public ApiResponse<ApprovalLinePreviewResponseDto> getLinePreview(@Valid @RequestBody Preview body) {
        return ok(resolver.getLinePreview(body.formCode(), access.actor(), body.line()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(menu = "APPROVAL_DRAFT", action = Action.CREATE)
    public ApiResponse<ApprovalDraftResponseDto> createDocument(@Valid @RequestBody DocumentRequest body) {
        return ok(service.createDocument(body));
    }

    @PutMapping("/{id}")
    public ApiResponse<ApprovalDocumentResponseDto> updateDocument(
            @PathVariable long id,
            @Valid @RequestBody DocumentRequest body) {
        return ok(service.updateDocument(id, body));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDocument(@PathVariable long id) {
        service.deleteDocument(id);
        return ok(null);
    }

    @GetMapping("/{id}")
    public ApiResponse<ApprovalDocumentResponseDto> getDocument(@PathVariable long id) {
        return ok(service.getDocument(id));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<ApprovalDocumentResponseDto> submitDocument(@PathVariable long id) {
        return ok(service.submitDocument(id));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<ApprovalDocumentResponseDto> approveDocument(
            @PathVariable long id,
            @RequestBody(required = false) Comment body) {
        return ok(service.decideDocument(id, true, body == null ? null : body.comment()));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<ApprovalDocumentResponseDto> rejectDocument(
            @PathVariable long id,
            @RequestBody(required = false) Comment body) {
        return ok(service.decideDocument(id, false, body == null ? null : body.comment()));
    }

    @PostMapping("/{id}/withdraw")
    public ApiResponse<ApprovalDocumentResponseDto> withdrawDocument(@PathVariable long id) {
        return ok(service.withdrawDocument(id));
    }

    @PostMapping("/{id}/resubmit")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ApprovalDraftResponseDto> resubmitDocument(@PathVariable long id) {
        return ok(service.resubmitDocument(id));
    }
}
