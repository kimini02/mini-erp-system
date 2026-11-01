package com.minierp.backend.domain.attendance.service;

import com.minierp.backend.global.persistence.SqlStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class AttendanceMonthlyQuery {
    private final SqlStore db;
    public String sql(String scope) {
        return """
          WITH RECURSIVE dates(work_date) AS (
            SELECT CAST(:start AS DATE) UNION ALL SELECT work_date+INTERVAL 1 DAY FROM dates WHERE work_date<:end
          ), selected_users AS (
            SELECT u.* FROM users u WHERE
            (u.status<>'RETIRED' OR u.retired_date>:start OR (u.retired_date IS NULL AND EXISTS(
              SELECT 1 FROM attendances old WHERE old.user_id=u.id AND old.work_date BETWEEN :start AND :end))) AND
          """ + scope + """
          )
          SELECT dates.work_date AS cell_date,u.id AS user_id,u.user_name,p.name AS title,d.name AS department,
            COALESCE(u.hire_date,DATE(u.created_at)) AS employed_from,u.retired_date,
            h.name AS holiday_name,IF(DAYOFWEEK(dates.work_date) IN (1,7) OR h.id IS NOT NULL,'HOLIDAY','WORKDAY') AS calendar_type,
            a.clock_in_at,a.clock_out_at,a.in_result,a.out_result,COALESCE(a.absent,0) AS absent,
            COALESCE(a.day_type,CASE WHEN DAYOFWEEK(dates.work_date) IN (1,7) OR h.id IS NOT NULL THEN 'HOLIDAY'
              WHEN l.leave_type IN ('HALF_AM','HALF_PM') THEN l.leave_type WHEN l.document_id IS NOT NULL THEN 'LEAVE' ELSE 'WORKDAY' END) AS day_type
          FROM dates LEFT JOIN selected_users u ON 1=1
          LEFT JOIN positions p ON p.id=u.position_id LEFT JOIN departments d ON d.id=u.department_id
          LEFT JOIN holidays h ON h.holiday_date=dates.work_date
          LEFT JOIN attendances a ON a.user_id=u.id AND a.work_date=dates.work_date
          LEFT JOIN leave_requests l ON l.document_id=(
            SELECT MIN(ld.id) FROM leave_requests lr JOIN approval_documents ld ON ld.id=lr.document_id
            WHERE ld.drafter_id=u.id AND ld.status='APPROVED' AND lr.start_date<=dates.work_date AND lr.end_date>=dates.work_date)
          ORDER BY u.user_name,u.id,dates.work_date
          """;
    }

    public List<Map<String, Object>> fetch(String scope, Map<String, Object> args) {
        return db.rows(sql(scope), args);
    }
}
