# MAPPO Documentation

## Local Development

### Prerequisites
- Python 3.11+
- GNU Make

### Bootstrap commands
```bash
# full bootstrap (deps + db migrate + lint/typecheck/test + phase gate + build)
make install

# deps only
make install-deps
```

`make install` now auto-starts/waits for local compose Postgres (`5433`) during `db-migrate` when needed.

### Process commands
```bash
make workflow-discipline-check
make docs-consistency-check
make golden-principles-check
make check-no-demo-leak
make lint-backend-file-size
make phase1-gate-fast
make phase1-gate-full
```

### Marketplace demo commands
```bash
export MAPPO_PUBLISHER_PRINCIPAL_OBJECT_ID="<azure-ad-object-id>"
make iac-stack-init
make iac-up
make iac-export-db-env
make azure-tenant-map SUBSCRIPTION_IDS="<sub1>,<sub2>"
make azure-onboard-multitenant-runtime CLIENT_ID="$MAPPO_AZURE_CLIENT_ID" SUBSCRIPTION_IDS="<sub1>,<sub2>"
make azure-cleanup-runtime-identity CLIENT_ID="$MAPPO_AZURE_CLIENT_ID" SUBSCRIPTION_IDS="<sub1>,<sub2>" DELETE_APP_REGISTRATION=true
source .data/mappo-db.env
make bootstrap-releases
make azure-preflight
make runtime-aca-deploy PULUMI_STACK="<stack>" SUBSCRIPTION_ID="<provider-sub>"
make runtime-db-migrate-job-run PULUMI_STACK="<stack>" SUBSCRIPTION_ID="<provider-sub>"
make runtime-easyauth-configure PULUMI_STACK="<stack>" SUBSCRIPTION_ID="<provider-sub>"
source .data/mappo-runtime.env
make deploy PULUMI_STACK="<stack>" SUBSCRIPTION_ID="<provider-sub>"
make marketplace-forwarder-package
make marketplace-forwarder-deploy RESOURCE_GROUP="<rg>" FUNCTION_APP_NAME="<name>" MAPPO_API_BASE_URL="$MAPPO_API_BASE_URL" MAPPO_INGEST_TOKEN="$MAPPO_MARKETPLACE_INGEST_TOKEN"
make runtime-aca-destroy RESOURCE_GROUP="<runtime-rg>"
make clean-slate-local
```

Simulation-only helpers:
```bash
make iac-export-targets
make marketplace-forwarder-replay-inventory FORWARDER_URL="<https://.../api/marketplace/events?code=...>"
```

### Partner Center API helpers
```bash
make partner-center-token
make partner-center-api URL="<https://api.partnercenter.microsoft.com/...>" [METHOD=GET]
```

### Azure execution guardrail env vars
```bash
# Multi-tenant authority mapping (subscription -> tenant)
# Supports JSON object or comma list:
#  subA=tenantGuidA,subB=tenantGuidB
MAPPO_AZURE_TENANT_BY_SUBSCRIPTION='{"sub-a":"tenant-guid-a","sub-b":"tenant-guid-b"}'

# Concurrency shaping
MAPPO_AZURE_MAX_RUN_CONCURRENCY=6
MAPPO_AZURE_MAX_SUBSCRIPTION_CONCURRENCY=2

# Retry/backoff for transient ARM/ACA errors
MAPPO_AZURE_MAX_RETRY_ATTEMPTS=5
MAPPO_AZURE_RETRY_BASE_DELAY_SECONDS=1.0
MAPPO_AZURE_RETRY_MAX_DELAY_SECONDS=20.0
MAPPO_AZURE_RETRY_JITTER_SECONDS=0.35

# Quota preflight behavior
MAPPO_AZURE_ENABLE_QUOTA_PREFLIGHT=true
MAPPO_AZURE_QUOTA_WARNING_HEADROOM_RATIO=0.1
MAPPO_AZURE_QUOTA_MIN_REMAINING_WARNING=2
```

## Engineering workflow discipline (before implementation)
- For non-trivial work, write/refresh `tasks/todo.md` first:
  - scope,
  - checkable plan,
  - verification commands.
- Re-plan immediately if assumptions fail.
- Do not mark work complete without running listed verification commands.
- Record recurring/systemic corrections in `tasks/lessons.md`.

## Terminology Contract
- Target: customer subscription deployment unit.
- Release: versioned Template Spec deployment payload.
- Deployment Run: one rollout execution over selected targets.

## Bug-to-Test Loop
1. Reproduce the issue and capture expected vs actual.
2. Add or extend a deterministic failing test.
3. Implement the fix.
4. Keep the test as regression coverage.
