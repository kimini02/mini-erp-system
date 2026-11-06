package com.minierp.backend.domain.attendance.service;

import com.minierp.backend.global.persistence.SqlStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Service
@RequiredArgsConstructor
public class AbsenceBatchWork {
    private final SqlStore db;
    private final AttendanceStore records;
    private final Clock clock;
    public record Counts(int processed, int skipped) {
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Counts run(LocalDate date) {
        int processed = 0;
        int skipped = 0;
        var now = LocalDateTime.now(clock);
        var candidates = db.rows("""
          SELECT u.id FROM users u WHERE (u.status<>'ON_LEAVE' AND COALESCE(u.hire_date,DATE(u.created_at))<=:date AND (u.status<>'RETIRED' OR u.retired_date>:date) AND u.user_pw IS NOT NULL)
          OR EXISTS(SELECT 1 FROM attendances a WHERE a.user_id=u.id AND a.work_date=:date) ORDER BY u.id
          """, map("date", date));
        for (var candidate : candidates) {
            long id = number(candidate, "id");
            var user = records.lockUser(id);
            var row = records.record(id, date, true);
            var day = records.day(id, date);
            LocalDate eligible = user.get("hire_date") == null ? ((LocalDateTime) user.get("created_at"))
                    .toLocalDate() : (LocalDate) user.get("hire_date");
            if (row == null) {
                if ("ON_LEAVE".equals(user.get("status")) || eligible.isAfter(date)
                        || ("RETIRED".equals(user.get("status"))
                                && (user.get("retired_date") == null
                                        || !date.isBefore((LocalDate) user.get("retired_date"))))
                        || !bool(user, "activated") || day.type().equals("HOLIDAY")) {
                    skipped++;
                    continue;
                }
                records.insert(id, date, records.schedule(id), day.type(), null, null, true, now);
            } else {
                records.judge(row, day.type(), (LocalDateTime) row.get("clock_in_at"),
                        (LocalDateTime) row.get("clock_out_at"), true, now);
            }
            processed++;
        }
        return new Counts(processed, skipped);
    }
}
