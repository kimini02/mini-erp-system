package com.minierp.backend.domain.approval.service;

import com.minierp.backend.domain.approval.dto.DocumentRequest;
import com.minierp.backend.domain.approval.dto.DocumentRequest.ManualStep;
import com.minierp.backend.domain.approval.handler.ApprovalFormHandler;
import com.minierp.backend.domain.approval.model.*;
import com.minierp.backend.domain.notification.service.NotificationService;
import com.minierp.backend.global.exception.*;
import com.minierp.backend.global.persistence.SqlStore;
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
    private final SqlStore db;
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
        return db.one(
                "SELECT d.*,f.code AS form_code,f.name AS form_name,f.doc_prefix FROM approval_documents d JOIN approval_forms f ON f.id=d.form_id WHERE d.id=:id",
                map("id", id), ErrorCode.DOCUMENT_NOT_FOUND);
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
        return db.count("""
            SELECT COUNT(*) FROM approval_documents d WHERE d.id=:id AND (d.drafter_id=:actor
            OR EXISTS(SELECT 1 FROM approval_lines l WHERE l.document_id=d.id AND l.approver_id=:actor)
            OR EXISTS(SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=:actor AND r.code='CHIEF'))
            """, map("id", id, "actor", actor)) > 0;
    }

    private void lockUser(long id) {
        db.one("SELECT id FROM users WHERE id=:id FOR UPDATE", map("id", id), ErrorCode.USER_NOT_FOUND);
    }

    private Map<String, Object> lockDocument(long id) {
        db.one("SELECT id FROM approval_documents WHERE id=:id FOR UPDATE", map("id", id),
                ErrorCode.DOCUMENT_NOT_FOUND);
        return raw(id);
    }

    private List<Map<String, Object>> lines(long id) {
        return db.rows("SELECT * FROM approval_lines WHERE document_id=:id ORDER BY step_order", map("id", id));
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
            db.update("""
                INSERT INTO approval_lines(document_id,step_order,line_kind,approver_id,approver_name,approver_title,approver_dept,source,rule_label,status,skip_reason)
                VALUES(:id,:order,:kind,:user,:name,:title,:dept,:source,:rule,:status,:skip)
                """, map("id", id, "order", r.order(), "kind", r.kind(), "user", p.get("id"), "name", p.get("name"),
                    "title", p.get("title"), "dept", p.get("department"), "source", r.source(), "rule", r.rule(),
                            "status", status, "skip", submitted ? r.skipReason() : null));
        }
    }

    private long insertDraft(
            Map<String, Object> form,
            String title,
            long user,
            long dept,
            boolean manual,
            Long original) {
        return db.insert("""
            INSERT INTO approval_documents(form_id,drafter_id,drafter_dept_id,title,status,version,manual_line,resubmitted_from_id,created_at,updated_at)
            VALUES(:form,:user,:dept,:title,'DRAFT',0,:manual,:original,:now,:now)
            """, map("form", form.get("id"), "user", user, "dept", dept, "title", title, "manual", manual, "original",
                original, "now", LocalDateTime.now(clock)));
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
        db.update("DELETE FROM approval_lines WHERE document_id=:id", map("id", id));
        storeLines(id, resolved, false);
        db.update(
                "UPDATE approval_documents SET title=:title,manual_line=:manual,version=version+1,updated_at=:now WHERE id=:id",
                map("id", id, "title", request.title(), "manual", request.line() != null,
                        "now", LocalDateTime.now(clock)));
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
        db.update("DELETE FROM approval_lines WHERE document_id=:id", map("id", id));
        db.update("DELETE FROM approval_documents WHERE id=:id", map("id", id));
    }

    private String nextNumber(String prefix, int year) {
        db.update(
                "INSERT INTO doc_no_sequences(prefix,doc_year,last_seq) VALUES(:prefix,:year,0) ON DUPLICATE KEY UPDATE last_seq=last_seq",
                map("prefix", prefix, "year", year));
        long seq = number(
                db.one("SELECT last_seq FROM doc_no_sequences WHERE prefix=:prefix AND doc_year=:year FOR UPDATE",
                        map("prefix", prefix, "year", year), ErrorCode.RESOURCE_NOT_FOUND), "last_seq") + 1;
        db.update("UPDATE doc_no_sequences SET last_seq=:seq WHERE prefix=:prefix AND doc_year=:year",
                map("prefix", prefix, "year", year, "seq", seq));
        return String.format("%s-%d-%04d", prefix, year, seq);
    }

    private void history(long id, Long actor, String action, String comment) {
        db.update(
                "INSERT INTO approval_histories(document_id,actor_id,action,comment,created_at) VALUES(:id,:actor,:action,:comment,:now)",
                map("id", id, "actor", actor, "action", action, "comment", comment, "now", LocalDateTime.now(clock)));
    }

    private void transition(Map<String, Object> d, String status, Integer step, boolean completed) {
        if (db.update(
                "UPDATE approval_documents SET status=:status,current_step=:step,completed_at=:completed,version=version+1,updated_at=:now WHERE id=:id AND version=:version",
                map("id", d.get("id"), "version", d.get("version"), "status", status, "step", step,
                        "completed", completed ? LocalDateTime.now(clock) : null,
                        "now", LocalDateTime.now(clock))) != 1) {
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
        db.update("DELETE FROM approval_lines WHERE document_id=:id", map("id", id));
        storeLines(id, resolved, true);
        var pending = lines(id).stream().filter(l -> "PENDING".equals(l.get("status"))).findFirst();
        db.update("UPDATE approval_documents SET doc_no=:no,submitted_at=:now WHERE id=:id",
                map("id", id, "no", no, "now", LocalDateTime.now(clock)));
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
        if (db.count(
                "SELECT COUNT(*) FROM approval_lines WHERE document_id=:id AND approver_id=:actor AND line_kind='APPROVER'",
                map("id", id, "actor", actor)) == 0) {
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
        if (db.update(
                "UPDATE approval_lines SET status=:status,acted_at=:now,comment=:comment WHERE id=:id AND status='PENDING'",
                map("id", line.get("id"), "status", approve ? "APPROVED" : "REJECTED", "now", LocalDateTime.now(clock),
                        "comment", comment)) != 1) {
            throw new BusinessException(ErrorCode.DOCUMENT_STATE_CONFLICT);
        }
        var h = handler((String) d.get("form_code"));
        var next = approve ? lines(id)
                .stream()
                .filter(l -> "WAITING".equals(l.get("status")))
                .findFirst() : Optional.< Map<String, Object>> empty();
        if (next.isPresent()) {
            db.update("UPDATE approval_lines SET status='PENDING' WHERE id=:id AND status='WAITING'",
                    map("id", next.get().get("id")));
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
        var history = db.rows(
                "SELECT h.action,h.actor_id,u.user_name,h.comment,h.created_at FROM approval_histories h LEFT JOIN users u ON u.id=h.actor_id WHERE h.document_id=:id ORDER BY h.id",
                map("id", id))
                .stream()
                .map(h -> map("action", h.get("action"),
                        "actor", h.get("actor_id") == null
                                ? null
                                : map("id", h.get("actor_id"), "name", h.get("user_name")),
                        "comment", h.get("comment"), "at", h.get("created_at")))
                .toList();
        boolean canAct = pending && line.stream()
                .anyMatch(l -> "PENDING".equals(l.get("status")) && number(l, "approver_id") == actor);
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
                        "canWithdraw", own && pending && line.stream()
                                .noneMatch(l -> "APPROVED".equals(l.get("status"))),
                        "canResubmit", own && Set.of("REJECTED", "WITHDRAWN").contains(d.get("status"))),
                "resubmittedFromId", d.get("resubmitted_from_id"));
    }

    private static Map<String, Object> snapshot(Map<String, Object> l) {
        return map("id", l.get("approver_id"), "name", l.get("approver_name"), "title", l.get("approver_title"),
                "department", l.get("approver_dept"));
    }
}
