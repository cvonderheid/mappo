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
- demo target fleet for direct Azure rollout; this uses marketplace-style registration and deregistration events
- app service target fleet for Azure DevOps pipeline experiments; this uses Pulumi inventory import/delete against MAPPO target APIs

## Current project shapes
### Azure Managed App Deployment Stack
- release source: GitHub-backed manifest source
- deployment mode: direct Azure rollout using Deployment Stacks
- current demo path: fully usable

### Azure App Service ADO Pipeline
- release source: Azure DevOps pipeline event
- deployment connection: Azure DevOps PAT-backed connection used by MAPPO to trigger the deployment pipeline
- deployment mode: Azure DevOps pipeline trigger
- release readiness comes from a separate ADO pipeline triggered by PR merge to `main`
- usable only when the selected Azure DevOps deployment pipeline owns the Azure credentials/service connections it needs
- targets are imported from App Service Pulumi inventory, not marketplace registration events

## Important boundaries
- This repo deploys MAPPO itself.
- `/Users/cvonderheid/workspace/mappo-managed-app` defines the customer workload release catalog.
- `/Users/cvonderheid/workspace/demo-app-service` defines the Azure App Service workload and ADO pipeline YAML for the pipeline demo.
- MAPPO owns rollout orchestration, visibility, retries, and stop policies.
- Azure does not fan out one global deployment automatically; MAPPO updates each selected target.
