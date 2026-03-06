package com.mappo.controlplane;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

abstract class PostgresIntegrationTestBase {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("mappo_test")
        .withUsername("mappo")
        .withPassword("mappo");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void registerDatasourceProps(DynamicPropertyRegistry registry) {
        registry.add("MAPPO_JDBC_DATABASE_URL", postgres::getJdbcUrl);
        registry.add("MAPPO_DB_USER", postgres::getUsername);
        registry.add("MAPPO_DB_PASSWORD", postgres::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void resetDatabase() {
        Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
            .migrate();

        jdbcTemplate.execute("""
            TRUNCATE TABLE
              target_log_events,
              target_stage_records,
              target_execution_records,
              run_targets,
              run_guardrail_warnings,
              run_wave_order,
              runs,
              release_parameter_defaults,
              release_verification_hints,
              releases,
              marketplace_events,
              forwarder_logs,
              target_registrations,
              target_tags,
              targets
            CASCADE
            """);
    }
}
