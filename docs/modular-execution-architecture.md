# MAPPO Modular Execution Architecture

Date: 2026-03-10
Status: proposed

## Purpose
MAPPO currently runs one production-shaped deployment model well:
- Azure target environments
- provider-hosted control plane
- Deployment Stack executor
- Blob-hosted ARM template artifacts
- publisher ACR-hosted runtime images

That is a good first product shape, but it is too specific to serve as the long-term orchestration core for other projects.

Future projects may need different combinations such as:
- Azure Lighthouse + Pulumi
- Azure Lighthouse + external CI/CD pipeline
- Marketplace publisher access + customer-local deployment artifacts
- single-tenant service principal + direct Azure SDK deployment

The architecture needs to evolve so that MAPPO is the orchestration core and each project plugs in its own access, materialization, deployment, and verification strategies.

## Problem Statement
The current model still makes release source type the dominant execution switch.
That is too narrow for a generalized control plane.

Today, the main execution path couples together:
- target access assumptions,
- deployment mechanism,
- artifact format,
- Azure implementation details,
- runtime verification assumptions.

That coupling is why `deployment_stack` currently feels like "the product" instead of "the first deployment adapter."

## Design Goals
1. Keep the run orchestration core stable.
2. Allow multiple project types without multiplying controller/service branches.
3. Separate identity/access concerns from deployment concerns.
4. Separate deployment driver from release artifact source.
5. Keep target health and verification pluggable.
6. Preserve current working Deployment Stack behavior as the first implementation.

## Non-Goals
- Do not introduce a second backend module yet.
- Do not convert the whole repo to a plugin framework immediately.
- Do not add Pulumi/Lighthouse/pipeline drivers in the same slice as the abstraction work.
- Do not change the current runtime UX or release flow yet.

## Core Model
The orchestration core should be expressed in four distinct axes.

### 1. Project
A project is the top-level deployable product definition inside MAPPO.
It answers:
- what kind of thing is being deployed,
- what deployment capability it uses,
- what access model it expects,
- what health model it uses.

Example project shapes:
- `managed-app-webapp`
- `lighthouse-pulumi-network`
- `lighthouse-ado-platform`

### 2. Access Strategy
Access strategy defines how MAPPO gains authority in the target environment.

Examples:
- `marketplace_publisher_access`
- `lighthouse_delegated_access`
- `single_tenant_service_principal`

This is a project/target concern, not a release concern.

### 3. Deployment Driver
Deployment driver defines how MAPPO applies a release.

Examples:
- `azure_deployment_stack`
- `azure_template_spec`
- `pulumi_automation`
- `pipeline_trigger`

This is the actual execution mechanism.

### 4. Release Artifact Source
Release artifact source defines where deployable inputs come from.

Examples:
- GitHub manifest row
- Blob-hosted ARM template URI
- Pulumi program repo/ref
- pipeline definition and input set

This is not the same thing as the deployment driver.
A deployment driver may consume several kinds of release material.

## Stable Orchestration Core
The following concepts should remain generic and reusable across projects:
- targets
- releases
- runs
- rollout strategy
- stages
- retry/resume
- verification result model
- logs/events
- stop conditions

The orchestration core should not need to know whether a project uses:
- Deployment Stacks,
- Template Specs,
- Pulumi Automation,
- Azure DevOps pipelines,
- GitHub Actions,
- or another driver.

## Proposed Execution Contracts
The next refactor should introduce explicit execution contracts.

### `TargetAccessResolver`
Resolves target-side access and validates it.

Responsibilities:
- validate target auth configuration
- create execution context for a target
- resolve tenant/subscription/resource authority

Examples:
- `MarketplacePublisherAccessResolver`
- `LighthouseAccessResolver`

### `ReleaseMaterializer`
Turns a release into driver-ready deployable inputs.

Responsibilities:
- fetch/resolve release artifact inputs
- validate required artifact references
- normalize parameters for the chosen driver

Examples:
- `BlobArmTemplateMaterializer`
- `GithubManifestMaterializer`
- `PulumiProgramMaterializer`

### `DeploymentDriver`
Executes preview/apply operations.

Responsibilities:
- preview target impact
- apply deployment
- normalize deployment result/failure metadata

Examples:
- `AzureDeploymentStackDriver`
- `AzureTemplateSpecDriver`
- `PulumiAutomationDriver`
- `PipelineTriggerDriver`

### `RuntimeHealthProvider`
Owns post-deploy verification and optional ongoing runtime checks.

Examples:
- `ContainerAppRuntimeHealthProvider`
- `HttpEndpointHealthProvider`
- `ExternalPipelineHealthProvider`

## Data Model Direction
The current `Release.sourceType` field is useful but too small to carry the full architecture.

Long term, MAPPO should add explicit project-level configuration.

### Proposed high-level entities
#### `ProjectDefinition`
- `id`
- `name`
- `platform`
- `accessStrategy`
- `deploymentDriver`
- `healthProvider`
- `projectConfig`

#### `Target`
- belongs to `projectId`
- target identifiers and metadata
- target execution/access config

#### `Release`
- belongs to `projectId`
- release metadata
- artifact references
- release input/config payload

#### `Run`
- belongs to `projectId`
- references `releaseId`
- contains rollout policy and target set selection

This lets MAPPO answer:
- which project owns this target,
- how that project authenticates,
- how that project deploys,
- how that project verifies runtime.

## Package Layout Direction
Do not move to multi-module packaging yet.
Refactor within the current backend module first.

### Proposed package structure
```text
com.mappo.controlplane.api
  admin
  releases
  runs
  targets
  live

com.mappo.controlplane.domain
  project
  release
  run
  target
  access
  execution
  health

com.mappo.controlplane.application
  admin
  releases
  runs
  targets
  orchestration

com.mappo.controlplane.infrastructure
  persistence
    jooq
  azure
    auth
    containerapps
    deploymentstack
    templatespec
    lighthouse
  github
  redis
  pulumi
  pipelines
    ado
    github

com.mappo.controlplane.shared
  config
  errors
  events
  util
```

### Practical migration rule
- `api`: HTTP contracts only
- `application`: orchestration and use-case coordination
- `domain`: interfaces, policies, and records central to the orchestration model
- `infrastructure`: Azure, GitHub, Redis, Pulumi, pipeline implementations

## First Implementation Seam
The first code move should not be a full package migration.
It should be an interface extraction around the current Deployment Stack flow.

### First seam to extract
Introduce a driver-oriented execution package with the current Deployment Stack implementation as the first adapter.

Specifically:
1. `DeploymentDriver`
2. `DeploymentPreviewDriver`
3. `TargetAccessResolver`
4. `ReleaseMaterializer`
5. `RuntimeHealthProvider`

Then wire the current stack path as:
- `MarketplacePublisherAccessResolver`
- `BlobArmTemplateMaterializer`
- `AzureDeploymentStackDriver`
- `ContainerAppRuntimeHealthProvider`

This keeps behavior unchanged while creating the abstraction boundary needed for:
- Lighthouse + Pulumi
- Lighthouse + pipeline trigger

## Why This Is Better Than Adding More Enums
Do not solve future project types by adding more top-level release source enums and switch blocks.

That approach would mix together:
- identity strategy,
- deployment driver,
- artifact source,
- verification strategy.

Those are independent concerns.
A growing enum matrix will turn `RunExecutionService` into a control-plane monolith.

## Recommended Phased Migration
### Phase 1: Extraction without behavior change
- define execution/access/materialization/health contracts
- adapt the current Deployment Stack implementation to those contracts
- keep current Azure demo behavior unchanged

### Phase 2: Project definition layer
- add `ProjectDefinition` and associate targets/releases/runs with a project
- move driver/access/health selection to project config

### Phase 3: Add one new deployment driver
Choose one:
- `pulumi_automation`
- `pipeline_trigger`

Do not add both in the same slice.

### Phase 4: Add one new access strategy
Choose one:
- `lighthouse_delegated_access`
- customer-local marketplace access variant

## Recommendation
The next code slice should be:
1. add the execution contracts and registry,
2. move the current Deployment Stack flow behind them,
3. leave the HTTP and data model mostly unchanged,
4. only then add project-level modeling.

That is the lowest-risk path to turn MAPPO from one deployment implementation into a general orchestration platform.
