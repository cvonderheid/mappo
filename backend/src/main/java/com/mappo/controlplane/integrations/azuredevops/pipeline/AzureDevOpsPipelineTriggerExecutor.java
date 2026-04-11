package com.mappo.controlplane.integrations.azuredevops.pipeline;

import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.domain.access.ResolvedTargetAccessContext;
import com.mappo.controlplane.domain.execution.DeploymentDriver;
import com.mappo.controlplane.integrations.azuredevops.pipeline.config.PipelineTriggerDriverConfig;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceType;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.model.ExternalExecutionHandleRecord;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.StageErrorDetailsRecord;
import com.mappo.controlplane.model.StageErrorRecord;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.service.providerconnection.ProviderConnectionCatalogService;
import com.mappo.controlplane.service.providerconnection.ProviderConnectionSecretResolver;
import com.mappo.controlplane.service.run.ReleaseMaterializerRegistry;
import com.mappo.controlplane.domain.execution.TargetDeploymentException;
import com.mappo.controlplane.domain.execution.TargetDeploymentOutcome;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(20)
@RequiredArgsConstructor
class AzureDevOpsPipelineTriggerExecutor implements DeploymentDriver {

    private static final String PROVIDER = "azure_devops_pipeline";

    private final ReleaseMaterializerRegistry releaseMaterializerRegistry;
    private final AzureDevOpsPipelineClient azureDevOpsPipelineClient;
    private final ProviderConnectionCatalogService providerConnectionCatalogService;
    private final ProviderConnectionSecretResolver providerConnectionSecretResolver;
    private final MappoProperties properties;

    @Override
    public boolean supports(ProjectDefinition project, ReleaseRecord release, boolean runtimeConfigured) {
        return project.deploymentDriver() == ProjectDeploymentDriverType.pipeline_trigger
            && project.releaseArtifactSource() == ProjectReleaseArtifactSourceType.external_deployment_inputs
            && release.sourceType() == MappoReleaseSourceType.external_deployment_inputs
            && project.deploymentDriverConfig() instanceof PipelineTriggerDriverConfig config
            && "azure_devops".equalsIgnoreCase(normalize(config.pipelineSystem()));
    }

    @Override
    public String verificationMessage(ProjectDefinition project, ReleaseRecord release) {
        return "Verification passed: external pipeline deployment completed successfully.";
    }

    @Override
    public TargetDeploymentOutcome deploy(
        String runId,
        ProjectDefinition project,
        ReleaseRecord release,
        TargetExecutionContextRecord target,
        ResolvedTargetAccessContext accessContext
    ) {
        String correlationId = correlationId(runId, target.targetId());
        AzureDevOpsPipelineInputs materializedInputs = releaseMaterializerRegistry.materialize(
            project,
            release,
            target,
            true,
            AzureDevOpsPipelineInputs.class
        );
        String personalAccessToken = resolvePersonalAccessToken(project, correlationId);
        AzureDevOpsPipelineInputs inputs = withPersonalAccessToken(materializedInputs, personalAccessToken);
        validateConfigured(inputs, target.targetId(), correlationId);

        AzureDevOpsPipelineRunRecord run;
        try {
            run = azureDevOpsPipelineClient.queueRun(inputs);
        } catch (AzureDevOpsClientException exception) {
            throw deploymentFailure(
                "ADO_PIPELINE_TRIGGER_FAILED",
                "Azure DevOps pipeline trigger request failed.",
                statusCode(exception),
                firstNonBlank(exception.getMessage(), exception.responseBody()),
                correlationId,
                null
            );
        }

        long pollTimeoutMs = normalizeMillis(properties.getAzureDevOps().getRunPollTimeoutMs(), 900_000L);
        long pollIntervalMs = normalizeMillis(properties.getAzureDevOps().getRunPollIntervalMs(), 5_000L);
        long startedAt = System.currentTimeMillis();
        while (!run.terminal()) {
            if (System.currentTimeMillis() - startedAt >= pollTimeoutMs) {
                throw deploymentFailure(
                    "ADO_PIPELINE_RUN_TIMEOUT",
                    "Azure DevOps pipeline run timed out while waiting for completion.",
                    null,
                    "runId=%s state=%s result=%s timeoutMs=%d".formatted(
                        run.runId(),
                        run.state(),
                        run.result(),
                        pollTimeoutMs
                    ),
                    correlationId,
                    run
                );
            }
            sleep(pollIntervalMs, correlationId, run);
            try {
                run = azureDevOpsPipelineClient.getRun(inputs, run.runId());
            } catch (AzureDevOpsClientException exception) {
                throw deploymentFailure(
                    "ADO_PIPELINE_TRIGGER_FAILED",
                    "Azure DevOps pipeline status request failed.",
                    statusCode(exception),
                    firstNonBlank(exception.getMessage(), exception.responseBody()),
                    correlationId,
                    run
                );
            }
        }

        ExternalExecutionHandleRecord handle = externalExecutionHandle(run);
        if (!run.succeeded()) {
            throw deploymentFailure(
                "ADO_PIPELINE_RUN_FAILED",
                "Azure DevOps pipeline run finished with status " + run.executionStatus() + ".",
                null,
                "runId=%s state=%s result=%s".formatted(run.runId(), run.state(), run.result()),
                correlationId,
                run
            );
        }

        return new TargetDeploymentOutcome(
            correlationId,
            "Azure DevOps pipeline run %s succeeded.".formatted(firstNonBlank(run.runName(), run.runId())),
            "",
            handle
        );
    }

    private void validateConfigured(AzureDevOpsPipelineInputs inputs, String targetId, String correlationId) {
        String pat = normalize(inputs.personalAccessToken());
        if (pat.isBlank()) {
            throw deploymentFailure(
                "ADO_PIPELINE_CONFIGURATION_INVALID",
                "Azure DevOps execution is not configured: missing deployment connection PAT secret.",
                null,
                "personalAccessToken is blank after resolving linked deployment connection PAT",
                correlationId,
                null
            );
        }

        if (blank(inputs.organization()) || blank(inputs.project()) || blank(inputs.pipelineId())) {
            throw deploymentFailure(
                "ADO_PIPELINE_CONFIGURATION_INVALID",
                "Azure DevOps execution is not configured for target " + targetId + ".",
                null,
                "organization, project, or pipelineId is blank",
                correlationId,
                null
            );
        }

        Map<String, String> parameters = inputs.templateParameters();
        String resourceGroup = firstNonBlank(
            parameters == null ? "" : parameters.get("targetResourceGroup"),
            parameters == null ? "" : parameters.get("resourceGroup")
        );
        String appName = firstNonBlank(
            parameters == null ? "" : parameters.get("targetAppName"),
            parameters == null ? "" : parameters.get("appServiceName")
        );
        if (resourceGroup.isBlank() || appName.isBlank()) {
            throw deploymentFailure(
                "ADO_PIPELINE_CONFIGURATION_INVALID",
                "Target execution metadata is incomplete for Azure DevOps pipeline deployment.",
                null,
                "targetResourceGroup/resourceGroup or targetAppName/appServiceName is blank",
                correlationId,
                null
            );
        }
    }

    private String resolvePersonalAccessToken(ProjectDefinition project, String correlationId) {
        String providerConnectionId = normalize(project.providerConnectionId());
        if (providerConnectionId.isBlank()) {
            throw deploymentFailure(
                "ADO_PIPELINE_CONFIGURATION_INVALID",
                "Azure DevOps execution is not configured: project has no linked deployment connection.",
                null,
                "project.providerConnectionId is blank",
                correlationId,
                null
            );
        }
        var connection = providerConnectionCatalogService.getRequired(providerConnectionId);
        if (connection.provider() == null || !"azure_devops".equals(connection.provider().name())) {
            throw deploymentFailure(
                "ADO_PIPELINE_CONFIGURATION_INVALID",
                "Azure DevOps execution is not configured: linked deployment connection is not Azure DevOps.",
                null,
                "providerConnectionId=%s provider=%s".formatted(providerConnectionId, connection.provider()),
                correlationId,
                null
            );
        }
        String token = normalize(providerConnectionSecretResolver.resolvePersonalAccessToken(connection));
        if (token.isBlank()) {
            throw deploymentFailure(
                "ADO_PIPELINE_CONFIGURATION_INVALID",
                "Azure DevOps execution is not configured: linked deployment connection PAT secret is unresolved.",
                null,
                "providerConnectionId=%s personalAccessTokenRef=%s".formatted(
                    providerConnectionId,
                    connection.personalAccessTokenRef()
                ),
                correlationId,
                null
            );
        }
        return token;
    }

    private AzureDevOpsPipelineInputs withPersonalAccessToken(
        AzureDevOpsPipelineInputs inputs,
        String personalAccessToken
    ) {
        return new AzureDevOpsPipelineInputs(
            normalize(inputs.organization()),
            normalize(inputs.project()),
            normalize(inputs.pipelineId()),
            normalize(inputs.branch()),
            normalize(inputs.descriptorPath()),
            normalize(inputs.versionField()),
            normalize(inputs.azureServiceConnectionName()),
            normalize(personalAccessToken),
            normalize(inputs.targetTenantId()),
            normalize(inputs.targetSubscriptionId()),
            normalize(inputs.targetId()),
            normalize(inputs.releaseId()),
            normalize(inputs.releaseVersion()),
            inputs.templateParameters()
        );
    }

    private void sleep(long pollIntervalMs, String correlationId, AzureDevOpsPipelineRunRecord run) {
        try {
            Thread.sleep(pollIntervalMs);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw deploymentFailure(
                "ADO_PIPELINE_TRIGGER_FAILED",
                "Azure DevOps pipeline polling was interrupted.",
                null,
                "runId=%s state=%s result=%s".formatted(
                    run == null ? "" : run.runId(),
                    run == null ? "" : run.state(),
                    run == null ? "" : run.result()
                ),
                correlationId,
                run
            );
        }
    }

    private TargetDeploymentException deploymentFailure(
        String code,
        String message,
        Integer statusCode,
        String detail,
        String correlationId,
        AzureDevOpsPipelineRunRecord run
    ) {
        ExternalExecutionHandleRecord handle = run == null ? null : externalExecutionHandle(run);
        return new TargetDeploymentException(
            message,
            new StageErrorRecord(
                code,
                message,
                new StageErrorDetailsRecord(
                    statusCode,
                    normalize(detail),
                    null,
                    code,
                    message,
                    null,
                    null,
                    correlationId,
                    run == null ? null : run.runName(),
                    run == null ? null : run.runId(),
                    null
                )
            ),
            correlationId,
            "",
            handle
        );
    }

    private ExternalExecutionHandleRecord externalExecutionHandle(AzureDevOpsPipelineRunRecord run) {
        return new ExternalExecutionHandleRecord(
            PROVIDER,
            normalize(run.runId()),
            firstNonBlank(run.runName(), run.runId()),
            firstNonBlank(run.executionStatus(), "unknown"),
            normalize(run.webUrl()),
            normalize(run.logsUrl()),
            OffsetDateTime.now(ZoneOffset.UTC)
        );
    }

    private int statusCode(AzureDevOpsClientException exception) {
        return exception.statusCode() <= 0 ? 502 : exception.statusCode();
    }

    private long normalizeMillis(long value, long fallback) {
        return value > 0 ? value : fallback;
    }

    private String correlationId(String runId, String targetId) {
        return "corr-" + normalize(runId) + "-" + normalize(targetId) + "-deploying";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!blank(value)) {
                return normalize(value);
            }
        }
        return "";
    }

    private boolean blank(String value) {
        return normalize(value).isBlank();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
