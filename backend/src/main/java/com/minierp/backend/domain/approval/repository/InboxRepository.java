package com.minierp.backend.domain.approval.repository;

import com.minierp.backend.domain.approval.service.FormService;
import com.minierp.backend.domain.user.service.EmployeeService;
import com.minierp.backend.global.exception.*;
import com.minierp.backend.global.persistence.SqlStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;
import java.util.stream.Collectors;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Repository
@RequiredArgsConstructor
public class InboxRepository {
    private final SqlStore db;
    private static final List<String> BOXES = List.of("todo", "in-progress", "done", "mine", "drafts", "reference");
    private String condition(String box) {
        return switch (box) {
            case "todo" ->
                    "d.status='IN_PROGRESS' AND EXISTS(SELECT 1 FROM approval_lines l WHERE l.approver_id=:actor AND l.status='PENDING' AND l.document_id=d.id AND l.line_kind='APPROVER')";
            case "in-progress" ->
                    "d.status='IN_PROGRESS' AND EXISTS(SELECT 1 FROM approval_lines l WHERE l.approver_id=:actor AND l.status='APPROVED' AND l.document_id=d.id AND l.line_kind='APPROVER')";
            case "done" ->
                    "d.status IN ('APPROVED','REJECTED') AND EXISTS(SELECT 1 FROM approval_lines l WHERE l.approver_id=:actor AND l.document_id=d.id AND l.line_kind='APPROVER')";
            case "mine" -> "d.drafter_id=:actor AND d.status<>'DRAFT'";
            case "drafts" -> "d.drafter_id=:actor AND d.status='DRAFT'";
            case "reference" ->
                    "EXISTS(SELECT 1 FROM approval_lines l WHERE l.approver_id=:actor AND l.document_id=d.id AND l.line_kind='REFERENCE')";
            default -> throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        };
    }

    public String selectionSql(String box, String formCode, String q) {
        String where = condition(box);
        if (formCode != null && !formCode.isBlank()) {
            where += " AND f.code=:form";
        }
        if (q != null && !q.isBlank()) {
            where += " AND (d.title LIKE :q ESCAPE '!' OR d.doc_no LIKE :q ESCAPE '!')";
        }
        return
                " FROM approval_documents d JOIN approval_forms f ON f.id=d.form_id JOIN users u ON u.id=d.drafter_id JOIN positions p ON p.id=u.position_id JOIN departments dep ON dep.id=d.drafter_dept_id WHERE "
                + where;
    }

    public String pageSql(String box, String formCode, String q) {
        return
                "SELECT d.id,d.doc_no,d.title,d.status,d.submitted_at,d.updated_at,f.code,u.user_name,p.name AS position_name,dep.name AS department_name"
                + selectionSql(box, formCode,
                q) + " ORDER BY " + (box.equals("drafts") ? "d.updated_at" : "d.submitted_at")
                        + " DESC,d.id DESC LIMIT :size OFFSET :offset";
    }

    public Map<String, Object> search(long actor, String box, String formCode, String q, int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        var args = map("actor", actor, "form", formCode,
                "q", q == null ? null : "%" + q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%",
                "size", size, "offset", (long) page * size);
        long total = db.count("SELECT COUNT(*)" + selectionSql(box, formCode, q), args);
        var documents = db.rows(pageSql(box, formCode, q), args);
        if (documents.isEmpty()) {
            return EmployeeService.page(List.of(), page, size, total);
        }
        var ids = documents.stream().map(d -> number(d, "id")).toList();
        var lines = db.rows(
                "SELECT document_id,approver_name,approver_title,status FROM approval_lines WHERE document_id IN (:ids) AND line_kind='APPROVER' ORDER BY document_id,step_order",
                map("ids", ids))
                .stream()
                .collect(Collectors.groupingBy(l -> number(l, "document_id")));
        var content = documents.stream().map(d -> {
            var progress = new ArrayList<String>();
            String status = (String) d.get("status");
            progress.add(status.equals("DRAFT") ? "CURRENT" : status.equals("WITHDRAWN") ? "WITHDRAWN" : "DONE");
            Object current = null;
            for (var l : lines.getOrDefault(number(d, "id"), List.of())) {
                String state = (String) l.get("status");
                progress.add(switch (state) {
                    case "APPROVED" -> "DONE";
                    case "PENDING" -> status.equals("IN_PROGRESS") ? "CURRENT" : status.equals("WITHDRAWN")
                            ? "WITHDRAWN" : "TODO";
                    case "REJECTED" -> "REJECTED";
                    case "SKIPPED" -> "SKIPPED";
                    default -> "TODO";
                });
                if (state.equals("PENDING") && status.equals("IN_PROGRESS")) {
                    current = map("name", l.get("approver_name"), "title", l.get("approver_title"));
                }
            }
            return map("id", d.get("id"), "docNo", d.get("doc_no"), "formCode", d.get("code"),
                    "formName", FormService.shortName((String) d.get("code")), "title", d.get("title"),
                    "drafter", map("name", d.get("user_name"), "title", d.get("position_name"),
                            "department", d.get("department_name")), "submittedAt", d.get("submitted_at"),
                    "updatedAt", d.get("updated_at"), "status", status, "steps", progress, "currentApprover", current);
        }).toList();
        return EmployeeService.page(content, page, size, total);
    }

    public Map<String, Object> counts(long actor) {
        String query = BOXES.stream()
                .map(box -> "(SELECT COUNT(*) FROM approval_documents d WHERE " + condition(box) + ") AS `" + box + "`")
                .collect(Collectors.joining(","));
        return db.one("SELECT " + query, map("actor", actor), ErrorCode.USER_NOT_FOUND);
    }
}
