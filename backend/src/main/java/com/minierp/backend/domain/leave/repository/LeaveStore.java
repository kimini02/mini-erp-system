package com.minierp.backend.domain.leave.repository;

import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.map;

@Repository
@RequiredArgsConstructor
public class LeaveStore {
    private final SqlStore db;

    public Map<String, Object> lockUser(long id) {
        return db.one("SELECT id FROM users WHERE id=:id FOR UPDATE", map("id", id), ErrorCode.USER_NOT_FOUND);
    }

    public long insertAdjustment(long user, int year, BigDecimal days, String memo, long actor, LocalDateTime now) {
        return db.insert(
                "INSERT INTO leave_ledger(user_id,leave_year,entry_type,days,memo,created_by,created_at) VALUES(:user,:year,'ADJUST',:days,:memo,:actor,:now)",
                map("user", user, "year", year, "days", days, "memo", memo, "actor", actor, "now", now));
    }

    public Map<String, Object> lockGrantUser(long id) {
        return db.one("SELECT id,status,hire_date FROM users WHERE id=:id FOR UPDATE", map("id", id),
                ErrorCode.USER_NOT_FOUND);
    }

    public int insertGrant(long id, int year, BigDecimal days, String key, String memo, LocalDateTime now) {
        return db.update(
                "INSERT INTO leave_ledger(user_id,leave_year,entry_type,days,grant_key,memo,created_at) VALUES(:id,:year,'GRANT',:days,:key,:memo,:now)",
                map("id", id, "year", year, "days", days, "key", key, "memo", memo, "now", now));
    }

    public int upsertBody(
            long id,
            String type,
            LocalDate start,
            LocalDate end,
            Long handover,
            String reason,
            LocalDateTime now) {
        return db.update("""
            INSERT INTO leave_requests(document_id,leave_type,start_date,end_date,used_days,handover_user_id,reason,created_at,updated_at)
            VALUES(:id,:type,:start,:end,0,:handover,:reason,:now,:now) ON DUPLICATE KEY UPDATE leave_type=VALUES(leave_type),start_date=VALUES(start_date),end_date=VALUES(end_date),used_days=0,handover_user_id=VALUES(handover_user_id),reason=VALUES(reason),updated_at=VALUES(updated_at)
            """,
                map("id", id, "type", type, "start", start, "end", end, "handover", handover,
                        "reason", reason, "now", now));
    }

    public int copyBody(long target, long source, LocalDateTime now) {
        return db.update(
                "INSERT INTO leave_requests(document_id,leave_type,start_date,end_date,used_days,handover_user_id,reason,created_at,updated_at) SELECT :target,leave_type,start_date,end_date,0,handover_user_id,reason,:now,:now FROM leave_requests WHERE document_id=:source",
                map("target", target, "source", source, "now", now));
    }

    public int deleteBody(long id) {
        return db.update("DELETE FROM leave_requests WHERE document_id=:id", map("id", id));
    }

    public int insertLedger(
            long user,
            int year,
            String type,
            BigDecimal days,
            long id,
            String memo,
            LocalDateTime now) {
        return db.update(
                "INSERT INTO leave_ledger(user_id,leave_year,entry_type,days,document_id,memo,created_at) VALUES(:user,:year,:type,:days,:id,:memo,:now)",
                map("user", user, "year", year, "type", type, "days", days, "id", id, "memo", memo, "now", now));
    }

    public int updateUsedDays(long id, BigDecimal days) {
        return db.update("UPDATE leave_requests SET used_days=:days WHERE document_id=:id",
                map("id", id, "days", days));
    }
}
