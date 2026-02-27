# MAPPO Pulumi IaC

This Pulumi project provisions demo target infrastructure for MAPPO rollout testing.

## What it creates (per target)
- Resource group
- Azure Container Apps managed environment
- Azure Container App

## Stack config contract
Pulumi config namespace is `mappo`.

Required to deploy targets:
- `mappo:targets` (array)

Optional defaults:
- `mappo:defaultLocation` (default: `eastus`)
- `mappo:defaultImage` (default: `mcr.microsoft.com/azuredocs/containerapps-helloworld:latest`)
- `mappo:defaultCpu` (default: `0.25`)
- `mappo:defaultMemory` (default: `0.5Gi`)

Use `/Users/cvonderheid/workspace/mappo/infra/pulumi/Pulumi.demo-10tenants.yaml.example` as a template for 10-tenant demo config.

## Commands
From repo root:
- `make iac-install`
- `make iac-preview`
- `make iac-up`
- `make iac-export-targets`
- `make iac-destroy`

## Notes
- `iac-preview` uses the selected Pulumi stack (`PULUMI_STACK`, default `dev`).
- `Pulumi.dev.yaml` ships with an empty target list for safe local preview.
- Make targets use a local Pulumi backend and set `PULUMI_CONFIG_PASSPHRASE=mappo-local-dev` by default.
- For live deploys, authenticate first with Azure (`az login`) and configure stack target subscriptions.
