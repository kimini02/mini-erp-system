package com.minierp.backend.domain.approval.repository;

import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.map;

@Repository
@RequiredArgsConstructor
public class DocumentStore {
    private final SqlStore db;

    public Map<String, Object> lockUser(long id) {
        return db.one("SELECT id FROM users WHERE id=:id FOR UPDATE", map("id", id), ErrorCode.USER_NOT_FOUND);
    }

    public Map<String, Object> lockDocument(long id) {
        return db.one("SELECT id FROM approval_documents WHERE id=:id FOR UPDATE", map("id", id),
                ErrorCode.DOCUMENT_NOT_FOUND);
    }

    public int insertLine(
            long id,
            int order,
            String kind,
            Object user,
            Object name,
            Object title,
            Object dept,
            String source,
            String rule,
            String status,
            String skip) {
        return db.update("""
                INSERT INTO approval_lines(document_id,step_order,line_kind,approver_id,approver_name,approver_title,approver_dept,source,rule_label,status,skip_reason)
                VALUES(:id,:order,:kind,:user,:name,:title,:dept,:source,:rule,:status,:skip)
                """,
                map("id", id, "order", order, "kind", kind, "user", user, "name", name, "title", title,
                        "dept", dept, "source", source, "rule", rule, "status", status, "skip", skip));
    }

    public long insertDraft(
            Object form,
            long user,
            long dept,
            String title,
            boolean manual,
            Long original,
            LocalDateTime now) {
        return db.insert("""
            INSERT INTO approval_documents(form_id,drafter_id,drafter_dept_id,title,status,version,manual_line,resubmitted_from_id,created_at,updated_at)
            VALUES(:form,:user,:dept,:title,'DRAFT',0,:manual,:original,:now,:now)
            """,
                map("form", form, "user", user, "dept", dept, "title", title, "manual", manual, "original", original,
                        "now", now));
    }

    public int deleteLines(long id) {
        return db.update("DELETE FROM approval_lines WHERE document_id=:id", map("id", id));
    }

    public int updateDraft(long id, String title, boolean manual, LocalDateTime now) {
        return db.update(
                "UPDATE approval_documents SET title=:title,manual_line=:manual,version=version+1,updated_at=:now WHERE id=:id",
                map("id", id, "title", title, "manual", manual, "now", now));
    }

    public int deleteDocument(long id) {
        return db.update("DELETE FROM approval_documents WHERE id=:id", map("id", id));
    }

    public int insertNumberSequence(String prefix, int year) {
        return db.update(
                "INSERT INTO doc_no_sequences(prefix,doc_year,last_seq) VALUES(:prefix,:year,0) ON DUPLICATE KEY UPDATE last_seq=last_seq",
                map("prefix", prefix, "year", year));
    }

    public Map<String, Object> lockNumberSequence(String prefix, int year) {
        return db.one("SELECT last_seq FROM doc_no_sequences WHERE prefix=:prefix AND doc_year=:year FOR UPDATE",
                map("prefix", prefix, "year", year), ErrorCode.RESOURCE_NOT_FOUND);
    }

    public int updateNumberSequence(String prefix, int year, long seq) {
        return db.update("UPDATE doc_no_sequences SET last_seq=:seq WHERE prefix=:prefix AND doc_year=:year",
                map("prefix", prefix, "year", year, "seq", seq));
    }

    public int insertHistory(long id, Long actor, String action, String comment, LocalDateTime now) {
        return db.update(
                "INSERT INTO approval_histories(document_id,actor_id,action,comment,created_at) VALUES(:id,:actor,:action,:comment,:now)",
                map("id", id, "actor", actor, "action", action, "comment", comment, "now", now));
    }

    public int updateTransition(
            Object id,
            Object version,
            String status,
            Integer step,
            LocalDateTime completed,
            LocalDateTime now) {
        return db.update(
                "UPDATE approval_documents SET status=:status,current_step=:step,completed_at=:completed,version=version+1,updated_at=:now WHERE id=:id AND version=:version",
                map("id", id, "version", version, "status", status, "step", step, "completed", completed, "now", now));
    }

    public int updateSubmission(long id, String no, LocalDateTime now) {
        return db.update("UPDATE approval_documents SET doc_no=:no,submitted_at=:now WHERE id=:id",
                map("id", id, "no", no, "now", now));
    }

    public int updateAction(Object id, String status, LocalDateTime now, String comment) {
        return db.update(
                "UPDATE approval_lines SET status=:status,acted_at=:now,comment=:comment WHERE id=:id AND status='PENDING'",
                map("id", id, "status", status, "now", now, "comment", comment));
    }

    public int updatePendingLine(Object id) {
        return db.update("UPDATE approval_lines SET status='PENDING' WHERE id=:id AND status='WAITING'", map("id", id));
    }
}
