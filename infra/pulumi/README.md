# MAPPO Pulumi IaC

This Pulumi project provisions a marketplace-accurate managed application demo surface for MAPPO.

## What it creates
- Per subscription:
- Managed application definition resource group (`Microsoft.Solutions/applicationDefinitions`)
- Managed application instances resource group (`Microsoft.Solutions/applications`)
- Per target:
- Managed application instance with its own managed resource group
- Managed environment + Container App deployed by the managed app template into that managed resource group

## Stack config contract
Pulumi config namespace is `mappo`.

Default target source:
- TypeScript profile `demo10` in `/Users/cvonderheid/workspace/mappo/infra/pulumi/targets.demo10.ts`

Required:
- Either `mappo:publisherPrincipalObjectId` or `mappo:publisherPrincipalObjectIds`.
  - `publisherPrincipalObjectId`: single principal object ID used for all target subscriptions.
  - `publisherPrincipalObjectIds`: per-subscription map when object IDs differ by tenant/subscription (common in cross-tenant demos).
  - Env fallback is supported for single value via `MAPPO_PUBLISHER_PRINCIPAL_OBJECT_ID`.

Optional:
- `mappo:defaultLocation` (default: `eastus`)
- `mappo:defaultImage` (default: `mcr.microsoft.com/azuredocs/containerapps-helloworld:latest`)
- `mappo:defaultCpu` (default: `0.25`)
- `mappo:defaultMemory` (default: `0.5Gi`)
- `mappo:targetProfile` (default: `demo10`, supported: `demo10`, `empty`)
- `mappo:demoSubscriptionId` (optional; if unset, Pulumi resolves from env or active `az account`)
- `mappo:targets` (optional explicit override array if you want to bypass profile generation)
- `mappo:publisherRoleDefinitionId` (default: Contributor role ID)
- `mappo:definitionNamePrefix` (default: `mappo-ma-def`)
- `mappo:definitionResourceGroupPrefix` (default: `rg-mappo-ma-def`)
- `mappo:applicationResourceGroupPrefix` (default: `rg-mappo-ma-apps`)
- `mappo:managedEnvironmentNamePrefix` (default: `cae-mappo-ma`)
- `mappo:managedAppNamePrefix` (default: `mappo-ma`)
- `mappo:managedResourceGroupPrefix` (default: `rg-mappo-ma-mrg`)
- `mappo:containerAppNamePrefix` (default: `ca-mappo-ma`)

## Commands
From repo root:
- `make iac-install`
- `make iac-stack-init`
- `make iac-preview`
- `make iac-up`
- `make iac-export-targets`
- `make iac-destroy`

## Notes
- `iac-preview` uses the selected Pulumi stack (`PULUMI_STACK`, default `dev`).
- `Pulumi.dev.yaml` defaults to `mappo:targetProfile=demo10`.
- Make targets use a local Pulumi backend and set `PULUMI_CONFIG_PASSPHRASE=mappo-local-dev` by default.
- For live deploys, authenticate first with Azure (`az login`) and configure stack target subscriptions.
- `make iac-export-targets` writes Pulumi output `mappoTargetInventory` into `.data/mappo-target-inventory.json` (or `IAC_TARGET_EXPORT` override) so MAPPO can import it directly.
