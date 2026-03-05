# Demo Fleet Runbook

This runbook provisions a target fleet separate from MAPPO control-plane infra and simulates
marketplace lifecycle events without Partner Center.

## Purpose

- `infra/pulumi`: MAPPO control plane.
- `infra/demo-fleet`: target resource groups + Container Apps across customer subscriptions.

MAPPO discovers/manages targets through simulated lifecycle events:
- `subscription_purchased`
- `subscription_suspended`
- `subscription_deleted`

## 1) Configure demo-fleet stack

```bash
make demo-fleet-configure \
  DEMO_FLEET_STACK=demo-fleet \
  PROVIDER_SUBSCRIPTION_ID="<provider-subscription-guid>" \
  CUSTOMER_SUBSCRIPTION_ID="<customer-subscription-guid>" \
  LOCATION="eastus"
```

Optional tenant overrides:

```bash
make demo-fleet-configure \
  DEMO_FLEET_STACK=demo-fleet \
  PROVIDER_SUBSCRIPTION_ID="<provider-subscription-guid>" \
  CUSTOMER_SUBSCRIPTION_ID="<customer-subscription-guid>" \
  PROVIDER_TENANT_ID="<provider-tenant-guid>" \
  CUSTOMER_TENANT_ID="<customer-tenant-guid>"
```

## 2) Bring fleet up and register targets

```bash
source .data/mappo-runtime.env
source .data/mappo-azure.env
make demo-fleet-up \
  DEMO_FLEET_STACK=demo-fleet \
  API_BASE_URL="$MAPPO_API_BASE_URL" \
  INGEST_TOKEN="$MAPPO_MARKETPLACE_INGEST_TOKEN"
```

What this does:
- `pulumi up` in `infra/demo-fleet`
- writes `.data/demo-fleet-target-inventory.json`
- sends `subscription_purchased` events to `POST /api/v1/admin/onboarding/events`

## 3) Simulate suspend/delete lifecycle

Suspend:

```bash
source .data/mappo-runtime.env
source .data/mappo-azure.env
make marketplace-ingest-events \
  INVENTORY_FILE=".data/demo-fleet-target-inventory.json" \
  API_BASE_URL="$MAPPO_API_BASE_URL" \
  INGEST_TOKEN="$MAPPO_MARKETPLACE_INGEST_TOKEN" \
  EVENT_TYPE="subscription_suspended" \
  EVENT_ID_PREFIX="evt-demo-suspend"
```

Delete/offboard + destroy:

```bash
source .data/mappo-runtime.env
source .data/mappo-azure.env
make demo-fleet-down \
  DEMO_FLEET_STACK=demo-fleet \
  API_BASE_URL="$MAPPO_API_BASE_URL" \
  INGEST_TOKEN="$MAPPO_MARKETPLACE_INGEST_TOKEN"
```

What `demo-fleet-down` does:
- exports latest inventory from stack output
- sends `subscription_deleted` events
- destroys demo-fleet resources

## 4) UI flow

- Open MAPPO UI and use **Demo** page.
- Click **Simulate Marketplace Event**.
- Select event type and target, then submit.
- Expected outcome card explains what MAPPO will do before submit.

## 5) Register releases from repo artifact manifest

Release registration is API-driven and idempotent on `(template_spec_id, template_spec_version)`
unless you explicitly pass `ALLOW_DUPLICATES=1`.

Local manifest file:

```bash
source .data/mappo-runtime.env
make release-ingest-from-repo \
  API_BASE_URL="$MAPPO_API_BASE_URL" \
  MANIFEST_FILE="/absolute/path/releases.manifest.json"
```

GitHub manifest:

```bash
source .data/mappo-runtime.env
make release-ingest-from-repo \
  API_BASE_URL="$MAPPO_API_BASE_URL" \
  GITHUB_REPO="<owner>/<repo>" \
  GITHUB_PATH="releases/releases.manifest.json" \
  GITHUB_REF="main"
```

Azure DevOps manifest:

```bash
source .data/mappo-runtime.env
make release-ingest-from-repo \
  API_BASE_URL="$MAPPO_API_BASE_URL" \
  ADO_ORG="<org>" \
  ADO_PROJECT="<project>" \
  ADO_REPOSITORY="<repo>" \
  ADO_PATH="/releases/releases.manifest.json" \
  ADO_REF="main"
```

Supported manifest shape:

```json
{
  "releases": [
    {
      "template_spec_id": "/subscriptions/<provider-sub>/resourceGroups/<rg>/providers/Microsoft.Resources/templateSpecs/<name>",
      "template_spec_version": "2026.03.04.1",
      "deployment_mode": "template_spec",
      "deployment_scope": "resource_group",
      "template_spec_version_id": "/subscriptions/<provider-sub>/resourceGroups/<rg>/providers/Microsoft.Resources/templateSpecs/<name>/versions/2026.03.04.1",
      "parameter_defaults": {
        "softwareVersion": "2026.03.04.1",
        "dataModelVersion": "3"
      },
      "release_notes": "Example release",
      "verification_hints": [
        "GET / returns softwareVersion 2026.03.04.1",
        "GET / returns dataModelVersion 3"
      ]
    }
  ]
}
```
