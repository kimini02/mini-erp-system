package com.minierp.backend.domain.attendance.handler;

import com.minierp.backend.domain.attendance.repository.AttendanceStore;
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
        String v = BodyValues.text(body, field, 5);
        if (!v.matches("[0-9]{2}:[0-9]{2}")) {
            throw BodyValues.invalid();
        }
        try {
            return LocalTime.parse(v);
        } catch (DateTimeException ex) {
            throw BodyValues.invalid();
        }
    }

    public void checkFormat(JsonNode b) {
        BodyValues.date(b, "workDate");
        BodyValues.text(b, "reason", 500);
        var in = optionalTime(b, "clockIn");
        var out = optionalTime(b, "clockOut");
        if (in == null && out == null) {
            throw BodyValues.invalid();
        }
        if (in != null && out != null && !out.isAfter(in)) {
            throw new BusinessException(ErrorCode.INVALID_ATTENDANCE_TIME);
        }
    }

    public void saveBody(ApprovalDocument d, JsonNode b) {
        checkFormat(b);
        store.updateBody(d.id(), BodyValues.date(b, "workDate"), optionalTime(b, "clockIn"),
                optionalTime(b, "clockOut"), b.path("reason").textValue());
    }

    private Map<String, Object> raw(long id) {
        return store.findBody(id);
    }

    private Object json(String text) {
        try {
            return text == null ? null : mapper.readTree(text);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Invalid stored attendance snapshot", ex);
        }
    }

    private String hh(Object value) {
        return value == null ? null : AttendanceStore.time(value).format(DateTimeFormatter.ofPattern("HH:mm"));
    }

    public Map<String, Object> body(long id) {
        var r = raw(id);
        return map("workDate", r.get("work_date"), "clockIn", hh(r.get("clock_in")), "clockOut", hh(r.get("clock_out")),
                "reason", r.get("reason"), "before", json((String) r.get("before_json")));
    }

    public void copyBody(long id, ApprovalDocument target) {
        store.insertBodyCopy(id, target.id());
    }

    public void deleteBody(long id) {
        store.deleteBody(id);
    }

    public void validate(ApprovalDraft draft) {
        var d = draft.document();
        var r = raw(d.id());
        var date = (LocalDate) r.get("work_date");
        var today = LocalDate.now(clock);
        if (!date.isBefore(today) || date.isBefore(today.minusDays(30))) {
            throw new BusinessException(ErrorCode.ATTENDANCE_FIX_OUT_OF_RANGE);
        }
        var old = records.record(d.drafterId(), date, false);
        if (records.day(d.drafterId(), date).type().equals("HOLIDAY") && old == null) {
            throw BodyValues.invalid();
        }
        mergedTimes(r, old, date);
        if (store.countOtherPending(d.drafterId(), date, d.id()) > 0) {
            throw new BusinessException(ErrorCode.ATTENDANCE_FIX_DUPLICATE);
        }
    }

    public void onSubmitted(ApprovalDocument d) {
        var r = raw(d.id());
        var date = (LocalDate) r.get("work_date");
        var old = records.record(d.drafterId(), date, false);
        var day = records.day(d.drafterId(), date);
        var in = old == null ? null : (LocalDateTime) old.get("clock_in_at");
        var out = old == null ? null : (LocalDateTime) old.get("clock_out_at");
        var verdict = old == null
                ? new AttendanceRules.Result(null, null, false)
                : new AttendanceRules.Result((String) old.get("in_result"), (String) old.get("out_result"),
                        bool(old, "absent"));
        String code = AttendanceRules.code(date, LocalDate.now(clock),
                old == null ? day.type() : (String) old.get("day_type"), verdict, in);
        try {
            store.updateBeforeSnapshot(d.id(), mapper.writeValueAsString(
                    map("clockIn", in == null ? null : hh(in.toLocalTime()),
                            "clockOut", out == null ? null : hh(out.toLocalTime()), "code", code)));
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

    public void onApproved(ApprovalDocument d) {
        var r = raw(d.id());
        var date = (LocalDate) r.get("work_date");
        var old = records.record(d.drafterId(), date, true);
        var day = records.day(d.drafterId(), date);
        var now = LocalDateTime.now(clock);
        var times = mergedTimes(r, old, date);
        var in = times.in();
        var out = times.out();
        if (old == null) {
            records.insert(d.drafterId(), date, records.schedule(d.drafterId()), day.type(), in, out, true, now);
        } else {
            records.judge(old, day.type(), in, out, true, now);
        }
        store.updateAttendanceFix(d.id(), d.drafterId(), date, now);
    }

    public void onRejected(ApprovalDocument d) {
    }

    public void onWithdrawn(ApprovalDocument d) {
    }
}
