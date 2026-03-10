# Marketplace Forwarder Runbook

This runbook prepares the production-like webhook path:

Marketplace lifecycle webhook -> Azure Function App forwarder -> MAPPO onboarding API (`/api/v1/admin/onboarding/events`)

## What Is Automated

- Function source package build (`./scripts/marketplace_forwarder_package.sh`)
- Function App provisioning + deployment via Azure CLI (`./scripts/marketplace_forwarder_deploy.sh`)
- Inventory replay through the deployed webhook endpoint (`./scripts/marketplace_forwarder_replay_inventory.sh`)

## 1) Package Function Code

```bash
./scripts/marketplace_forwarder_package.sh \
  --function-app-name "fa-mappo-marketplace-forwarder-<suffix>"
```

Output:
- `.data/marketplace-forwarder-function.zip`

Notes:
- Packaging is Maven-driven from `/Users/cvonderheid/workspace/mappo/integrations/marketplace-forwarder-function`.
- The script runs the Azure Functions Maven packaging goal, then zips the staged artifact for `config-zip` deployment.

## 2) Deploy Function App

If you deployed MAPPO runtime via ACA script, load runtime outputs first:

```bash
source .data/mappo-runtime.env
```

```bash
./scripts/marketplace_forwarder_deploy.sh \
  --stack <stack> \
  --location "eastus" \
  --subscription-id "<provider-subscription-id>" \
  --mappo-ingest-token "<same-token-as-MAPPO_MARKETPLACE_INGEST_TOKEN>"
```

If `MAPPO_API_BASE_URL` is omitted, deploy script falls back to `.data/mappo-runtime.env`.

Notes:
- `--stack` defaults the resource group to `rg-mappo-marketplace-forwarder-<stack>`.
- `--stack` defaults the Function App name to `fa-mappo-marketplace-forwarder-<stack>-<subtoken>`.

Script prints:
- `function_url`
- `webhook_url` (includes function key query string when available)

Use `webhook_url` in Partner Center technical configuration.

Important:
- For Managed Application notifications, Azure calls the webhook with `/resource` appended.
- MAPPO forwarder accepts both:
  - `/api/marketplace/events`
  - `/api/marketplace/events/resource`

## 3) Test Through Real Webhook Path

Replay inventory through Function App (instead of calling MAPPO backend directly):

```bash
./scripts/marketplace_forwarder_replay_inventory.sh \
  --forwarder-url "https://<function-app>.azurewebsites.net/api/marketplace/events?code=<function-key>"
```

Expected:
- Function App returns MAPPO onboarding response bodies.
- `GET /api/v1/admin/onboarding/registrations` shows registered targets.
- `GET /api/v1/admin/onboarding/events` shows applied onboarding events.
- `GET /api/v1/admin/onboarding/forwarder-logs/page` shows recent forwarder logs.
- Forwarder failures are posted to `POST /api/v1/admin/onboarding/forwarder-logs` and visible in the Admin `Forwarder Logs` tab.

## Function Payload Contract

The forwarder supports:

1. Direct MAPPO onboarding payload shape (already normalized, camelCase).
2. Marketplace wrapper payload with explicit MAPPO target block. The forwarder accepts both camelCase and legacy snake_case field names, then emits camelCase to MAPPO:

```json
{
  "id": "evt-123",
  "eventType": "subscription_purchased",
  "mappoTarget": {
    "tenantId": "<tenant-guid>",
    "subscriptionId": "<subscription-guid>",
    "containerAppResourceId": "/subscriptions/.../providers/Microsoft.App/containerApps/...",
    "managedApplicationId": "/subscriptions/.../providers/Microsoft.Solutions/applications/...",
    "managedResourceGroupId": "/subscriptions/.../resourceGroups/...",
    "targetGroup": "prod",
    "region": "eastus",
    "environment": "prod",
    "tier": "standard"
  }
}
```

## Security Guidance

- Keep MAPPO onboarding endpoint token-gated:
  - `MAPPO_MARKETPLACE_INGEST_TOKEN` on backend
  - matching `MAPPO_INGEST_TOKEN` in Function App app settings
- Keep Function App at `FUNCTION` auth level and use webhook URL with `?code=...`.
- Add rate limits and monitoring (App Insights / alerts) before live demos with higher event volume.

### About Service Tags

Use identity/token controls first. Do not assume a dedicated "Marketplace webhook" Azure service tag exists for Function inbound restrictions.

Why:
- Marketplace SaaS webhook docs focus on webhook endpoint + token validation and retry behavior.
- Azure service tag catalogs expose many first-party tags, but not a dedicated marketplace-webhook inbound tag.

Recommended model:
- Function key + MAPPO ingest token + payload validation.
- If stricter network controls are required, place APIM/Application Gateway in front and enforce additional auth/rules there.

## Manual Partner Center Steps (Still Required)

- Offer/plan creation, listing content, and publish workflow in Partner Center UI.
- Webhook URL placement in technical configuration.
- Certification / private audience configuration.

See also:
- `/Users/cvonderheid/workspace/mappo/docs/demo-azure-topology.md`
