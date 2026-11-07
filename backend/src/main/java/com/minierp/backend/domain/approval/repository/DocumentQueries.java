package com.minierp.backend.domain.approval.repository;

import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.map;

@Repository
@RequiredArgsConstructor
public class DocumentQueries {
    private final SqlStore db;

    public Map<String, Object> findDocument(long id) {
        return db.one(
                "SELECT d.*,f.code AS form_code,f.name AS form_name,f.doc_prefix FROM approval_documents d JOIN approval_forms f ON f.id=d.form_id WHERE d.id=:id",
                map("id", id), ErrorCode.DOCUMENT_NOT_FOUND);
    }

    public long countReadableDocument(long id, long actor) {
        return db.count("""
            SELECT COUNT(*) FROM approval_documents d WHERE d.id=:id AND (d.drafter_id=:actor
            OR EXISTS(SELECT 1 FROM approval_lines l WHERE l.document_id=d.id AND l.approver_id=:actor)
            OR EXISTS(SELECT 1 FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=:actor AND r.code='CHIEF'))
            """,
                map("id", id, "actor", actor));
    }

    public List<Map<String, Object>> findLines(long id) {
        return db.rows("SELECT * FROM approval_lines WHERE document_id=:id ORDER BY step_order", map("id", id));
    }

    public long countApprover(long id, long actor) {
        return db.count(
                "SELECT COUNT(*) FROM approval_lines WHERE document_id=:id AND approver_id=:actor AND line_kind='APPROVER'",
                map("id", id, "actor", actor));
    }

    public List<Map<String, Object>> findHistory(long id) {
        return db.rows(
                "SELECT h.action,h.actor_id,u.user_name,h.comment,h.created_at FROM approval_histories h LEFT JOIN users u ON u.id=h.actor_id WHERE h.document_id=:id ORDER BY h.id",
                map("id", id));
    }
}
