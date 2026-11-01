package com.minierp.backend.integration;

import com.minierp.backend.support.MariaDbIntegrationTest;
import com.minierp.backend.domain.user.entity.User;
import com.minierp.backend.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.TimeZone;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class AuditClockMariaDbTest extends MariaDbIntegrationTest {
    @Autowired
    UserRepository users;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    Clock clock;
    @Test
    void auditTimestampUsesInjectedClockWithUtcJvm() {
        TimeZone previous = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            String id = UUID.randomUUID().toString();
            User user = users.saveAndFlush(User
                    .create("시각 검증", id, id + "@example.test", "unused-encoded", "01", "사원"));
            assertThat(jdbc.queryForObject("SELECT created_at FROM users WHERE id = ?", LocalDateTime.class, user
                    .getId())).isEqualTo(LocalDateTime.now(clock));
            assertThat(users.findById(user.getId()).orElseThrow().getCreatedAt()).isEqualTo(LocalDateTime.now(clock));
        } finally {
            TimeZone.setDefault(previous);
        }
    }
}
