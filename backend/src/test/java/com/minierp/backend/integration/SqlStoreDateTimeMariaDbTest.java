package com.minierp.backend.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.minierp.backend.domain.user.entity.User;
import com.minierp.backend.domain.user.entity.UserRole;
import com.minierp.backend.domain.user.repository.UserRepository;
import com.minierp.backend.global.exception.ErrorCode;
import com.minierp.backend.global.persistence.SqlStore;
import com.minierp.backend.global.security.JwtTokenProvider;
import com.minierp.backend.support.MariaDbIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SqlStoreDateTimeMariaDbTest extends MariaDbIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired JwtTokenProvider tokens;
    @Autowired JdbcTemplate jdbc;
    @Autowired SqlStore store;
    @Autowired Clock clock;
    private TimeZone previousZone;

    @BeforeEach void useUtcJvm() {
        previousZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
    }

    @AfterEach void restoreJvmZone() {
        TimeZone.setDefault(previousZone);
    }

    @Test void userListPreservesKoreanDatabaseDateTimeWithoutOffsetWithUtcJvm() throws Exception {
        String login = "datetime-" + UUID.randomUUID();
        User user = User.create("시각 검증", login, login + "@example.test", "unused-encoded", "01", "사원");
        user.changeRole(UserRole.ADMIN);
        users.saveAndFlush(user);
        LocalDateTime koreanTime = LocalDateTime.now(clock).withHour(21).withMinute(7).withSecond(29).withNano(213456000);
        jdbc.update("UPDATE users SET created_at=?, updated_at=NULL, hire_date=? WHERE id=?",
                koreanTime, koreanTime.toLocalDate(), user.getId());
        // Read text directly from MariaDB so the expected value cannot inherit a JDBC timezone conversion.
        String databaseTime = jdbc.queryForObject(
                "SELECT DATE_FORMAT(created_at,'%Y-%m-%dT%H:%i:%s.%f') FROM users WHERE id=?", String.class, user.getId());
        assertThat(databaseTime).isEqualTo(koreanTime.toString());
        String token = tokens.generateAccessToken(user.getId().toString(), user.getTokenVersion());
        var response = mvc.perform(get("/api/v1/users").param("q", login)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        var content = mapper.readTree(response).path("data").path("content");
        assertThat(content.size()).isEqualTo(1);
        assertThat(content.get(0).path("createdAt").asText()).isEqualTo(databaseTime);
        assertThat(content.get(0).path("hireDate").asText()).isEqualTo(koreanTime.toLocalDate().toString());
        assertThat(content.get(0).path("updatedAt").isNull()).isTrue();
    }

    @Test void rowsAndOneNormalizeTemporalValuesRegardlessOfColumnName() throws Exception {
        // M2 submittedAt/completedAt follow the same query boundary as M1 createdAt.
        String sql = """
                SELECT CAST(:at AS DATETIME(6)) AS submittedAt, CAST(:day AS DATE) AS workDate,
                       CAST(NULL AS DATETIME(6)) AS completedAt, 7 AS version, 'unchanged' AS title
                """;
        LocalDateTime expected = LocalDateTime.now(clock).withNano(123456000);
        var args = Map.of("at", expected, "day", expected.toLocalDate());
        var rows = store.rows(sql, args);
        var one = store.one(sql, args, ErrorCode.RESOURCE_NOT_FOUND);
        for (var row : java.util.List.of(rows.get(0), one)) {
            assertThat(row.get("submittedAt")).isEqualTo(expected);
            assertThat(row.get("workDate")).isEqualTo(expected.toLocalDate());
            assertThat(row.get("completedAt")).isNull();
            assertThat(((Number) row.get("version")).intValue()).isEqualTo(7);
            assertThat(row.get("title")).isEqualTo("unchanged");
            var json = mapper.readTree(mapper.writeValueAsString(row));
            assertThat(json.path("submittedAt").asText()).isEqualTo(expected.toString());
            assertThat(json.path("workDate").asText()).isEqualTo(expected.toLocalDate().toString());
            assertThat(json.path("completedAt").isNull()).isTrue();
        }
    }
}
