# MAPPO Deployment Runbook

For the clean first-run handoff path, see
[`docs/production-handoff-walkthrough.md`](./production-handoff-walkthrough.md).

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
./mvnw -pl infra/pulumi -DskipTests compile
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
The local/demo environment template is:
- `./mappo.env.example`

Pulumi has separate templates because platform infrastructure and runtime apps
are separate stacks:
- `./pulumi-platform.env.example`
- `./pulumi-runtime.env.example`

Create local copies under `.data/` and do not commit filled values:
```bash
mkdir -p .data
cp mappo.env.example .data/mappo.env
cp pulumi-platform.env.example .data/pulumi-platform.env
cp pulumi-runtime.env.example .data/pulumi-runtime.env
chmod 600 .data/mappo.env .data/pulumi-platform.env .data/pulumi-runtime.env
```

## Publish artifacts only
Use Maven when you want to publish runtime artifacts. Maven must not run Pulumi
or mutate Azure.

Required values:
- `MAPPO_IMAGE_PREFIX`: ACR login server from the platform stack output
- `MAPPO_RUNTIME_IMAGE_TAG`: derived by `scripts/source_runtime_deploy_env.sh`
  from the Maven project version plus Git hash
- `MAPPO_DOCKER_USERNAME`: `00000000-0000-0000-0000-000000000000` for ACR token auth
- `MAPPO_DOCKER_PASSWORD`: short-lived token from `az acr login --expose-token`

`scripts/source_runtime_deploy_env.sh` loads platform identity from
`.data/pulumi-platform.env` and runtime-only settings from
`.data/pulumi-runtime.env`. Do not duplicate platform stack, subscription,
location, or Pulumi passphrase in the runtime env file.

Command:
```bash
source scripts/source_runtime_deploy_env.sh

./mvnw deploy \
  -Ddocker.image.prefix="$MAPPO_IMAGE_PREFIX" \
  -Dmappo.image.tag="$MAPPO_RUNTIME_IMAGE_TAG"
```

The image tag defaults to the Maven project version plus the 12-character Git
commit, for example `1.0.0-SNAPSHOT-c9225249259d`. Override
`-Dmappo.image.tag=...` only for an intentional one-off publish.

## Apply Azure infrastructure
Use Pulumi directly when you want to apply hosted Azure infrastructure changes.

Platform stack:
```bash
set -a
source .data/pulumi-platform.env
set +a

./mvnw -pl infra/pulumi -DskipTests compile

cd infra/pulumi
pulumi preview --stack "<platform-stack>" --diff
pulumi up --stack "<platform-stack>"
```

Runtime app stack:
```bash
source scripts/source_runtime_deploy_env.sh

./mvnw -pl infra/pulumi -DskipTests compile

cd infra/pulumi
pulumi preview --stack "<runtime-stack>" --diff
pulumi up --stack "<runtime-stack>"
```

Pulumi owns Azure resource creation and updates. Maven owns
build/test/package/image publishing.

Platform resources should live together in one runtime resource group:
- managed Postgres, including a Pulumi-generated admin password for fresh stacks
- Container Apps environment
- runtime ACR
- Redis
- MAPPO Azure Key Vault
- managed identity

Runtime app resources are created by the runtime stack:
- backend and frontend Container Apps
- backend Flyway init container for startup migrations
- frontend EasyAuth Entra app registration and redirect URI

Demo target resources remain separate and are owned by the Pulumi stacks under
`infra/demo`.

## External system secrets
For the hosted Azure runtime, external system secrets should live in MAPPO's
Azure Key Vault.

Current runtime behavior:
- `infra/pulumi` provisions the runtime Key Vault in the platform resource group
- it grants the runtime managed identity access to secrets
- it can also grant an operator/service-principal object id from
  `mappo:keyVaultAccessObjectId` or `MAPPO_AZURE_KEY_VAULT_ACCESS_OBJECT_ID`
- it injects `MAPPO_AZURE_KEY_VAULT_URL` into the backend Container App

Recommended usage:
1. create a secret in the MAPPO Key Vault
2. in MAPPO Admin, choose `Use Azure Key Vault secret`
3. enter the secret name only

Current supported secret reference forms:
- `kv:secret-name`
- `env:VAR_NAME`
- named MAPPO Secret References, stored as `secret:<reference-id>`
- provider default backend secret refs for legacy/default paths

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
The customer workload release catalog is managed outside this repository. For
the VECTR demo, the current local repo is:
- `../mappo-managed-app`

Typical operator/demo sequence:
1. create/publish a new release in the managed-app release catalog repo
2. push the repo changes
3. in MAPPO, open the project's Releases page
4. click `Check for new releases`
5. preview and start the deployment from Project -> Deployments
