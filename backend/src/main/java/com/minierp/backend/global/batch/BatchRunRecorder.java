package com.minierp.backend.global.batch;

import com.minierp.backend.global.persistence.SqlStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.*;
import java.util.Map;
import com.minierp.backend.global.exception.ErrorCode;
import static com.minierp.backend.global.persistence.SqlStore.*;

@Service
@RequiredArgsConstructor
public class BatchRunRecorder {
    private final SqlStore db;
    private final Clock clock;
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Map<String, Object> record(
            String job,
            LocalDate date,
            String status,
            int processed,
            int skipped,
            String message,
            LocalDateTime started) {
        long id = db.insert(
                "INSERT INTO batch_runs(job_name,target_date,status,processed_count,skipped_count,message,started_at,finished_at) VALUES(:job,:date,:status,:processed,:skipped,:message,:started,:finished)",
                map("job", job, "date", date, "status", status, "processed", processed, "skipped", skipped,
                        "message", message, "started", started, "finished", LocalDateTime.now(clock)));
        return db.one(
                "SELECT id,job_name AS jobName,target_date AS targetDate,status,processed_count AS processedCount,skipped_count AS skippedCount,message,started_at AS startedAt,finished_at AS finishedAt FROM batch_runs WHERE id=:id",
                map("id", id), ErrorCode.INVALID_INPUT_VALUE);
    }
}
