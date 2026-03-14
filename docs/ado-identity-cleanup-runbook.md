# ADO Identity Cleanup Runbook

Date: 2026-03-14

## Purpose
Safely clean up retired Azure DevOps service-connection identity artifacts in the intended order:
1. service hook subscription(s)
2. service connection
3. Azure RBAC assignments for the service principal
4. tenant-local service principal(s)
5. optional app registration

## Script
- `/Users/cvonderheid/workspace/mappo/scripts/ado_cleanup_service_connection_identity.sh`

## Prerequisites
- Azure CLI installed and logged in (`az login`)
- Azure DevOps CLI extension available (`az devops -h`)
- `jq` and `curl` installed
- ADO PAT only if service-hook cleanup is required:
  - set `AZURE_DEVOPS_EXT_PAT=<pat>` or pass `--ado-pat <pat>`

## Dry-Run First (Required)
```bash
/Users/cvonderheid/workspace/mappo/scripts/ado_cleanup_service_connection_identity.sh \
  --organization "https://dev.azure.com/pg123" \
  --project "demo-app-service" \
  --service-connection-name "mappo-ado-demo-rg-contributor" \
  --rbac-subscriptions "1adaaa48-139a-477b-a8c8-0e6289d6d199,597f46c7-2ce0-440e-962d-453e486f159d"
```

Expected result: script prints resolved service connection id/SP id/subscription scopes and exits with:
- `dry-run only. Re-run with --yes to execute.`

## Execute Cleanup
```bash
/Users/cvonderheid/workspace/mappo/scripts/ado_cleanup_service_connection_identity.sh \
  --organization "https://dev.azure.com/pg123" \
  --project "demo-app-service" \
  --service-connection-name "mappo-ado-demo-rg-contributor" \
  --rbac-subscriptions "1adaaa48-139a-477b-a8c8-0e6289d6d199,597f46c7-2ce0-440e-962d-453e486f159d" \
  --delete-app-registration true \
  --require-service-hook-cleanup \
  --yes
```

Notes:
- `--require-service-hook-cleanup` fails fast if PAT is missing or no matching hook is found.
- By default, service-hook matching uses consumer URL contains:
  - `/api/v1/admin/releases/webhooks/ado`
- You can target one exact hook with:
  - `--service-hook-id <id>`

## Post-Run Verification
1. ADO service connection removed:
```bash
az devops service-endpoint list \
  --organization "https://dev.azure.com/pg123" \
  --project "demo-app-service" \
  --query "[?name=='mappo-ado-demo-rg-contributor']"
```
2. SP RBAC removed from target subscriptions:
```bash
az role assignment list \
  --assignee "35bd7871-e6eb-406e-8909-34711fb3f8dc" \
  --scope "/subscriptions/1adaaa48-139a-477b-a8c8-0e6289d6d199"
```
3. Entra app/SP removed only if `--delete-app-registration true` was used.

