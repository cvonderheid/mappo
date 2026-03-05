# MAPPO Pulumi IaC

This Pulumi project provisions a marketplace-accurate managed application demo surface for MAPPO.

## What it creates
- Per subscription:
- Managed application definition resource group (`Microsoft.Solutions/applicationDefinitions`)
- Managed application instances resource group (`Microsoft.Solutions/applications`)
- Per target:
- Managed application instance with its own managed resource group
- Managed environment + Container App deployed by the managed app template into that managed resource group
- Optional control-plane persistence:
- Azure Database for PostgreSQL Flexible Server + `mappo` database (provider/control-plane subscription)

## Stack config contract
Pulumi config namespace is `mappo`.

Default target source:
- TypeScript profile `empty` (no targets) to avoid accidental bulk provisioning.
- For deterministic live demos, configure explicit targets via:
  - `make iac-configure-marketplace-demo PROVIDER_SUBSCRIPTION_ID=<id> CUSTOMER_SUBSCRIPTION_ID=<id> PULUMI_STACK=<stack>`

Required:
- Either `mappo:publisherPrincipalObjectId` or `mappo:publisherPrincipalObjectIds`.
  - `publisherPrincipalObjectId`: single principal object ID used for all target subscriptions.
  - `publisherPrincipalObjectIds`: per-subscription map when object IDs differ by tenant/subscription (common in cross-tenant demos).
  - Env fallback is supported for single value via `MAPPO_PUBLISHER_PRINCIPAL_OBJECT_ID`.

Required when `mappo:controlPlanePostgresEnabled=true`:
- `mappo:controlPlanePostgresAdminPassword` (secret) or env `MAPPO_CONTROL_PLANE_DB_ADMIN_PASSWORD`.

Optional:
- `mappo:defaultLocation` (default: `eastus`)
- `mappo:defaultImage` (default: `docker.io/library/python:3.11-alpine`)
- `mappo:defaultSoftwareVersion` (default: `2026.02.20.1`)
- `mappo:defaultDataModelVersion` (default: `1`)
- `mappo:defaultCpu` (default: `0.25`)
- `mappo:defaultMemory` (default: `0.5Gi`)
- `mappo:targetProfile` (default: `empty`, supported: `demo10`, `empty`)
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
- `mappo:controlPlanePostgresEnabled` (default: `false`)
- `mappo:controlPlaneSubscriptionId` (default: resolved from `mappo:demoSubscriptionId`/active az subscription)
- `mappo:controlPlaneLocation` (default: `mappo:defaultLocation`)
- `mappo:controlPlaneResourceGroupPrefix` (default: `rg-mappo-control-plane`)
- `mappo:controlPlanePostgresServerNamePrefix` (default: `pg-mappo`)
- `mappo:controlPlanePostgresDatabaseName` (default: `mappo`)
- `mappo:controlPlanePostgresAdminLogin` (default: `mappoadmin`)
- `mappo:controlPlanePostgresVersion` (default: `16`)
- `mappo:controlPlanePostgresSkuName` (default: `Standard_B1ms`)
- `mappo:controlPlanePostgresStorageSizeGb` (default: `32`)
- `mappo:controlPlanePostgresBackupRetentionDays` (default: `7`)
- `mappo:controlPlanePostgresPublicNetworkAccess` (default: `true`)
- `mappo:controlPlanePostgresAllowAzureServices` (default: `true`)
- `mappo:controlPlanePostgresAllowedIpRanges` (optional array of `x.x.x.x` or `x.x.x.x-y.y.y.y`)

## Commands
From repo root:
- `make iac-install`
- `make iac-stack-init`
- `make iac-preview`
- `make iac-up`
- `make iac-export-targets`
- `make iac-export-db-env`
- `make iac-destroy`

## Notes
- `iac-preview` uses the selected Pulumi stack (`PULUMI_STACK`, default `dev`).
- `Pulumi.dev.yaml` defaults to `mappo:targetProfile=demo10`.
- Make targets use a local Pulumi backend and set `PULUMI_CONFIG_PASSPHRASE=mappo-local-dev` by default.
- For live deploys, authenticate first with Azure (`az login`) and configure stack target subscriptions.
- `make iac-export-targets` writes Pulumi output `mappoTargetInventory` into `.data/mappo-target-inventory.json` (or `IAC_TARGET_EXPORT` override) so MAPPO can import it directly.
- `make iac-export-db-env` writes managed DB runtime env exports into `.data/mappo-db.env` (or `IAC_DB_ENV_EXPORT` override).
