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

Required demo RBAC for the MAPPO runtime service principal:
- Contributor on each target resource group.
- Reader on the mirrored Template Spec resource group in each target subscription.
- Contributor on each subscription's shared demo-fleet managed environment resource group so ARM can perform `Microsoft.App/managedEnvironments/join/action` during Container App deployment.

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

Release registration is API-driven and idempotent on `(source_ref, source_version)`
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
  --github-repo "cvonderheid/mappo-managed-app" \
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
      "source_ref": "/subscriptions/<provider-sub>/resourceGroups/<rg>/providers/Microsoft.Resources/templateSpecs/<name>",
      "source_version": "2026.03.04.1",
      "source_type": "template_spec",
      "deployment_scope": "resource_group",
      "source_version_ref": "/subscriptions/<provider-sub>/resourceGroups/<rg>/providers/Microsoft.Resources/templateSpecs/<name>/versions/2026.03.04.1",
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

## 6) Cross-tenant Template Spec note

For this demo, each release's Template Spec version must exist in every target subscription under the same path shape:
- resource group: `rg-mappo-control-plane-c0d51042`
- Template Spec name: `mappo-webapp-managed-app`
- version: release `source_version`

Reason:
- ARM deployments in the customer tenant cannot link directly to a provider-tenant Template Spec version ID.
- MAPPO's Java executor rewrites the subscription segment of the provider-side `source_version_ref` to the target subscription and expects a mirrored Template Spec version to exist there.

Practical workflow for a new demo release:
1. publish the Template Spec version in the provider subscription,
2. publish the same version in the customer subscription under the mirrored resource group/name,
3. ingest the release manifest from `cvonderheid/mappo-managed-app`,
4. start the MAPPO deployment run.
