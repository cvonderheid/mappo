# MAPPO Runtime ACA Runbook

This runbook deploys MAPPO backend and frontend into Azure Container Apps (production-like control-plane runtime).

## What It Provisions

- Dedicated runtime resource group (outside Pulumi-managed RGs)
- Dedicated ACA environment
- Dedicated ACR with backend/frontend images
- External ingress for:
  - backend API (`/api/v1/...`)
  - frontend UI

## Prerequisites

- `make iac-up PULUMI_STACK=<stack>` completed
- Docker Desktop running (used as fallback image builder when ACR Tasks are restricted)
- `.data/mappo-azure.env` exists and includes:
  - `MAPPO_AZURE_TENANT_ID`
  - `MAPPO_AZURE_CLIENT_ID`
  - `MAPPO_AZURE_CLIENT_SECRET`
  - `MAPPO_AZURE_TENANT_BY_SUBSCRIPTION`
  - `MAPPO_MARKETPLACE_INGEST_TOKEN`
- `.data/mappo-db.env` exists (from `make iac-export-db-env`)

## 1) Deploy Runtime

```bash
make runtime-aca-deploy PULUMI_STACK=<stack> SUBSCRIPTION_ID="<provider-subscription-id>"
make runtime-easyauth-configure PULUMI_STACK=<stack> SUBSCRIPTION_ID="<provider-subscription-id>"
source .data/mappo-runtime.env
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

## 2) Validate Runtime

```bash
curl -fsSL "$MAPPO_RUNTIME_BACKEND_URL/api/v1/health/live"
open "$MAPPO_RUNTIME_FRONTEND_URL"
```

EasyAuth validation:
- Opening `$MAPPO_RUNTIME_FRONTEND_URL` should redirect to Microsoft Entra sign-in when not already authenticated.

## 3) Wire Forwarder to Runtime API

```bash
make marketplace-forwarder-deploy \
  RESOURCE_GROUP="rg-mappo-marketplace-forwarder" \
  FUNCTION_APP_NAME="fa-mappo-marketplace-forwarder-<suffix>" \
  SUBSCRIPTION_ID="<provider-subscription-id>" \
  MAPPO_INGEST_TOKEN="$MAPPO_MARKETPLACE_INGEST_TOKEN"
```

Notes:
- `MAPPO_API_BASE_URL` is optional in this command if `.data/mappo-runtime.env` exists; script auto-loads it.

## 4) Teardown Runtime

```bash
make runtime-aca-destroy
```

Or explicit:

```bash
make runtime-aca-destroy RESOURCE_GROUP="<runtime-rg>" SUBSCRIPTION_ID="<provider-subscription-id>"
```
