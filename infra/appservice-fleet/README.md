# mappo-appservice-fleet

Pulumi program that provisions a lightweight Azure App Service target fleet for the
`azure-appservice-ado-pipeline` MAPPO project.

This module is Sprint 1 infrastructure for the second real MAPPO project:

- project: `azure-appservice-ado-pipeline`
- deployment driver: `pipeline_trigger`
- platform: Azure App Service

It provisions one Linux App Service target per configured subscription and exports a
MAPPO-compatible target inventory JSON payload.

## What it creates

For each configured target, the stack creates:

- a resource group
- a Linux App Service plan
- a Linux Web App
- App Settings that make the sample application self-identifying

The current sample app is packaged from:

- `/Users/cvonderheid/workspace/mappo/delivery/appservice-demo-app`

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

That inventory is meant to feed MAPPO onboarding through:

- `/Users/cvonderheid/workspace/mappo/scripts/marketplace_ingest_events.sh`

## Recommended workflow

1. Configure the stack:

```bash
./scripts/appservice_fleet_configure.sh \
  --stack appservice-demo \
  --first-subscription-id 1adaaa48-139a-477b-a8c8-0e6289d6d199 \
  --second-subscription-id 597f46c7-2ce0-440e-962d-453e486f159d
```

2. Provision the targets, deploy the sample app, and register them in MAPPO:

```bash
./scripts/appservice_fleet_up.sh \
  --stack appservice-demo \
  --api-base-url https://api.mappopoc.com
```

2.5 Configure the ADO project runtime settings in MAPPO (no seeded demo defaults):

```bash
./scripts/project_configure_ado.sh \
  --api-base-url https://api.mappopoc.com \
  --ado-organization https://dev.azure.com/<org> \
  --ado-project <project> \
  --ado-pipeline-id <pipeline-id> \
  --service-connection-name <service-connection-name>
```

3. Tear the fleet down when needed:

```bash
./scripts/appservice_fleet_down.sh --stack appservice-demo
```

## Maven helpers

The root reactor exposes convenience goals for this Pulumi module:

- `./mvnw -N exec:exec@appservice-fleet-pulumi-up`
- `./mvnw -N exec:exec@appservice-fleet-pulumi-destroy`

Those only run Pulumi. The higher-level shell scripts above are still the intended
operator flow because they also package/deploy the sample app and emit onboarding
events for MAPPO.
