package com.minierp.backend.domain.attendance.service;

import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.permission.*;
import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Service
@RequiredArgsConstructor
public class AttendanceAdminService {
    private final SqlStore db;
    private final PermissionAccess access;
    private final AttendanceMonthlyQuery query;
    private final AttendanceStore records;
    private final AbsenceBatchService absence;
    private final Clock clock;
    private static final String MENU = "ATTENDANCE_MONTHLY";
    public static final List<String> LEGEND = List.of("ON_TIME", "LATE", "EARLY", "MISSING", "ABSENT", "LEAVE",
            "HALF_AM", "HALF_PM", "HOLIDAY", "HOLIDAY_WORK", "NONE", "FUTURE");
    private String scope(Long department, boolean sub, Map<String, Object> args) {
        var scope = access.scope(MENU);
        String where = scope.predicate("u", args);
        if (department != null) {
            if (!scope.includesDepartment(department)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            db.one("SELECT id FROM departments WHERE id=:id", map("id", department), ErrorCode.DEPARTMENT_NOT_FOUND);
            args.put("departments", sub ? access.descendants(department) : List.of(department));
            where += " AND u.department_id IN (:departments)";
        }
        return where;
    }

    private boolean employed(LocalDate date, LocalDate start, LocalDate retired) {
        return !date.isBefore(start) && (retired == null || date.isBefore(retired));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> monthly(String value, Long department, boolean sub) {
        var month = AttendanceService.month(value);
        var args = map("start", month.atDay(1), "end", month.atEndOfMonth());
        var data = query.fetch(scope(department, sub, args), args);
        var today = LocalDate.now(clock);
        Map<LocalDate, Map<String, Object>> days = new TreeMap<>();
        Map<Long, Map<String, Object>> rows = new LinkedHashMap<>();
        for (var r : data) {
            var date = (LocalDate) r.get("cell_date");
            days.putIfAbsent(date,
                    map("date", date, "weekday", date.getDayOfWeek().toString().substring(0, 3),
                            "dayType", r.get("calendar_type"), "holidayName", r.get("holiday_name")));
            if (r.get("user_id") == null) {
                continue;
            }
            long id = number(r, "user_id");
            var row = rows.computeIfAbsent(id,
                    k -> map(
                            "user", map("id", id, "name", r.get("user_name"), "title", r.get("title"),
                                    "department", r.get("department")), "cells", new ArrayList<Map<String, Object>>(),
                            "totals", map("late", 0, "early", 0, "absent", 0, "missing", 0, "leaveDays", 0.0)));
            boolean employed = employed(date, (LocalDate) r.get("employed_from"), (LocalDate) r.get("retired_date"));
            String type = (String) r.get("day_type");
            var verdict = new AttendanceRules.Result((String) r.get("in_result"), (String) r.get("out_result"),
                    bool(r, "absent"));
            String code = employed ? AttendanceRules.code(date, today, type, verdict,
                    (LocalDateTime) r.get("clock_in_at")) : "NONE";
            cells(row).add(map("date", date, "code", code));
            if (employed) {
                var t = totals(row);
                add(t, "late", "LATE".equals(verdict.inResult()));
                add(t, "early", "EARLY".equals(verdict.outResult()));
                add(t, "absent", verdict.absent());
                add(t, "missing", "MISSING".equals(verdict.outResult()));
                t.put("leaveDays",
                        ((Number) t.get("leaveDays"))
                                .doubleValue() + (type.equals("LEAVE") ? 1 : type.startsWith("HALF_") ?.5 : 0));
            }
        }
        return map("month", month.toString(), "days", days.values(), "rows", rows.values(), "legend", LEGEND);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> cells(Map<String, Object> r) {
        return (List<Map<String, Object>>) r.get("cells");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> totals(Map<String, Object> r) {
        return (Map<String, Object>) r.get("totals");
    }

    private void add(Map<String, Object> t, String key, boolean yes) {
        if (yes) {
            t.put(key, ((Number) t.get(key)).intValue() + 1);
        }
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(long user, LocalDate date) {
        access.assertUserInScope(MENU, user);
        var u = db.one(
                "SELECT id,user_name,COALESCE(hire_date,DATE(created_at)) AS employed_from,retired_date FROM users WHERE id=:id",
                map("id", user), ErrorCode.USER_NOT_FOUND);
        var row = records.record(user, date, false);
        var day = records.day(user, date);
        var s = row == null ? records.schedule(user) : records.snapshot(row);
        var in = row == null ? null : (LocalDateTime) row.get("clock_in_at");
        var out = row == null ? null : (LocalDateTime) row.get("clock_out_at");
        String type = row == null ? day.type() : (String) row.get("day_type");
        var verdict = row == null ? new AttendanceRules.Result(null, null, false) : new AttendanceRules.Result(
                (String) row.get("in_result"), (String) row.get("out_result"), bool(row, "absent"));
        String code = employed(date, (LocalDate) u.get("employed_from"), (LocalDate) u.get("retired_date"))
                ? AttendanceRules.code(date, LocalDate.now(clock), type, verdict, in) : "NONE";
        String reason = reason(code, in, out, s, type, "EARLY".equals(verdict.outResult()));
        var schedule = s.view();
        schedule.remove("source");
        schedule.remove("departmentName");
        return map("user", map("id", user, "name", u.get("user_name")), "date", date, "code", code, "clockInAt", in,
                "clockOutAt", out, "schedule", schedule, "reason", reason,
                "leaveDocument", day.leave() == null ? null : map("id", day.leave().get("documentId"),
                        "docNo", day.leave().get("docNo")),
                "fixDocument", row == null || row.get("fix_document_id") == null ? null : map(
                        "id", row.get("fix_document_id"), "docNo", row.get("fix_doc_no")),
                "judgedAt", row == null ? null : row.get("judged_at"));
    }

    private String reason(
            String code,
            LocalDateTime in,
            LocalDateTime out,
            AttendanceStore.Schedule s,
            String type,
            boolean early) {
        var f = DateTimeFormatter.ofPattern("HH:mm");
        return switch (code) {
            case "LATE" -> "출근 " + in.format(f) + " > 기준 "
                    + (type.equals("HALF_AM") ? s.start().plusHours(5) : s.start())
                    .format(f) + " + 유예 " + s.grace() + "분" + (early ? " (조퇴도 기록됨)" : "");
            case "EARLY" -> "퇴근 " + out.format(f) + " < 기준 "
                    + (type.equals("HALF_PM") ? s.start().plusHours(5) : s.end())
                    .format(f);
            case "ABSENT" -> "근무일에 출근 기록과 승인된 종일 휴가가 없습니다.";
            case "MISSING" -> "출근 후 퇴근 기록이 없습니다.";
            case "LEAVE", "HALF_AM", "HALF_PM" -> "승인된 휴가를 반영한 판정입니다.";
            case "HOLIDAY", "HOLIDAY_WORK" -> "휴일에는 지각·조퇴를 판정하지 않습니다.";
            case "ON_TIME" -> "저장된 근무시간과 유예 기준을 충족했습니다.";
            default -> "판정할 근태 기록이 없거나 재직 기간 밖의 날짜입니다.";
        };
    }

    public Object rejudge(LocalDate date) {
        if (access.scope(MENU).scope() != DataScope.ALL) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return absence.run(date);
    }

    @SuppressWarnings("unchecked")
    public byte[] csv(String month, Long department, boolean sub) {
        var table = monthly(month, department, sub);
        StringBuilder out = new StringBuilder("\ufeff");
        List<String> header = new ArrayList<>(List.of("이름", "직급", "부서"));
        for (var day : (Collection<Map<String, Object>>) table.get("days")) {
            header.add(day.get("date").toString());
        }
        header.addAll(List.of("지각", "조퇴", "결근", "퇴근 누락", "휴가 일수"));
        csvLine(out, header);
        for (var row : (Collection<Map<String, Object>>) table.get("rows")) {
            var u = (Map<String, Object>) row.get("user");
            var values = new ArrayList<String>();
            for (String key : List.of("name", "title", "department")) {
                values.add(Objects.toString(u.get(key), ""));
            }
            for (var c : cells(row)) {
                values.add(csvLabel(c.get("code").toString()));
            }
            for (String key : List.of("late", "early", "absent", "missing", "leaveDays")) {
                values.add(totals(row).get(key).toString());
            }
            csvLine(out, values);
        }
        return out.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String csvLabel(String code) {
        return switch (code) {
            case "ON_TIME" -> "정상";
            case "LATE" -> "지각";
            case "EARLY" -> "조퇴";
            case "MISSING" -> "퇴근 누락";
            case "ABSENT" -> "결근";
            case "LEAVE" -> "휴가";
            case "HALF_AM" -> "오전 반차";
            case "HALF_PM" -> "오후 반차";
            case "HOLIDAY_WORK" -> "휴일 근무";
            case "HOLIDAY", "NONE", "FUTURE" -> "";
            default -> throw new IllegalArgumentException("Unknown attendance code: " + code);
        };
    }

    private void csvLine(StringBuilder out, List<String> values) {
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                out.append(',');
            }
            String v = values.get(i);
            if (v.matches("^[=+@-].*")) {
                v = "'" + v;
            }
            out.append('"').append(v.replace("\"", "\"\"")).append('"');
        }
        out.append("\r\n");
    }
}
