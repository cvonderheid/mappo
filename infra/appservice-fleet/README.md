# mappo-appservice-fleet

Pulumi program that provisions a lightweight Azure App Service target fleet for the
`azure-appservice-ado-pipeline` MAPPO project.

This module is Sprint 1 infrastructure for the second real MAPPO project:

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

- `./scripts/appservice_fleet_import_targets.sh`

## Recommended workflow

1. Configure the stack:

```bash
./scripts/appservice_fleet_configure.sh \
  --stack appservice-demo \
  --first-subscription-id 00000000-0000-0000-0000-000000000400 \
  --second-subscription-id 00000000-0000-0000-0000-000000000300
```

2. Provision the targets, deploy the sample app, and register them in MAPPO:

```bash
./scripts/appservice_fleet_up.sh \
  --stack appservice-demo \
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

4. Tear the fleet down when needed:

```bash
./scripts/appservice_fleet_down.sh \
  --stack appservice-demo \
  --api-base-url https://api.example.mappo.local
```

## Maven helpers

The root reactor exposes convenience goals for this Pulumi module:

- `./mvnw -N exec:exec@appservice-fleet-pulumi-up`
- `./mvnw -N exec:exec@appservice-fleet-pulumi-destroy`

Those only run Pulumi. The higher-level shell scripts above are still the intended
operator flow because they also package/deploy the sample app and import/delete
MAPPO targets from the Pulumi inventory.
