package com.minierp.backend.domain.approval.repository;

import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.map;

@Repository
@RequiredArgsConstructor
public class FormQueries {
    private final SqlStore db;

    public List<Map<String, Object>> findForms() {
        return db.rows("""
          SELECT f.*,COUNT(CASE WHEN s.line_kind='APPROVER' THEN 1 END) AS approvers,
          COUNT(CASE WHEN s.line_kind='REFERENCE' THEN 1 END) AS refs
          FROM approval_forms f LEFT JOIN approval_form_steps s ON s.form_id=f.id GROUP BY f.id ORDER BY f.id
          """,
                Map.of());
    }

    public List<Map<String, Object>> findSteps(Object id) {
        return db.rows(
                "SELECT s.*,r.code AS role_code,r.name AS role_name,u.user_name FROM approval_form_steps s LEFT JOIN roles r ON r.id=s.role_id LEFT JOIN users u ON u.id=s.user_id WHERE s.form_id=:id ORDER BY s.step_order",
                map("id", id));
    }

    public Map<String, Object> findRole(String code) {
        return db.one("SELECT id FROM roles WHERE code=:code", map("code", code), ErrorCode.ROLE_NOT_FOUND);
    }

    public Map<String, Object> findUser(Long id) {
        return db.one("SELECT id FROM users WHERE id=:id", map("id", id), ErrorCode.USER_NOT_FOUND);
    }
}
