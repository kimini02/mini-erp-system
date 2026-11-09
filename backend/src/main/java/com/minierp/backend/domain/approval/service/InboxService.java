package com.minierp.backend.domain.approval.service;

import com.minierp.backend.domain.approval.repository.InboxRepository;
import com.minierp.backend.domain.approval.dto.ApprovalInboxItemResponseDto;
import com.minierp.backend.domain.approval.dto.ApprovalInboxCountsResponseDto;
import com.minierp.backend.global.permission.PermissionAccess;
import com.minierp.backend.global.exception.BusinessException;
import com.minierp.backend.global.exception.ErrorCode;
import com.minierp.backend.global.response.PageResponseDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.number;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InboxService {
    private final InboxRepository repository;
    private final PermissionAccess access;

    public PageResponseDto<ApprovalInboxItemResponseDto> getInboxDocuments(
            String box,
            String form,
            String query,
            int page,
            int size) {
        long actor = access.actor();
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        var result = repository.search(actor, box, form, query, page, size);
        @SuppressWarnings("unchecked")
        var documents = (List<Map<String, Object>>) result.get("content");
        @SuppressWarnings("unchecked")
        var lines = (Map<Long, List<Map<String, Object>>>) result.get("lines");
        var content = documents.stream().map(document -> inboxItem(document, lines)).toList();
        return PageResponseDto.of(content, page, size, number(result, "total"));
    }

    private ApprovalInboxItemResponseDto inboxItem(
            Map<String, Object> document,
            Map<Long, List<Map<String, Object>>> lines) {
        var progress = progress(document, lines.getOrDefault(number(document, "id"), List.of()));
        return new ApprovalInboxItemResponseDto(number(document, "id"),
                (String) document.get("doc_no"), (String) document.get("code"),
                FormService.shortName((String) document.get("code")), (String) document.get("title"),
                ApprovalInboxItemResponseDto.Drafter.from(document),
                (LocalDateTime) document.get("submitted_at"), (LocalDateTime) document.get("updated_at"),
                progress.status(), progress.steps(), progress.currentApprover());
    }

    private Progress progress(Map<String, Object> document, List<Map<String, Object>> lines) {
        var progress = new ArrayList<String>();
        String status = (String) document.get("status");
        progress.add(status.equals("DRAFT") ? "CURRENT" : status.equals("WITHDRAWN") ? "WITHDRAWN" : "DONE");
        ApprovalInboxItemResponseDto.CurrentApprover current = null;
        for (var line : lines) {
            String state = (String) line.get("status");
            progress.add(switch (state) {
                case "APPROVED" -> "DONE";
                case "PENDING" -> status.equals("IN_PROGRESS")
                        ? "CURRENT"
                        : status.equals("WITHDRAWN") ? "WITHDRAWN" : "TODO";
                case "REJECTED" -> "REJECTED";
                case "SKIPPED" -> "SKIPPED";
                default -> "TODO";
            });
            if (state.equals("PENDING") && status.equals("IN_PROGRESS")) {
                current = ApprovalInboxItemResponseDto.CurrentApprover.from(line);
            }
        }
        return new Progress(status, progress, current);
    }

    private record Progress(
            String status,
            List<String> steps,
            ApprovalInboxItemResponseDto.CurrentApprover currentApprover) {
    }

    public ApprovalInboxCountsResponseDto getInboxCounts() {
        return ApprovalInboxCountsResponseDto.from(repository.counts(access.actor()));
    }
}
