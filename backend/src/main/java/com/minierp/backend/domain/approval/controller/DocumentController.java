package com.minierp.backend.domain.approval.controller;

import com.minierp.backend.domain.approval.dto.DocumentRequest;
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

    private ApiResponse<?> ok(Object data) {
        return ApiResponse.success(clock, data);
    }

    @PostMapping("/line-preview")
    @RequirePermission(menu = "APPROVAL_DRAFT", action = Action.CREATE)
    public ApiResponse<?> preview(@Valid @RequestBody Preview body) {
        return ok(resolver.preview(body.formCode(), access.actor(), body.line()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission(menu = "APPROVAL_DRAFT", action = Action.CREATE)
    public ApiResponse<?> create(@Valid @RequestBody DocumentRequest body) {
        return ok(service.create(body));
    }

    @PutMapping("/{id}")
    public ApiResponse<?> update(@PathVariable long id, @Valid @RequestBody DocumentRequest body) {
        return ok(service.update(id, body));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<?> delete(@PathVariable long id) {
        service.delete(id);
        return ok(null);
    }

    @GetMapping("/{id}")
    public ApiResponse<?> detail(@PathVariable long id) {
        return ok(service.detail(id));
    }

    @PostMapping("/{id}/submit")
    public ApiResponse<?> submit(@PathVariable long id) {
        return ok(service.submit(id));
    }

    @PostMapping("/{id}/approve")
    public ApiResponse<?> approve(@PathVariable long id, @RequestBody(required = false) Comment body) {
        return ok(service.act(id, true, body == null ? null : body.comment()));
    }

    @PostMapping("/{id}/reject")
    public ApiResponse<?> reject(@PathVariable long id, @RequestBody(required = false) Comment body) {
        return ok(service.act(id, false, body == null ? null : body.comment()));
    }

    @PostMapping("/{id}/withdraw")
    public ApiResponse<?> withdraw(@PathVariable long id) {
        return ok(service.withdraw(id));
    }

    @PostMapping("/{id}/resubmit")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<?> resubmit(@PathVariable long id) {
        return ok(service.resubmit(id));
    }
}
