package com.minierp.backend.domain.leave.service;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.batches.enabled", havingValue = "true", matchIfMissing = true)
public class LeaveGrantScheduler {
    private final LeaveGrantService service;
    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Seoul")
    public void grant() {
        service.runToday();
    }
}
