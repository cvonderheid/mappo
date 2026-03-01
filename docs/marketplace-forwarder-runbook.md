# Marketplace Forwarder Runbook

This runbook prepares the production-like webhook path:

Marketplace lifecycle webhook -> Azure Function App forwarder -> MAPPO onboarding API (`/api/v1/admin/onboarding/events`)

## What Is Automated

- Function source package build (`make marketplace-forwarder-package`)
- Function App provisioning + deployment via Azure CLI (`make marketplace-forwarder-deploy`)
- Inventory replay through the deployed webhook endpoint (`make marketplace-forwarder-replay-inventory`)

## 1) Package Function Code

```bash
make marketplace-forwarder-package
```

Output:
- `.data/marketplace-forwarder-function.zip`

## 2) Deploy Function App

If you deployed MAPPO runtime via ACA script, load runtime outputs first:

```bash
source .data/mappo-runtime.env
```

```bash
make marketplace-forwarder-deploy \
  RESOURCE_GROUP="rg-mappo-marketplace-forwarder" \
  FUNCTION_APP_NAME="fa-mappo-marketplace-forwarder-<suffix>" \
  LOCATION="eastus" \
  SUBSCRIPTION_ID="<provider-subscription-id>" \
  MAPPO_INGEST_TOKEN="<same-token-as-MAPPO_MARKETPLACE_INGEST_TOKEN>"
```

If `MAPPO_API_BASE_URL` is omitted, deploy script falls back to `.data/mappo-runtime.env`.

Script prints:
- `function_url`
- `webhook_url` (includes function key query string when available)

Use `webhook_url` in Partner Center technical configuration.

## 3) Test Through Real Webhook Path

Replay inventory through Function App (instead of calling MAPPO backend directly):

```bash
make marketplace-forwarder-replay-inventory \
  FORWARDER_URL="https://<function-app>.azurewebsites.net/api/marketplace/events?code=<function-key>"
```

Expected:
- Function App returns MAPPO onboarding response bodies.
- `GET /api/v1/admin/onboarding` shows applied events and registered targets.

## Function Payload Contract

The forwarder supports:

1. Direct MAPPO onboarding payload shape (already normalized).
2. Marketplace wrapper payload with explicit MAPPO target block:

```json
{
  "id": "evt-123",
  "event_type": "subscription_purchased",
  "mappo_target": {
    "tenant_id": "<tenant-guid>",
    "subscription_id": "<subscription-guid>",
    "container_app_resource_id": "/subscriptions/.../providers/Microsoft.App/containerApps/...",
    "managed_application_id": "/subscriptions/.../providers/Microsoft.Solutions/applications/...",
    "managed_resource_group_id": "/subscriptions/.../resourceGroups/...",
    "target_group": "prod",
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
- `/Users/cvonderheid/workspace/mappo/docs/marketplace-portal-playbook.md`
