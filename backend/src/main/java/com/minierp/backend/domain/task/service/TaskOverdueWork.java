package com.minierp.backend.domain.task.service;

import com.minierp.backend.domain.notification.service.NotificationService;
import com.minierp.backend.domain.task.repository.TaskQueries;
import com.minierp.backend.domain.task.repository.TaskStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Service
@RequiredArgsConstructor
public class TaskOverdueWork {
    private final TaskQueries queries;
    private final TaskStore store;
    private final NotificationService notifications;
    private final Clock clock;
    public record Result(int processed, int skipped) {
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public Result run(LocalDate today) {
        int processed = 0;
        int skipped = 0;
        // Same first lock as all board writes; id ordering prevents cross-project lock inversion.
        var projects = queries.findOverdueProjects(today);
        for (var projectId : projects) {
            long project = number(projectId, "project_id");
            var locked = store.findLockedProject(project);
            if (locked.isEmpty()) {
                continue;
            }
            var tasks = queries.findOverdueTasks(project, today);
            for (var taskRow : tasks) {
                Object taskId = taskRow.get("id");
                Object user = taskRow.get("user_id");
                Object due = taskRow.get("end_date");
                var now = LocalDateTime.now(clock);
                // All claim writers hold the project lock; NOT EXISTS excludes previously claimed keys.
                // The unique key rejects any unexpected competing writer and rolls back the batch.
                int inserted = store.insertDueClaim(taskId, user, due, now);
                if (inserted != 1) {
                    skipped++;
                    continue;
                }
                long task = number(taskRow, "id");
                String title = taskRow.get("project_key") + "-" + taskRow.get("task_no") + " "
                        + taskRow.get("task_title");
                notifications.storeTaskOverdue(number(taskRow, "user_id"), title,
                        "/projects/" + project + "/board?taskId=" + task);
                store.insertOverdueHistory(taskId, user, now);
                processed++;
            }
        }
        return new Result(processed, skipped);
    }
}
