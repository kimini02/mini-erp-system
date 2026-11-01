package com.minierp.backend.domain.user.repository;

import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.permission.ScopeFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Repository
@RequiredArgsConstructor
public class EmployeeRepository {
    private final SqlStore db;
    public record Selection(List<Long> ids, long total, Map<String, Long> counts) {
    }

    public Selection search(ScopeFilter scope, String q, List<Long> departments, String status, int page, int size,
            String sort) {
        Map<String, Object> args = new HashMap<>();
        String where = scope.predicate("u", args);
        if (q != null && !q.isBlank()) {
            args.put("q", q.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%");
            where += " AND (u.user_name LIKE :q ESCAPE '!' OR u.login_id LIKE :q ESCAPE '!')";
        }
        if (departments != null) {
            args.put("departments", departments);
            where += departments.isEmpty() ? " AND 1=0" : " AND u.department_id IN (:departments)";
        }
        Map<String, Long> counts = new LinkedHashMap<>(Map.of("ACTIVE", 0L, "ON_LEAVE", 0L, "RETIRED", 0L));
        for (var row : db.rows("SELECT u.status,COUNT(*) AS n FROM users u WHERE " + where + " GROUP BY u.status",
                args)) {
            counts.put((String) row.get("status"), number(row, "n"));
        }
        if (status != null) {
            args.put("status", status);
            where += " AND u.status=:status";
        }
        long total = db.count("SELECT COUNT(*) FROM users u WHERE " + where, args);
        args.put("size", size);
        args.put("offset", (long) page * size);
        List<Long> ids = db.rows("SELECT u.id FROM users u WHERE " + where + " ORDER BY " + sort
                + ",u.id LIMIT :size OFFSET :offset", args).stream().map(r -> number(r, "id")).toList();
        return new Selection(ids, total, counts);
    }
}
