# MAPPO Pulumi IaC (Java)

This Pulumi project provisions the MAPPO platform runtime infrastructure:

- control-plane PostgreSQL
- Container Apps environment
- backend and frontend Container Apps
- runtime ACR
- Redis
- Key Vault
- managed identity
- frontend EasyAuth Entra app registration and redirect URIs

It does not provision target workloads. The target fleet lives in
[`./infra/demo/targets-azure-delivery`](./infra/demo/targets-azure-delivery).

## Runtime

- Pulumi runtime: `java`
- Entry point: `com.mappo.pulumi.Main`
- Build file: `infra/pulumi/pom.xml`

## What it creates

- Optional control-plane persistence:
  - Azure Database for PostgreSQL Flexible Server + `mappo` database
  - generated admin password when no password is supplied
- Optional runtime platform:
  - shared runtime resource group
  - Container Apps environment
  - ACR
  - Redis
  - Key Vault
  - backend Container App with Flyway init container
  - frontend Container App
  - EasyAuth Entra app registration and redirect URI management

## Stack config contract

Pulumi config namespace is `mappo`.

Common optional keys:
- `mappo:defaultLocation` (`eastus`)
- `mappo:controlPlanePostgresEnabled` (`false`)
- `mappo:controlPlaneSubscriptionId`
- `mappo:controlPlaneLocation`
- `mappo:runtimeEnabled` (`false`)
- `mappo:runtimeAppsEnabled` (`false`)
- `mappo:runtimeSubscriptionId`
- `mappo:runtimeResourceGroupName`
- `mappo:runtimeLocation`
- `mappo:imageTag`

Required when `mappo:runtimeAppsEnabled=true`:
- `mappo:azureTenantId`
- `mappo:azureClientId` (secret)
- `mappo:azureClientSecret` (secret)
- `mappo:marketplaceIngestToken` (secret)

The runtime app phase expects Maven-published images to already exist in the
Pulumi-created ACR:
- `mappo-backend:<imageTag>`
- `mappo-frontend:<imageTag>`
- `mappo-flyway:<imageTag>`

Fresh stack sequence:

1. Run Pulumi with `mappo:runtimeEnabled=true`, `mappo:controlPlanePostgresEnabled=true`, and `mappo:runtimeAppsEnabled=false`.
2. Read `runtimeAcrLoginServer` from Pulumi outputs and publish artifacts with Maven.
3. Set `mappo:imageTag` to the Maven image tag.
4. Set `mappo:runtimeAppsEnabled=true`.
5. Run Pulumi again to create/update Container Apps and EasyAuth.

## Commands

Compile only:

```bash
./mvnw -pl infra/pulumi -DskipTests compile
```

Preview:

```bash
cd infra/pulumi
pulumi login --local
pulumi stack select <stack> || pulumi stack init <stack>
pulumi preview --stack <stack>
```

Apply:

```bash
cd infra/pulumi
pulumi up --stack <stack> --yes
```

Destroy:

```bash
cd infra/pulumi
pulumi destroy --stack <stack> --yes
```

Target fleet provisioning:
- [`./infra/demo/targets-azure-delivery/README.md`](./infra/demo/targets-azure-delivery/README.md)
- [`./infra/demo/targets-pipeline-delivery/README.md`](./infra/demo/targets-pipeline-delivery/README.md)
- [`./scripts/targets_azure_delivery_up.sh`](./scripts/targets_azure_delivery_up.sh)
- [`./scripts/targets_pipeline_delivery_up.sh`](./scripts/targets_pipeline_delivery_up.sh)
