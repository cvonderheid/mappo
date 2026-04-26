# Final Handoff Walkthrough

This walkthrough is the final validation path for handing MAPPO to a new developer.
It covers Azure cleanup, environment setup, a fresh Azure stand-up, and verification.

## Ground Rules

- Do not delete Azure resources until the resource group has been classified and approved.
- Keep demo target resource groups separate from the MAPPO platform resource group.
- Use `.data/mappo.env` as the only local environment file.
- Do not commit `.data/mappo.env` or any real secrets.
- Use `mappo.env.example` as the handoff template for required values.

## Current Azure Inventory

Inventory captured from the active Azure CLI subscription:

- Subscription: `c0d51042-7d0a-41f7-b270-151e4c4ea263`
- Account: `cvonde1@yahoo.com`
- Current Pulumi runtime stack: none
- Current handoff resource group: none

### Resource Groups

| Resource group | Classification | Notes |
| --- | --- | --- |
| `rg-mappo-runtime-handoff-20260425155612` | Former MAPPO platform | Deleted during handoff cleanup. |
| `rg-mappo-runtime-demo` | Legacy MAPPO platform | Delete submitted during handoff cleanup. |
| `rg-mappo-control-plane-c0d51042` | Legacy control plane | Delete submitted during handoff cleanup. |
| `rg-mappo-marketplace-forwarder-demo` | Legacy forwarder | Delete submitted during handoff cleanup. |
| Legacy direct Azure delivery target resource groups | Demo target infrastructure | Delete submitted during handoff cleanup. |
| `rg-mappo-dns-demo` | DNS | Keep unless intentionally retiring `mappopoc.com`. |
| `DefaultResourceGroup-EUS` | Azure default | Leave alone unless manually reviewed. |
| `DefaultResourceGroup-CUS` | Azure default | Leave alone unless manually reviewed. |

After deletion completes, the only MAPPO resource group expected to remain in this
subscription is:

- `rg-mappo-dns-demo`

### Additional Subscriptions

Additional MAPPO-looking resource groups were found under tenant
`5476530d-fba1-4cd5-b2c0-fa118c5ff36e`.

Subscription `1adaaa48-139a-477b-a8c8-0e6289d6d199`:

| Resource group | Classification | Notes |
| --- | --- | --- |
| `rg-mappo-targets-azure-delivery-targets-azure-delivery-1adaaa48` | Demo targets infrastructure | Delete submitted during handoff cleanup. |
| Legacy direct Azure delivery target resource groups | Demo target infrastructure | Deleted during handoff cleanup. |
| Legacy pipeline delivery target resource groups | ADO App Service demo target infrastructure | Deleted during handoff cleanup. |
| `rg-mappo-runtime-demo` | Legacy runtime artifact | Deleted during handoff cleanup. |

Subscription `597f46c7-2ce0-440e-962d-453e486f159d`:

| Resource group | Classification | Notes |
| --- | --- | --- |
| Legacy pipeline delivery target resource groups | ADO App Service demo target infrastructure | Deleted during handoff cleanup. |

## Cleanup Sequence

1. Confirm the current handoff stack can be rebuilt from `infra/pulumi`.
2. Confirm whether `rg-mappo-runtime-demo`, `rg-mappo-control-plane-c0d51042`, and `rg-mappo-marketplace-forwarder-demo` are no longer needed.
3. Export or snapshot any database state that must be preserved.
4. Delete approved legacy resource groups.
5. Stand up a fresh stack using a new timestamped stack name.
6. Verify the frontend, backend health, DB migrations, Redis, Key Vault, and marketplace forwarder.
7. Delete the previous handoff resource group only after the replacement is verified.

## Local Environment Setup

Start from a clean local environment:

```bash
cp mappo.env.example .data/mappo.env
chmod 600 .data/mappo.env
```

Edit `.data/mappo.env` and fill in the required values for the selected workflow.

For local Docker Compose only, the defaults are enough:

```bash
set -a
source .data/mappo.env
set +a

./mvnw clean install
docker compose -f infra/docker-compose.yml up --build
```

For Azure deployment, fill in at minimum:

- `PULUMI_CONFIG_PASSPHRASE`
- `AZURE_SUBSCRIPTION_ID`
- `MAPPO_RUNTIME_SUBSCRIPTION_ID`
- `MAPPO_RUNTIME_LOCATION`
- `MAPPO_AZURE_TENANT_ID`
- `MAPPO_AZURE_CLIENT_ID`
- `MAPPO_AZURE_CLIENT_SECRET`
- `MAPPO_MARKETPLACE_INGEST_TOKEN`
- `MAPPO_AZURE_KEY_VAULT_ACCESS_OBJECT_ID` if a human/operator should be able to view Key Vault secrets

Optional provider/demo values:

- `MAPPO_MANAGED_APP_RELEASE_REPO`
- `MAPPO_MANAGED_APP_RELEASE_PATH`
- `MAPPO_MANAGED_APP_RELEASE_REF`
- `MAPPO_MANAGED_APP_RELEASE_WEBHOOK_SECRET`
- `MAPPO_MANAGED_APP_RELEASE_GITHUB_TOKEN`
- `AZURE_DEVOPS_EXT_PAT`
- `MAPPO_AZURE_DEVOPS_PERSONAL_ACCESS_TOKEN`
- `MAPPO_AZURE_DEVOPS_WEBHOOK_SECRET`

## Fresh Azure Stand-Up

Use a timestamped stack/resource suffix:

```bash
export MAPPO_HANDOFF_SUFFIX="$(date -u +%Y%m%d%H%M%S)"
export PULUMI_STACK="handoff-${MAPPO_HANDOFF_SUFFIX}"
```

Authenticate:

```bash
az login
az account set --subscription "$AZURE_SUBSCRIPTION_ID"
```

Load environment:

```bash
set -a
source .data/mappo.env
set +a
```

Create the platform resources that Maven needs for artifact publishing. Keep
apps disabled for this first Pulumi pass because the image tags do not exist in
the new ACR yet.

```bash
cd infra/pulumi
pulumi stack select "$PULUMI_STACK" || pulumi stack init "$PULUMI_STACK"
pulumi config set mappo:controlPlanePostgresEnabled true
pulumi config set mappo:runtimeEnabled true
pulumi config set mappo:runtimeAppsEnabled false
pulumi config set mappo:runtimeSubscriptionId "$MAPPO_RUNTIME_SUBSCRIPTION_ID"
pulumi config set mappo:runtimeLocation "$MAPPO_RUNTIME_LOCATION"
pulumi config set mappo:azureTenantId "$MAPPO_AZURE_TENANT_ID"
pulumi config set --secret mappo:azureClientId "$MAPPO_AZURE_CLIENT_ID"
pulumi config set --secret mappo:azureClientSecret "$MAPPO_AZURE_CLIENT_SECRET"
pulumi config set --secret mappo:marketplaceIngestToken "$MAPPO_MARKETPLACE_INGEST_TOKEN"
if [[ -n "${MAPPO_AZURE_KEY_VAULT_ACCESS_OBJECT_ID:-}" ]]; then
  pulumi config set mappo:keyVaultAccessObjectId "$MAPPO_AZURE_KEY_VAULT_ACCESS_OBJECT_ID"
fi
pulumi up --stack "$PULUMI_STACK"
cd ../..
```

Publish runtime artifacts. Maven does not run Pulumi and does not mutate Azure.

```bash
export MAPPO_IMAGE_PREFIX="$(cd infra/pulumi && pulumi stack output runtimeAcrLoginServer --stack "$PULUMI_STACK")"
export MAPPO_DOCKER_USERNAME="$(az acr credential show --name "$(cd infra/pulumi && pulumi stack output runtimeAcrName --stack "$PULUMI_STACK")" --query username -o tsv)"
export MAPPO_DOCKER_PASSWORD="$(az acr credential show --name "$(cd infra/pulumi && pulumi stack output runtimeAcrName --stack "$PULUMI_STACK")" --query 'passwords[0].value' -o tsv)"
export MAPPO_RUNTIME_IMAGE_TAG="$(./mvnw -q -N initialize help:evaluate -Dexpression=mappo.image.tag -DforceStdout)"

./mvnw deploy -Ddocker.image.prefix="$MAPPO_IMAGE_PREFIX" -Dmappo.image.tag="$MAPPO_RUNTIME_IMAGE_TAG"
```

Enable runtime apps and EasyAuth from Pulumi:

```bash
cd infra/pulumi
pulumi config set mappo:imageTag "$MAPPO_RUNTIME_IMAGE_TAG"
pulumi config set mappo:runtimeAppsEnabled true
pulumi up --stack "$PULUMI_STACK"
cd ../..
```

Record outputs:

```bash
printf '%s\n' "$PULUMI_STACK" > .data/current-handoff-stack
printf '%s\n' "$MAPPO_RUNTIME_IMAGE_TAG" > .data/current-handoff-tag
cd infra/pulumi
pulumi stack output runtimeBackendUrl --stack "$PULUMI_STACK"
pulumi stack output runtimeFrontendUrl --stack "$PULUMI_STACK"
pulumi stack output runtimeKeyVaultUri --stack "$PULUMI_STACK"
cd ../..
```

## Verification

After deployment, verify:

```bash
export MAPPO_API_BASE_URL="$(cd infra/pulumi && pulumi stack output runtimeBackendUrl --stack "$PULUMI_STACK")"
export MAPPO_RUNTIME_FRONTEND_URL="$(cd infra/pulumi && pulumi stack output runtimeFrontendUrl --stack "$PULUMI_STACK")"

curl -fsS "$MAPPO_API_BASE_URL/healthz"
curl -fsS "$MAPPO_API_BASE_URL/api/v1/health"
```

In the UI:

1. Open `$MAPPO_RUNTIME_FRONTEND_URL`.
2. Confirm Admin -> Secret Inventory loads.
3. Confirm Admin -> Release Sources loads and webhook URLs match the new API base URL.
4. Confirm Admin -> Deployment Connections loads.
5. Confirm Project -> Config loads and the project flow diagram renders.
6. Confirm Project -> Releases can check for new releases for configured release sources.
7. Confirm Project -> Deployments can preview a deployment.

## EasyAuth / Entra App Registration

EasyAuth is Pulumi-owned when `mappo:runtimeAppsEnabled=true`.

Pulumi creates:

- the Entra app registration
- the app service principal
- the app password used by Container Apps EasyAuth
- the frontend callback redirect URI
- the frontend Container Apps auth config

Do not manually add stale callback URLs to the Entra app registration. If a
frontend URL changes, rerun Pulumi so the redirect URI is reconciled from the
current frontend app output.

## Post-Verification Cleanup

After the replacement stack is verified:

1. Delete approved legacy resource groups.
2. Destroy or delete obsolete Pulumi stacks that have no resources.
3. Keep only the current `.data/mappo.env` plus any intentionally retained inventory files.
4. Update this document with the final resource group and URLs.
