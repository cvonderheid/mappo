package com.mappo.controlplane;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@TestPropertySource(properties = {
    "mappo.marketplace-ingest-token=",
    "MAPPO_MARKETPLACE_INGEST_TOKEN=",
    "mappo.runtime-probe.enabled=false",
    "mappo.redis.enabled=false"
})
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
        registry.add("mappo.marketplace-ingest-token", () -> "");
        registry.add("MAPPO_MARKETPLACE_INGEST_TOKEN", () -> "");
        registry.add("mappo.runtime-probe.enabled", () -> "false");
        registry.add("mappo.redis.enabled", () -> "false");
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
              target_external_execution_handles,
              target_execution_records,
              run_targets,
              run_guardrail_warnings,
              run_wave_order,
              runs,
              release_parameter_defaults,
              release_verification_hints,
              releases,
              project_configuration_audit_events,
              release_webhook_deliveries,
              marketplace_events,
              forwarder_logs,
              target_runtime_probes,
              target_execution_config_entries,
              target_registrations,
              target_tags,
              targets
            CASCADE
            """);
    }
}
