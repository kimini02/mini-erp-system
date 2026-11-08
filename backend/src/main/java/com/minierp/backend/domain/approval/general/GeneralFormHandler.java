package com.minierp.backend.domain.approval.general;

import com.fasterxml.jackson.databind.JsonNode;
import com.minierp.backend.domain.approval.handler.*;
import com.minierp.backend.domain.approval.model.*;
import com.minierp.backend.domain.approval.repository.GeneralRequestStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.minierp.backend.domain.approval.dto.ApprovalGeneralBodyResponseDto;

@Component
@RequiredArgsConstructor
public class GeneralFormHandler implements ApprovalFormHandler {
    private final GeneralRequestStore store;
    public String formCode() {
        return "GENERAL";
    }

    public void checkFormat(JsonNode body) {
        BodyValues.text(body, "content", 16000);
        BodyValues.optionalLong(body, "amount");
    }

    public void saveBody(ApprovalDocument document, JsonNode body) {
        checkFormat(body);
        store.upsertBody(document.id(), body.path("content").textValue(), BodyValues.optionalLong(body, "amount"));
    }

    public ApprovalGeneralBodyResponseDto body(long id) {
        return ApprovalGeneralBodyResponseDto.from(store.findBody(id));
    }

    public void copyBody(long source, ApprovalDocument target) {
        store.copyBody(target.id(), source);
    }

    public void deleteBody(long id) {
        store.deleteBody(id);
    }

    public void validate(ApprovalDraft draft) {
    }

    public void onSubmitted(ApprovalDocument document) {
    }

    public void onApproved(ApprovalDocument document) {
    }

    public void onRejected(ApprovalDocument document) {
    }

    public void onWithdrawn(ApprovalDocument document) {
    }
}
