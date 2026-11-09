package com.minierp.backend.domain.attendance.service;

import com.minierp.backend.domain.attendance.repository.AttendanceMonthlyQuery;
import com.minierp.backend.domain.attendance.dto.*;
import com.minierp.backend.domain.attendance.repository.AttendanceStore;
import com.minierp.backend.domain.attendance.repository.AttendanceQueries;
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
    private final AttendanceQueries queries;
    private final PermissionAccess access;
    private final AttendanceMonthlyQuery query;
    private final AttendanceStore records;
    private final AbsenceBatchService absence;
    private final Clock clock;
    private static final String MENU = "ATTENDANCE_MONTHLY";
    public static final List<String> LEGEND = List.of("ON_TIME", "LATE", "EARLY", "MISSING", "ABSENT", "LEAVE",
            "HALF_AM", "HALF_PM", "HOLIDAY", "HOLIDAY_WORK", "NONE", "FUTURE");
    private ScopeFilter scope(Long department) {
        var scope = access.scope(MENU);
        if (department != null) {
            if (!scope.includesDepartment(department)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            queries.findDepartment(department);
        }
        return scope;
    }

    private boolean employed(LocalDate date, LocalDate start, LocalDate retired) {
        return !date.isBefore(start) && (retired == null || date.isBefore(retired));
    }

    @Transactional(readOnly = true)
    public AttendanceMonthlyResponseDto getMonthlyAttendance(String value, Long department, boolean sub) {
        var month = AttendanceService.parseMonth(value);
        var start = month.atDay(1);
        var end = month.atEndOfMonth();
        var scope = scope(department);
        var departments = department == null ? null : sub ? access.descendants(department) : List.of(department);
        var data = query.findMonthly(start, end, scope, departments);
        var today = LocalDate.now(clock);
        return monthlyTable(month, data, today);
    }

    private AttendanceMonthlyResponseDto monthlyTable(
            YearMonth month,
            List<Map<String, Object>> data,
            LocalDate today) {
        Map<LocalDate, AttendanceMonthlyResponseDto.Day> days = new TreeMap<>();
        Map<Long, MonthlyRow> rows = new LinkedHashMap<>();
        for (var record : data) {
            var date = (LocalDate) record.get("cell_date");
            days.putIfAbsent(date,
                    new AttendanceMonthlyResponseDto.Day(date, date.getDayOfWeek().toString().substring(0, 3),
                            (String) record.get("calendar_type"), (String) record.get("holiday_name")));
            if (record.get("user_id") == null) {
                continue;
            }
            long id = number(record, "user_id");
            var row = rows.computeIfAbsent(id,
                    userId -> new MonthlyRow(AttendanceMonthlyResponseDto.User.from(record)));
            boolean employed = employed(date, (LocalDate) record.get("employed_from"),
                    (LocalDate) record.get("retired_date"));
            String type = (String) record.get("day_type");
            var verdict = new AttendanceRules.Result((String) record.get("in_result"),
                    (String) record.get("out_result"),
                    bool(record, "absent"));
            String code = employed
                    ? AttendanceRules.code(date, today, type, verdict, (LocalDateTime) record.get("clock_in_at"))
                    : "NONE";
            row.cells.add(new AttendanceMonthlyResponseDto.Cell(date, code));
            if (employed) {
                row.add(verdict, type);
            }
        }
        return new AttendanceMonthlyResponseDto(month.toString(), days.values(),
                rows.values().stream().map(MonthlyRow::toResponse).toList(), LEGEND);
    }

    private static class MonthlyRow {
        private final AttendanceMonthlyResponseDto.User user;
        private final List<AttendanceMonthlyResponseDto.Cell> cells = new ArrayList<>();
        private int late;
        private int early;
        private int absent;
        private int missing;
        private double leaveDays;

        private MonthlyRow(AttendanceMonthlyResponseDto.User user) {
            this.user = user;
        }

        private void add(AttendanceRules.Result verdict, String type) {
            if ("LATE".equals(verdict.inResult())) late++;
            if ("EARLY".equals(verdict.outResult())) early++;
            if (verdict.absent()) absent++;
            if ("MISSING".equals(verdict.outResult())) missing++;
            leaveDays += type.equals("LEAVE") ? 1 : type.startsWith("HALF_") ? .5 : 0;
        }

        private AttendanceMonthlyResponseDto.Row toResponse() {
            return new AttendanceMonthlyResponseDto.Row(user, cells,
                    new AttendanceMonthlyResponseDto.Totals(late, early, absent, missing, leaveDays));
        }
    }

    @Transactional(readOnly = true)
    public AttendanceRecordResponseDto getAttendanceRecord(long user, LocalDate date) {
        access.assertUserInScope(MENU, user);
        var userRow = queries.findUser(user);
        var row = records.record(user, date, false);
        var day = records.day(user, date);
        var scheduleSnapshot = row == null ? records.schedule(user) : records.snapshot(row);
        var in = row == null ? null : (LocalDateTime) row.get("clock_in_at");
        var out = row == null ? null : (LocalDateTime) row.get("clock_out_at");
        String type = row == null ? day.type() : (String) row.get("day_type");
        var verdict = row == null
                ? new AttendanceRules.Result(null, null, false)
                : new AttendanceRules.Result((String) row.get("in_result"), (String) row.get("out_result"),
                        bool(row, "absent"));
        String code = employed(date, (LocalDate) userRow.get("employed_from"), (LocalDate) userRow.get("retired_date"))
                ? AttendanceRules.code(date, LocalDate.now(clock), type, verdict, in)
                : "NONE";
        String reason = reason(code, in, out, scheduleSnapshot, type, "EARLY".equals(verdict.outResult()));
        var schedule = AttendanceScheduleDetailResponseDto.from(scheduleSnapshot);
        return new AttendanceRecordResponseDto(
                new AttendanceRecordResponseDto.User(user, (String) userRow.get("user_name")),
                date, code, in, out, schedule, reason,
                day.leave() == null
                        ? null
                        : new AttendanceDocumentResponseDto(number(day.leave(), "documentId"),
                        (String) day.leave().get("docNo")),
                row == null || row.get("fix_document_id") == null
                        ? null
                        : new AttendanceDocumentResponseDto(number(row, "fix_document_id"),
                                (String) row.get("fix_doc_no")),
                row == null ? null : (LocalDateTime) row.get("judged_at"));
    }

    private String reason(
            String code,
            LocalDateTime in,
            LocalDateTime out,
            AttendanceStore.Schedule scheduleSnapshot,
            String type,
            boolean early) {
        var format = DateTimeFormatter.ofPattern("HH:mm");
        return switch (code) {
            case "LATE" -> "출근 " + in.format(format) + " > 기준 "
                    + (type.equals("HALF_AM") ? scheduleSnapshot.start().plusHours(5) : scheduleSnapshot.start())
                            .format(format)
                    + " + 유예 " + scheduleSnapshot.grace() + "분" + (early ? " (조퇴도 기록됨)" : "");
            case "EARLY" -> "퇴근 " + out.format(format) + " < 기준 "
                    + (type.equals("HALF_PM") ? scheduleSnapshot.start().plusHours(5) : scheduleSnapshot.end())
                            .format(format);
            case "ABSENT" -> "근무일에 출근 기록과 승인된 종일 휴가가 없습니다.";
            case "MISSING" -> "출근 후 퇴근 기록이 없습니다.";
            case "LEAVE", "HALF_AM", "HALF_PM" -> "승인된 휴가를 반영한 판정입니다.";
            case "HOLIDAY", "HOLIDAY_WORK" -> "휴일에는 지각·조퇴를 판정하지 않습니다.";
            case "ON_TIME" -> "저장된 근무시간과 유예 기준을 충족했습니다.";
            default -> "판정할 근태 기록이 없거나 재직 기간 밖의 날짜입니다.";
        };
    }

    public AttendanceBatchRunResponseDto rejudgeAttendance(LocalDate date) {
        if (access.scope(MENU).scope() != DataScope.ALL) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
        return AttendanceBatchRunResponseDto.from(absence.run(date));
    }

    public byte[] exportMonthlyAttendance(String month, Long department, boolean sub) {
        var table = getMonthlyAttendance(month, department, sub);
        StringBuilder out = new StringBuilder("\ufeff");
        List<String> header = new ArrayList<>(List.of("이름", "직급", "부서"));
        for (var day : table.days()) {
            header.add(day.date().toString());
        }
        header.addAll(List.of("지각", "조퇴", "결근", "퇴근 누락", "휴가 일수"));
        csvLine(out, header);
        for (var row : table.rows()) {
            var user = row.user();
            var values = new ArrayList<String>();
            values.add(Objects.toString(user.name(), ""));
            values.add(Objects.toString(user.title(), ""));
            values.add(Objects.toString(user.department(), ""));
            for (var cell : row.cells()) {
                values.add(csvLabel(cell.code()));
            }
            var totals = row.totals();
            values.add(Integer.toString(totals.late()));
            values.add(Integer.toString(totals.early()));
            values.add(Integer.toString(totals.absent()));
            values.add(Integer.toString(totals.missing()));
            values.add(Double.toString(totals.leaveDays()));
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
            String value = values.get(i);
            if (value.matches("^[=+@-].*")) {
                value = "'" + value;
            }
            out.append('"').append(value.replace("\"", "\"\"")).append('"');
        }
        out.append("\r\n");
    }
}
