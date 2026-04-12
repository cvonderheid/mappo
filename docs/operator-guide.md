# MAPPO Operator Guide

## Mental model
MAPPO is a release orchestration product.

Use it in this order:
1. Configure how MAPPO receives release information.
2. Configure how MAPPO talks to deployment systems.
3. Configure one project.
4. Register that project's targets.
5. Check for releases.
6. Preview and start a deployment.

## Global admin screens
### Deployment Connections
Use this page to configure outbound authenticated access to external deployment systems.

Examples:
- Azure DevOps PAT-backed connection
- future provider connections for other systems

This page should answer:
- can MAPPO authenticate?
- what external projects/resources can MAPPO discover?

Secrets for external deployment systems should live in MAPPO's Azure Key Vault when the hosted runtime is used.

Operator rule:
- create the secret in MAPPO's Azure Key Vault
- in MAPPO, choose `Use Azure Key Vault secret`
- enter only the Key Vault secret name, not the secret value

Fallbacks still supported:
- `Use MAPPO backend secret` for the single built-in provider default
- `Use backend environment variable` for legacy/demo environments

### Release Sources
Use this page to configure inbound release notifications and refresh wiring.

Examples:
- GitHub webhook/manifest source
- Azure DevOps release event source

This page should answer:
- where do release notifications come from?
- what webhook URL should the external system call?
- what secret verifies the event?

Release webhook secrets follow the same model as Deployment Connections:
- prefer Azure Key Vault secret references
- use backend env vars only when you intentionally run without Key Vault

## Project screens
### Config
Use this page to tie global release/deployment plumbing to one project.

Important sections:
- **General**: project identity and display name
- **Release Source**: which global release source feeds this project
- **Deployment Driver**: how this project deploys
- **Runtime Health**: how MAPPO checks deployed targets

Notes:
- avoid treating metadata as runtime behavior
- if a setting is really infrastructure-owned, it should be read-only or removed from normal operator flow

### Targets
Use this page as the inventory/configuration view of where this project can deploy.

A target is one deployable environment for the project.
Examples:
- one customer subscription
- one managed resource group
- one application deployment destination

### Registration Events
Use this page to review how targets got into the system.
This is history, not the main creation page.

### Releases
Use this page to:
- see known releases for the project
- check for new releases from the linked source
- inspect release metadata

### Fleet
Use this page as the operational status view.
This should answer:
- what targets exist?
- what version are they on?
- what is their runtime health?
- what was the last rollout result?

### Deployments
Use this page to:
- preview a rollout
- start a rollout
- inspect past runs and failures

This is currently the strongest operator page in the product and should be the standard for clarity.

## Current hosted demo flow
### Direct Azure rollout demo
1. Project: `Azure Managed App Deployment Stack`
2. Release Source: GitHub-backed manifest source
3. Deployment mode: direct Azure rollout using Deployment Stacks
4. Targets: two registered demo targets across subscriptions
5. Releases: check for new releases after a publisher push
6. Deployments: preview and start rollout

### Azure DevOps pipeline demo
1. Project: `Azure App Service ADO Pipeline`
2. Deployment Connection: Azure DevOps PAT-backed connection
3. Deployment mode: Azure DevOps pipeline trigger
4. Prerequisite: the selected Azure DevOps pipeline must own whatever Azure credentials/service connections it needs

## Operator guidance rules
- Prefer discovered dropdowns over typed IDs.
- Prefer plain language over backend enum names.
- Keep configuration where the operator actually uses it.
- Keep history pages separate from action pages.
- If a screen only exposes internal schema, remove or fold it into the real workflow.
