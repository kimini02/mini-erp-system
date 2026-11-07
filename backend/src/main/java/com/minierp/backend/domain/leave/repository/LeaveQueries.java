package com.minierp.backend.domain.leave.repository;

import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import com.minierp.backend.global.permission.ScopeFilter;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.map;

@Repository
@RequiredArgsConstructor
public class LeaveQueries {
    private final SqlStore db;

    public List<Map<String, Object>> findGrantRuns(int limit) {
        return db.rows(
                "SELECT id,job_name AS jobName,target_date AS targetDate,status,processed_count AS processedCount,skipped_count AS skippedCount,message,started_at AS startedAt,finished_at AS finishedAt FROM batch_runs WHERE job_name='LEAVE_GRANT' ORDER BY id DESC LIMIT :limit",
                map("limit", limit));
    }

    public Map<String, Object> findBalance(long user, int year) {
        return db.one("""
            SELECT COALESCE(SUM(CASE WHEN entry_type='GRANT' THEN days ELSE 0 END),0) AS granted,
            -COALESCE(SUM(CASE WHEN entry_type='USE' THEN days ELSE 0 END),0) AS used,
            -COALESCE(SUM(CASE WHEN entry_type IN ('RESERVE','RELEASE') THEN days ELSE 0 END),0) AS reserved,
            COALESCE(SUM(days),0) AS available FROM leave_ledger WHERE user_id=:user AND leave_year=:year
            """,
                map("user", user, "year", year), ErrorCode.USER_NOT_FOUND);
    }

    public List<Map<String, Object>> findGrantCandidates() {
        return db.rows("SELECT id FROM users WHERE status IN ('ACTIVE','ON_LEAVE') ORDER BY id", Map.of());
    }

    public long countLegacyGrants(long id, int year) {
        return db.count(
                "SELECT COUNT(*) FROM leave_ledger WHERE user_id=:id AND leave_year=:year AND entry_type='GRANT' AND grant_key IS NULL",
                map("id", id, "year", year));
    }

    public List<Map<String, Object>> findGrantKeys(long id) {
        return db.rows("SELECT grant_key FROM leave_ledger WHERE user_id=:id AND grant_key IS NOT NULL", map("id", id));
    }

    public Map<String, Object> findHandoverUser(Long id) {
        return db.one("SELECT id FROM users WHERE id=:id", map("id", id), ErrorCode.USER_NOT_FOUND);
    }

    public Map<String, Object> findRequest(long id) {
        return db.one("SELECT * FROM leave_requests WHERE document_id=:id", map("id", id),
                ErrorCode.DOCUMENT_NOT_FOUND);
    }

    public Map<String, Object> findHandoverName(Object id) {
        return db.one("SELECT id,user_name AS name FROM users WHERE id=:id", map("id", id), ErrorCode.USER_NOT_FOUND);
    }

    public Map<String, Object> findAvailable(long user, int year) {
        return db.one(
                "SELECT COALESCE(SUM(days),0) AS available FROM leave_ledger WHERE user_id=:user AND leave_year=:year",
                map("user", user, "year", year), ErrorCode.USER_NOT_FOUND);
    }

    public long countOverlap(long user, long id, LocalDate start, LocalDate end) {
        return db.count("""
            SELECT COUNT(*) FROM leave_requests l JOIN approval_documents d ON d.id=l.document_id
            WHERE d.drafter_id=:user AND d.status IN ('IN_PROGRESS','APPROVED') AND d.id<>:id AND l.start_date<=:end AND l.end_date>=:start
            """,
                map("user", user, "id", id, "start", start, "end", end));
    }

    public List<Map<String, Object>> findReservations(long id) {
        return db.rows(
                "SELECT leave_year,-SUM(days) AS days FROM leave_ledger WHERE document_id=:id AND entry_type='RESERVE' GROUP BY leave_year",
                map("id", id));
    }

    public long countBalances(int year, int size, long offset, ScopeFilter scope, Long department, String prefix) {
        var args = balanceParameters(year, size, offset);
        String where = balanceWhere(scope, args, department, prefix);
        return db.count("SELECT COUNT(*) FROM users u WHERE " + where, args);
    }

    public List<Map<String, Object>> findBalances(
            int year,
            int size,
            long offset,
            ScopeFilter scope,
            Long department,
            String prefix) {
        var args = balanceParameters(year, size, offset);
        String where = balanceWhere(scope, args, department, prefix);
        return db.rows("""
          SELECT u.id,u.user_name,p.name AS title,d.name AS department,u.hire_date,
          COALESCE(l.granted,0) AS granted,COALESCE(l.used,0) AS used,COALESCE(l.reserved,0) AS reserved,COALESCE(l.adjusted,0) AS adjusted,COALESCE(l.available,0) AS available
          FROM users u JOIN departments d ON d.id=u.department_id JOIN positions p ON p.id=u.position_id LEFT JOIN (
            SELECT user_id,SUM(CASE WHEN entry_type='GRANT' THEN days ELSE 0 END) AS granted,
            -SUM(CASE WHEN entry_type='USE' THEN days ELSE 0 END) AS used,
            -SUM(CASE WHEN entry_type IN ('RESERVE','RELEASE') THEN days ELSE 0 END) AS reserved,
            SUM(CASE WHEN entry_type='ADJUST' THEN days ELSE 0 END) AS adjusted,SUM(days) AS available
            FROM leave_ledger WHERE leave_year=:year GROUP BY user_id
          ) l ON l.user_id=u.id WHERE
          """ + where + " ORDER BY u.user_name,u.id LIMIT :size OFFSET :offset",
                args);
    }

    public Map<String, Object> findAdjustmentBalance(long id, int year) {
        return db.one(
                "SELECT COALESCE(SUM(days),0) AS balance FROM leave_ledger WHERE user_id=:id AND leave_year=:year",
                map("id", id, "year", year), ErrorCode.USER_NOT_FOUND);
    }

    public List<Map<String, Object>> findLedger(long user, int year, String type) {
        var args = map("user", user, "year", year);
        String filter = "";
        if (type != null) {
            args.put("type", type);
            filter = " WHERE l.entry_type=:type";
        }
        return db.rows("""
            SELECT l.*,d.doc_no,d.status FROM (
              SELECT x.*,SUM(x.days) OVER(ORDER BY x.created_at,x.id ROWS UNBOUNDED PRECEDING) AS balance
              FROM leave_ledger x WHERE x.user_id=:user AND x.leave_year=:year
            ) l LEFT JOIN approval_documents d ON d.id=l.document_id
            """ + filter + " ORDER BY l.created_at DESC,l.id DESC",
                args);
    }

    private Map<String, Object> balanceParameters(int year, int size, long offset) {
        return map("year", year, "size", size, "offset", offset);
    }

    private String balanceWhere(ScopeFilter scope, Map<String, Object> args, Long department, String prefix) {
        String where = scope.predicate("u", args);
        if (department != null) {
            args.put("dept", department);
            where += " AND u.department_id=:dept";
        }
        if (prefix != null) {
            args.put("q", prefix);
            where += " AND (u.user_name LIKE :q ESCAPE '!' OR u.login_id LIKE :q ESCAPE '!')";
        }
        return where;
    }
}
