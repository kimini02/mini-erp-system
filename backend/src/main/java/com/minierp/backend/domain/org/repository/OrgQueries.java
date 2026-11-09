package com.minierp.backend.domain.org.repository;

import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import static com.minierp.backend.global.persistence.SqlStore.map;

@Repository
@RequiredArgsConstructor
public class OrgQueries {
    private final SqlStore db;
    private final JdbcTemplate jdbc;

    public Map<String, Object> findDepartment(long id) {
        return db.one("SELECT * FROM departments WHERE id=:id", map("id", id), ErrorCode.DEPARTMENT_NOT_FOUND);
    }

    public List<Map<String, Object>> findDepartments() {
        return jdbc.queryForList("""
            SELECT d.id,d.name,d.code,d.parent_id,d.head_user_id,h.user_name AS head_name,p.name AS head_position
            FROM departments d LEFT JOIN users h ON h.id=d.head_user_id LEFT JOIN positions p ON p.id=h.position_id
            ORDER BY d.sort_order,d.id
            """);
    }

    public List<Map<String, Object>> findDepartmentMemberCounts() {
        return jdbc.queryForList("SELECT department_id,COUNT(*) AS members FROM users GROUP BY department_id");
    }

    public Map<String, Object> findHead(Object id) {
        return db.one(
                "SELECT u.id,u.user_name AS name,p.name AS position,d.name AS departmentName FROM users u JOIN positions p ON p.id=u.position_id JOIN departments d ON d.id=u.department_id WHERE u.id=:id",
                map("id", id), ErrorCode.USER_NOT_FOUND);
    }

    public long countDepartmentMembers(long id) {
        return db.count("SELECT COUNT(*) FROM users WHERE department_id=:id", map("id", id));
    }

    public long countChildDepartments(long id) {
        return db.count("SELECT COUNT(*) FROM departments WHERE parent_id=:id", map("id", id));
    }

    public Map<String, Object> findSchedule(long id) {
        return db.one(
                "SELECT id,name,TIME_FORMAT(start_time,'%H:%i') AS startTime,TIME_FORMAT(end_time,'%H:%i') AS endTime,late_grace_min AS lateGraceMin FROM work_schedules WHERE id=:id",
                map("id", id), ErrorCode.RESOURCE_NOT_FOUND);
    }

    public Map<String, Object> findDefaultSchedule() {
        return db.one("SELECT id FROM work_schedules WHERE is_default=1", Map.of(), ErrorCode.RESOURCE_NOT_FOUND);
    }

    public List<Map<String, Object>> findPositions() {
        return db.rows("SELECT id,name,rank_order AS rankOrder FROM positions ORDER BY rank_order,id", Map.of());
    }

    public Map<String, Object> findPosition(long id) {
        return db.one("SELECT id FROM positions WHERE id=:id", map("id", id), ErrorCode.POSITION_NOT_FOUND);
    }

    public long countPositionMembers(long id) {
        return db.count("SELECT COUNT(*) FROM users WHERE position_id=:id", map("id", id));
    }

    public List<Map<String, Object>> findHolidays(LocalDate start, LocalDate end) {
        return db.rows(
                "SELECT id,holiday_date AS date,name,substitute FROM holidays WHERE holiday_date>=:start AND holiday_date<:end ORDER BY holiday_date",
                map("start", start, "end", end));
    }

    public long countHolidaysOnDate(LocalDate date) {
        return db.count("SELECT COUNT(*) FROM holidays WHERE holiday_date=:date", map("date", date));
    }

    public Map<String, Object> findHolidayIdentity(long id) {
        return db.one("SELECT id FROM holidays WHERE id=:id", map("id", id), ErrorCode.RESOURCE_NOT_FOUND);
    }

    public Map<String, Object> findHoliday(long id) {
        return db.one("SELECT id,holiday_date AS date,name,substitute FROM holidays WHERE id=:id", map("id", id),
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    public List<Map<String, Object>> findHolidayDates(LocalDate start, LocalDate end) {
        return db.rows("SELECT holiday_date FROM holidays WHERE holiday_date BETWEEN :start AND :end",
                map("start", start, "end", end));
    }
}
