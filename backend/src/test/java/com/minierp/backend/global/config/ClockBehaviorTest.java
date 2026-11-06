package com.minierp.backend.global.config;

import com.minierp.backend.domain.project.entity.Project;
import com.minierp.backend.domain.task.entity.Task;
import com.minierp.backend.domain.task.entity.TaskStatus;
import com.minierp.backend.global.entity.Priority;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import static org.assertj.core.api.Assertions.assertThat;

class ClockBehaviorTest {
    @Test
    void productionClockUsesSeoulEvenAtUtcDateBoundary() {
        Clock production = new ClockConfig().clock();
        assertThat(production.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
        Clock midnight = Clock.fixed(Instant.parse("2026-12-31T15:00:00Z"), production.getZone());
        assertThat(LocalDate.now(midnight)).isEqualTo(LocalDate.now(midnight.withZone(ZoneOffset.UTC)).plusDays(1));
    }

    @ParameterizedTest
    @ValueSource(strings = { "2026-04-06T00:00:00Z", "2026-12-31T15:00:00Z" })
    void changingFixedDatePreservesOverdueRules(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), new ClockConfig().clock().getZone());
        LocalDate today = LocalDate.now(clock);
        var project = Project.create("시계 검증", "", today.minusDays(7), today.plusDays(7), Priority.MEDIUM);
        var task = Task.create("지연 판정", "", today.minusDays(1), TaskStatus.DOING, Priority.MEDIUM, project);
        assertThat(task.isOverdue(today)).isTrue();
        assertThat(task.isOverdue(today.minusDays(1))).isFalse();
        task.changeStatus(TaskStatus.DONE);
        assertThat(task.isOverdue(today)).isFalse();
    }
}
