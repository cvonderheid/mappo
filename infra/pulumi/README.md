# MAPPO Pulumi IaC

This Pulumi project provisions demo target infrastructure for MAPPO rollout testing.

## What it creates (per target)
- Resource group
- Azure Container Apps managed environment
- Azure Container App

## Stack config contract
Pulumi config namespace is `mappo`.

Default target source:
- TypeScript profile `demo10` in `/Users/cvonderheid/workspace/mappo/infra/pulumi/targets.demo10.ts`

Optional defaults:
- `mappo:defaultLocation` (default: `eastus`)
- `mappo:defaultImage` (default: `mcr.microsoft.com/azuredocs/containerapps-helloworld:latest`)
- `mappo:defaultCpu` (default: `0.25`)
- `mappo:defaultMemory` (default: `0.5Gi`)
- `mappo:targetProfile` (default: `demo10`, supported: `demo10`, `empty`)
- `mappo:environmentMode` (default: `shared_per_subscription`, supported: `shared_per_subscription`, `per_target`)
- `mappo:demoSubscriptionId` (optional; if unset, Pulumi resolves from env or active `az account`)
- `mappo:targets` (optional explicit override array if you want to bypass profile generation)

## Commands
From repo root:
- `make iac-install`
- `make iac-preview`
- `make iac-up`
- `make iac-export-targets`
- `make iac-destroy`

## Notes
- `iac-preview` uses the selected Pulumi stack (`PULUMI_STACK`, default `dev`).
- `Pulumi.dev.yaml` defaults to `mappo:targetProfile=demo10`.
- Make targets use a local Pulumi backend and set `PULUMI_CONFIG_PASSPHRASE=mappo-local-dev` by default.
- For live deploys, authenticate first with Azure (`az login`) and configure stack target subscriptions.
