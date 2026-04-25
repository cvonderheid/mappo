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
              targets,
              provider_connection_ado_projects,
              projects,
              release_ingest_endpoints,
              provider_connections,
              secret_references
            CASCADE
            """);

        seedIntegrationTestDefaults();
    }

    private void seedIntegrationTestDefaults() {
        jdbcTemplate.execute("""
            INSERT INTO public.secret_references (id, name, provider, usage, mode, backend_ref)
            VALUES
              ('ado-runtime-pat', 'Azure DevOps Runtime PAT', 'azure_devops', 'deployment_api_credential', 'mappo_default', 'mappo.azure-devops.personal-access-token'),
              ('ado-webhook-secret', 'Azure DevOps Webhook Secret', 'azure_devops', 'webhook_verification', 'mappo_default', 'mappo.azure-devops.webhook-secret'),
              ('github-release-webhook', 'GitHub Release Webhook Secret', 'github', 'webhook_verification', 'mappo_default', 'mappo.managed-app-release.webhook-secret')
            """);

        jdbcTemplate.execute("""
            INSERT INTO public.release_ingest_endpoints (
              id,
              name,
              provider,
              enabled,
              secret_ref,
              repo_filter,
              branch_filter,
              pipeline_id_filter,
              manifest_path
            )
            VALUES
              (
                'github-managed-app-default',
                'GitHub Managed App Default',
                'github',
                true,
                'secret:github-release-webhook',
                'example-org/mappo-release-catalog',
                'main',
                NULL,
                'releases/releases.manifest.json'
              ),
              (
                'ado-pipeline-default',
                'Azure DevOps Pipeline Default',
                'azure_devops',
                true,
                'secret:ado-webhook-secret',
                NULL,
                NULL,
                NULL,
                NULL
              )
            """);

        jdbcTemplate.execute("""
            INSERT INTO public.provider_connections (
              id,
              name,
              provider,
              enabled,
              organization_filter,
              personal_access_token_ref
            )
            VALUES (
              'ado-default',
              'Azure DevOps Default Connection',
              'azure_devops',
              true,
              NULL,
              'secret:ado-runtime-pat'
            )
            """);

        jdbcTemplate.execute("""
            INSERT INTO public.projects (
              id,
              name,
              access_strategy,
              access_strategy_config,
              deployment_driver,
              deployment_driver_config,
              release_artifact_source,
              release_artifact_source_config,
              runtime_health_provider,
              runtime_health_provider_config,
              release_ingest_endpoint_id,
              provider_connection_id,
              theme_key
            )
            VALUES
              (
                'azure-managed-app-deployment-stack',
                'Azure Managed App Deployment Stack',
                'azure_workload_rbac',
                '{"authModel":"provider_service_principal","requiresAzureCredential":true,"requiresTargetExecutionMetadata":true}'::jsonb,
                'azure_deployment_stack',
                '{"previewMode":"ARM_WHAT_IF","supportsPreview":true,"supportsExternalExecutionHandle":false}'::jsonb,
                'blob_arm_template',
                '{"descriptor":"blob_uri_manifest","templateUriField":"templateUri"}'::jsonb,
                'azure_container_app_http',
                '{"path":"/health","expectedStatus":200,"timeoutMs":5000}'::jsonb,
                'github-managed-app-default',
                NULL,
                'harbor-teal'
              ),
              (
                'azure-managed-app-template-spec',
                'Azure Managed App Template Spec',
                'azure_workload_rbac',
                '{"authModel":"provider_service_principal","requiresAzureCredential":true,"requiresTargetExecutionMetadata":true}'::jsonb,
                'azure_template_spec',
                '{"supportsPreview":false,"supportsExternalExecutionHandle":false}'::jsonb,
                'template_spec_resource',
                '{"descriptor":"template_spec_release","versionRefField":"sourceVersionRef"}'::jsonb,
                'azure_container_app_http',
                '{"path":"/health","expectedStatus":200,"timeoutMs":5000}'::jsonb,
                'github-managed-app-default',
                NULL,
                'vectr-signal'
              ),
              (
                'azure-appservice-ado-pipeline',
                'Azure App Service ADO Pipeline',
                'azure_workload_rbac',
                '{"authModel":"pipeline_owned","requiresAzureCredential":false,"requiresTargetExecutionMetadata":true}'::jsonb,
                'pipeline_trigger',
                '{"pipelineSystem":"azure_devops","organization":"","project":"","pipelineId":"","branch":"main","supportsExternalExecutionHandle":true,"supportsExternalLogs":true}'::jsonb,
                'external_deployment_inputs',
                '{"sourceSystem":"azure_devops","descriptorPath":"pipelineInputs","versionField":"artifactVersion"}'::jsonb,
                'http_endpoint',
                '{"path":"/health","expectedStatus":200,"timeoutMs":5000}'::jsonb,
                'ado-pipeline-default',
                'ado-default',
                'scalr-slate'
              )
            """);
    }
}
