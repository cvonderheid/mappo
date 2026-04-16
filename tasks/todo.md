# MAPPO Active Backlog

## Next demo / operator UX
- Finalize the engineering demo flow for both active projects:
  - Direct Azure project (`VECTR: Azure Managed App Deployment Stack`): keep Pulumi as the source of target infrastructure, send marketplace-style registration/deregistration events, verify targets appear/disappear in MAPPO, create GitHub managed-app releases, and deploy directly through Azure.
  - Done locally: App Service ADO project (`Azure App Service ADO Pipeline`) now has Pulumi inventory import/delete scripts instead of manual Bulk Import or marketplace-style offboarding events.
  - Done locally: ADO release-readiness pipeline template, deployment pipeline template, release PR helper, release source config helper, and service hook config helper are in place.
  - Done locally: Demo page is now a demo-only guide/status page with command snippets and the low-level event simulator under `Advanced`.
  - Remaining live validation: commit/push `/Users/cvonderheid/workspace/demo-app-service` release pipeline files, create/update the actual ADO release-readiness pipeline, run `scripts/ado_release_webhook_bootstrap.sh` for the service hook/backend webhook secret, reconfigure the live `azure-appservice-ado-pipeline` project with ADO org `https://dev.azure.com/pg123`, project `demo-app-service`, deployment pipeline id `1`, branch `main`, and pipeline-owned Azure credentials wording.
  - Remaining live validation: run App Service fleet up/import, generate an ADO release PR, confirm MAPPO release creation, start an ADO pipeline deployment, and validate teardown removes active targets while preserving historical registration events.
- Rename or restructure the current `Managed App` page so project registration history and global integration plumbing are not mixed.

## Data and configuration cleanup
- Support first-class selectable secret references for multiple accounts of the same provider type.

## Pre-production work
- Remove non-essential seeded data from Flyway migrations.
- Cut a clean baseline migration before a production release.
- Reevaluate string IDs versus numeric surrogate keys.
- Continue backend package cleanup by bounded context.
