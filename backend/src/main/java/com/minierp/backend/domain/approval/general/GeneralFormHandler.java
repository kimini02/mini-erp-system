package com.minierp.backend.domain.approval.general;

import com.fasterxml.jackson.databind.JsonNode;
import com.minierp.backend.domain.approval.handler.*;
import com.minierp.backend.domain.approval.model.*;
import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Map;
import static com.minierp.backend.global.persistence.SqlStore.map;

@Component
@RequiredArgsConstructor
public class GeneralFormHandler implements ApprovalFormHandler {
    private final SqlStore db;
    public String formCode() {
        return "GENERAL";
    }

    public void checkFormat(JsonNode b) {
        BodyValues.text(b, "content", 16000);
        BodyValues.optionalLong(b, "amount");
    }

    public void saveBody(ApprovalDocument d, JsonNode b) {
        checkFormat(b);
        db.update(
                "INSERT INTO general_requests(document_id,content,amount) VALUES(:id,:content,:amount) ON DUPLICATE KEY UPDATE content=VALUES(content),amount=VALUES(amount)",
                map("id", d.id(), "content", b.path("content").textValue(),
                        "amount", BodyValues.optionalLong(b, "amount")));
    }

    public Map<String, Object> body(long id) {
        return db.one("SELECT content,amount FROM general_requests WHERE document_id=:id", map("id", id),
                ErrorCode.DOCUMENT_NOT_FOUND);
    }

    public void copyBody(long source, ApprovalDocument target) {
        db.update(
                "INSERT INTO general_requests(document_id,content,amount) SELECT :target,content,amount FROM general_requests WHERE document_id=:source",
                map("target", target.id(), "source", source));
    }

    public void deleteBody(long id) {
        db.update("DELETE FROM general_requests WHERE document_id=:id", map("id", id));
    }

    public void validate(ApprovalDraft draft) {
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
