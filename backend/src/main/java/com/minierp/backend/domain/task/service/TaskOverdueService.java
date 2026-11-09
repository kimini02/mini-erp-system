package com.minierp.backend.domain.task.service;

import com.minierp.backend.global.batch.BatchRunRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TaskOverdueService {
    private final TaskOverdueWork work;
    private final BatchRunRecorder runs;
    private final Clock clock;
    public Map<String, Object> runToday() {
        var today = LocalDate.now(clock);
        var started = LocalDateTime.now(clock);
        try {
            var run = work.run(today);
            return runs.record("TASK_OVERDUE", today, "SUCCESS", run.processed(), run.skipped(), null, started);
        } catch (RuntimeException e) {
            runs.record("TASK_OVERDUE", today, "FAILED", 0, 0, "마감 알림에 실패했습니다. 서버 상태를 확인한 뒤 다시 실행하세요.", started);
            throw e;
        }
    }
}
