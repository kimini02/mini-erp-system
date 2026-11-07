package com.minierp.backend.domain.approval.service;

import com.minierp.backend.domain.approval.dto.DocumentRequest;
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
                .filter(h -> h.formCode().equals(code))
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.FORM_NOT_FOUND));
    }

    private Map<String, Object> raw(long id) {
        return queries.findDocument(id);
    }

    private ApprovalDocument model(Map<String, Object> d) {
        return new ApprovalDocument(number(d, "id"), number(d, "drafter_id"), number(d, "drafter_dept_id"),
                (String) d.get("form_code"));
    }

    private void own(Map<String, Object> d) {
        if (number(d, "drafter_id") != access.actor()) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    private void state(Map<String, Object> d, String expected) {
        if (!expected.equals(d.get("status"))) {
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
                .map(r -> new ManualStep(number(r, "approver_id"), (String) r.get("line_kind")))
                .toList();
    }

    private void storeLines(long id, List<ApprovalLineResolver.Resolved> resolved, boolean submitted) {
        boolean pending = false;
        for (var r : resolved) {
            if (r.kind().equals("REFERENCE") && r.skipReason() != null) {
                continue;
            }
            String status = null;
            if (r.kind().equals("APPROVER")) {
                if (r.skipReason() != null && submitted) {
                    status = "SKIPPED";
                } else if (submitted && !pending) {
                    status = "PENDING";
                    pending = true;
                } else {
                    status = "WAITING";
                }
            }
            var p = r.person();
            store.insertLine(id, r.order(), r.kind(), p.get("id"), p.get("name"), p.get("title"), p.get("department"),
                    r.source(), r.rule(), status, submitted ? r.skipReason() : null);
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
        return manual == null ? List.of() : resolver.resolve(form, user, dept, manual);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> create(DocumentRequest request) {
        long actor = access.actor();
        var form = resolver.form(request.formCode());
        var user = resolver.person(actor);
        handler(request.formCode()).checkFormat(request.body());
        var resolved = checkDraftLine(form, actor, number(user, "department_id"), request.line());
        lockUser(actor);
        long id = insertDraft(form, request.title(), actor, number(user, "department_id"), request.line() != null,
                null);
        var doc = new ApprovalDocument(id, actor, number(user, "department_id"), request.formCode());
        handler(request.formCode()).saveBody(doc, request.body());
        storeLines(id, resolved, false);
        return map("id", id, "status", "DRAFT");
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> update(long id, DocumentRequest request) {
        var initial = raw(id);
        own(initial);
        lockUser(number(initial, "drafter_id"));
        var d = lockDocument(id);
        state(d, "DRAFT");
        if (!request.formCode().equals(d.get("form_code"))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        var form = resolver.form(request.formCode());
        handler(request.formCode()).checkFormat(request.body());
        var resolved = checkDraftLine(form, number(d, "drafter_id"), number(d, "drafter_dept_id"), request.line());
        handler(request.formCode()).saveBody(model(d), request.body());
        store.deleteLines(id);
        storeLines(id, resolved, false);
        store.updateDraft(id, request.title(), request.line() != null, LocalDateTime.now(clock));
        return detail(id);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void delete(long id) {
        var d = raw(id);
        own(d);
        lockUser(number(d, "drafter_id"));
        d = lockDocument(id);
        state(d, "DRAFT");
        handler((String) d.get("form_code")).deleteBody(id);
        store.deleteLines(id);
        store.deleteDocument(id);
    }

    private String nextNumber(String prefix, int year) {
        store.insertNumberSequence(prefix, year);
        long seq = number(store.lockNumberSequence(prefix, year), "last_seq") + 1;
        store.updateNumberSequence(prefix, year, seq);
        return String.format("%s-%d-%04d", prefix, year, seq);
    }

    private void history(long id, Long actor, String action, String comment) {
        store.insertHistory(id, actor, action, comment, LocalDateTime.now(clock));
    }

    private void transition(Map<String, Object> d, String status, Integer step, boolean completed) {
        if (store.updateTransition(d.get("id"), d.get("version"), status, step,
                completed ? LocalDateTime.now(clock) : null, LocalDateTime.now(clock)) != 1) {
            throw new BusinessException(ErrorCode.DOCUMENT_STATE_CONFLICT);
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> submit(long id) {
        var initial = raw(id);
        own(initial);
        lockUser(number(initial, "drafter_id"));
        var current = raw(id);
        state(current, "DRAFT");
        var form = resolver.form((String) current.get("form_code"));
        var resolved = resolver.resolve(form, number(current, "drafter_id"), number(current, "drafter_dept_id"),
                bool(current, "manual_line") ? manual(id) : null);
        var h = handler((String) current.get("form_code"));
        h.validate(new ApprovalDraft(model(current)));
        String no = nextNumber((String) current.get("doc_prefix"), LocalDate.now(clock).getYear());
        var d = lockDocument(id);
        state(d, "DRAFT");
        store.deleteLines(id);
        storeLines(id, resolved, true);
        var pending = lines(id).stream().filter(l -> "PENDING".equals(l.get("status"))).findFirst();
        store.updateSubmission(id, no, LocalDateTime.now(clock));
        h.onSubmitted(model(d));
        transition(d, pending.isPresent() ? "IN_PROGRESS" : "APPROVED",
                pending.map(l -> ((Number) l.get("step_order")).intValue()).orElse(null), pending.isEmpty());
        if (pending.isEmpty()) {
            h.onApproved(model(d));
        }
        history(id, access.actor(), "SUBMIT", null);
        history(id, null, "LINE_GENERATED", "양식 결재선 스냅샷");
        if (pending.isPresent()) {
            notifications.store(number(pending.get(), "approver_id"), id, "APPROVAL_REQUESTED",
                    (String) d.get("title"));
        } else {
            notifications.store(number(d, "drafter_id"), id, "APPROVED", (String) d.get("title"));
        }
        for (var r : resolved) {
            if (r.kind().equals("REFERENCE") && r.skipReason() == null) {
                notifications.store(number(r.person(), "id"), id, "REFERENCED", (String) d.get("title"));
            }
        }
        return detail(id);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> act(long id, boolean approve, String comment) {
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
        var d = lockDocument(id);
        state(d, "IN_PROGRESS");
        var line = lines(id)
                .stream()
                .filter(l -> "PENDING".equals(l.get("status")) && number(l, "approver_id") == actor)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_STATE_CONFLICT));
        if (store.updateAction(line.get("id"), approve ? "APPROVED" : "REJECTED", LocalDateTime.now(clock),
                comment) != 1) {
            throw new BusinessException(ErrorCode.DOCUMENT_STATE_CONFLICT);
        }
        var h = handler((String) d.get("form_code"));
        var next = approve
                ? lines(id).stream().filter(l -> "WAITING".equals(l.get("status"))).findFirst()
                : Optional.< Map<String, Object>> empty();
        if (next.isPresent()) {
            store.updatePendingLine(next.get().get("id"));
            transition(d, "IN_PROGRESS", ((Number) next.get().get("step_order")).intValue(), false);
            notifications.store(number(next.get(), "approver_id"), id, "APPROVAL_REQUESTED", (String) d.get("title"));
        } else {
            transition(d, approve ? "APPROVED" : "REJECTED", null, true);
            if (approve) {
                h.onApproved(model(d));
            } else {
                h.onRejected(model(d));
            }
            notifications.store(number(d, "drafter_id"), id, approve ? "APPROVED" : "REJECTED",
                    (String) d.get("title"));
        }
        history(id, actor, approve ? "APPROVE" : "REJECT", comment);
        return detail(id);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> withdraw(long id) {
        var initial = raw(id);
        own(initial);
        lockUser(number(initial, "drafter_id"));
        var d = lockDocument(id);
        var line = lines(id);
        if (line.stream().anyMatch(l -> "APPROVED".equals(l.get("status")))) {
            throw new BusinessException(ErrorCode.WITHDRAW_NOT_ALLOWED);
        }
        state(d, "IN_PROGRESS");
        transition(d, "WITHDRAWN", null, true);
        handler((String) d.get("form_code")).onWithdrawn(model(d));
        history(id, access.actor(), "WITHDRAW", null);
        line.stream()
                .filter(l -> "PENDING".equals(l.get("status")))
                .forEach(l -> notifications.store(number(l, "approver_id"), id, "WITHDRAWN", (String) d.get("title")));
        return detail(id);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> resubmit(long id) {
        var initial = raw(id);
        own(initial);
        lockUser(number(initial, "drafter_id"));
        var d = lockDocument(id);
        if (!Set.of("REJECTED", "WITHDRAWN").contains(d.get("status"))) {
            throw new BusinessException(ErrorCode.DOCUMENT_STATE_CONFLICT);
        }
        var form = resolver.form((String) d.get("form_code"));
        var user = resolver.person(access.actor());
        var manual = bool(d, "manual_line") ? manual(id) : null;
        var resolved = checkDraftLine(form, access.actor(), number(user, "department_id"), manual);
        long newId = insertDraft(form, (String) d.get("title"), access.actor(), number(user, "department_id"),
                manual != null, id);
        handler((String) d.get("form_code"))
                .copyBody(id,
                        new ApprovalDocument(newId, access.actor(), number(user, "department_id"),
                                (String) d.get("form_code")));
        storeLines(newId, resolved, false);
        return map("id", newId, "status", "DRAFT");
    }

    public Map<String, Object> detail(long id) {
        var d = raw(id);
        long actor = access.actor();
        if (!canRead(id, actor)) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        var line = lines(id);
        boolean pending = "IN_PROGRESS".equals(d.get("status"));
        boolean own = number(d, "drafter_id") == actor;
        var drafter = resolver.person(number(d, "drafter_id"));
        var publicLine = line.stream()
                .filter(l -> "APPROVER".equals(l.get("line_kind")))
                .map(l -> map("order", l.get("step_order"), "kind", l.get("line_kind"), "approver", snapshot(l),
                        "source", l.get("source"), "rule", l.get("rule_label"), "status", l.get("status"),
                        "skipReason", l.get("skip_reason"), "actedAt", l.get("acted_at"), "comment", l.get("comment")))
                .toList();
        var history = queries.findHistory(id)
                .stream()
                .map(h -> map("action", h.get("action"),
                        "actor", h.get("actor_id") == null
                                ? null
                                : map("id", h.get("actor_id"), "name", h.get("user_name")),
                        "comment", h.get("comment"), "at", h.get("created_at")))
                .toList();
        boolean canAct = pending
                && line.stream().anyMatch(l -> "PENDING".equals(l.get("status")) && number(l, "approver_id") == actor);
        return map("id", id, "docNo", d.get("doc_no"),
                "form", map("code", d.get("form_code"), "name", d.get("form_name")), "title", d.get("title"),
                "status", d.get("status"),
                "drafter", map("id", drafter.get("id"), "name", drafter.get("name"), "title", drafter.get("title"),
                        "department", drafter.get("department")), "submittedAt", d.get("submitted_at"),
                "completedAt", d.get("completed_at"), "body", handler((String) d.get("form_code")).body(id),
                "line", publicLine,
                "references", line.stream()
                        .filter(l -> "REFERENCE".equals(l.get("line_kind")))
                        .map(DocumentService::snapshot)
                        .toList(), "history", history,
                "myActions", map("canApprove", canAct, "canReject", canAct,
                        "canWithdraw", own && pending
                                && line.stream().noneMatch(l -> "APPROVED".equals(l.get("status"))),
                        "canResubmit", own && Set.of("REJECTED", "WITHDRAWN").contains(d.get("status"))),
                "resubmittedFromId", d.get("resubmitted_from_id"));
    }

    private static Map<String, Object> snapshot(Map<String, Object> l) {
        return map("id", l.get("approver_id"), "name", l.get("approver_name"), "title", l.get("approver_title"),
                "department", l.get("approver_dept"));
    }
}
