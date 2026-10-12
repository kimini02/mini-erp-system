package com.minierp.backend.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Extend for real MariaDB tests. Each subclass starts with a new empty database.
 * Do not run subclasses in parallel: the inherited container is shared.
 * Closing each context prevents cached connections to a stopped container.
 */
@SpringBootTest
@ActiveProfiles("mariadb")
@Testcontainers(disabledWithoutDocker = true)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class MariaDbIntegrationTest {

    @Container
    protected static final MariaDBContainer<?> DATABASE =
            new MariaDBContainer<>("mariadb:10.11")
                    .withDatabaseName("workflow_test");

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DATABASE::getJdbcUrl);
        registry.add("spring.datasource.username", DATABASE::getUsername);
        registry.add("spring.datasource.password", DATABASE::getPassword);
        registry.add("spring.datasource.driver-class-name", DATABASE::getDriverClassName);
    }
}
