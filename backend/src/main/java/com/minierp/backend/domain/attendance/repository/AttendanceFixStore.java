package com.minierp.backend.domain.attendance.repository;

import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.map;

@Repository
@RequiredArgsConstructor
public class AttendanceFixStore {
    private final SqlStore db;

    public int upsertBody(long id, LocalDate date, LocalTime in, LocalTime out, String reason) {
        return db.update("""
          INSERT INTO attendance_fix_requests(document_id,work_date,clock_in,clock_out,reason) VALUES(:id,:date,:in,:out,:reason)
          ON DUPLICATE KEY UPDATE work_date=VALUES(work_date),clock_in=VALUES(clock_in),clock_out=VALUES(clock_out),reason=VALUES(reason),before_json=NULL
          """,
                map("id", id, "date", date, "in", in, "out", out, "reason", reason));
    }

    public Map<String, Object> findBody(long id) {
        return db.one("SELECT * FROM attendance_fix_requests WHERE document_id=:id", map("id", id),
                ErrorCode.DOCUMENT_NOT_FOUND);
    }

    public int copyBody(long id, long target) {
        return db.update(
                "INSERT INTO attendance_fix_requests(document_id,work_date,clock_in,clock_out,reason) SELECT :target,work_date,clock_in,clock_out,reason FROM attendance_fix_requests WHERE document_id=:id",
                map("id", id, "target", target));
    }

    public int deleteBody(long id) {
        return db.update("DELETE FROM attendance_fix_requests WHERE document_id=:id", map("id", id));
    }

    public long countOtherPending(long user, LocalDate date, long id) {
        return db.count(
                "SELECT COUNT(*) FROM attendance_fix_requests f JOIN approval_documents d ON d.id=f.document_id WHERE d.drafter_id=:user AND d.status='IN_PROGRESS' AND f.work_date=:date AND d.id<>:id",
                map("user", user, "date", date, "id", id));
    }

    public int updateBeforeSnapshot(long id, String json) {
        return db.update("UPDATE attendance_fix_requests SET before_json=:json WHERE document_id=:id",
                map("id", id, "json", json));
    }

    public int updateAttendanceFix(long doc, long user, LocalDate date, LocalDateTime now) {
        return db.update(
                "UPDATE attendances SET absent=0,fix_document_id=:doc,judged_at=:now,updated_at=:now WHERE user_id=:user AND work_date=:date",
                map("doc", doc, "user", user, "date", date, "now", now));
    }
}
