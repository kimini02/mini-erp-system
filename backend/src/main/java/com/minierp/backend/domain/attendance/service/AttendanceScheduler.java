package com.minierp.backend.domain.attendance.service;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor @ConditionalOnProperty(name="app.batches.enabled",havingValue="true",matchIfMissing=true)
public class AttendanceScheduler {
    private final AbsenceBatchService absence;
    @Scheduled(cron="0 10 0 * * *",zone="Asia/Seoul") public void absence() {absence.runPreviousDay();}
}
