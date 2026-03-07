# MAPPO Runtime ACA Runbook

This runbook deploys MAPPO backend and frontend into Azure Container Apps (production-like control-plane runtime).

## What It Provisions

- Dedicated runtime resource group (outside Pulumi-managed RGs)
- Dedicated ACA environment
- Dedicated ACR with backend/frontend images
- Dedicated ACA migration job (`job-mappo-db-<stack>`) running Flyway migrations
- External ingress for:
  - backend API (`/api/v1/...`)
  - frontend UI

## Prerequisites

- `cd infra/pulumi && pulumi up --stack <stack> --yes` completed
- Docker Desktop running (used as fallback image builder when ACR Tasks are restricted)
- `.data/mappo-azure.env` exists and includes:
  - `MAPPO_AZURE_TENANT_ID`
  - `MAPPO_AZURE_CLIENT_ID`
  - `MAPPO_AZURE_CLIENT_SECRET`
  - `MAPPO_AZURE_TENANT_BY_SUBSCRIPTION`
  - `MAPPO_MARKETPLACE_INGEST_TOKEN`
- `.data/mappo-db.env` exists (from `./scripts/iac_export_db_env.sh --stack <stack>`)

## Preferred Command Surface

Publish-only:

```bash
export MAPPO_IMAGE_PREFIX="<acr-login-server>"
./mvnw deploy \
  -Ddocker.image.prefix="$MAPPO_IMAGE_PREFIX" \
  -Dmappo.image.tag="<image-tag>"
```

Azure rollout:

```bash
export MAPPO_IMAGE_PREFIX="<acr-login-server>"
./mvnw -Pazure deploy \
  -Ddocker.image.prefix="$MAPPO_IMAGE_PREFIX" \
  -Dmappo.image.tag="<image-tag>" \
  -Dpulumi.stack="<stack>"
```

The `azure` profile orchestrates:
- `pulumi up`
- migration job prepare/create
- migration job execution
- backend/frontend runtime update
- forwarder deployment

The scripts below remain the underlying operator primitives for targeted reruns or debugging.

## 1) Deploy Runtime

```bash
./scripts/runtime_aca_deploy.sh --stack <stack> --subscription-id "<provider-subscription-id>"
./scripts/runtime_easyauth_configure.sh --stack <stack> --subscription-id "<provider-subscription-id>"
source .data/mappo-runtime.env
```

On-demand migration rerun:
```bash
./scripts/runtime_db_migrate_job_run.sh --stack <stack> --subscription-id "<provider-subscription-id>"
```

Outputs in `.data/mappo-runtime.env`:
- `MAPPO_RUNTIME_BACKEND_URL`
- `MAPPO_RUNTIME_FRONTEND_URL`
- `MAPPO_API_BASE_URL`

Outputs in `.data/mappo-easyauth.env`:
- `MAPPO_EASYAUTH_CLIENT_ID`
- `MAPPO_EASYAUTH_CLIENT_SECRET`
- `MAPPO_EASYAUTH_CALLBACK_URL`

Quota notes:
- If your subscription allows only one ACA environment, script automatically reuses an existing environment.
- If ACR Tasks are disabled, script automatically falls back to local `docker buildx --platform linux/amd64 --push`.
- Runtime deploy blocks on migration job success before rolling backend/frontend revisions.

## 2) Validate Runtime

```bash
curl -fsSL "$MAPPO_RUNTIME_BACKEND_URL/api/v1/health/live"
open "$MAPPO_RUNTIME_FRONTEND_URL"
```

EasyAuth validation:
- Opening `$MAPPO_RUNTIME_FRONTEND_URL` should redirect to Microsoft Entra sign-in when not already authenticated.

## 3) Wire Forwarder to Runtime API

```bash
./scripts/marketplace_forwarder_deploy.sh \
  --stack <stack> \
  --subscription-id "<provider-subscription-id>" \
  --mappo-ingest-token "$MAPPO_MARKETPLACE_INGEST_TOKEN"
```

Notes:
- `MAPPO_API_BASE_URL` is optional in this command if `.data/mappo-runtime.env` exists; script auto-loads it.
- `--stack` now supplies default names for the resource group and Function App.

## 4) Teardown Runtime

```bash
./scripts/runtime_aca_destroy.sh
```

Or explicit:

```bash
./scripts/runtime_aca_destroy.sh --resource-group "<runtime-rg>" --subscription-id "<provider-subscription-id>"
```
