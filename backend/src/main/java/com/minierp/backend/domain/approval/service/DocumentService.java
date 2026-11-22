package com.minierp.backend.domain.approval.service;

import com.minierp.backend.domain.approval.dto.DocumentRequest;
import com.minierp.backend.domain.approval.dto.ApprovalDraftResponseDto;
import com.minierp.backend.domain.approval.dto.ApprovalDocumentResponseDto;
import com.minierp.backend.domain.approval.dto.ApprovalPersonResponseDto;
import com.minierp.backend.domain.approval.dto.DocumentRequest.ManualStep;
import com.minierp.backend.domain.approval.handler.ApprovalFormHandler;
import com.minierp.backend.domain.approval.model.*;
import com.minierp.backend.domain.notification.service.NotificationService;
import com.minierp.backend.global.exception.*;
import com.minierp.backend.domain.approval.repository.DocumentQueries;
import com.minierp.backend.domain.approval.repository.DocumentStore;
import com.minierp.backend.global.permission.PermissionAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class DocumentService {
    private final DocumentQueries queries;
    private final DocumentStore store;
    private final PermissionAccess access;
    private final ApprovalLineResolver resolver;
    private final List<ApprovalFormHandler> handlers;
    private final NotificationService notifications;
    private final Clock clock;
    private ApprovalFormHandler handler(String code) {
        return handlers.stream()
                .filter(formHandler -> formHandler.formCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.FORM_NOT_FOUND));
    }

    private Map<String, Object> raw(long id) {
        return queries.findDocument(id);
    }

    private ApprovalDocument model(Map<String, Object> document) {
        return new ApprovalDocument(number(document, "id"), number(document, "drafter_id"),
                number(document, "drafter_dept_id"),
                (String) document.get("form_code"));
    }

    private void own(Map<String, Object> document) {
        if (number(document, "drafter_id") != access.actor()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void state(Map<String, Object> document, String expected) {
        if (!expected.equals(document.get("status"))) {
            throw new BusinessException(ErrorCode.DOCUMENT_STATE_CONFLICT);
        }
    }

    private boolean canRead(long id, long actor) {
        return queries.countReadableDocument(id, actor) > 0;
    }

    private void lockUser(long id) {
        store.lockUser(id);
    }

    private Map<String, Object> lockDocument(long id) {
        store.lockDocument(id);
        return raw(id);
    }

    private List<Map<String, Object>> lines(long id) {
        return queries.findLines(id);
    }

    private List<ManualStep> manual(long id) {
        return lines(id)
                .stream()
                .map(lineRow -> new ManualStep(number(lineRow, "approver_id"), (String) lineRow.get("line_kind")))
                .toList();
    }

    private void storeLines(long id, List<ApprovalLineResolver.Resolved> resolved, boolean submitted) {
        boolean pending = false;
        for (var resolvedStep : resolved) {
            // Like NO_REFERENCE, a skipped rule without a person has no snapshot row to store.
            if (resolvedStep.skipReason() != null
                    && (resolvedStep.person() == null || resolvedStep.kind().equals("REFERENCE"))) {
                continue;
            }
            String status = null;
            if (resolvedStep.kind().equals("APPROVER")) {
                if (resolvedStep.skipReason() != null && submitted) {
                    status = "SKIPPED";
                } else if (submitted && !pending) {
                    status = "PENDING";
                    pending = true;
                } else {
                    status = "WAITING";
                }
            }
            var person = resolvedStep.person();
            store.insertLine(id, resolvedStep.order(), resolvedStep.kind(), person.get("id"), person.get("name"),
                    person.get("title"), person.get("department"),
                    resolvedStep.source(), resolvedStep.rule(), status, submitted ? resolvedStep.skipReason() : null);
        }
    }

    private long insertDraft(
            Map<String, Object> form,
            String title,
            long user,
            long dept,
            boolean manual,
            Long original) {
        return store.insertDraft(form.get("id"), user, dept, title, manual, original, LocalDateTime.now(clock));
    }

    private List<ApprovalLineResolver.Resolved> checkDraftLine(
            Map<String, Object> form,
            long user,
            long dept,
            List<ManualStep> manual) {
        // Rules are resolved only on submission. Explicit manual lines must name real active users.
        return manual == null ? List.of() : resolver.resolveLine(form, user, dept, manual);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApprovalDraftResponseDto createDocument(DocumentRequest request) {
        long actor = access.actor();
        var form = resolver.getForm(request.formCode());
        var user = resolver.getPerson(actor);
        handler(request.formCode()).checkFormat(request.body());
        var resolved = checkDraftLine(form, actor, number(user, "department_id"), request.line());
        lockUser(actor);
        long id = insertDraft(form, request.title(), actor, number(user, "department_id"), request.line() != null,
                null);
        var doc = new ApprovalDocument(id, actor, number(user, "department_id"), request.formCode());
        handler(request.formCode()).saveBody(doc, request.body());
        storeLines(id, resolved, false);
        return new ApprovalDraftResponseDto(id, "DRAFT");
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApprovalDocumentResponseDto updateDocument(long id, DocumentRequest request) {
        var initial = raw(id);
        own(initial);
        lockUser(number(initial, "drafter_id"));
        var document = lockDocument(id);
        state(document, "DRAFT");
        if (!request.formCode().equals(document.get("form_code"))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        var form = resolver.getForm(request.formCode());
        handler(request.formCode()).checkFormat(request.body());
        var resolved = checkDraftLine(form, number(document, "drafter_id"),
                number(document, "drafter_dept_id"),
                request.line());
        handler(request.formCode()).saveBody(model(document), request.body());
        store.deleteLines(id);
        storeLines(id, resolved, false);
        store.updateDraft(id, request.title(), request.line() != null, LocalDateTime.now(clock));
        return getDocument(id);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void deleteDocument(long id) {
        var document = raw(id);
        own(document);
        lockUser(number(document, "drafter_id"));
        document = lockDocument(id);
        state(document, "DRAFT");
        handler((String) document.get("form_code")).deleteBody(id);
        store.deleteLines(id);
        store.deleteDocument(id);
    }

    private String nextNumber(String prefix, int year) {
        store.insertNumberSequence(prefix, year);
        long sequence = number(store.lockNumberSequence(prefix, year), "last_seq") + 1;
        store.updateNumberSequence(prefix, year, sequence);
        return String.format("%s-%d-%04d", prefix, year, sequence);
    }

    private void history(long id, Long actor, String action, String comment) {
        store.insertHistory(id, actor, action, comment, LocalDateTime.now(clock));
    }

    private void transition(Map<String, Object> document, String status, Integer step, boolean completed) {
        if (store.updateTransition(document.get("id"), document.get("version"), status, step,
                completed ? LocalDateTime.now(clock) : null, LocalDateTime.now(clock)) != 1) {
            throw new BusinessException(ErrorCode.DOCUMENT_STATE_CONFLICT);
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApprovalDocumentResponseDto submitDocument(long id) {
        var initial = raw(id);
        own(initial);
        lockUser(number(initial, "drafter_id"));
        var current = raw(id);
        state(current, "DRAFT");
        var form = resolver.getForm((String) current.get("form_code"));
        var resolved = resolver.resolveLine(form, number(current, "drafter_id"), number(current, "drafter_dept_id"),
                bool(current, "manual_line") ? manual(id) : null);
        var formHandler = handler((String) current.get("form_code"));
        formHandler.validate(new ApprovalDraft(model(current)));
        String documentNumber = nextNumber((String) current.get("doc_prefix"), LocalDate.now(clock).getYear());
        var document = lockDocument(id);
        state(document, "DRAFT");
        store.deleteLines(id);
        storeLines(id, resolved, true);
        var pending = lines(id).stream().filter(lineRow -> "PENDING".equals(lineRow.get("status"))).findFirst();
        store.updateSubmission(id, documentNumber, LocalDateTime.now(clock));
        formHandler.onSubmitted(model(document));
        transition(document, pending.isPresent() ? "IN_PROGRESS" : "APPROVED",
                pending.map(lineRow -> ((Number) lineRow.get("step_order")).intValue()).orElse(null),
                pending.isEmpty());
        if (pending.isEmpty()) {
            formHandler.onApproved(model(document));
        }
        history(id, access.actor(), "SUBMIT", null);
        history(id, null, "LINE_GENERATED", "양식 결재선 스냅샷");
        notifySubmission(id, document, pending, resolved);
        return getDocument(id);
    }

    private void notifySubmission(
            long id,
            Map<String, Object> document,
            Optional<Map<String, Object>> pending,
            List<ApprovalLineResolver.Resolved> resolved) {
        if (pending.isPresent()) {
            notifications.store(number(pending.get(), "approver_id"), id, "APPROVAL_REQUESTED",
                    (String) document.get("title"));
        } else {
            notifications.store(number(document, "drafter_id"), id, "APPROVED", (String) document.get("title"));
        }
        for (var resolvedStep : resolved) {
            if (resolvedStep.kind().equals("REFERENCE") && resolvedStep.skipReason() == null) {
                notifications.store(number(resolvedStep.person(), "id"), id, "REFERENCED",
                        (String) document.get("title"));
            }
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApprovalDocumentResponseDto decideDocument(long id, boolean approve, String comment) {
        long actor = access.actor();
        var initial = raw(id);
        if (queries.countApprover(id, actor) == 0) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        if (!approve && (comment == null || comment.isBlank())) {
            throw new BusinessException(ErrorCode.REJECT_REASON_REQUIRED);
        }
        if (comment != null && comment.length() > 1000) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        lockUser(number(initial, "drafter_id"));
        var document = lockDocument(id);
        state(document, "IN_PROGRESS");
        var line = lines(id)
                .stream()
                .filter(lineRow -> "PENDING".equals(lineRow.get("status")) && number(lineRow, "approver_id") == actor)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_STATE_CONFLICT));
        if (store.updateAction(line.get("id"), approve ? "APPROVED" : "REJECTED", LocalDateTime.now(clock),
                comment) != 1) {
            throw new BusinessException(ErrorCode.DOCUMENT_STATE_CONFLICT);
        }
        var formHandler = handler((String) document.get("form_code"));
        var next = approve
                ? lines(id).stream().filter(lineRow -> "WAITING".equals(lineRow.get("status"))).findFirst()
                : Optional.< Map<String, Object>> empty();
        completeDecision(document, next, formHandler, approve, id);
        history(id, actor, approve ? "APPROVE" : "REJECT", comment);
        return getDocument(id);
    }

    private void completeDecision(
            Map<String, Object> document,
            Optional<Map<String, Object>> next,
            ApprovalFormHandler formHandler,
            boolean approve,
            long id) {
        if (next.isPresent()) {
            store.updatePendingLine(next.get().get("id"));
            transition(document, "IN_PROGRESS", ((Number) next.get().get("step_order")).intValue(), false);
            notifications.store(number(next.get(), "approver_id"), id, "APPROVAL_REQUESTED",
                    (String) document.get("title"));
        } else {
            transition(document, approve ? "APPROVED" : "REJECTED", null, true);
            if (approve) {
                formHandler.onApproved(model(document));
            } else {
                formHandler.onRejected(model(document));
            }
            notifications.store(number(document, "drafter_id"), id, approve ? "APPROVED" : "REJECTED",
                    (String) document.get("title"));
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApprovalDocumentResponseDto withdrawDocument(long id) {
        var initial = raw(id);
        own(initial);
        lockUser(number(initial, "drafter_id"));
        var document = lockDocument(id);
        var line = lines(id);
        if (line.stream().anyMatch(lineRow -> "APPROVED".equals(lineRow.get("status")))) {
            throw new BusinessException(ErrorCode.WITHDRAW_NOT_ALLOWED);
        }
        state(document, "IN_PROGRESS");
        transition(document, "WITHDRAWN", null, true);
        handler((String) document.get("form_code")).onWithdrawn(model(document));
        history(id, access.actor(), "WITHDRAW", null);
        line.stream()
                .filter(lineRow -> "PENDING".equals(lineRow.get("status")))
                .forEach(lineRow -> notifications.store(number(lineRow, "approver_id"), id, "WITHDRAWN",
                        (String) document.get("title")));
        return getDocument(id);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ApprovalDraftResponseDto resubmitDocument(long id) {
        var initial = raw(id);
        own(initial);
        lockUser(number(initial, "drafter_id"));
        var document = lockDocument(id);
        if (!Set.of("REJECTED", "WITHDRAWN").contains(document.get("status"))) {
            throw new BusinessException(ErrorCode.DOCUMENT_STATE_CONFLICT);
        }
        var form = resolver.getForm((String) document.get("form_code"));
        var user = resolver.getPerson(access.actor());
        var manual = bool(document, "manual_line") ? manual(id) : null;
        var resolved = checkDraftLine(form, access.actor(), number(user, "department_id"), manual);
        long newId = insertDraft(form, (String) document.get("title"), access.actor(), number(user, "department_id"),
                manual != null, id);
        handler((String) document.get("form_code"))
                .copyBody(id,
                        new ApprovalDocument(newId, access.actor(), number(user, "department_id"),
                                (String) document.get("form_code")));
        storeLines(newId, resolved, false);
        return new ApprovalDraftResponseDto(newId, "DRAFT");
    }

    public ApprovalDocumentResponseDto getDocument(long id) {
        var document = raw(id);
        long actor = access.actor();
        if (!canRead(id, actor)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        var line = lines(id);
        boolean pending = "IN_PROGRESS".equals(document.get("status"));
        boolean own = number(document, "drafter_id") == actor;
        var drafter = resolver.getPerson(number(document, "drafter_id"));
        var publicLine = line.stream()
                .filter(lineRow -> "APPROVER".equals(lineRow.get("line_kind")))
                .map(ApprovalDocumentResponseDto.Line::from)
                .toList();
        var history = queries.findHistory(id).stream().map(ApprovalDocumentResponseDto.History::from).toList();
        boolean canAct = pending
                && line.stream()
                        .anyMatch(lineRow -> "PENDING".equals(lineRow.get("status"))
                                && number(lineRow, "approver_id") == actor);
        return new ApprovalDocumentResponseDto(id, (String) document.get("doc_no"),
                ApprovalDocumentResponseDto.Form.from(document), (String) document.get("title"),
                (String) document.get("status"),
                ApprovalPersonResponseDto.from(drafter), (LocalDateTime) document.get("submitted_at"),
                (LocalDateTime) document.get("completed_at"), handler((String) document.get("form_code")).body(id),
                publicLine,
                line.stream()
                        .filter(lineRow -> "REFERENCE".equals(lineRow.get("line_kind")))
                        .map(ApprovalPersonResponseDto::fromSnapshot)
                        .toList(), history,
                new ApprovalDocumentResponseDto.MyActions(canAct, canAct,
                        own && pending && line.stream().noneMatch(lineRow -> "APPROVED".equals(lineRow.get("status"))),
                        own && Set.of("REJECTED", "WITHDRAWN").contains(document.get("status"))),
                document.get("resubmitted_from_id") == null ? null : number(document, "resubmitted_from_id"));
    }

}
