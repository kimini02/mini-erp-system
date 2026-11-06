package com.minierp.backend.domain.user.repository;

import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.map;

@Repository
@RequiredArgsConstructor
public class EmployeeQueries {
    private final SqlStore db;

    public Map<String, Object> findUser(long id) {
        return db.one("SELECT * FROM users WHERE id=:id", map("id", id), ErrorCode.USER_NOT_FOUND);
    }

    public long countPrivilegedRoles(long id) {
        return db.count(
                "SELECT COUNT(*) FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id=:id AND r.code<>'EMPLOYEE'",
                map("id", id));
    }

    public List<Map<String, Object>> findEmployeeViews(List<Long> ids) {
        return db.rows("""
            SELECT u.id,u.login_id,u.user_name,u.user_email,u.department_id,u.position_id,u.hire_date,u.retired_date,u.status,u.user_pw,
              (SELECT COALESCE(SUM(l.days),0) FROM leave_ledger l WHERE l.user_id=u.id) AS remaining_annual_leave,u.created_at,u.updated_at,d.name AS department_name,d.code AS department_code,p.name AS position_name
            FROM users u JOIN departments d ON d.id=u.department_id JOIN positions p ON p.id=u.position_id WHERE u.id IN (:ids)
            """,
                map("ids", ids));
    }

    public List<Map<String, Object>> findEmployeeRoles(List<Long> ids) {
        return db.rows(
                "SELECT ur.user_id,r.code,r.name FROM user_roles ur JOIN roles r ON r.id=ur.role_id WHERE ur.user_id IN (:ids) ORDER BY r.id",
                map("ids", ids));
    }

    public Map<String, Object> findDepartment(Long id) {
        return db.one("SELECT id FROM departments WHERE id=:id", map("id", id), ErrorCode.DEPARTMENT_NOT_FOUND);
    }

    public List<Map<String, Object>> findLoginSequence(int year) {
        return db.rows("SELECT last_seq FROM login_id_sequences WHERE hire_year=:year", map("year", year));
    }

    public List<Map<String, Object>> findRolesByCodes(Set<String> codes) {
        return db.rows("SELECT id,code FROM roles WHERE code IN (:codes)", map("codes", codes));
    }

    public Map<String, Object> findPositionName(Long id) {
        return db.one("SELECT name FROM positions WHERE id=:id", map("id", id), ErrorCode.POSITION_NOT_FOUND);
    }

    public long countEmail(String email) {
        return db.count("SELECT COUNT(*) FROM users WHERE user_email=:email", map("email", email));
    }

    public Map<String, Object> findPosition(Long id) {
        return db.one("SELECT id FROM positions WHERE id=:id", map("id", id), ErrorCode.POSITION_NOT_FOUND);
    }

    public Map<String, String> findSortColumns() {
        return Map.of("userName", "u.user_name", "loginId", "u.login_id", "hireDate", "u.hire_date",
                "status", "u.status");
    }

    public String findSortOrder(Map<String, String> columns, String[] parts) {
        return columns.get(parts[0]) + (parts.length == 2 ? " " + parts[1] : " ASC");
    }

    public List<Map<String, Object>> findActiveUsers(int size, String prefix, Long departmentId) {
        var args = map("size", size);
        String where = "u.status='ACTIVE'";
        if (prefix != null) {
            where += " AND (u.user_name LIKE :q ESCAPE '!' OR u.login_id LIKE :q ESCAPE '!')";
            args.put("q", prefix);
        }
        if (departmentId != null) {
            where += " AND u.department_id=:dept";
            args.put("dept", departmentId);
        }
        return db.rows(
                "SELECT u.id,u.user_name AS name,p.name AS title,d.name AS department,d.id AS departmentId FROM users u JOIN positions p ON p.id=u.position_id JOIN departments d ON d.id=u.department_id WHERE "
                        + where + " ORDER BY u.user_name,u.id LIMIT :size", args);
    }
}
