# MAPPO Architecture

## Overview
MAPPO is a provider-hosted control plane for rolling releases across registered Azure targets.

Current hosted demo truth:
- MAPPO itself runs in Azure Container Apps.
- Releases are sourced from external systems such as GitHub.
- The primary proven rollout path is direct Azure rollout using Deployment Stacks.
- The Azure DevOps pipeline path exists as a second deployment mode. MAPPO triggers the selected pipeline; the pipeline owns its Azure credentials/service connections.
- The current demo does not use live `Microsoft.Solutions/applications` instances and does not use Template Specs.

## Core objects
- **Project**: one deployable product/application in MAPPO.
- **Release Source**: how MAPPO learns that new releases exist.
- **Release**: one concrete versioned deployment definition.
- **Deployment Connection**: outbound authenticated connection MAPPO uses to talk to external deployment systems.
- **Target**: one deployable environment for a project.
- **Deployment Run**: rollout of one release across one or more selected targets.

## Operator model
MAPPO is easiest to understand as four questions:
1. Where do releases come from?
   - Admin -> Release Sources
2. How does MAPPO talk to external deployment systems?
   - Admin -> Deployment Connections
3. Where can this project deploy?
   - Project -> Targets / Registration Events
4. What happened when we rolled out?
   - Project -> Fleet / Releases / Deployments

## Inbound vs outbound boundaries
### Inbound
Release Sources receive or refresh release information from external systems.
Examples:
- GitHub webhook or manifest refresh
- Azure DevOps service hook or pipeline event

### Outbound
Deployment Connections hold MAPPO's authenticated access to external deployment systems.
Examples:
- Azure DevOps PAT-backed connection
- future GitHub or Pulumi provider connection

This separation is intentional:
- Release Sources answer **how MAPPO hears about versions**.
- Deployment Connections answer **how MAPPO talks back out to deployment systems**.

## Deployment modes
### Direct Azure rollout
MAPPO updates each selected target directly in Azure using that target's Deployment Stack.

Characteristics:
- target-by-target execution is owned by MAPPO
- rollout ordering, retries, stop policies, and health decisions stay in MAPPO
- Azure is the execution target, not the rollout orchestrator

### Pipeline-driven rollout
MAPPO triggers an external pipeline per selected target instead of mutating Azure directly.

Current implementation:
- Azure DevOps pipeline trigger
- project, repo, branch, and pipeline discovery through the selected Deployment Connection
- Azure credentials and service connections are pipeline-owned; MAPPO should not model them as project configuration

## Runtime components
- **Backend API**: Spring Boot API, orchestration, persistence, Azure integrations
- **Frontend UI**: React UI protected by EasyAuth
- **Postgres**: control-plane database
- **Marketplace forwarder**: Azure Function that forwards registration events into MAPPO

## Current hosting shape
- Control-plane data services are provisioned by Pulumi.
- Runtime app deployment is driven by Maven + Azure CLI/Container Apps scripts under the `delivery` lifecycle.
- Target fleets are provisioned separately from the MAPPO runtime.
- The workload release catalog lives outside this repo in `/Users/cvonderheid/workspace/mappo-managed-app`.

## Contracts that matter
- Backend OpenAPI is authoritative: `/Users/cvonderheid/workspace/mappo/backend/target/openapi/openapi.json`
- Frontend generated types come from that OpenAPI artifact.
- Publisher release manifests should describe release artifacts, not MAPPO-internal project routing.

## Extensibility direction
MAPPO should stay organized around:
- inbound release sources
- outbound deployment connections
- project-scoped deployment modes
- target-scoped deployment destinations

That keeps the control plane reusable across:
- direct Azure rollouts
- Azure DevOps pipeline rollouts
- future Lighthouse/Pulumi or other external deployment drivers
