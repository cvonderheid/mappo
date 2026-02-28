# MAPPO Live Demo Checklist (Marketplace-Accurate Managed App)

Use this checklist for a demo aligned to the Marketplace managed application model:
- MAPPO control plane in provider tenant,
- managed application instances deployed in customer subscriptions,
- ACA workloads running inside managed resource groups,
- MAPPO orchestrating upgrades against those ACA targets.

## 1) Target Topology

- [ ] Provider tenant exists for MAPPO control plane and publisher identity.
- [ ] At least 2 customer tenants exist (10 preferred for full demo).
- [ ] Each customer subscription has one managed application instance installed.
- [ ] Each managed application instance creates a managed resource group with a target Container App.

## 2) Provider Setup

- [ ] Create service principal + env file via script:
  - `make azure-auth-bootstrap`
- [ ] Load env file in your shell:
  - `source .data/mappo-azure.env`
- [ ] Ensure managed application authorizations grant provider runtime identity enough rights on managed resource groups for:
  - reading Container App state,
  - performing deployment operations,
  - reading deployment status and health signals.

## 3) Customer Onboarding (Marketplace Simulation)

- [ ] Publish/assign managed app plan (private plan is fine for demo).
- [ ] Deploy managed app instance in each target customer subscription.
- [ ] Verify managed application resource exists (`Microsoft.Solutions/applications`) and points to a managed resource group.
- [ ] Verify exactly one intended target Container App exists in each managed resource group (or document selection rule if multiple exist).

## 4) MAPPO Inventory + Runtime

- [ ] Discover targets from managed app instances:
  - `make managed-app-discover-targets SUBSCRIPTION_IDS="<provider-sub>,<customer-sub>"`
- [ ] Import fleet into MAPPO:
  - `make import-targets`
- [ ] Ensure release catalog exists (managed-demo-refresh does this automatically):
  - `make bootstrap-releases`
- [ ] Run readiness check:
  - `make azure-preflight`
- [ ] Start backend in Azure mode:
  - `make dev-backend-azure`
- [ ] Start frontend:
  - `make dev-frontend`

## 5) Validation Run

- [ ] Create a single-target canary run and verify stage progression:
  - `VALIDATING -> DEPLOYING -> VERIFYING -> SUCCEEDED`
- [ ] Verify per-target logs include structured error details and Azure correlation IDs.
- [ ] Execute 10-target rollout with stop policy enabled.
- [ ] Confirm retry/resume behavior on failed or halted runs.
- [ ] Confirm fleet view reflects latest deployed release per successful target.
