# MAPPO Architecture

## Overview
MAPPO is a provider-tenant control plane that orchestrates release rollouts across customer subscriptions in multiple tenants (via Azure Lighthouse delegated access).

## Core Model
- Target: a customer-tenant subscription + managed app instance.
- Release: a Template Spec version and deployment metadata.
- Deployment Run: execution of one release across selected targets.

## Control Plane Components
1. API service
- Owns Targets/Releases/Runs APIs.
- Exposes UI-facing status/query surfaces.

2. Orchestrator service
- Expands target sets by tags/groups.
- Applies rollout strategy (all-at-once or waves).
- Enforces stop thresholds and concurrency caps.

3. Per-target executor
- Executes state machine per target:
  - `QUEUED` -> `VALIDATING` -> `DEPLOYING` -> `VERIFYING` -> (`SUCCEEDED` | `FAILED`)
- Captures stage timestamps, errors, and correlation IDs.

4. Persistence
- Stores fleet state, run history, per-target stage records, and logs.
- Retains run/deployment history for 3 months.

## Control / Data / Verification Boundaries
- Control flow: run/wave scheduling and per-target stage transitions.
- Data flow: release parameters and target-scoped overrides.
- Verification flow: post-deploy health checks and rollout halt decisions.

## Deployment Direction
- App services hosted on Azure Container Apps.
- Azure APIs accessed using delegated Lighthouse permissions.
- UI and API are separate deployable containers.

## Determinism + Legibility Contract
- Stage transitions are append-only and timestamped.
- Every failed target has a structured error and correlation IDs.
- Run-level summaries derive from per-target stage truth.
