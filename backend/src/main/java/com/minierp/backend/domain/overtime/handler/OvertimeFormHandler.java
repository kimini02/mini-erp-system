package com.minierp.backend.domain.overtime.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.minierp.backend.domain.approval.handler.*;
import com.minierp.backend.domain.approval.model.*;
import com.minierp.backend.domain.overtime.repository.OvertimeStore;
import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class OvertimeFormHandler implements ApprovalFormHandler {
    private final OvertimeStore store;
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
        store.upsertBody(d.id(), BodyValues.date(b, "overtimeDate"), BodyValues.time(b, "startTime"),
                BodyValues.time(b, "endTime"), b.path("reason").textValue(), LocalDateTime.now(clock));
    }

    public Map<String, Object> body(long id) {
        return store.findBody(id);
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

    public void onSubmitted(ApprovalDocument d) {
    }

    public void onApproved(ApprovalDocument d) {
    }

    public void onRejected(ApprovalDocument d) {
    }

    public void onWithdrawn(ApprovalDocument d) {
    }
}
