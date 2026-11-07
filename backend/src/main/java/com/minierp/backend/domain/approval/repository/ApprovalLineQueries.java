package com.minierp.backend.domain.approval.repository;

import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.map;

@Repository
@RequiredArgsConstructor
public class ApprovalLineQueries {
    private final SqlStore db;

    public Map<String, Object> findForm(String code) {
        return db.one("SELECT * FROM approval_forms WHERE code=:code", map("code", code), ErrorCode.FORM_NOT_FOUND);
    }

    public Map<String, Object> findPerson(long id) {
        return db.one("""
        SELECT u.id,u.user_name AS name,p.name AS title,d.name AS department,u.department_id,u.status
        FROM users u JOIN positions p ON p.id=u.position_id JOIN departments d ON d.id=u.department_id WHERE u.id=:id
        """, map("id", id), ErrorCode.APPROVER_NOT_FOUND);
    }

    public List<Map<String, Object>> findSteps(Object id) {
        return db.rows("SELECT * FROM approval_form_steps WHERE form_id=:id ORDER BY step_order", map("id", id));
    }

    public List<Map<String, Object>> findRoleHolder(Object role) {
        return db.rows(
                "SELECT u.id FROM users u JOIN user_roles ur ON ur.user_id=u.id WHERE ur.role_id=:role AND u.status='ACTIVE' ORDER BY u.id LIMIT 1",
                map("role", role));
    }

    public Map<String, Object> findDepartmentName(Long id) {
        return db.one("SELECT name FROM departments WHERE id=:id", map("id", id), ErrorCode.APPROVER_NOT_FOUND);
    }

    public Map<String, Object> findDepartmentHead(Long id) {
        return db.one("SELECT parent_id,head_user_id FROM departments WHERE id=:id", map("id", id),
                ErrorCode.APPROVER_NOT_FOUND);
    }

    public Map<String, Object> findParent(long id) {
        return db.one("SELECT parent_id FROM departments WHERE id=:id", map("id", id), ErrorCode.APPROVER_NOT_FOUND);
    }
}
