package com.minierp.backend.domain.leave.service;

import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.permission.*;
import com.minierp.backend.global.exception.*;
import com.minierp.backend.domain.user.service.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.math.BigDecimal;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Service
@RequiredArgsConstructor
public class LeaveAdminService {
    private final SqlStore db;
    private final PermissionAccess access;
    private final LeaveGrantService grants;
    private final LeaveQueryService query;
    private final Clock clock;
    private static final String MENU = "LEAVE_ADMIN";
    private int year(Integer requested) {
        int year = requested == null ? LocalDate.now(clock).getYear() : requested;
        if (year < 1 || year > 9999) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return year;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> balances(Integer requested, Long department, String search, int page, int size) {
        if (page < 0 || size < 1 || size > 200) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        var args = map("year", year(requested), "size", size, "offset", (long) page * size);
        var scope = access.scope(MENU);
        String where = scope.predicate("u", args);
        if (department != null) {
            if (!scope.includesDepartment(department)) {
                throw new BusinessException(ErrorCode.ACCESS_DENIED);
            }
            args.put("dept", department);
            where += " AND u.department_id=:dept";
        }
        if (search != null && !search.isBlank()) {
            args.put("q", "%" + search.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
            where += " AND (u.user_name LIKE :q ESCAPE '!' OR u.login_id LIKE :q ESCAPE '!')";
        }
        long total = db.count("SELECT COUNT(*) FROM users u WHERE " + where, args);
        var rows = db.rows("""
          SELECT u.id,u.user_name,p.name AS title,d.name AS department,u.hire_date,
          COALESCE(l.granted,0) AS granted,COALESCE(l.used,0) AS used,COALESCE(l.reserved,0) AS reserved,COALESCE(l.adjusted,0) AS adjusted,COALESCE(l.available,0) AS available
          FROM users u JOIN departments d ON d.id=u.department_id JOIN positions p ON p.id=u.position_id LEFT JOIN (
            SELECT user_id,SUM(CASE WHEN entry_type='GRANT' THEN days ELSE 0 END) AS granted,
            -SUM(CASE WHEN entry_type='USE' THEN days ELSE 0 END) AS used,
            -SUM(CASE WHEN entry_type IN ('RESERVE','RELEASE') THEN days ELSE 0 END) AS reserved,
            SUM(CASE WHEN entry_type='ADJUST' THEN days ELSE 0 END) AS adjusted,SUM(days) AS available
            FROM leave_ledger WHERE leave_year=:year GROUP BY user_id
          ) l ON l.user_id=u.id WHERE
          """ + where + " ORDER BY u.user_name,u.id LIMIT :size OFFSET :offset", args).stream()
                .map(r -> map(
                        "user", map("id", r.get("id"), "name", r.get("user_name"), "title", r.get("title"),
                                "department", r.get("department")), "hireDate", r.get("hire_date"),
                        "hireDateMissing", r.get("hire_date") == null, "granted", r.get("granted"),
                        "used", r.get("used"), "reserved", r.get("reserved"), "adjusted", r.get("adjusted"),
                        "available", r.get("available")))
                        .toList();
        return EmployeeService.page(rows, page, size, total);
    }

    @Transactional(readOnly = true)
    public Object ledger(long user, Integer year) {
        access.assertUserInScope(MENU, user);
        return query.ledgerFor(user, year);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Map<String, Object> adjust(long user, int year, BigDecimal days, String memo) {
        access.assertUserInScope(MENU, user);
        year(year);
        if (days == null || days.signum() == 0 || days.remainder(new BigDecimal("0.5")).signum() != 0
                || days.abs().compareTo(
                new BigDecimal("9999999.5")) > 0 || memo == null || memo.isBlank() || memo.length() > 500) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        db.one("SELECT id FROM users WHERE id=:id FOR UPDATE", map("id", user), ErrorCode.USER_NOT_FOUND);
        var args = map("id", user, "year", year);
        var current = (BigDecimal) db.one(
                "SELECT COALESCE(SUM(days),0) AS balance FROM leave_ledger WHERE user_id=:id AND leave_year=:year",
                args, ErrorCode.USER_NOT_FOUND)
                .get("balance");
        if (current.add(days).signum() < 0) {
            throw new BusinessException(ErrorCode.LEAVE_BALANCE_NEGATIVE);
        }
        long id = db.insert(
                "INSERT INTO leave_ledger(user_id,leave_year,entry_type,days,memo,created_by,created_at) VALUES(:user,:year,'ADJUST',:days,:memo,:actor,:now)",
                map("user", user, "year", year, "days", days, "memo", memo, "actor", access.actor(),
                        "now", LocalDateTime.now(clock)));
        return map("id", id, "userId", user, "year", year, "available", current.add(days));
    }

    private void all() {
        if (access.scope(MENU).scope() != DataScope.ALL) {
            throw new BusinessException(ErrorCode.ACCESS_DENIED);
        }
    }

    public Object grant(LocalDate date) {
        all();
        return grants.run(date);
    }

    @Transactional(readOnly = true)
    public Object runs(int limit) {
        all();
        if (limit < 1 || limit > 200) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return db.rows(
                "SELECT id,job_name AS jobName,target_date AS targetDate,status,processed_count AS processedCount,skipped_count AS skippedCount,message,started_at AS startedAt,finished_at AS finishedAt FROM batch_runs WHERE job_name='LEAVE_GRANT' ORDER BY id DESC LIMIT :limit",
                map("limit", limit));
    }
}
