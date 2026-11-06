package com.minierp.backend.domain.user.repository;

import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.map;

@Repository
@RequiredArgsConstructor
public class EmployeeStore {
    private final SqlStore db;

    public List<Map<String, Object>> lockChief() {
        return db.rows("SELECT id FROM roles WHERE code='CHIEF' FOR UPDATE", Map.of());
    }

    public List<Map<String, Object>> lockActiveChiefs() {
        return db.rows(
                "SELECT u.id FROM users u JOIN user_roles ur ON ur.user_id=u.id JOIN roles r ON r.id=ur.role_id WHERE u.status='ACTIVE' AND r.code='CHIEF' FOR UPDATE",
                Map.of());
    }

    public List<Map<String, Object>> lockHeadDepartments(long id) {
        return db.rows("SELECT id FROM departments WHERE head_user_id=:id FOR UPDATE", map("id", id));
    }

    public int insertLoginSequence(int year) {
        return db.update(
                "INSERT INTO login_id_sequences(hire_year,last_seq) VALUES(:year,0) ON DUPLICATE KEY UPDATE hire_year=VALUES(hire_year)",
                map("year", year));
    }

    public Map<String, Object> lockLoginSequence(int year) {
        return db.one("SELECT last_seq FROM login_id_sequences WHERE hire_year=:year FOR UPDATE", map("year", year),
                ErrorCode.RESOURCE_NOT_FOUND);
    }

    public int updateLoginSequence(int year, long next) {
        return db.update("UPDATE login_id_sequences SET last_seq=:next WHERE hire_year=:year",
                map("year", year, "next", next));
    }

    public long insertEmployee(
            String login,
            String name,
            String email,
            Long department,
            Long position,
            LocalDate hire,
            LocalDateTime now) {
        return db.insert("""
            INSERT INTO users(login_id,user_name,user_email,user_pw,department_id,position_id,hire_date,status,must_change_pw,token_version,
              created_at,updated_at)
            VALUES(:login,:name,:email,NULL,:department,:position,:hire,'ACTIVE',0,0,:now,:now)
            """,
                map("login", login, "name", name, "email", email, "department", department,
                        "position", position, "hire", hire, "now", now));
    }

    public int insertUserRole(long user, long role) {
        return db.update("INSERT INTO user_roles(user_id,role_id) VALUES(:user,:role)",
                map("user", user, "role", role));
    }

    public Map<String, Object> lockUser(long id) {
        return db.one("SELECT * FROM users WHERE id=:id FOR UPDATE", map("id", id), ErrorCode.USER_NOT_FOUND);
    }

    public int updateEmployee(
            long id,
            String name,
            String email,
            Long dept,
            Long position,
            LocalDate hire,
            LocalDateTime now) {
        return db.update(
                "UPDATE users SET user_name=COALESCE(:name,user_name),user_email=COALESCE(:email,user_email),department_id=COALESCE(:dept,department_id),position_id=COALESCE(:position,position_id),hire_date=COALESCE(:hire,hire_date),updated_at=:now WHERE id=:id",
                map("id", id, "name", name, "email", email, "dept", dept, "position", position,
                        "hire", hire, "now", now));
    }

    public int updateStatus(long id, String status, LocalDate date, int increment, LocalDateTime now) {
        return db.update(
                "UPDATE users SET retired_date=CASE WHEN :status='RETIRED' THEN :date WHEN :status<>'RETIRED' AND status='RETIRED' THEN NULL ELSE retired_date END,status=:status,token_version=token_version+:increment,updated_at=:now WHERE id=:id",
                map("id", id, "status", status, "date", date, "increment", increment, "now", now));
    }

    public int deleteUserRoles(long id) {
        return db.update("DELETE FROM user_roles WHERE user_id=:id", map("id", id));
    }

    public int insertAssignedRole(long id, long role) {
        return db.update("INSERT INTO user_roles(user_id,role_id) VALUES(:id,:role)", map("id", id, "role", role));
    }
}
