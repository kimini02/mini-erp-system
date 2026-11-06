package com.minierp.backend.domain.overtime.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.minierp.backend.domain.approval.handler.*;
import com.minierp.backend.domain.approval.model.*;
import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.map;

@Component
@RequiredArgsConstructor
public class OvertimeFormHandler implements ApprovalFormHandler {
    private final SqlStore db;
    private final Clock clock;
    public String formCode() {
        return "OVERTIME";
    }

    public void checkFormat(JsonNode b) {
        BodyValues.date(b, "overtimeDate");
        BodyValues.time(b, "startTime");
        BodyValues.time(b, "endTime");
        BodyValues.text(b, "reason", 500);
    }

    public void saveBody(ApprovalDocument d, JsonNode b) {
        checkFormat(b);
        db.update("""
            INSERT INTO overtime_requests(document_id,overtime_date,start_time,end_time,reason,created_at,updated_at)
            VALUES(:id,:date,:start,:end,:reason,:now,:now) ON DUPLICATE KEY UPDATE overtime_date=VALUES(overtime_date),start_time=VALUES(start_time),end_time=VALUES(end_time),reason=VALUES(reason),updated_at=VALUES(updated_at)
            """, map("id", d.id(), "date", BodyValues.date(b, "overtimeDate"), "start", BodyValues.time(b, "startTime"),
                "end", BodyValues.time(b, "endTime"), "reason", b.path("reason").textValue(),
                "now", LocalDateTime.now(clock)));
    }

    public Map<String, Object> body(long id) {
        return db.one(
                "SELECT overtime_date AS overtimeDate,TIME_FORMAT(start_time,'%H:%i:%s') AS startTime,TIME_FORMAT(end_time,'%H:%i:%s') AS endTime,reason FROM overtime_requests WHERE document_id=:id",
                map("id", id), ErrorCode.DOCUMENT_NOT_FOUND);
    }

    public void copyBody(long source, ApprovalDocument target) {
        db.update(
                "INSERT INTO overtime_requests(document_id,overtime_date,start_time,end_time,reason,created_at,updated_at) SELECT :target,overtime_date,start_time,end_time,reason,:now,:now FROM overtime_requests WHERE document_id=:source",
                map("target", target.id(), "source", source, "now", LocalDateTime.now(clock)));
    }

    public void deleteBody(long id) {
        db.update("DELETE FROM overtime_requests WHERE document_id=:id", map("id", id));
    }

    public void validate(ApprovalDraft draft) {
        if (db.count("SELECT COUNT(*) FROM overtime_requests WHERE document_id=:id AND end_time>start_time",
                map("id", draft.document().id())) != 1) {
            throw new BusinessException(ErrorCode.INVALID_OVERTIME_TIME);
        }
    }

    public void onSubmitted(ApprovalDocument d) {
    }

    public void onApproved(ApprovalDocument d) {
    }

    public void onRejected(ApprovalDocument d) {
    }

    public void onWithdrawn(ApprovalDocument d) {
    }
}
