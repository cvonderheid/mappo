# mappo-targets-pipeline-delivery

Pulumi program that provisions lightweight Azure App Service targets for the
`azure-appservice-ado-pipeline` MAPPO project.

This module is demo infrastructure for the pipeline-delivery MAPPO project:

- project: `azure-appservice-ado-pipeline`
- deployment driver: `pipeline_trigger`
- platform: Azure App Service
- ADO workload repo: `../sample-app-service`

It provisions one Linux App Service target per configured subscription and exports a
MAPPO-compatible target inventory JSON payload.

## What it creates

For each configured target, the stack creates:

- a resource group
- a Linux App Service plan
- a Linux Web App
- App Settings that make the sample application self-identifying

The current sample app is packaged from:

- `./delivery/appservice-demo-app`

The Azure DevOps deployment and release-readiness pipelines live in:

- `../sample-app-service`

After the release-readiness pipeline exists in Azure DevOps, configure its MAPPO webhook with:

```bash
./scripts/ado_release_webhook_bootstrap.sh \
  --pipeline-id <release-readiness-pipeline-id> \
  --replace-existing
```

## Outputs

The main output is:

- `mappoTargetInventory`

Each target entry includes:

- `project_id = azure-appservice-ado-pipeline`
- `tenant_id`
- `subscription_id`
- target tags
- `metadata.execution_config` with:
  - `resourceGroup`
  - `appServiceName`
  - `webAppResourceId`
  - `runtimeBaseUrl`
  - `runtimeHealthPath`
  - `runtimeExpectedStatus`

That inventory is meant to feed MAPPO target import through:

- `./scripts/targets_pipeline_delivery_import_targets.sh`

## Recommended workflow

1. Configure the stack:

```bash
./scripts/targets_pipeline_delivery_configure.sh \
  --stack targets-pipeline-delivery \
  --first-subscription-id 00000000-0000-0000-0000-000000000400 \
  --second-subscription-id 00000000-0000-0000-0000-000000000300
```

2. Provision the targets, deploy the sample app, and register them in MAPPO:

```bash
./scripts/targets_pipeline_delivery_up.sh \
  --stack targets-pipeline-delivery \
  --api-base-url https://api.example.mappo.local
```

2.5 Configure the ADO project runtime settings in MAPPO (no seeded demo defaults):

```bash
./scripts/project_configure_ado.sh \
  --api-base-url https://api.example.mappo.local \
  --ado-organization https://dev.azure.com/<org> \
  --ado-project <project> \
  --ado-pipeline-id <pipeline-id>
```

2.6 Configure the ADO release source to listen to the release-readiness pipeline:

```bash
./scripts/release_source_configure_ado.sh \
  --api-base-url https://api.example.mappo.local \
  --pipeline-id <release-readiness-pipeline-id>
```

2.7 Configure the ADO service hook for the same release-readiness pipeline:

```bash
./scripts/ado_release_hook_configure.sh \
  --organization https://dev.azure.com/<org> \
  --project <ado-project> \
  --pipeline-id <release-readiness-pipeline-id> \
  --mappo-api-base-url https://api.example.mappo.local
```

3. Generate an ADO demo release by opening and completing a release PR:

```bash
./scripts/ado_appservice_release_pr.sh \
  --organization https://dev.azure.com/<org> \
  --project <ado-project> \
  --repository <ado-repository> \
  --version 2026.04.12.1
```

4. Tear the targets down when needed:

```bash
./scripts/targets_pipeline_delivery_down.sh \
  --stack targets-pipeline-delivery \
  --api-base-url https://api.example.mappo.local
```

## Maven

Maven compiles this Java Pulumi module as part of the repo build. Pulumi lifecycle
commands intentionally stay in the shell scripts above so Maven does not mutate
Azure resources.
