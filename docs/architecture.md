# MAPPO Architecture

## Overview
MAPPO is a provider-tenant control plane that orchestrates release rollouts across customer subscriptions in multiple tenants using Azure Managed Application onboarding.

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
- Applies Azure guardrails before run start:
  - run-level concurrency cap,
  - per-subscription concurrency cap,
  - quota preflight checks (ACA usage per subscription/region).

3. Per-target executor
- Executes state machine per target:
  - `QUEUED` -> `VALIDATING` -> `DEPLOYING` -> `VERIFYING` -> (`SUCCEEDED` | `FAILED`)
- Captures stage timestamps, errors, and correlation IDs.
- Adapter boundary supports execution modes (`demo` and `azure`) so orchestration and persistence stay unchanged across runtimes.

4. Persistence
- Stores fleet state, run history, per-target stage records, and logs.
- Retains run/deployment history for 3 months.

## Control / Data / Verification Boundaries
- Control flow: run/wave scheduling and per-target stage transitions.
- Data flow: release parameters and target-scoped overrides.
- Verification flow: post-deploy health checks and rollout halt decisions.

## Azure Rate-Limit + Quota Guardrails
- API retries use exponential backoff + jitter for transient Azure failures (`408`, `409`, `429`, `5xx`) and honor `Retry-After` when present.
- Scheduler batches target execution by subscription to avoid write bursts against ARM.
- Optional quota preflight queries ACA usage for each target subscription/region and can reduce concurrency when quota headroom is low.
- Guardrail decisions are persisted with the deployment run (`concurrency`, `subscription_concurrency`, `guardrail_warnings`) for operator visibility.

## Deployment Direction
- App services hosted on Azure Container Apps.
- Azure APIs accessed through provider identity authorization on managed resource groups created by managed application instances.
- UI and API are separate deployable containers.
- Demo automation boundary:
  - Pulumi IaC provisions managed app definitions, managed app instances, shared ACA environments, and exports MAPPO inventory.
  - Partner Center offer lifecycle is handled via API/CLI helper scripts.
  - Portal-only steps are documented in `/Users/cvonderheid/workspace/mappo/docs/marketplace-portal-playbook.md`.

## Determinism + Legibility Contract
- Stage transitions are append-only and timestamped.
- Every failed target has a structured error and correlation IDs.
- Run-level summaries derive from per-target stage truth.
