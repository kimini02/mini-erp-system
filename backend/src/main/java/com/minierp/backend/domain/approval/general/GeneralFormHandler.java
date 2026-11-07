package com.minierp.backend.domain.approval.general;

import com.fasterxml.jackson.databind.JsonNode;
import com.minierp.backend.domain.approval.handler.*;
import com.minierp.backend.domain.approval.model.*;
import com.minierp.backend.domain.approval.repository.GeneralRequestStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class GeneralFormHandler implements ApprovalFormHandler {
    private final GeneralRequestStore store;
    public String formCode() {
        return "GENERAL";
    }

    public void checkFormat(JsonNode b) {
        BodyValues.text(b, "content", 16000);
        BodyValues.optionalLong(b, "amount");
    }

    public void saveBody(ApprovalDocument d, JsonNode b) {
        checkFormat(b);
        store.upsertBody(d.id(), b.path("content").textValue(), BodyValues.optionalLong(b, "amount"));
    }

    public Map<String, Object> body(long id) {
        return store.findBody(id);
    }

    public void copyBody(long source, ApprovalDocument target) {
        store.copyBody(target.id(), source);
    }

    public void deleteBody(long id) {
        store.deleteBody(id);
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
