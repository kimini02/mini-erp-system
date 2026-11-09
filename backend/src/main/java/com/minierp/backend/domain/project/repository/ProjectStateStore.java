package com.minierp.backend.domain.project.repository;

import com.minierp.backend.global.persistence.SqlStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Repository
@RequiredArgsConstructor
public class ProjectStateStore {
    private final SqlStore db;
    private final Clock clock;
    /** Caller must hold this project's row lock before any task write. */
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<String, Object> recalculate(long project) {
        var counts = db.rows("SELECT task_status,COUNT(*) AS n FROM tasks WHERE project_id=:id GROUP BY task_status",
                map("id", project));
        long total = 0;
        long done = 0;
        long todo = 0;
        for (var row : counts) {
            long statusCount = number(row, "n");
            total += statusCount;
            if ("DONE".equals(row.get("task_status"))) {
                done = statusCount;
            }
            if ("TODO".equals(row.get("task_status"))) {
                todo = statusCount;
            }
        }
        String status = total == 0 || todo == total ? "READY" : done == total ? "DONE" : "PROGRESS";
        db.update("UPDATE projects SET status=:status,updated_at=:now WHERE id=:id",
                map("id", project, "status", status, "now", LocalDateTime.now(clock)));
        return map("id", project, "status", status, "taskCount", total, "doneCount", done,
                "progressPct", total == 0 ? 0 : done * 100 / total);
    }
}
