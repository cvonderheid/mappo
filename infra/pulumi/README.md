# MAPPO Pulumi IaC (Java)

This Pulumi project supports two explicit stack kinds:

- `platform`: source-code independent Azure infrastructure
- `runtime`: source-code dependent Container Apps runtime

Both stack kinds are implemented by the same Java Pulumi module. The stack kind
is selected with `MAPPO_PULUMI_STACK_KIND` or Pulumi config `mappo:stackKind`.

## Platform Stack

The platform stack creates infrastructure that can exist before any MAPPO image
has been built:

- runtime resource group
- Azure Database for PostgreSQL Flexible Server + `mappo` database
- Container Apps environment
- runtime ACR
- Redis
- Key Vault
- managed identity

From the repository root, use `pulumi-platform.env.example` as the template:

```bash
cp pulumi-platform.env.example .data/pulumi-platform.env
set -a
source .data/pulumi-platform.env
set +a

./mvnw -pl infra/pulumi -DskipTests compile

cd infra/pulumi
pulumi login --local
pulumi stack init <platform-stack>
pulumi preview --stack <platform-stack> --diff
pulumi up --stack <platform-stack> --yes
```

Important platform outputs:

- `runtimeAcrName`
- `runtimeAcrLoginServer`
- `runtimeResourceGroupName`
- `runtimeKeyVaultName`
- `runtimeKeyVaultUri`
- `controlPlaneDatabaseUrl`

## Runtime Stack

The runtime stack consumes platform outputs through a Pulumi `StackReference` and
creates app resources that depend on published MAPPO images:

- backend Container App
- frontend Container App
- backend Flyway init container
- frontend EasyAuth Entra app registration and redirect URI
- Container App env vars and secrets

From the repository root, use `pulumi-runtime.env.example` as the template for
runtime-only values. `scripts/source_runtime_deploy_env.sh` loads
`.data/pulumi-platform.env` first, then `.data/pulumi-runtime.env`, so platform
stack identity remains in one place.

```bash
cp pulumi-runtime.env.example .data/pulumi-runtime.env
source scripts/source_runtime_deploy_env.sh

./mvnw -pl infra/pulumi -DskipTests compile

cd infra/pulumi
pulumi login --local
pulumi stack init <runtime-stack>
pulumi preview --stack <runtime-stack> --diff
pulumi up --stack <runtime-stack> --yes
```

Required runtime inputs:

- `MAPPO_PLATFORM_STACK` from `.data/pulumi-platform.env`
- `MAPPO_MARKETPLACE_INGEST_TOKEN`

`MAPPO_RUNTIME_IMAGE_TAG` is not stored in `.data/pulumi-runtime.env`; source
`scripts/source_runtime_deploy_env.sh` to derive it from the current Maven
project version and Git commit.

The hosted backend uses the Pulumi-created managed identity through
`DefaultAzureCredential`. Grant Azure RBAC to the platform output
`runtimeManagedIdentityPrincipalId` for target subscriptions/resource groups
that MAPPO should update directly.

Advanced overrides such as explicit resource group names, PostgreSQL settings,
runtime sizing, CORS, EasyAuth toggles, and operator Key Vault access IDs are
available through Pulumi config or env fallback, but are intentionally omitted
from the handoff templates.

The runtime stack expects these images to exist in the platform ACR:

- `mappo-backend:<MAPPO_RUNTIME_IMAGE_TAG>`
- `mappo-frontend:<MAPPO_RUNTIME_IMAGE_TAG>`
- `mappo-flyway:<MAPPO_RUNTIME_IMAGE_TAG>`

## Compile

```bash
./mvnw -pl infra/pulumi -DskipTests compile
```

## Target Workloads

Target workloads are not part of the MAPPO platform/runtime stacks. Demo target
provisioning lives under:

- [`../demo/targets-azure-delivery`](../demo/targets-azure-delivery)
- [`../demo/targets-pipeline-delivery`](../demo/targets-pipeline-delivery)
