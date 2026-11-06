package com.minierp.backend.support;

import com.minierp.backend.domain.user.entity.User;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

public abstract class BoardTestSupport extends AttendanceTestSupport {
    protected long project(User owner, int count) {
        long id = db.insert(
                "INSERT INTO projects(title,project_key,start_date,end_date,status,priority,leader_id,created_at,last_task_seq) VALUES('측정',:key,:today,:end,'PROGRESS','MEDIUM',:leader,:now,:count)",
                map("key", UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(), "today", date,
                        "end", date.plusMonths(1), "leader", owner.getId(), "now", LocalDateTime.now(clock),
                        "count", count));
        for (long user : List.of(owner.getId(), employee.getId(), outsider.getId()).stream().distinct().toList()) {
            db.update("INSERT INTO project_members(project_id,user_id,created_at) VALUES(:p,:u,:now)",
                    map("p", id, "u", user, "now", LocalDateTime.now(clock)));
        }
        List<Object[]> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++) {
            rows.add(new Object[] {
                id, i, i * 1024L, date.plusMonths(1), LocalDateTime.now(clock)
            });
        }
        jdbc.batchUpdate(
                "INSERT INTO tasks(project_id,task_no,position,end_date,created_at,task_title,task_content,task_status,priority) VALUES(?,?,?,?,?,'카드','','DOING','MEDIUM')",
                rows);
        return id;
    }

    protected List<Long> taskIds(long project) {
        return jdbc.queryForList("SELECT id FROM tasks WHERE project_id=? ORDER BY task_no", Long.class, project);
    }

    protected long duplicates(long project) {
        return jdbc.queryForObject(
                "SELECT COUNT(*)-COUNT(DISTINCT position) FROM tasks WHERE project_id=? AND task_status='DOING'",
                Long.class, project);
    }
}
