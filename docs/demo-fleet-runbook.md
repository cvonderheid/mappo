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
./scripts/demo_fleet_configure.sh \
  --stack demo-fleet \
  --provider-subscription-id "<provider-subscription-guid>" \
  --customer-subscription-id "<customer-subscription-guid>" \
  --location eastus
```

Optional tenant overrides:

```bash
./scripts/demo_fleet_configure.sh \
  --stack demo-fleet \
  --provider-subscription-id "<provider-subscription-guid>" \
  --customer-subscription-id "<customer-subscription-guid>" \
  --provider-tenant-id "<provider-tenant-guid>" \
  --customer-tenant-id "<customer-tenant-guid>"
```

## 2) Bring fleet up and register targets

```bash
source .data/mappo-runtime.env
source .data/mappo-azure.env
./scripts/demo_fleet_up.sh \
  --stack demo-fleet \
  --api-base-url "$MAPPO_API_BASE_URL" \
  --ingest-token "$MAPPO_MARKETPLACE_INGEST_TOKEN"
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
./scripts/marketplace_ingest_events.sh \
  --inventory-file ".data/demo-fleet-target-inventory.json" \
  --api-base-url "$MAPPO_API_BASE_URL" \
  --ingest-token "$MAPPO_MARKETPLACE_INGEST_TOKEN" \
  --event-type "subscription_suspended" \
  --event-id-prefix "evt-demo-suspend"
```

Delete/offboard + destroy:

```bash
source .data/mappo-runtime.env
source .data/mappo-azure.env
./scripts/demo_fleet_down.sh \
  --stack demo-fleet \
  --api-base-url "$MAPPO_API_BASE_URL" \
  --ingest-token "$MAPPO_MARKETPLACE_INGEST_TOKEN"
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
./scripts/release_ingest_from_repo.sh \
  --api-base-url "$MAPPO_API_BASE_URL" \
  --manifest-file "/absolute/path/releases.manifest.json"
```

GitHub manifest:

```bash
source .data/mappo-runtime.env
./scripts/release_ingest_from_repo.sh \
  --api-base-url "$MAPPO_API_BASE_URL" \
  --github-repo "<owner>/<repo>" \
  --github-path "releases/releases.manifest.json" \
  --github-ref "main"
```

Azure DevOps manifest:

```bash
source .data/mappo-runtime.env
./scripts/release_ingest_from_repo.sh \
  --api-base-url "$MAPPO_API_BASE_URL" \
  --ado-org "<org>" \
  --ado-project "<project>" \
  --ado-repository "<repo>" \
  --ado-path "/releases/releases.manifest.json" \
  --ado-ref "main"
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
