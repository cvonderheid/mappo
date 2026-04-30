# External System Contract Inventory

This inventory covers external-system boundaries that require installation or operator configuration and where MAPPO owns a custom request/configuration schema.

Out of scope:
- raw Azure SDK calls as standalone integrations
- infrastructure bootstrap details such as ACR image push, Pulumi stack outputs, database migrations, or EasyAuth setup
- ordinary internal product API calls that do not configure or receive data from an external system
- generated frontend OpenAPI output; the generated file can help consumers, but it is not the source contract

## Contract ownership

The entries below are not all the same kind of contract.

- **MAPPO-owned contracts** are payloads or config objects this repo invented and must keep stable for operators, scripts, forwarders, or publisher repos.
- **External-system-native contracts** are GitHub, Azure DevOps, or Azure shapes that MAPPO consumes or calls. MAPPO does not own those whole schemas.
- **Adapter contracts** are the subset and interpretation MAPPO applies to an external-system-native payload. For example, GitHub owns the webhook payload, but MAPPO owns which fields it reads and how those fields map into a release ingest operation.

When this document lists a webhook or Azure DevOps API, read it as the MAPPO integration contract unless it explicitly says the whole payload is MAPPO-owned.

## Inventory

| External system or boundary | Direction | Ownership | Custom schema MAPPO owns | Operator/install configuration |
| --- | --- | --- | --- | --- |
| GitHub release source | GitHub -> MAPPO, then MAPPO -> GitHub | Mixed: GitHub owns webhook delivery; MAPPO owns release source config, field interpretation, and manifest format | Release source config, GitHub webhook fields MAPPO reads, `releases.manifest.json` rows | Release Source provider, webhook secret, repository/branch filters, manifest path |
| Azure DevOps release-ready source | Azure DevOps -> MAPPO | Mixed: ADO owns service-hook envelope; MAPPO owns release source config and field interpretation | Release source config and ADO service-hook fields MAPPO reads | Release Source provider, webhook secret, branch/pipeline filters |
| Azure DevOps deployment connection and discovery | MAPPO -> Azure DevOps | Mixed: ADO owns organization/project/PAT semantics; MAPPO owns deployment connection records and secret reference rules | Deployment connection config and PAT secret-reference format | Deployment Connection provider, organization URL, PAT reference |
| Azure DevOps deployment pipeline trigger | MAPPO -> Azure DevOps | Mixed: ADO owns queue-run API; MAPPO owns project config and `templateParameters` payload content | Project deployment config and pipeline `templateParameters` payload | Project deployment driver, linked ADO connection, project/repository/pipeline/branch |
| Secret references, including Azure Key Vault-backed secrets | MAPPO -> runtime secret source | MAPPO-owned pointer schema over runtime secret stores | Secret reference records and `secret:`, `env:`, `kv:` backend-reference formats | Secret Reference provider, usage, mode, backend reference |
| Azure project deployment/runtime configuration | MAPPO -> Azure and target runtime | MAPPO-owned config that selects Azure behavior; Azure owns ARM/resource schemas | Project access/deployment/artifact/runtime-health config objects | Project configuration for Azure deployment stack/template spec or runtime health |
| Target onboarding and marketplace forwarder ingest | External forwarder/importer/operator -> MAPPO | MAPPO-owned inbound API for forwarders/importers/operators | Onboarding event payload, forwarder log payload, target metadata patch payload | Ingest token, project id, target tenant/subscription/runtime metadata |

## Shared frontend API locations

Most frontend screens call typed helpers in:
- `frontend/src/lib/api.ts`
- `frontend/src/lib/types.ts`

The generated OpenAPI schema is in `frontend/src/lib/api/generated/schema.ts`. Treat it as generated consumer support, not as the source of truth.

## GitHub Release Source

Purpose: configure a release source that receives GitHub webhooks, verifies the webhook secret, and fetches a release manifest from the configured repository/ref/path.

Backend contract files:
- `backend/src/main/java/com/mappo/controlplane/api/ReleaseIngestController.java`
- `backend/src/main/java/com/mappo/controlplane/api/request/ReleaseIngestEndpointCreateRequest.java`
- `backend/src/main/java/com/mappo/controlplane/api/request/ReleaseIngestEndpointPatchRequest.java`
- `backend/src/main/java/com/mappo/controlplane/integrations/github/release/GithubReleaseIngestProviderDescriptor.java`
- `backend/src/main/java/com/mappo/controlplane/integrations/github/release/GithubReleaseWebhookHandlerImpl.java`
- `backend/src/main/java/com/mappo/controlplane/service/releaseingest/ReleaseIngestEndpointMutationService.java`

Frontend contract files:
- `frontend/src/components/ReleaseIngestConfigPage.tsx`
- `frontend/src/components/ProjectSettingsPage.tsx`
- `frontend/src/components/ProjectFlowContracts.ts`
- `frontend/src/lib/api.ts`

Related scripts/docs:
- `scripts/github_release_webhook_bootstrap.sh`
- `scripts/release_ingest_from_repo.sh`
- `docs/operator-guide.md`

Configure the release source:

```bash
curl -sS -X POST "$MAPPO_API_BASE_URL/api/v1/release-ingest/endpoints" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "GitHub Managed App Default",
    "provider": "github",
    "enabled": true,
    "secretRef": "kv:mappo-github-webhook-secret",
    "repoFilter": "example-org/mappo-release-catalog",
    "branchFilter": "main",
    "manifestPath": "releases/releases.manifest.json"
  }'
```

Example webhook call:

```bash
payload='{"ref":"refs/heads/main","repository":{"full_name":"example-org/mappo-release-catalog"},"commits":[{"modified":["releases/releases.manifest.json"]}]}'
sig="sha256=$(printf '%s' "$payload" | openssl dgst -sha256 -hmac "$GITHUB_WEBHOOK_SECRET" -binary | xxd -p -c 256)"

curl -sS -X POST "$MAPPO_API_BASE_URL/api/v1/release-ingest/endpoints/$ENDPOINT_ID/webhooks/github" \
  -H "Content-Type: application/json" \
  -H "x-github-event: push" \
  -H "x-github-delivery: demo-delivery-001" \
  -H "x-hub-signature-256: $sig" \
  -d "$payload"
```

## GitHub Release Manifest

Purpose: define release rows that MAPPO can ingest from GitHub manually or through the GitHub release source.

Ownership: this is a MAPPO-owned publisher contract. GitHub only stores and serves the file; GitHub does not define `releases.manifest.json`.

Default path: `releases/releases.manifest.json` is MAPPO's default convention from backend runtime configuration. Operators can override it with the Release Source `manifestPath` or manual ingest `path`.

Backend contract files:
- `backend/src/main/java/com/mappo/controlplane/api/AdminController.java`
- `backend/src/main/java/com/mappo/controlplane/api/request/ReleaseManifestIngestRequest.java`
- `backend/src/main/java/com/mappo/controlplane/service/release/ReleaseManifestParser.java`
- `backend/src/main/java/com/mappo/controlplane/service/release/ReleaseManifestRowParser.java`
- `backend/src/main/java/com/mappo/controlplane/service/release/ParsedReleaseManifest.java`
- `backend/src/main/java/com/mappo/controlplane/integrations/github/release/HttpGithubReleaseManifestSourceClient.java`

Frontend contract files:
- `frontend/src/components/ReleaseIngestDrawer.tsx`
- `frontend/src/components/ReleasesPage.tsx`
- `frontend/src/components/ProjectFlowContracts.ts`
- `frontend/src/lib/api.ts`

Accepted document shapes:

```json
[
  { "source_ref": "...", "source_version": "...", "source_type": "...", "source_version_ref": "..." }
]
```

or:

```json
{
  "releases": [
    { "source_ref": "...", "source_version": "...", "source_type": "...", "source_version_ref": "..." }
  ]
}
```

Required manifest row fields:
- `source_ref` or `sourceRef`: stable logical artifact-family id.
- `source_version` or `sourceVersion`: operator-visible release version.
- `source_type` or `sourceType`: one of `template_spec`, `bicep`, `deployment_stack`, `external_deployment_inputs`.
- `source_version_ref` or `sourceVersionRef`: immutable artifact reference MAPPO can fetch or pass through at deployment time.

Common optional fields:
- `deployment_scope` or `deploymentScope`: `resource_group` by default; also supports `subscription`.
- `execution_settings`, `executionSettings`, or `deployment_mode_settings`.
- `execution_settings.arm_mode` or `armMode`: `incremental` by default; also supports `complete`.
- `execution_settings.what_if_on_canary` or `whatIfOnCanary`: defaults to `false`.
- `execution_settings.verify_after_deploy` or `verifyAfterDeploy`: defaults to `true`.
- `parameter_defaults` or `parameterDefaults`: string map of release-level deployment parameters.
- `external_inputs`, `externalInputs`, `deployment_inputs`, `deploymentInputs`, `pipeline_inputs`, or `pipelineInputs`: string map of external pipeline/deployment values.
- `verification_hints` or `verificationHints`: string array.
- `release_notes` or `releaseNotes`: free text.

Manual ingest call:

```bash
curl -sS -X POST "$MAPPO_API_BASE_URL/api/v1/admin/releases/ingest/github" \
  -H "Content-Type: application/json" \
  -d '{
    "repo": "example-org/mappo-release-catalog",
    "ref": "main",
    "path": "releases/releases.manifest.json",
    "allowDuplicates": false,
    "projectId": "azure-managed-app-deployment-stack"
  }'
```

Example `releases.manifest.json`:

```json
[
  {
    "source_ref": "github://example-org/mappo-release-catalog/managed-app/mainTemplate.json",
    "source_version": "2026.04.30.1",
    "source_type": "deployment_stack",
    "source_version_ref": "https://raw.githubusercontent.com/example-org/mappo-release-catalog/main/managed-app/mainTemplate.json",
    "deployment_scope": "resource_group",
    "execution_settings": {
      "arm_mode": "incremental",
      "what_if_on_canary": false,
      "verify_after_deploy": true
    },
    "parameter_defaults": {
      "containerImage": "contoso.azurecr.io/app:2026.04.30.1"
    },
    "verification_hints": ["Check /health after rollout"]
  }
]
```

## Azure DevOps Release-Ready Source

Purpose: configure Azure DevOps service hooks that tell MAPPO a release-ready pipeline completed successfully.

Backend contract files:
- `backend/src/main/java/com/mappo/controlplane/api/ReleaseIngestController.java`
- `backend/src/main/java/com/mappo/controlplane/api/request/ReleaseIngestEndpointCreateRequest.java`
- `backend/src/main/java/com/mappo/controlplane/api/request/ReleaseIngestEndpointPatchRequest.java`
- `backend/src/main/java/com/mappo/controlplane/integrations/azuredevops/release/AzureDevOpsReleaseIngestProviderDescriptor.java`
- `backend/src/main/java/com/mappo/controlplane/integrations/azuredevops/release/AzureDevOpsReleaseWebhookHandlerImpl.java`
- `backend/src/main/java/com/mappo/controlplane/integrations/azuredevops/release/AzureDevOpsReleaseWebhookPayloadRecord.java`
- `backend/src/main/java/com/mappo/controlplane/integrations/azuredevops/release/AzureDevOpsReleaseWebhookPayloadService.java`
- `backend/src/main/java/com/mappo/controlplane/service/releaseingest/ReleaseIngestEndpointMutationService.java`

Frontend contract files:
- `frontend/src/components/ReleaseIngestConfigPage.tsx`
- `frontend/src/components/ProjectSettingsPage.tsx`
- `frontend/src/components/ProjectFlowContracts.ts`
- `frontend/src/lib/api.ts`

Related scripts/docs:
- `scripts/ado_release_hook_configure.sh`
- `scripts/release_source_configure_ado.sh`
- `docs/azure-devops-pipeline-project-setup.md`

Configure the release source:

```bash
curl -sS -X POST "$MAPPO_API_BASE_URL/api/v1/release-ingest/endpoints" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "ADO Release Readiness",
    "provider": "azure_devops",
    "enabled": true,
    "secretRef": "kv:mappo-ado-webhook-secret",
    "branchFilter": "main",
    "pipelineIdFilter": "42"
  }'
```

Example service-hook call:

```bash
curl -sS -X POST "$MAPPO_API_BASE_URL/api/v1/release-ingest/endpoints/$ENDPOINT_ID/webhooks/ado?projectId=azure-appservice-ado-pipeline&token=$ADO_WEBHOOK_SECRET" \
  -H "Content-Type: application/json" \
  -H "x-event-type: ms.vss-pipelines.run-state-changed-event" \
  -H "x-vss-deliveryid: demo-ado-delivery-001" \
  -d '{
    "eventType": "ms.vss-pipelines.run-state-changed-event",
    "resource": {
      "run": {
        "id": "123",
        "name": "2026.04.30.1",
        "state": "completed",
        "result": "succeeded",
        "_links": {
          "web": {
            "href": "https://dev.azure.com/example-org/sample/_build/results?buildId=123"
          }
        },
        "resources": {
          "repositories": {
            "self": {
              "refName": "refs/heads/main"
            }
          }
        }
      },
      "pipeline": {
        "id": "42",
        "name": "Release Readiness"
      }
    },
    "resourceContainers": {
      "project": {
        "baseUrl": "https://dev.azure.com/example-org",
        "name": "sample-app-service"
      }
    }
  }'
```

## Azure DevOps Deployment Connection

Purpose: configure outbound authenticated access that MAPPO can use for Azure DevOps project discovery and deployment pipeline triggers.

Backend contract files:
- `backend/src/main/java/com/mappo/controlplane/api/ProviderConnectionsController.java`
- `backend/src/main/java/com/mappo/controlplane/api/request/ProviderConnectionCreateRequest.java`
- `backend/src/main/java/com/mappo/controlplane/api/request/ProviderConnectionPatchRequest.java`
- `backend/src/main/java/com/mappo/controlplane/api/request/ProviderConnectionVerifyRequest.java`
- `backend/src/main/java/com/mappo/controlplane/integrations/azuredevops/connection/AzureDevOpsProviderConnectionProviderDescriptor.java`
- `backend/src/main/java/com/mappo/controlplane/integrations/azuredevops/connection/DefaultAzureDevOpsProviderConnectionDiscoveryHandler.java`
- `backend/src/main/java/com/mappo/controlplane/service/providerconnection/ProviderConnectionMutationService.java`
- `backend/src/main/java/com/mappo/controlplane/service/providerconnection/ProviderConnectionSecretResolver.java`

Frontend contract files:
- `frontend/src/components/ProviderConnectionsConfigPage.tsx`
- `frontend/src/components/ProjectSettingsPage.tsx`
- `frontend/src/lib/api.ts`

Create a connection:

```bash
curl -sS -X POST "$MAPPO_API_BASE_URL/api/v1/provider-connections" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Azure DevOps Runtime",
    "provider": "azure_devops",
    "enabled": true,
    "organizationUrl": "https://dev.azure.com/example-org",
    "personalAccessTokenRef": "kv:mappo-ado-runtime-pat"
  }'
```

Verify before saving:

```bash
curl -sS -X POST "$MAPPO_API_BASE_URL/api/v1/provider-connections/ado/verify" \
  -H "Content-Type: application/json" \
  -d '{
    "provider": "azure_devops",
    "organizationUrl": "https://dev.azure.com/example-org",
    "personalAccessTokenRef": "kv:mappo-ado-runtime-pat"
  }'
```

## Azure DevOps Deployment Pipeline Trigger

Purpose: configure a MAPPO project so a deployment run queues an Azure DevOps pipeline with target and release inputs.

Backend contract files:
- `backend/src/main/java/com/mappo/controlplane/api/ProjectsController.java`
- `backend/src/main/java/com/mappo/controlplane/api/request/ProjectConfigurationPatchRequest.java`
- `backend/src/main/java/com/mappo/controlplane/integrations/azuredevops/AzureDevOpsProjectConfigDescriptorConfiguration.java`
- `backend/src/main/java/com/mappo/controlplane/integrations/azuredevops/pipeline/AzureDevOpsExternalInputsMaterializer.java`
- `backend/src/main/java/com/mappo/controlplane/integrations/azuredevops/pipeline/AzureDevOpsPipelineInputs.java`
- `backend/src/main/java/com/mappo/controlplane/integrations/azuredevops/pipeline/AzureDevOpsPipelineTriggerExecutor.java`
- `backend/src/main/java/com/mappo/controlplane/integrations/azuredevops/pipeline/config/ExternalDeploymentInputsArtifactSourceConfig.java`
- `backend/src/main/java/com/mappo/controlplane/integrations/azuredevops/pipeline/config/PipelineTriggerDriverConfig.java`

Frontend contract files:
- `frontend/src/components/ProjectSettingsPage.tsx`
- `frontend/src/components/ProjectFlowContracts.ts`
- `frontend/src/lib/api.ts`

Configure a project to use the deployment pipeline:

```bash
curl -sS -X PATCH "$MAPPO_API_BASE_URL/api/v1/projects/$PROJECT_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "providerConnectionId": "1",
    "deploymentDriver": "pipeline_trigger",
    "deploymentDriverConfig": {
      "pipelineSystem": "azure_devops",
      "organization": "https://dev.azure.com/example-org",
      "project": "sample-app-service",
      "repository": "sample-app-service",
      "pipelineId": "123",
      "branch": "main",
      "supportsExternalExecutionHandle": true,
      "supportsExternalLogs": true
    },
    "releaseArtifactSource": "external_deployment_inputs",
    "releaseArtifactSourceConfig": {
      "sourceSystem": "azure_devops",
      "descriptorPath": "pipelineInputs",
      "versionField": "artifactVersion"
    }
  }'
```

Payload MAPPO queues to Azure DevOps:

```json
{
  "resources": {
    "repositories": {
      "self": {
        "refName": "refs/heads/main"
      }
    }
  },
  "templateParameters": {
    "targetTenantId": "<target tenant>",
    "targetSubscriptionId": "<target subscription>",
    "targetId": "<mappo target id>",
    "targetResourceGroup": "<target resource group>",
    "targetAppName": "<target app name>",
    "mappoReleaseId": "<mappo release id>",
    "mappoReleaseVersion": "2026.04.30.1",
    "appVersion": "2026.04.30.1"
  }
}
```

## Secret References

Purpose: let operators configure reusable secret pointers without storing secret values in MAPPO records.

Backend contract files:
- `backend/src/main/java/com/mappo/controlplane/api/SecretReferencesController.java`
- `backend/src/main/java/com/mappo/controlplane/api/request/SecretReferenceCreateRequest.java`
- `backend/src/main/java/com/mappo/controlplane/api/request/SecretReferencePatchRequest.java`
- `backend/src/main/java/com/mappo/controlplane/application/secretreference/SecretReferencePrefixes.java`
- `backend/src/main/java/com/mappo/controlplane/service/secretreference/SecretReferenceMutationService.java`
- `backend/src/main/java/com/mappo/controlplane/service/secretreference/SecretReferenceResolver.java`
- `backend/src/main/java/com/mappo/controlplane/integrations/azure/auth/AzureKeyVaultSecretResolver.java`

Frontend contract files:
- `frontend/src/components/SecretReferencesConfigPage.tsx`
- `frontend/src/components/ProviderConnectionsConfigPage.tsx`
- `frontend/src/components/ReleaseIngestConfigPage.tsx`
- `frontend/src/lib/api.ts`

Supported providers:
- `azure_devops`
- `github`

Supported usages:
- `deployment_api_credential`
- `webhook_verification`

Supported modes:
- `mappo_default`
- `environment_variable`
- `key_vault_secret`

Backend references created or accepted by other contracts:
- provider default, such as `mappo.azure-devops.personal-access-token`
- `secret:<secret-reference-id>`
- `env:<ENV_VAR_NAME>`
- `kv:<key-vault-secret-name>`

Create a Key Vault-backed secret reference:

```bash
curl -sS -X POST "$MAPPO_API_BASE_URL/api/v1/secret-references" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Azure DevOps Runtime PAT",
    "provider": "azure_devops",
    "usage": "deployment_api_credential",
    "mode": "key_vault_secret",
    "backendRef": "mappo-ado-runtime-pat"
  }'
```

Use the saved reference from another configuration record:

```json
{
  "personalAccessTokenRef": "secret:12"
}
```

## Azure Project Deployment And Runtime Configuration

Purpose: configure how one MAPPO project deploys to Azure and verifies runtime health. This is listed because operators author MAPPO config objects that select the Azure behavior; the underlying Azure SDK calls are intentionally not listed as separate external-system contracts.

Backend contract files:
- `backend/src/main/java/com/mappo/controlplane/api/ProjectsController.java`
- `backend/src/main/java/com/mappo/controlplane/api/request/ProjectCreateRequest.java`
- `backend/src/main/java/com/mappo/controlplane/api/request/ProjectConfigurationPatchRequest.java`
- `backend/src/main/java/com/mappo/controlplane/integrations/azure/AzureProjectConfigDescriptorConfiguration.java`
- `backend/src/main/java/com/mappo/controlplane/integrations/azure/access/config/AzureWorkloadRbacAccessStrategyConfig.java`
- `backend/src/main/java/com/mappo/controlplane/integrations/azure/access/config/LighthouseDelegatedAccessStrategyConfig.java`
- `backend/src/main/java/com/mappo/controlplane/integrations/azure/deploymentstack/config/AzureDeploymentStackDriverConfig.java`
- `backend/src/main/java/com/mappo/controlplane/integrations/azure/deploymentstack/config/BlobArmTemplateArtifactSourceConfig.java`
- `backend/src/main/java/com/mappo/controlplane/integrations/azure/templatespec/config/AzureTemplateSpecDriverConfig.java`
- `backend/src/main/java/com/mappo/controlplane/integrations/azure/templatespec/config/TemplateSpecResourceArtifactSourceConfig.java`
- `backend/src/main/java/com/mappo/controlplane/integrations/azure/runtime/config/AzureContainerAppHttpRuntimeHealthProviderConfig.java`
- `backend/src/main/java/com/mappo/controlplane/domain/project/HttpEndpointRuntimeHealthProviderConfig.java`

Frontend contract files:
- `frontend/src/components/ProjectSettingsPage.tsx`
- `frontend/src/components/ProjectFlowDiagram.tsx`
- `frontend/src/components/ProjectFlowContracts.ts`
- `frontend/src/lib/api.ts`

Configure an Azure deployment stack project:

```bash
curl -sS -X PATCH "$MAPPO_API_BASE_URL/api/v1/projects/$PROJECT_ID" \
  -H "Content-Type: application/json" \
  -d '{
    "accessStrategy": "azure_workload_rbac",
    "accessStrategyConfig": {
      "authModel": "provider_service_principal",
      "requiresAzureCredential": true,
      "requiresTargetExecutionMetadata": true
    },
    "deploymentDriver": "azure_deployment_stack",
    "deploymentDriverConfig": {
      "supportsPreview": true,
      "previewMode": "ARM_WHAT_IF",
      "supportsExternalExecutionHandle": false
    },
    "releaseArtifactSource": "blob_arm_template",
    "releaseArtifactSourceConfig": {
      "descriptor": "blob_uri_manifest",
      "templateUriField": "templateUri"
    },
    "runtimeHealthProvider": "azure_container_app_http",
    "runtimeHealthProviderConfig": {
      "path": "/health",
      "expectedStatus": 200,
      "timeoutMs": 5000
    }
  }'
```

## Target Onboarding And Marketplace Forwarder Ingest

Purpose: let external marketplace forwarders, import scripts, or operators register deployable targets and send forwarder diagnostics into MAPPO.

Backend contract files:
- `backend/src/main/java/com/mappo/controlplane/api/AdminController.java`
- `backend/src/main/java/com/mappo/controlplane/api/request/OnboardingEventRequest.java`
- `backend/src/main/java/com/mappo/controlplane/api/request/OnboardingEventMetadataRequest.java`
- `backend/src/main/java/com/mappo/controlplane/api/request/TargetRegistrationMetadataRequest.java`
- `backend/src/main/java/com/mappo/controlplane/api/request/TargetRegistrationPatchRequest.java`
- `backend/src/main/java/com/mappo/controlplane/api/request/ForwarderLogIngestRequest.java`
- `backend/src/main/java/com/mappo/controlplane/api/request/ForwarderLogDetailsRequest.java`
- `backend/src/main/java/com/mappo/controlplane/service/admin/MarketplaceOnboardingCommandService.java`
- `backend/src/main/java/com/mappo/controlplane/service/admin/MarketplaceOnboardingTargetFactory.java`

Frontend contract files:
- `frontend/src/components/TargetOnboardingDrawer.tsx`
- `frontend/src/components/TargetsPage.tsx`
- `frontend/src/components/ForwarderLogsPage.tsx`
- `frontend/src/components/AdminTables.tsx`
- `frontend/src/lib/api.ts`

Related scripts/docs:
- `scripts/marketplace_ingest_events.sh`
- `scripts/marketplace_forwarder_deploy.sh`
- `scripts/targets_pipeline_delivery_configure.sh`
- `scripts/targets_pipeline_delivery_import_targets.sh`
- `docs/operator-guide.md`

Ingest a target onboarding event:

```bash
curl -sS -X POST "$MAPPO_API_BASE_URL/api/v1/admin/onboarding/events" \
  -H "Content-Type: application/json" \
  -H "x-mappo-ingest-token: $MAPPO_MARKETPLACE_INGEST_TOKEN" \
  -d '{
    "eventId": "evt-import-001",
    "eventType": "subscription_purchased",
    "projectId": "azure-appservice-ado-pipeline",
    "tenantId": "11111111-1111-1111-1111-111111111111",
    "subscriptionId": "22222222-2222-2222-2222-222222222222",
    "displayName": "Contoso Prod",
    "customerName": "Contoso",
    "targetGroup": "prod",
    "region": "eastus",
    "environment": "prod",
    "tier": "standard",
    "metadata": {
      "source": "targets-pipeline-delivery-iac-import",
      "marketplacePayloadId": "pulumi-stack-001",
      "executionConfig": {
        "targetResourceGroup": "rg-contoso-prod",
        "targetAppName": "app-contoso-prod",
        "slotName": "production",
        "healthPath": "/health"
      }
    }
  }'
```

Ingest a forwarder log:

```bash
curl -sS -X POST "$MAPPO_API_BASE_URL/api/v1/admin/onboarding/forwarder-logs" \
  -H "Content-Type: application/json" \
  -H "x-mappo-ingest-token: $MAPPO_MARKETPLACE_INGEST_TOKEN" \
  -d '{
    "logId": "log-forwarder-001",
    "level": "error",
    "message": "forwarder rejected event",
    "eventId": "evt-import-001",
    "eventType": "subscription_purchased",
    "tenantId": "11111111-1111-1111-1111-111111111111",
    "subscriptionId": "22222222-2222-2222-2222-222222222222",
    "functionAppName": "mappo-forwarder-prod",
    "forwarderRequestId": "req-001",
    "backendStatusCode": 400,
    "details": {
      "detail": "customer mapping missing",
      "backendResponse": "{\"detail\":\"bad request\"}"
    }
  }'
```
