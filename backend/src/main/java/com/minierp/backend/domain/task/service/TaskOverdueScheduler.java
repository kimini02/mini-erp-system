package com.minierp.backend.domain.task.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.batches.enabled", havingValue = "true", matchIfMissing = true)
public class TaskOverdueScheduler {
    private final TaskOverdueService service;
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void notifyOverdue() {
        service.runToday();
    }
}
