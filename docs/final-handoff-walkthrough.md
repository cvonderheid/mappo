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
| `rg-mappo-demo-target-demo-target-01` | Demo target | Delete submitted during handoff cleanup. |
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
| `rg-mappo-demo-fleet-demo-fleet-1adaaa48` | Demo fleet infrastructure | Delete submitted during handoff cleanup. |
| `rg-mappo-demo-target-demo-target-02` | Demo target | Deleted during handoff cleanup. |
| `rg-mappo-appservice-target-ado-target-01` | ADO App Service demo target | Deleted during handoff cleanup. |
| `rg-mappo-runtime-demo` | Legacy runtime artifact | Deleted during handoff cleanup. |

Subscription `597f46c7-2ce0-440e-962d-453e486f159d`:

| Resource group | Classification | Notes |
| --- | --- | --- |
| `rg-mappo-appservice-target-ado-target-02` | ADO App Service demo target | Deleted during handoff cleanup. |

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
- `MAPPO_CONTROL_PLANE_SUBSCRIPTION_ID`
- `MAPPO_RUNTIME_LOCATION`
- `MAPPO_IMAGE_PREFIX`
- `MAPPO_DOCKER_USERNAME`
- `MAPPO_DOCKER_PASSWORD`
- `MAPPO_AZURE_TENANT_ID`
- `MAPPO_AZURE_CLIENT_ID`
- `MAPPO_AZURE_CLIENT_SECRET`
- `MAPPO_MARKETPLACE_INGEST_TOKEN`

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

Publish runtime artifacts. Maven does not run Pulumi and does not mutate Azure.

```bash
./mvnw deploy \
  -Ddocker.image.prefix="$MAPPO_IMAGE_PREFIX"
```

Apply infrastructure from Pulumi:

```bash
cd infra/pulumi
pulumi up --stack "$PULUMI_STACK"
cd ../..
```

Record outputs:

```bash
printf '%s\n' "$PULUMI_STACK" > .data/current-handoff-stack
./mvnw -q help:evaluate -Dexpression=project.version -DforceStdout > .data/current-handoff-tag
```

## Verification

After deployment, verify:

```bash
source .data/mappo.env

curl -fsS "$MAPPO_API_BASE_URL/healthz"
curl -fsS "$MAPPO_API_BASE_URL/api/v1/health"
```

In the UI:

1. Open the frontend URL recorded in `.data/mappo.env`.
2. Confirm Admin -> Secret Inventory loads.
3. Confirm Admin -> Release Sources loads and webhook URLs match the new API base URL.
4. Confirm Admin -> Deployment Connections loads.
5. Confirm Project -> Config loads and the project flow diagram renders.
6. Confirm Project -> Releases can check for new releases for configured release sources.
7. Confirm Project -> Deployments can preview a deployment.

## EasyAuth / Entra App Registration

During cleanup, the stale EasyAuth app registration `mappo-ui-easyauth-demo`
(`cea04ff0-519a-4555-be96-b5b2cc1212bc`) was deleted because it only referenced
legacy frontend callback URLs.

Current state:

- EasyAuth is configured by `scripts/runtime_easyauth_configure.sh` after the
  frontend Container App URL exists.
- The script now replaces redirect URIs with the current frontend callback URL
  plus explicit `MAPPO_EASYAUTH_EXTRA_REDIRECT_URIS`; it does not preserve stale
  redirect URIs from older deployments.
- Full Pulumi ownership is possible, but it should be done with a follow-up
  runtime IaC change: either move the frontend Container App into Pulumi so the
  callback URL is an output, or add a second Pulumi stack that consumes the
  deployed frontend URL as config. The current `infra/pulumi` stack only owns
  ARM control-plane resources and does not own the script-created runtime
  Container Apps.

## Post-Verification Cleanup

After the replacement stack is verified:

1. Delete approved legacy resource groups.
2. Destroy or delete obsolete Pulumi stacks that have no resources.
3. Keep only the current `.data/mappo.env` plus any intentionally retained inventory files.
4. Update this document with the final resource group and URLs.
