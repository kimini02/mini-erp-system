package com.minierp.backend.domain.overtime.repository;

import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.map;

@Repository
@RequiredArgsConstructor
public class OvertimeStore {
    private final SqlStore db;

    public int upsertBody(long id, LocalDate date, LocalTime start, LocalTime end, String reason, LocalDateTime now) {
        return db.update("""
            INSERT INTO overtime_requests(document_id,overtime_date,start_time,end_time,reason,created_at,updated_at)
            VALUES(:id,:date,:start,:end,:reason,:now,:now) ON DUPLICATE KEY UPDATE overtime_date=VALUES(overtime_date),start_time=VALUES(start_time),end_time=VALUES(end_time),reason=VALUES(reason),updated_at=VALUES(updated_at)
            """,
                map("id", id, "date", date, "start", start, "end", end, "reason", reason, "now", now));
    }

    public Map<String, Object> findBody(long id) {
        return db.one(
                "SELECT overtime_date AS overtimeDate,TIME_FORMAT(start_time,'%H:%i:%s') AS startTime,TIME_FORMAT(end_time,'%H:%i:%s') AS endTime,reason FROM overtime_requests WHERE document_id=:id",
                map("id", id), ErrorCode.DOCUMENT_NOT_FOUND);
    }

    public int copyBody(long target, long source, LocalDateTime now) {
        return db.update(
                "INSERT INTO overtime_requests(document_id,overtime_date,start_time,end_time,reason,created_at,updated_at) SELECT :target,overtime_date,start_time,end_time,reason,:now,:now FROM overtime_requests WHERE document_id=:source",
                map("target", target, "source", source, "now", now));
    }

    public int deleteBody(long id) {
        return db.update("DELETE FROM overtime_requests WHERE document_id=:id", map("id", id));
    }

    public long countValidTime(long id) {
        return db.count("SELECT COUNT(*) FROM overtime_requests WHERE document_id=:id AND end_time>start_time",
                map("id", id));
    }
}
