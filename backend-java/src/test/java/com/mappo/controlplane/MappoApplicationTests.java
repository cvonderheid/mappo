package com.mappo.controlplane;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class MappoApplicationTests {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("mappo_test")
        .withUsername("mappo")
        .withPassword("mappo");

    @DynamicPropertySource
    static void registerDatasourceProps(DynamicPropertyRegistry registry) {
        registry.add("MAPPO_JDBC_DATABASE_URL", postgres::getJdbcUrl);
        registry.add("MAPPO_DB_USER", postgres::getUsername);
        registry.add("MAPPO_DB_PASSWORD", postgres::getPassword);
    }

    @Test
    void contextLoads() {
    }
}
