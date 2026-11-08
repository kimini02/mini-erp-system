package com.minierp.backend.domain.overtime.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.minierp.backend.domain.approval.handler.*;
import com.minierp.backend.domain.approval.model.*;
import com.minierp.backend.domain.overtime.repository.OvertimeStore;
import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.*;
import com.minierp.backend.domain.overtime.dto.OvertimeBodyResponseDto;

@Component
@RequiredArgsConstructor
public class OvertimeFormHandler implements ApprovalFormHandler {
    private final OvertimeStore store;
    private final Clock clock;
    public String formCode() {
        return "OVERTIME";
    }

    public void checkFormat(JsonNode body) {
        BodyValues.date(body, "overtimeDate");
        BodyValues.time(body, "startTime");
        BodyValues.time(body, "endTime");
        BodyValues.text(body, "reason", 500);
    }

    public void saveBody(ApprovalDocument document, JsonNode body) {
        checkFormat(body);
        store.upsertBody(document.id(), BodyValues.date(body, "overtimeDate"), BodyValues.time(body, "startTime"),
                BodyValues.time(body, "endTime"), body.path("reason").textValue(), LocalDateTime.now(clock));
    }

    public OvertimeBodyResponseDto body(long id) {
        return OvertimeBodyResponseDto.from(store.findBody(id));
    }

    public void copyBody(long source, ApprovalDocument target) {
        store.copyBody(target.id(), source, LocalDateTime.now(clock));
    }

    public void deleteBody(long id) {
        store.deleteBody(id);
    }

    public void validate(ApprovalDraft draft) {
        if (store.countValidTime(draft.document().id()) != 1) {
            throw new BusinessException(ErrorCode.INVALID_OVERTIME_TIME);
        }
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
