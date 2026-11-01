package com.minierp.backend.global.persistence;

import com.minierp.backend.global.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class SqlStore {
    private final NamedParameterJdbcTemplate jdbc;
    public List<Map<String, Object>> rows(String sql, Map<String, ?> args) {
        var rows = jdbc.queryForList(sql, args);
        // DATETIME/DATE hold Korean local values, not instants. Preserve those values
        // before Jackson can serialize JDBC dates using its timezone (including in M2).
        rows.forEach(row -> row.replaceAll((column, value) -> localTemporalValue(value)));
        return rows;
    }

    private static Object localTemporalValue(Object value) {
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate();
        }
        return value;
    }

    public Map<String, Object> one(String sql, Map<String, ?> args, ErrorCode error) {
        var rows = rows(sql, args);
        if (rows.isEmpty()) {
            throw new BusinessException(error);
        }
        return rows.get(0);
    }

    public long count(String sql, Map<String, ?> args) {
        return jdbc.queryForObject(sql, args, Long.class);
    }

    public int update(String sql, Map<String, ?> args) {
        return jdbc.update(sql, args);
    }

    public long insert(String sql, Map<String, ?> args) {
        var key = new GeneratedKeyHolder();
        jdbc.update(sql, new MapSqlParameterSource(args), key, new String[] {
            "id"
        });
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    public static long number(Map<String, Object> row, String key) {
        return ((Number) row.get(key)).longValue();
    }

    public static boolean bool(Map<String, Object> row, String key) {
        Object v = row.get(key);
        return v instanceof Boolean b ? b : ((Number) v).intValue() != 0;
    }

    public static Map<String, Object> map(Object... pairs) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            result.put((String) pairs[i], pairs[i + 1]);
        }
        return result;
    }
}
