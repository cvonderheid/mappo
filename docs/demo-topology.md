# MAPPO Demo Topology

## Scope
This document describes the current hosted demo at a high level. It intentionally avoids retired paths such as Template Specs and old sprint-era setup scripts.

## Current truth
- MAPPO runtime is hosted in Azure Container Apps.
- Postgres is hosted in Azure Database for PostgreSQL Flexible Server.
- Marketplace-style registration events flow through an Azure Function forwarder.
- The primary proven rollout path is direct Azure rollout using Deployment Stacks.
- Release artifacts are published from `/Users/cvonderheid/workspace/mappo-managed-app`.

## Main Azure resource areas
### Control plane
Provisioned by Pulumi:
- control-plane Postgres
- baseline infrastructure needed by the hosted MAPPO runtime

### Runtime
Applied by the Azure delivery lifecycle:
- backend Container App
- frontend Container App
- Flyway migration job
- runtime image rollout

### Marketplace forwarder
Applied by the Azure delivery lifecycle:
- Azure Function App that forwards registration events into MAPPO

### Target fleets
Provisioned separately from the control plane:
- demo target fleet for direct Azure rollout
- app service target fleet for Azure DevOps pipeline experiments

## Current project shapes
### Azure Managed App Deployment Stack
- release source: GitHub-backed manifest source
- deployment mode: direct Azure rollout using Deployment Stacks
- current demo path: fully usable

### Azure App Service ADO Pipeline
- release source and deployment connection can be configured
- deployment mode: Azure DevOps pipeline trigger
- usable only when the Azure DevOps project has a real Azure service connection

## Important boundaries
- This repo deploys MAPPO itself.
- `/Users/cvonderheid/workspace/mappo-managed-app` defines the customer workload release catalog.
- MAPPO owns rollout orchestration, visibility, retries, and stop policies.
- Azure does not fan out one global deployment automatically; MAPPO updates each selected target.
