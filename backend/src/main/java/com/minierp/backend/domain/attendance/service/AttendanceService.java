package com.minierp.backend.domain.attendance.service;

import com.minierp.backend.domain.attendance.repository.AttendanceStore;
import com.minierp.backend.domain.attendance.dto.*;
import com.minierp.backend.domain.attendance.repository.AttendanceQueries;
import com.minierp.backend.global.exception.*;
import com.minierp.backend.global.permission.PermissionAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class AttendanceService {
    private final AttendanceStore records;
    private final AttendanceQueries queries;
    private final Clock clock;
    private final PermissionAccess access;
    public AttendanceTodayResponseDto getTodayAttendance() {
        return view(access.actor(), LocalDate.now(clock));
    }

    private AttendanceTodayResponseDto view(long user, LocalDate date) {
        var row = records.record(user, date, false);
        var context = records.day(user, date);
        var schedule = row == null ? records.schedule(user) : records.snapshot(row);
        var month = YearMonth.now(clock);
        var counts = queries.findMonthlyCounts(user, month.atDay(1), month.atEndOfMonth());
        return render(date, row, context, schedule, number(counts, "late_count"), number(counts, "absent_count"));
    }

    /** Home uses one joined snapshot; the response rules are shared with /attendance/today. */
    public AttendanceTodayResponseDto getHomeAttendance() {
        var date = LocalDate.now(clock);
        var attendanceRow = records.homeSnapshot(access.actor(), date);
        Map<String, Object> leave = attendanceRow.get("leave_id") == null
                ? null
                : map("type", attendanceRow.get("leave_type"),
                        "documentId", attendanceRow.get("leave_id"),
                        "docNo", attendanceRow.get("leave_no"));
        String holiday = (String) attendanceRow.get("holiday_name");
        boolean off = holiday != null || date.getDayOfWeek() == DayOfWeek.SATURDAY
                || date.getDayOfWeek() == DayOfWeek.SUNDAY;
        String type = off
                ? "HOLIDAY"
                : leave == null
                        ? "WORKDAY"
                        : leave.get("type").toString().startsWith("HALF_") ? leave.get("type").toString() : "LEAVE";
        return render(date, attendanceRow.get("id") == null ? null : attendanceRow,
                new AttendanceStore.Day(type, holiday, leave),
                records.snapshot(attendanceRow), number(attendanceRow, "late_count"),
                number(attendanceRow, "absent_count"));
    }

    private AttendanceTodayResponseDto render(
            LocalDate date,
            Map<String, Object> row,
            AttendanceStore.Day context,
            AttendanceStore.Schedule schedule,
            long late,
            long absent) {
        var in = row == null ? null : (LocalDateTime) row.get("clock_in_at");
        var out = row == null ? null : (LocalDateTime) row.get("clock_out_at");
        String day = row == null ? context.type() : (String) row.get("day_type");
        var now = LocalDateTime.now(clock);
        var hypothetical = AttendanceRules.judge(date, day, schedule.start(), schedule.end(), schedule.grace(), in, now,
                false);
        return new AttendanceTodayResponseDto(date, day, context.holiday(),
                AttendanceScheduleResponseDto.from(schedule),
                in, out, row == null ? null : (String) row.get("in_result"),
                row == null ? null : (String) row.get("out_result"),
                AttendanceTodayResponseDto.Leave.from(context.leave()),
                in == null && !context.type().equals("LEAVE") && date.equals(now.toLocalDate()), in != null,
                "EARLY".equals(hypothetical.outResult()), late, absent);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AttendanceTodayResponseDto clockIn() {
        long user = access.actor();
        records.lockUser(user);
        var now = LocalDateTime.now(clock);
        var date = now.toLocalDate();
        var existing = records.record(user, date, true);
        if (existing != null && existing.get("clock_in_at") != null) {
            throw new BusinessException(ErrorCode.ALREADY_CLOCKED_IN);
        }
        var day = records.day(user, date);
        if (day.type().equals("LEAVE")) {
            throw new BusinessException(ErrorCode.ATTENDANCE_ON_LEAVE);
        }
        if (existing == null) {
            records.insert(user, date, records.schedule(user), day.type(), now, null, false, now);
        } else {
            records.judge(existing, day.type(), now, null, false, now);
        }
        return view(user, date);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AttendanceTodayResponseDto clockOut() {
        long user = access.actor();
        records.lockUser(user);
        var now = LocalDateTime.now(clock);
        var date = now.toLocalDate();
        var row = records.record(user, date, true);
        if (row == null || row.get("clock_in_at") == null) {
            date = date.minusDays(1);
            row = records.record(user, date, true);
            if (row == null || row.get("clock_in_at") == null
                    || (row.get("clock_out_at") != null
                            && !((LocalDateTime) row.get("clock_out_at")).toLocalDate().equals(now.toLocalDate()))) {
                throw new BusinessException(ErrorCode.NOT_CLOCKED_IN);
            }
        }
        records.judge(row, (String) row.get("day_type"), (LocalDateTime) row.get("clock_in_at"), now, false, now);
        return view(user, date);
    }

    public static YearMonth parseMonth(String text) {
        try {
            if (text == null || !text.matches("\\d{4}-\\d{2}")) {
                throw new IllegalArgumentException();
            }
            return YearMonth.parse(text);
        } catch (java.time.DateTimeException | IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    public AttendanceMyMonthResponseDto getMyMonthlyAttendance(String text) {
        var month = parseMonth(text);
        long user = access.actor();
        var today = LocalDate.now(clock);
        Map<LocalDate, Map<String, Object>> found = new HashMap<>();
        queries.findMyMonth(user, month.atDay(1), month.atEndOfMonth())
                .forEach(attendanceRow -> found.put((LocalDate) attendanceRow.get("work_date"), attendanceRow));
        List<AttendanceMyMonthResponseDto.Day> days = new ArrayList<>();
        int work = 0;
        int present = 0;
        int late = 0;
        int early = 0;
        int absent = 0;
        int missing = 0;
        double leave = 0;
        for (LocalDate date = month.atDay(1); !date.isAfter(month.atEndOfMonth()); date = date.plusDays(1)) {
            var row = found.get(date);
            var context = records.day(user, date);
            String type = row == null ? context.type() : (String) row.get("day_type");
            if (!type.equals("HOLIDAY")) {
                work++;
            }
            if (type.equals("LEAVE")) {
                leave++;
            } else if (type.startsWith("HALF_")) {
                leave +=.5;
            }
            if (row == null) {
                days.add(new AttendanceMyMonthResponseDto.EmptyDay(date, type));
                continue;
            }
            if (row.get("clock_in_at") != null) {
                present++;
            }
            if ("LATE".equals(row.get("in_result"))) {
                late++;
            }
            if ("EARLY".equals(row.get("out_result"))) {
                early++;
            }
            if (bool(row, "absent")) {
                absent++;
            }
            if ("MISSING".equals(row.get("out_result"))) {
                missing++;
            }
            days.add(new AttendanceMyMonthResponseDto.RecordedDay(date, type,
                    (LocalDateTime) row.get("clock_in_at"), (LocalDateTime) row.get("clock_out_at"),
                    (String) row.get("in_result"), (String) row.get("out_result"), bool(row, "absent"),
                    row.get("fix_document_id") == null
                            ? null
                            : new AttendanceDocumentResponseDto(number(row, "fix_document_id"),
                                    (String) row.get("fix_doc_no"))));
        }
        return new AttendanceMyMonthResponseDto(month.toString(),
                new AttendanceMyMonthResponseDto.Summary(work, present, late, early, absent, missing, leave), days);
    }
}
