package com.minierp.backend.integration;

import com.fasterxml.jackson.databind.*;
import com.minierp.backend.BackendApplication;
import com.minierp.backend.domain.user.entity.*;
import com.minierp.backend.domain.user.repository.UserRepository;
import com.minierp.backend.domain.task.service.TaskOverdueService;
import com.minierp.backend.support.FixedClockTestConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.*;
import java.net.URI;
import java.net.http.*;
import java.security.SecureRandom;
import java.sql.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class M5ProductionTest {
    private static String secret() {
        byte[] b = new byte[32];
        new SecureRandom().nextBytes(b);
        return Base64.getEncoder().withoutPadding().encodeToString(b);
    }

    private static final String appPassword = secret();
    private static final String migratorPassword = secret();
    @Container
    static final MariaDBContainer<?> maria = new MariaDBContainer<>("mariadb:10.11").withDatabaseName("mini_erp")
            .withPassword(secret());
    @Container
    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
    static ConfigurableApplicationContext context;
    static JdbcTemplate jdbc;
    static ObjectMapper mapper;
    static int appPort;
    static int managementPort;
    static final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    @BeforeAll
    static void boot() throws Exception {
        // Real accounts, scoped to the disposable database. Passwords are bound, never logged.
        try (var admin = DriverManager.getConnection(maria.getJdbcUrl(), "root", maria.getPassword())) {
            for (var pair : Map.of("workflow_app", appPassword, "workflow_migrator", migratorPassword).entrySet()) {
                try (var statement = admin.prepareStatement("CREATE USER '" + pair.getKey()
                        + "'@'%' IDENTIFIED BY ?")) {
                    statement.setString(1, pair.getValue());
                    statement.execute();
                }
            }
            try (var statement = admin.createStatement()) {
                statement.execute("GRANT ALL PRIVILEGES ON mini_erp.* TO 'workflow_migrator'@'%'");
                statement.execute("GRANT SELECT,INSERT,UPDATE,DELETE ON mini_erp.* TO 'workflow_app'@'%'");
            }
        }
        Map<String, Object> settings = new HashMap<>();
        settings.put("DB_APP_USERNAME", "workflow_app");
        settings.put("DB_APP_PASSWORD", appPassword);
        settings.put("DB_MIGRATOR_USERNAME", "workflow_migrator");
        settings.put("DB_MIGRATOR_PASSWORD", migratorPassword);
        settings.put("JWT_SECRET", secret());
        settings.put("spring.datasource.url", maria.getJdbcUrl());
        settings.put("spring.data.redis.host", redis.getHost());
        settings.put("spring.data.redis.port", redis.getMappedPort(6379));
        settings.put("spring.datasource.hikari.connection-timeout", 1000);
        settings.put("server.port", 0);
        settings.put("management.server.port", 0);
        settings.put("app.batches.enabled", false);
        settings.put("INITIAL_ADMIN_LOGIN_ID", "");
        settings.put("INITIAL_ADMIN_NAME", "");
        settings.put("INITIAL_ADMIN_EMAIL", "");
        settings.put("INITIAL_ADMIN_PASSWORD", "");
        context = new SpringApplicationBuilder(BackendApplication.class, FixedClockTestConfiguration.class)
                .profiles("prod").properties("spring.config.location=classpath:application-prod.properties")
                .initializers(c -> c.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("isolated-production-test", settings))).run();
        jdbc = context.getBean(JdbcTemplate.class);
        mapper = context.getBean(ObjectMapper.class);
        appPort = context.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        managementPort = context.getEnvironment().getRequiredProperty("local.management.port", Integer.class);
    }

    @AfterAll
    static void close() {
        if (context != null) {
            context.close();
        }
    }

    static HttpResponse<String> request(int port, String method, String path, String token,
            Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15));
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (body == null) {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            builder.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        }
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    static JsonNode api(String method, String path, String token, Object body) throws Exception {
        var response = request(appPort, method, path, token, body);
        // Do not print payloads: successful login responses contain bearer tokens.
        assertThat(response.statusCode()).as(method + " " + path).isBetween(200, 299);
        return mapper.readTree(response.body()).path("data");
    }

    static User user(String name, UserRole role, String password) {
        var user = User.create(name, UUID.randomUUID().toString(), UUID.randomUUID() + "@example.test", context
                .getBean(PasswordEncoder.class).encode(password), "01", "사원");
        user.changeRole(role);
        return context.getBean(UserRepository.class).saveAndFlush(user);
    }

    @Test
    @Order(1)
    void separatedAccountsMigrateAndPerformRepresentativeWritesButRejectDdl() throws Exception {
        assertThat(jdbc.queryForObject("SELECT CURRENT_USER()", String.class)).startsWith("workflow_app@");
        assertThat(jdbc
                .queryForList("SELECT DISTINCT installed_by FROM flyway_schema_history WHERE version IS NOT NULL",
                String.class)).containsExactly("workflow_migrator");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success=1 AND version IS NOT NULL", Integer.class))
                .isEqualTo(6);
        String password = secret();
        var leader = user("M5 approver", UserRole.TEAM_LEADER, password);
        var employee = user("M5 drafter", UserRole.USER, password);
        var chief = user("M5 chief", UserRole.ADMIN, password);
        jdbc.update("UPDATE departments SET head_user_id=? WHERE id=2", leader.getId());
        jdbc.update("UPDATE departments SET head_user_id=? WHERE id=1", chief.getId());
        var today = LocalDate.now(context.getBean(Clock.class));
        jdbc.update(
                "INSERT INTO leave_ledger(user_id,leave_year,entry_type,days,memo,created_at) VALUES(?,?,'GRANT',10,'M5 test',?)",
                employee.getId(), today.getYear(), LocalDateTime.now(context.getBean(Clock.class)));
        String employeeToken = api("POST", "/api/v1/auth/login", null, Map
                .of("id", employee.getLoginId(), "password", password)).path("accessToken").asText();
        String leaderToken = api("POST", "/api/v1/auth/login", null, Map
                .of("id", leader.getLoginId(), "password", password)).path("accessToken").asText();
        String chiefToken = api("POST", "/api/v1/auth/login", null, Map
                .of("id", chief.getLoginId(), "password", password)).path("accessToken").asText();
        assertThat(employeeToken).isNotBlank();
        assertThat(leaderToken).isNotBlank();
        long doc = api("POST", "/api/v1/approval-documents", employeeToken, Map
                .of("formCode", "GENERAL", "title", "M5 DML", "body", Map.of("content", "DML verification"))).path("id")
                .asLong();
        api("POST", "/api/v1/approval-documents/" + doc + "/submit", employeeToken, null);
        api("POST", "/api/v1/approval-documents/" + doc + "/approve", leaderToken, Map.of());
        api("POST", "/api/v1/approval-documents/" + doc + "/approve", chiefToken, Map.of());
        assertThat(jdbc.queryForObject("SELECT status FROM approval_documents WHERE id=?", String.class, doc))
                .isEqualTo("APPROVED");
        api("POST", "/api/v1/attendance/clock-in", employeeToken, null);
        long project = api("POST", "/api/v1/projects", leaderToken, Map
                .of("key", "M5", "title", "M5 DML", "startDate", today, "endDate", today.plusMonths(1), "leaderId",
                leader.getId())).path("id").asLong();
        long task = api("POST", "/api/v1/projects/" + project + "/tasks", leaderToken, Map
                .of("title", "M5 overdue", "endDate", today.minusDays(1), "assigneeIds", List.of(leader.getId())))
                .path("id").asLong();
        api("POST", "/api/v1/tasks/" + task + "/move", leaderToken, Map.of("status", "DOING", "version", 0));
        context.getBean(TaskOverdueService.class).runToday();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM task_due_alerts WHERE task_id=?", Integer.class, task))
                .isEqualTo(1);
        try (var connection = jdbc.getDataSource().getConnection();
                var statement = connection.createStatement()) {
            for (String sql : List.of("DROP TABLE tasks", "ALTER TABLE tasks ADD COLUMN forbidden_m5 INT",
                    "CREATE TABLE forbidden_m5(id INT)")) {
                SQLException error = org.junit.jupiter.api.Assertions
                        .assertThrows(SQLException.class, () -> statement.execute(sql));
                assertThat(error.getErrorCode()).isEqualTo(1142);
                System.out.printf("M5_DDL sql=%s code=%d state=%s%n", sql, error.getErrorCode(), error.getSQLState());
            }
        }
        System.out.println(
                "M5_DB migrations=6 installed_by=workflow_migrator runtime=workflow_app writes=login,submit,approve,clock-in,move,overdue SUCCESS");
    }

    @Test
    @Order(2)
    void healthIsAnonymousAndOnlyOnManagementPortWithNoDetailsOrCors() throws Exception {
        var health = request(managementPort, "GET", "/actuator/health", null, null);
        assertThat(health.statusCode()).isEqualTo(200);
        assertThat(mapper.readTree(health.body())).isEqualTo(mapper.readTree("{\"status\":\"UP\"}"));
        for (String path : List.of("/actuator/health", "/actuator/env", "/swagger-ui/index.html", "/swagger-ui.html",
                "/v3/api-docs")) {
            assertThat(request(appPort, "GET", path, null, null).statusCode()).as(path).isEqualTo(404);
        }
        for (String path : List.of("/actuator", "/actuator/env", "/actuator/info", "/actuator/metrics")) {
            assertThat(request(managementPort, "GET", path, null, null).statusCode()).as(path).isEqualTo(404);
        }
        var cross = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + appPort + "/api/v1/auth/login"))
                .header("Origin", "http://localhost:5173").header("Access-Control-Request-Method", "POST")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).build();
        var response = http.send(cross, HttpResponse.BodyHandlers.ofString());
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
        System.out.println(
                "M5_HEALTH management=200 body={\"status\":\"UP\"} app_actuator=404 swagger=404 api_docs=404 other_management=404 cors_allow_origin=absent");
    }

    @Test
    @Order(3)
    void redisFailureReportsDownWithoutDetails() throws Exception {
        redis.getDockerClient().pauseContainerCmd(redis.getContainerId()).exec();
        try {
            var health = request(managementPort, "GET", "/actuator/health", null, null);
            assertThat(health.statusCode()).isEqualTo(503);
            assertThat(mapper.readTree(health.body())).isEqualTo(mapper.readTree("{\"status\":\"DOWN\"}"));
            System.out.println("M5_HEALTH redis_paused=503 body={\"status\":\"DOWN\"}");
        } finally {
            redis.getDockerClient().unpauseContainerCmd(redis.getContainerId()).exec();
        }
        long until = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        while (request(managementPort, "GET", "/actuator/health", null, null).statusCode() != 200 && System.nanoTime()
                < until) {
            Thread.sleep(100);
        }
        assertThat(request(managementPort, "GET", "/actuator/health", null, null).statusCode()).isEqualTo(200);
    }

    @Test
    @Order(4)
    void dbFailureReportsDownWithoutDetails() throws Exception {
        // Redis is healthy again; this failure is caused only by the DB.
        maria.stop();
        var contributor = context.getBean("dbHealthContributor", org.springframework.boot.actuate.health
                .HealthContributor.class);
        assertThat(((org.springframework.boot.actuate.health.HealthIndicator) contributor).health().getStatus()
                .getCode()).isEqualTo("DOWN");
        var health = request(managementPort, "GET", "/actuator/health", null, null);
        assertThat(health.statusCode()).isEqualTo(503);
        assertThat(mapper.readTree(health.body())).isEqualTo(mapper.readTree("{\"status\":\"DOWN\"}"));
        System.out.println("M5_HEALTH db_stopped=503 body={\"status\":\"DOWN\"} db_contributor=DOWN");
    }
}
