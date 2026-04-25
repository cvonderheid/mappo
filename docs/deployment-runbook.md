# MAPPO Deployment Runbook

## Local build and test
```bash
./mvnw clean install
./mvnw verify
```

Useful module-level commands:
```bash
./mvnw -pl backend test
./mvnw -pl frontend compile
./mvnw -pl frontend test
```

## Local runtime
Build first, then start the local stack:
```bash
./mvnw clean install
docker compose -f infra/docker-compose.yml up --build
```

Current local runtime shape:
- backend runs from the prebuilt local image
- frontend runs in hot-reload mode

## Environment and secrets
The consolidated environment template is:
- `./mappo.env.example`

For local/demo work, copy it to `.data/mappo.env`, fill in real values, then source it:
```bash
cp mappo.env.example .data/mappo.env
set -a
source .data/mappo.env
set +a
```

`.data/mappo.env` is the single local/demo environment file. Legacy split files belong under `.data/archive/` only if you need to keep a temporary backup while migrating values.

## Publish artifacts only
Use this when you want to publish runtime artifacts without mutating Azure.

Required environment:
- `MAPPO_IMAGE_PREFIX`
- Docker credentials that can push to that registry

Command:
```bash
./mvnw deploy \
  -Ddocker.image.prefix="$MAPPO_IMAGE_PREFIX" \
  -Dmappo.image.tag="<image-tag>"
```

## Publish and roll out to Azure
Use this when you want to publish artifacts and then apply the hosted runtime update.

Required environment:
- `MAPPO_IMAGE_PREFIX`
- `MAPPO_DOCKER_USERNAME`
- `MAPPO_DOCKER_PASSWORD`
- `PULUMI_CONFIG_PASSPHRASE`
- Azure CLI already authenticated

Command:
```bash
./mvnw -Pazure deploy \
  -Ddocker.image.prefix="$MAPPO_IMAGE_PREFIX" \
  -Dmappo.image.tag="<image-tag>" \
  -Dpulumi.stack="<stack>"
```

The `azure` profile performs:
- Pulumi apply
- runtime Container Apps prepare/apply
- Flyway migration job execution
- frontend/backend update
- marketplace forwarder deployment

MAPPO platform resources should live together in the runtime resource group:
- managed Postgres from `infra/pulumi`
- backend and frontend Container Apps
- Container Apps environment, unless Azure quota forces reuse of an existing environment
- migration Container App job
- runtime ACR
- Azure Managed Redis
- MAPPO Azure Key Vault
- marketplace forwarder Function App and its storage account

Demo target resources remain separate and are owned by the demo fleet Pulumi stacks.
The default platform resource group name is `rg-mappo-runtime-<stack>`. Override it with `MAPPO_RUNTIME_RESOURCE_GROUP`, `MAPPO_CONTROL_PLANE_RESOURCE_GROUP`, or Pulumi config `mappo:controlPlaneResourceGroupName` when needed.

## External system secrets
For the hosted Azure runtime, external system secrets should live in MAPPO's Azure Key Vault.

Current runtime behavior:
- `scripts/runtime_aca_deploy.sh` provisions or reuses a Key Vault in the runtime resource group
- it grants the MAPPO Azure service principal `Key Vault Secrets User`
- it injects `MAPPO_AZURE_KEY_VAULT_URL` into the backend container app

Recommended usage:
1. create a secret in the MAPPO Key Vault
2. in MAPPO Admin, choose `Use Azure Key Vault secret`
3. enter the secret name only

Current supported secret reference forms:
- `kv:secret-name`
- `env:VAR_NAME`
- provider default backend secret refs (legacy/default path)

## OpenAPI and frontend client contract
Backend OpenAPI export:
```bash
./mvnw -pl backend verify
```

Frontend type generation and verification:
```bash
./mvnw -pl frontend generate-sources
./mvnw -pl frontend compile
./mvnw -pl frontend test
```

Important files:
- OpenAPI: `./backend/target/openapi/openapi.json`
- generated frontend schema: `./frontend/src/lib/api/generated/schema.ts`

## Publisher release flow
The customer workload release catalog is managed in:
- `../mappo-release-catalog`

Typical operator/demo sequence:
1. create/publish a new release in `mappo-release-catalog`
2. push the repo changes
3. in MAPPO, open the project's Releases page
4. click `Check for new releases`
5. preview and start the deployment from Project -> Deployments
