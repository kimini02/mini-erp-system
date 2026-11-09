package com.minierp.backend.domain.attendance.repository;

import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.map;

@Repository
@RequiredArgsConstructor
public class AttendanceQueries {
    private final SqlStore db;

    public Map<String, Object> findMonthlyCounts(long user, LocalDate begin, LocalDate end) {
        return db.one(
                "SELECT COALESCE(SUM(in_result='LATE'),0) AS late_count,COALESCE(SUM(absent=1),0) AS absent_count FROM attendances WHERE user_id=:user AND work_date BETWEEN :begin AND :end",
                map("user", user, "begin", begin, "end", end), ErrorCode.USER_NOT_FOUND);
    }

    public List<Map<String, Object>> findMyMonth(long user, LocalDate start, LocalDate end) {
        return db.rows(
                "SELECT a.*,d.doc_no AS fix_doc_no FROM attendances a LEFT JOIN approval_documents d ON d.id=a.fix_document_id WHERE a.user_id=:user AND a.work_date BETWEEN :start AND :end",
                map("user", user, "start", start, "end", end));
    }

    public Map<String, Object> findDepartment(Long id) {
        return db.one("SELECT id FROM departments WHERE id=:id", map("id", id), ErrorCode.DEPARTMENT_NOT_FOUND);
    }

    public Map<String, Object> findUser(long id) {
        return db.one(
                "SELECT id,user_name,COALESCE(hire_date,DATE(created_at)) AS employed_from,retired_date FROM users WHERE id=:id",
                map("id", id), ErrorCode.USER_NOT_FOUND);
    }

    public List<Map<String, Object>> findAbsenceCandidates(LocalDate date) {
        return db.rows("""
          SELECT u.id FROM users u WHERE (u.status<>'ON_LEAVE' AND COALESCE(u.hire_date,DATE(u.created_at))<=:date AND (u.status<>'RETIRED' OR u.retired_date>:date) AND u.user_pw IS NOT NULL)
          OR EXISTS(SELECT 1 FROM attendances a WHERE a.user_id=u.id AND a.work_date=:date) ORDER BY u.id
          """, map("date", date));
    }
}
