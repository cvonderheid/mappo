# MAPPO Production Handoff Walkthrough

This document is the clean first-run path for a developer or operator taking over
MAPPO from a fresh checkout. It intentionally omits historical cleanup notes.

## Scope

This walkthrough covers:

- local repository validation
- local Docker Compose runtime
- Azure-hosted MAPPO platform creation with Pulumi
- Maven artifact publishing to the Pulumi-created ACR
- Azure-hosted MAPPO runtime app creation with Pulumi
- hosted runtime verification
- where to put external-system secrets
- optional demo target provisioning

Maven builds and publishes artifacts. Pulumi owns Azure infrastructure. Do not
use Maven to create or update Azure resources.

For the operator-facing project setup path, especially the Azure DevOps
release-readiness pipeline plus Azure DevOps deployment pipeline flow, use
[`azure-devops-pipeline-project-setup.md`](./azure-devops-pipeline-project-setup.md)
as the project setup manual after the hosted runtime is available.

## Prerequisites

Install:

- Java 21
- Docker Desktop
- Node.js 20+
- Azure CLI
- Pulumi CLI
- Git
- `jq`

You also need:

- Azure subscription where the MAPPO runtime will run
- permission to create resource groups, ACR, Container Apps, Redis, Key Vault,
  managed identities, PostgreSQL Flexible Server, and Entra app registrations
- a Pulumi passphrase for local stack encryption
- optional GitHub and Azure DevOps tokens if you want to wire the demo release
  sources during the first pass

## 1. Clone And Enter The Repo

```bash
git clone <repo-url> mappo
cd mappo
```

Confirm the toolchain:

```bash
java -version
docker version
az version
pulumi version
./mvnw -v
```

## 2. Build And Test Locally

Run the full Maven build:

```bash
./mvnw clean install
```

Run the verification suite:

```bash
./mvnw verify
```

Useful targeted checks:

```bash
./mvnw -pl backend test
./mvnw -pl frontend compile
./mvnw -pl frontend test
./mvnw -pl infra/pulumi -DskipTests compile
./mvnw -pl infra/demo/targets-azure-delivery,infra/demo/targets-pipeline-delivery -am -DskipTests compile
```

## 3. Run Locally With Docker Compose

The local Docker Compose stack works without Azure secrets. It starts Postgres,
Redis, Flyway migrations, backend, and frontend.

```bash
docker compose -f infra/docker-compose.yml up --build
```

Verify:

```bash
curl -fsS http://localhost:8010/healthz
curl -fsS http://localhost:8010/api/v1/health
```

Open the UI:

```text
http://localhost:5174
```

Stop the stack:

```bash
docker compose -f infra/docker-compose.yml down
```

To remove local database state:

```bash
docker compose -f infra/docker-compose.yml down -v
```

## 4. Create Local Environment Files

MAPPO uses three local environment files during handoff:

- `.data/mappo.env`: local runtime and demo helper values
- `.data/pulumi-platform.env`: source-code independent Azure infrastructure
- `.data/pulumi-runtime.env`: runtime-only Container Apps settings

Create the files from templates:

```bash
mkdir -p .data
cp mappo.env.example .data/mappo.env
cp pulumi-platform.env.example .data/pulumi-platform.env
cp pulumi-runtime.env.example .data/pulumi-runtime.env
chmod 600 .data/mappo.env .data/pulumi-platform.env .data/pulumi-runtime.env
```

Fill `.data/pulumi-platform.env` first:

```bash
export PULUMI_CONFIG_PASSPHRASE="<local-pulumi-passphrase>"
export MAPPO_PULUMI_STACK_KIND="platform"
export MAPPO_PLATFORM_STACK="platform-<timestamp-or-name>"
export MAPPO_RUNTIME_SUBSCRIPTION_ID="<runtime-subscription-id>"
export MAPPO_RUNTIME_LOCATION="<azure-region>"
```

Notes:

- `MAPPO_PLATFORM_STACK` is the Pulumi platform stack name. Use a stable value
  for the walkthrough because later steps source this file repeatedly.
- `MAPPO_RUNTIME_SUBSCRIPTION_ID` is the Azure subscription where the hosted
  MAPPO platform and runtime apps will be created.
- `AZURE_SUBSCRIPTION_ID` is not required by this walkthrough.
- PostgreSQL and control-plane resources use the same subscription as the runtime stack. There is no separate control-plane subscription input.
- `MAPPO_AZURE_TENANT_ID` is optional. Pulumi derives it from `az login` when
  it is blank. Do not add it unless the active Azure CLI tenant is not the one
  the stack should use.
- Advanced overrides such as resource group names, PostgreSQL settings, and
  human Key Vault access object IDs are supported by Pulumi config/env fallback,
  but are intentionally left out of the minimal handoff template.

The hosted backend uses the Pulumi-created managed identity through
`DefaultAzureCredential`. No Azure client secret is required in the runtime env.
If MAPPO needs to update target subscriptions or resource groups outside the
runtime resource group, grant Azure RBAC to the platform output
`runtimeManagedIdentityPrincipalId`.

Authenticate to Azure:

```bash
set -a
source .data/pulumi-platform.env
set +a

az login
az account set --subscription "$MAPPO_RUNTIME_SUBSCRIPTION_ID"
az account show --query "{name:name, subscription:id, tenant:tenantId}" -o table
```

## 5. Create The Pulumi Platform Stack

The platform stack creates source-code independent Azure resources:

- runtime resource group
- PostgreSQL Flexible Server and `mappo` database
- Container Apps environment
- runtime ACR
- Redis
- Key Vault
- managed identity

Use a clear stack name. For handoff testing, a timestamped stack is useful.
Write it into `.data/pulumi-platform.env` before sourcing the file so the value
does not get reset later:

```bash
export MAPPO_PLATFORM_STACK="platform-$(date -u +%Y%m%d%H%M%S)"
if grep -q '^export MAPPO_PLATFORM_STACK=' .data/pulumi-platform.env; then
  perl -0pi -e 's|export MAPPO_PLATFORM_STACK=".*"|export MAPPO_PLATFORM_STACK="'"$MAPPO_PLATFORM_STACK"'"|' .data/pulumi-platform.env
else
  printf '\nexport MAPPO_PLATFORM_STACK="%s"\n' "$MAPPO_PLATFORM_STACK" >> .data/pulumi-platform.env
fi
```

Initialize and apply the platform stack:

```bash
set -a
source .data/pulumi-platform.env
set +a

./mvnw -pl infra/pulumi -DskipTests compile

cd infra/pulumi
pulumi login --local
pulumi stack init "$MAPPO_PLATFORM_STACK"
pulumi preview --stack "$MAPPO_PLATFORM_STACK" --diff
pulumi up --stack "$MAPPO_PLATFORM_STACK" --yes
cd ../..
```

Record the important outputs:

```bash
cd infra/pulumi
pulumi stack output --stack "$MAPPO_PLATFORM_STACK"
cd ../..
```

## 6. Publish Runtime Images With Maven

Load the runtime deployment environment. This sources platform identity from
`.data/pulumi-platform.env`, then runtime-only settings from
`.data/pulumi-runtime.env`, derives the current image tag from Maven/Git, reads
ACR outputs from the platform stack, and creates a short-lived ACR token for
Maven.

```bash
source scripts/source_runtime_deploy_env.sh
```

Publish the images:

```bash
./mvnw deploy \
  -Ddocker.image.prefix="$MAPPO_IMAGE_PREFIX" \
  -Dmappo.image.tag="$MAPPO_RUNTIME_IMAGE_TAG"
```

Maven publishes:

- `mappo-backend:<tag>`
- `mappo-frontend:<tag>`
- `mappo-flyway:<tag>`
- marketplace forwarder package artifact

Maven does not run Pulumi.

Do not persist `MAPPO_RUNTIME_IMAGE_TAG`; it is derived from the current Maven
project version and Git commit each time `scripts/source_runtime_deploy_env.sh`
is sourced.

Then edit `.data/pulumi-runtime.env` and fill the runtime-only secret:

```bash
export MAPPO_MARKETPLACE_INGEST_TOKEN="<random-long-token>"
```

If the stack should expose the frontend through an existing Azure DNS zone, fill
the optional custom-domain values before running the runtime stack. Pulumi will
create the DNS records, managed certificate, Container App binding, and Entra
redirect URI.

```bash
export MAPPO_BACKEND_CUSTOM_DOMAIN="api.mappopoc.com"
export MAPPO_FRONTEND_CUSTOM_DOMAIN="www.mappopoc.com"
export MAPPO_FRONTEND_DNS_ZONE_NAME="mappopoc.com"
export MAPPO_FRONTEND_DNS_ZONE_RESOURCE_GROUP="rg-mappo-dns-demo"
export MAPPO_BACKEND_CUSTOM_DOMAIN_CERTIFICATE_ENABLED="false"
export MAPPO_FRONTEND_CUSTOM_DOMAIN_CERTIFICATE_ENABLED="false"
```

Container Apps custom domains are a two-step Azure workflow. The first runtime
`pulumi up` must run with both custom-domain certificate flags set to `"false"`.
That first pass creates DNS records and attaches the hostnames to the Container
Apps. After that succeeds, set both custom-domain certificate flags to `"true"`
and run the runtime `pulumi up` again to request the managed certificates and
switch the hostname bindings to SNI.

If Azure returns `RequireCustomHostnameInEnvironment`, the certificate flags
were enabled before the hostname binding existed. Set both flags back to
`"false"` and rerun the first runtime `pulumi up`.

## 7. Create The Pulumi Runtime App Stack

The runtime app stack consumes platform outputs through a Pulumi stack reference
and creates source-code dependent resources:

- backend Container App
- frontend Container App
- backend Flyway init container
- frontend EasyAuth Entra app registration and redirect URI
- Container App env vars and secrets

Use a matching runtime stack name:

```bash
source scripts/source_runtime_deploy_env.sh
export MAPPO_RUNTIME_STACK="${MAPPO_PLATFORM_STACK/platform/runtime}"
```

Apply the runtime stack:

```bash
./mvnw -pl infra/pulumi -DskipTests compile

cd infra/pulumi
pulumi stack init "$MAPPO_RUNTIME_STACK"
pulumi preview --stack "$MAPPO_RUNTIME_STACK" --diff
pulumi up --stack "$MAPPO_RUNTIME_STACK" --yes
cd ../..
```

Capture the backend URL for hosted health checks and demo/helper scripts:

```bash
export MAPPO_API_BASE_URL="$(cd infra/pulumi && pulumi stack output runtimeBackendUrl --stack "$MAPPO_RUNTIME_STACK")"
```

Optionally persist `MAPPO_API_BASE_URL` in `.data/mappo.env` for demo scripts.

## 8. Verify The Hosted Runtime

Check backend health:

```bash
curl -fsS "$MAPPO_API_BASE_URL/healthz"
curl -fsS "$MAPPO_API_BASE_URL/api/v1/health"
```

Open the frontend:

```bash
cd infra/pulumi
pulumi stack output runtimeFrontendUrl --stack "$MAPPO_RUNTIME_STACK"
cd ../..
```

Expected result:

- browser redirects through Entra/EasyAuth
- signed-in user reaches the MAPPO UI
- Admin pages load
- Secret Inventory shows the runtime Key Vault capability

If the frontend redirects but login fails, check the Pulumi-created Entra app
registration and confirm it has a redirect URI matching:

```text
<runtimeFrontendUrl>/.auth/login/aad/callback
```

## 9. External System Secrets

Hosted MAPPO should resolve external-system credentials from the Pulumi-created
Key Vault whenever possible.

Create Key Vault secrets with Azure CLI:

```bash
export MAPPO_KEY_VAULT_NAME="$(cd infra/pulumi && pulumi stack output runtimeKeyVaultName --stack "$MAPPO_PLATFORM_STACK")"

az keyvault secret set \
  --vault-name "$MAPPO_KEY_VAULT_NAME" \
  --name "github-release-webhook-secret" \
  --value "<secret-value>"

az keyvault secret set \
  --vault-name "$MAPPO_KEY_VAULT_NAME" \
  --name "ado-personal-access-token" \
  --value "<pat-value>"

az keyvault secret set \
  --vault-name "$MAPPO_KEY_VAULT_NAME" \
  --name "ado-release-webhook-secret" \
  --value "<secret-value>"
```

Then configure MAPPO:

1. Open Admin -> Secret Inventory.
2. Create or update a secret reference.
3. Choose Azure Key Vault.
4. Enter the Key Vault secret name only.
5. Use that secret reference from Admin -> Release Sources or Admin ->
   Deployment Connections.

## 10. Optional Demo Targets

The MAPPO platform/runtime stacks do not create customer/demo target workloads.
Demo targets live under `infra/demo`.

### Direct Azure delivery targets

Configure:

```bash
./scripts/targets_azure_delivery_configure.sh \
  --stack targets-azure-delivery \
  --provider-subscription-id <subscription-id-1> \
  --customer-subscription-id <subscription-id-2> \
  --location <azure-region>
```

Provision and send marketplace-style registration events:

```bash
./scripts/targets_azure_delivery_up.sh \
  --stack targets-azure-delivery \
  --api-base-url "$MAPPO_API_BASE_URL"
```

Tear down and send deregistration events:

```bash
./scripts/targets_azure_delivery_down.sh \
  --stack targets-azure-delivery \
  --api-base-url "$MAPPO_API_BASE_URL"
```

### Pipeline delivery targets

Configure:

```bash
./scripts/targets_pipeline_delivery_configure.sh \
  --stack targets-pipeline-delivery \
  --first-subscription-id <subscription-id-1> \
  --second-subscription-id <subscription-id-2> \
  --location <azure-region>
```

Provision App Service targets, deploy the sample app, and import targets into
MAPPO:

```bash
./scripts/targets_pipeline_delivery_up.sh \
  --stack targets-pipeline-delivery \
  --api-base-url "$MAPPO_API_BASE_URL"
```

Tear down and delete active targets from MAPPO:

```bash
./scripts/targets_pipeline_delivery_down.sh \
  --stack targets-pipeline-delivery \
  --api-base-url "$MAPPO_API_BASE_URL"
```

## 11. Day-Two Update Flow

For code-only changes, reuse the existing platform stack, publish new images,
then update only the runtime app stack:

```bash
export MAPPO_PLATFORM_STACK=<existing-platform-stack>
export MAPPO_RUNTIME_STACK=<existing-runtime-stack>
perl -0pi -e 's|export MAPPO_PLATFORM_STACK=".*"|export MAPPO_PLATFORM_STACK="'"$MAPPO_PLATFORM_STACK"'"|' .data/pulumi-platform.env

source scripts/source_runtime_deploy_env.sh

./mvnw deploy \
  -Ddocker.image.prefix="$MAPPO_IMAGE_PREFIX" \
  -Dmappo.image.tag="$MAPPO_RUNTIME_IMAGE_TAG"

./mvnw -pl infra/pulumi -DskipTests compile

cd infra/pulumi
pulumi up --stack "$MAPPO_RUNTIME_STACK" --yes
cd ../..
```

For platform infrastructure changes:

```bash
./mvnw -pl infra/pulumi -DskipTests compile

cd infra/pulumi
pulumi preview --stack "$MAPPO_PLATFORM_STACK" --diff
pulumi up --stack "$MAPPO_PLATFORM_STACK" --yes
cd ../..
```

For runtime app infrastructure changes:

```bash
./mvnw -pl infra/pulumi -DskipTests compile

cd infra/pulumi
pulumi preview --stack "$MAPPO_RUNTIME_STACK" --diff
pulumi up --stack "$MAPPO_RUNTIME_STACK" --yes
cd ../..
```

## 12. Handoff Checklist

Before handing the environment to the next operator, confirm:

- `./mvnw clean install` passes
- local Docker Compose starts and health checks pass
- hosted backend health checks pass
- hosted frontend login works
- Pulumi platform and runtime stack outputs are recorded
- `.data/mappo.env`, `.data/pulumi-platform.env`, and
  `.data/pulumi-runtime.env` exist locally but are not committed
- required external secrets are in Key Vault
- Admin -> Secret Inventory references Key Vault secrets by name
- demo target stacks can be created and destroyed independently from the MAPPO
  platform/runtime stacks
