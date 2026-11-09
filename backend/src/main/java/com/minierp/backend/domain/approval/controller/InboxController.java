package com.minierp.backend.domain.approval.controller;

import com.minierp.backend.domain.approval.service.InboxService;
import com.minierp.backend.domain.approval.dto.ApprovalInboxItemResponseDto;
import com.minierp.backend.domain.approval.dto.ApprovalInboxCountsResponseDto;
import com.minierp.backend.global.response.PageResponseDto;
import com.minierp.backend.global.permission.*;
import com.minierp.backend.global.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.Clock;

@RestController
@RequestMapping("/api/v1/approval-inbox")
@RequiredArgsConstructor
public class InboxController {
    private final InboxService service;
    private final Clock clock;
    @GetMapping
    @RequirePermission(menu = "APPROVAL_INBOX", action = Action.VIEW)
    public ApiResponse<PageResponseDto<ApprovalInboxItemResponseDto>> getInboxDocuments(
            @RequestParam(defaultValue = "todo") String box,
            @RequestParam(required = false) String formCode,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(clock, service.getInboxDocuments(box, formCode, query, page, size));
    }

    @GetMapping("/counts")
    @RequirePermission(menu = "APPROVAL_INBOX", action = Action.VIEW)
    public ApiResponse<ApprovalInboxCountsResponseDto> getInboxCounts() {
        return ApiResponse.success(clock, service.getInboxCounts());
    }
}
