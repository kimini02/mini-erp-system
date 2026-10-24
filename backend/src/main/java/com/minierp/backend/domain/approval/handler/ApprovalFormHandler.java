package com.minierp.backend.domain.approval.handler;
import com.fasterxml.jackson.databind.JsonNode;
import com.minierp.backend.domain.approval.model.*;
import java.util.Map;
/** Called synchronously by the approval service inside its transaction. */
public interface ApprovalFormHandler {
    String formCode();
    void checkFormat(JsonNode body);
    void saveBody(ApprovalDocument doc, JsonNode body);
    Map<String,Object> body(long documentId);
    void copyBody(long sourceId, ApprovalDocument target);
    void deleteBody(long documentId);
    void validate(ApprovalDraft draft);
    void onSubmitted(ApprovalDocument doc);
    void onApproved(ApprovalDocument doc);
    void onRejected(ApprovalDocument doc);
    void onWithdrawn(ApprovalDocument doc);
}
