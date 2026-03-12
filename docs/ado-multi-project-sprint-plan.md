# MAPPO Multi-Project ADO Sprint Plan

Date: 2026-03-11
Status: in progress

## Purpose
MAPPO currently proves one real deployment project:
- project: `azure-managed-app-deployment-stack`
- deployment driver: `azure_deployment_stack`
- target subscriptions:
  - `c0d51042-7d0a-41f7-b270-151e4c4ea263`
  - `1adaaa48-139a-477b-a8c8-0e6289d6d199`

The next platform milestone is to prove that MAPPO can orchestrate more than one project, with more than one deployment driver, across more than one delegated subscription set.

The first non-Deployment-Stack driver will be:
- deployment driver: `pipeline_trigger`
- external system: Azure DevOps Pipelines
- target platform: Azure App Service
- access strategy: `lighthouse_delegated_access`

## Current Ground Truth
Today, MAPPO effectively has:
- 1 real project
- 1 real deployment driver
- 2 real target subscriptions

The next additional customer subscription is:
- subscription: `597f46c7-2ce0-440e-962d-453e486f159d`
- tenant: `5476530d-fba1-4cd5-b2c0-fa118c5ff36e`

## Outcome We Want
At the end of this plan, MAPPO should be able to demonstrate:
1. project switching in the UI
2. one project using Deployment Stacks
3. one project using Azure DevOps pipelines
4. targets spanning multiple subscriptions
5. a shared orchestration core that does not assume all projects are Azure ARM/Deployment Stack based

## Project 1
### Name
`azure-managed-app-deployment-stack`

### Role
This remains the reference Azure-native project.

### Driver
`azure_deployment_stack`

### Access strategy
Current provider-hosted Azure control-plane model

### Target subscriptions
- `c0d51042-7d0a-41f7-b270-151e4c4ea263`
- `1adaaa48-139a-477b-a8c8-0e6289d6d199`

## Project 2
### Name
`azure-appservice-ado-pipeline`

### Role
This is the first proof that MAPPO can orchestrate a non-Deployment-Stack project.

### Driver
`pipeline_trigger`

### Access strategy
`lighthouse_delegated_access`

### Platform
Azure App Service

### Target subscriptions
- `1adaaa48-139a-477b-a8c8-0e6289d6d199`
- `597f46c7-2ce0-440e-962d-453e486f159d`

### Application shape
Keep the deployed app intentionally simple:
- small web app
- returns release version
- returns deployment marker or build metadata
- no database required for the first ADO project

## Auth Model
### MAPPO to Azure
MAPPO continues to use its provider-side Azure control-plane identity model.

### ADO to Azure
The ADO pipeline should use:
- an Azure service connection
- backed by a service principal in the managing tenant
- delegated access to the customer subscriptions through Azure Lighthouse

This should not depend on the MAPPO ACA managed identity.

### ADO to MAPPO
ADO should notify MAPPO that a release is ready through a webhook or service hook.

Recommended trigger:
- pipeline/build completion

Not recommended:
- raw repo push events

Reason:
- MAPPO should ingest only after deployable artifacts are actually published.

## Sprint 1
### Goal
Prepare the platform and Azure environment for the first ADO-backed project.

### Scope
1. Harden generic deployment-driver capabilities
2. Finalize access-resolution seams for non-Azure-native drivers
3. Define the ADO project model in MAPPO
4. Provision the second project's Azure targets
5. Set up ADO auth and project foundations

### Deliverables
1. Deployment-driver capability model finalized
- preview support
- cancel support
- external run URL/ID
- external logs support

2. Access-resolution model finalized
- `TargetAccessResolver`
- resolved access-context object usable by external drivers

3. ADO project configuration model added
- project-level pipeline driver config
- release input model for ADO-backed releases
- target input model for App Service targets

4. Azure target infrastructure provisioned
- lightweight App Service deployment target in:
  - `1adaaa48-139a-477b-a8c8-0e6289d6d199`
  - `597f46c7-2ce0-440e-962d-453e486f159d`

5. Azure DevOps project bootstrap completed
- ADO project created
- service connection created
- Lighthouse delegation validated for the service principal

### Progress
- [x] Added the second-project Azure target module under `infra/appservice-fleet`
- [x] Wired `infra/appservice-fleet` into the root Maven reactor
- [x] Added sample App Service workload packaging under `delivery/appservice-demo-app`
- [x] Added `appservice_fleet_configure/up/down/package.sh` operator scripts
- [x] Generalized inventory ingest so non-managed-app projects can register targets with project-specific execution config
- [ ] Provision App Service targets in both customer subscriptions
- [ ] Bootstrap the Azure DevOps project and service connection

### Acceptance criteria
- MAPPO data model can represent an ADO-backed project without Azure Deployment Stack assumptions
- the second project's Azure targets exist
- ADO auth model is proven against both customer subscriptions
- no ADO deployment driver code is required to manually prove the service connection works

## Sprint 2
### Goal
Implement the first real `pipeline_trigger` driver for Azure DevOps.

### Scope
1. Trigger pipeline runs from MAPPO
2. Poll and normalize external pipeline state
3. Fetch log summaries and deep links
4. Wire release ingest for the ADO-backed project
5. Validate project-specific Fleet and Deployments UX

### Deliverables
1. ADO pipeline driver MVP
- start pipeline run
- poll run status
- persist external run ID/URL
- fetch run summary/log links
- map pipeline state into MAPPO stages

2. ADO pipeline definition
- parameterized deployment for App Service
- inputs:
  - tenant
  - subscription
  - resource group
  - app service name
  - release version
  - artifact/package version

3. ADO release-ingest path
- service hook triggers MAPPO
- MAPPO fetches canonical release metadata from ADO repo or artifact source
- release is created in project 2

4. UI validation
- project switcher cleanly scopes:
  - Fleet
  - Deployments
  - Demo
  - Admin

### Acceptance criteria
- project 2 can deploy through ADO to both target subscriptions
- MAPPO run detail shows external pipeline identity and status cleanly
- release ingest for project 2 does not reuse GitHub-specific assumptions

## Sprint 3
### Goal
Harden the multi-project, multi-driver experience after the first ADO proof.

### Scope
1. Driver-specific UX cleanup
2. Better external execution observability
3. Retry/resume hardening for pipeline-driven runs
4. Decide whether the next driver is Pulumi Automation

### Deliverables
1. Driver capability UX
- show preview type
- show external log support
- show cancel support

2. Better auditability
- ADO release webhook/service-hook history
- external run handle audit trail

3. Retry/resume semantics for pipeline runs
- retry failed targets by creating new pipeline runs
- define resume semantics honestly for pipeline drivers

4. Decision point
- choose Sprint 4 as:
  - `pulumi_automation`
  - or deeper ADO/Lighthouse hardening

### Acceptance criteria
- MAPPO feels coherent with two different driver types
- operational metadata for external runs is good enough for demo and operator use
- there is a clear decision on whether to move next to Pulumi

## Explicit Non-Goals
- Do not add Pulumi driver in parallel with ADO.
- Do not make the ADO sample app complicated.
- Do not add a database to the ADO sample app in the first pass.
- Do not promise generic preview/cancel semantics beyond what the ADO driver can actually support.

## Recommended Order Of Work
1. Sprint 1 platform prep and Azure target setup
2. Sprint 2 ADO pipeline driver MVP
3. Sprint 3 hardening and multi-project polish
4. Re-evaluate Pulumi after the ADO driver proves the abstraction
