package com.minierp.backend.domain.attendance.handler;

import com.minierp.backend.domain.attendance.repository.AttendanceStore;
import com.minierp.backend.domain.attendance.dto.AttendanceFixBodyResponseDto;
import com.fasterxml.jackson.databind.*;
import com.minierp.backend.domain.approval.handler.*;
import com.minierp.backend.domain.approval.model.*;
import com.minierp.backend.domain.attendance.service.*;
import com.minierp.backend.domain.attendance.repository.AttendanceFixStore;
import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Component
@RequiredArgsConstructor
public class AttendanceFixFormHandler implements ApprovalFormHandler {
    private final AttendanceFixStore store;
    private final AttendanceStore records;
    private final Clock clock;
    private final ObjectMapper mapper;
    public String formCode() {
        return "ATTENDANCE_FIX";
    }

    private LocalTime optionalTime(JsonNode body, String field) {
        if (!body.hasNonNull(field)) {
            return null;
        }
        String value = BodyValues.text(body, field, 5);
        if (!value.matches("[0-9]{2}:[0-9]{2}")) {
            throw BodyValues.invalid();
        }
        try {
            return LocalTime.parse(value);
        } catch (DateTimeException ex) {
            throw BodyValues.invalid();
        }
    }

    public void checkFormat(JsonNode body) {
        BodyValues.date(body, "workDate");
        BodyValues.text(body, "reason", 500);
        var in = optionalTime(body, "clockIn");
        var out = optionalTime(body, "clockOut");
        if (in == null && out == null) {
            throw BodyValues.invalid();
        }
        if (in != null && out != null && !out.isAfter(in)) {
            throw new BusinessException(ErrorCode.INVALID_ATTENDANCE_TIME);
        }
    }

    public void saveBody(ApprovalDocument document, JsonNode body) {
        checkFormat(body);
        store.upsertBody(document.id(), BodyValues.date(body, "workDate"), optionalTime(body, "clockIn"),
                optionalTime(body, "clockOut"), body.path("reason").textValue());
    }

    private Map<String, Object> raw(long id) {
        return store.findBody(id);
    }

    private JsonNode json(String text) {
        try {
            return text == null ? null : mapper.readTree(text);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Invalid stored attendance snapshot", ex);
        }
    }

    private String formatTime(Object value) {
        return value == null ? null : AttendanceStore.time(value).format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    public AttendanceFixBodyResponseDto body(long id) {
        var correction = raw(id);
        return new AttendanceFixBodyResponseDto((LocalDate) correction.get("work_date"),
                formatTime(correction.get("clock_in")),
                formatTime(correction.get("clock_out")), (String) correction.get("reason"),
                json((String) correction.get("before_json")));
    }

    public void copyBody(long id, ApprovalDocument target) {
        store.copyBody(id, target.id());
    }

    public void deleteBody(long id) {
        store.deleteBody(id);
    }

    public void validate(ApprovalDraft draft) {
        var document = draft.document();
        var correction = raw(document.id());
        var date = (LocalDate) correction.get("work_date");
        requireCorrectionDate(date);
        var old = records.record(document.drafterId(), date, false);
        if (records.day(document.drafterId(), date).type().equals("HOLIDAY") && old == null) {
            throw BodyValues.invalid();
        }
        mergedTimes(correction, old, date);
        if (store.countOtherPending(document.drafterId(), date, document.id()) > 0) {
            throw new BusinessException(ErrorCode.ATTENDANCE_FIX_DUPLICATE);
        }
    }

    private void requireCorrectionDate(LocalDate date) {
        var today = LocalDate.now(clock);
        if (!date.isBefore(today) || date.isBefore(today.minusDays(30))) {
            throw new BusinessException(ErrorCode.ATTENDANCE_FIX_OUT_OF_RANGE);
        }
    }

    public void onSubmitted(ApprovalDocument document) {
        var correction = raw(document.id());
        var date = (LocalDate) correction.get("work_date");
        var old = records.record(document.drafterId(), date, false);
        var day = records.day(document.drafterId(), date);
        var in = old == null ? null : (LocalDateTime) old.get("clock_in_at");
        var out = old == null ? null : (LocalDateTime) old.get("clock_out_at");
        var verdict = old == null
                ? new AttendanceRules.Result(null, null, false)
                : new AttendanceRules.Result((String) old.get("in_result"), (String) old.get("out_result"),
                        bool(old, "absent"));
        String code = AttendanceRules.code(date, LocalDate.now(clock),
                old == null ? day.type() : (String) old.get("day_type"), verdict, in);
        try {
            store.updateBeforeSnapshot(document.id(), mapper.writeValueAsString(
                    map("clockIn", in == null ? null : formatTime(in.toLocalTime()),
                            "clockOut", out == null ? null : formatTime(out.toLocalTime()), "code", code)));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record Times(LocalDateTime in, LocalDateTime out) {
    }

    private Times mergedTimes(Map<String, Object> correction, Map<String, Object> old, LocalDate date) {
        LocalDateTime in = correction.get("clock_in") == null
                ? (old == null ? null : (LocalDateTime) old.get("clock_in_at"))
                : date.atTime(AttendanceStore.time(correction.get("clock_in")));
        LocalDateTime out = correction.get("clock_out") == null
                ? (old == null ? null : (LocalDateTime) old.get("clock_out_at"))
                : date.atTime(AttendanceStore.time(correction.get("clock_out")));
        if (in == null) {
            throw new BusinessException(ErrorCode.ATTENDANCE_FIX_CLOCK_IN_REQUIRED);
        }
        if (out != null && !out.isAfter(in)) {
            throw new BusinessException(ErrorCode.INVALID_ATTENDANCE_TIME);
        }
        return new Times(in, out);
    }

    public void onApproved(ApprovalDocument document) {
        var correction = raw(document.id());
        var date = (LocalDate) correction.get("work_date");
        var old = records.record(document.drafterId(), date, true);
        var day = records.day(document.drafterId(), date);
        var now = LocalDateTime.now(clock);
        var times = mergedTimes(correction, old, date);
        var in = times.in();
        var out = times.out();
        if (old == null) {
            records.insert(document.drafterId(), date, records.schedule(document.drafterId()), day.type(),
                    in, out, true, now);
        } else {
            records.judge(old, day.type(), in, out, true, now);
        }
        store.updateAttendanceFix(document.id(), document.drafterId(), date, now);
    }

    public void onRejected(ApprovalDocument document) {
    }

    public void onWithdrawn(ApprovalDocument document) {
    }
}
