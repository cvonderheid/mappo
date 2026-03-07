# MAPPO

MAPPO is a multi-tenant deployment control plane for Azure Managed Apps.

## Build System

This repository now uses Maven as the primary workflow runner for build, test, code generation, and packaging.

Core commands:

```bash
./mvnw -v
./mvnw clean install
./mvnw -pl backend compile
./mvnw -pl backend test
./mvnw verify
```

Deployment commands:

```bash
export MAPPO_IMAGE_PREFIX="<acr-login-server>"

# publish MAPPO runtime artifacts only
./mvnw deploy \
  -Ddocker.image.prefix="$MAPPO_IMAGE_PREFIX" \
  -Dmappo.image.tag="<image-tag>"

# publish artifacts, then run the Azure rollout path
./mvnw -Pazure deploy \
  -Ddocker.image.prefix="$MAPPO_IMAGE_PREFIX" \
  -Dmappo.image.tag="<image-tag>" \
  -Dpulumi.stack="<stack>"
```

Notes:
- `deploy` publishes backend, frontend, and Flyway images and packages the forwarder artifact, but does not mutate Azure runtime state.
- `deploy -Pazure` then runs the Azure rollout path for this repo:
  - `pulumi up`
  - prepare/create the runtime migration job
  - run the migration job
  - apply backend/frontend runtime updates
  - deploy the webhook forwarder
- Azure rollout expects `.data/mappo-azure.env` and `.data/mappo-db.env` to already exist.

Contract workflow:

```bash
# export authoritative OpenAPI from the Java backend
./mvnw -pl backend verify

# regenerate and verify the frontend against that artifact
./mvnw -pl frontend generate-sources
./mvnw -pl frontend compile
./mvnw -pl frontend test
./mvnw -pl frontend package
```

Contract artifact paths:
- backend OpenAPI export: `/Users/cvonderheid/workspace/mappo/backend/target/openapi/openapi.json`
- frontend generated schema: `/Users/cvonderheid/workspace/mappo/frontend/src/lib/api/generated/schema.ts`

Operational automation runs directly through `scripts/` and Pulumi:

```bash
# release ingest from repo/file
./scripts/release_ingest_from_repo.sh \
  --api-base-url "$MAPPO_API_BASE_URL" \
  --github-repo cvonderheid/mappo-managed-app \
  --github-path releases/releases.manifest.json \
  --github-ref main

# demo fleet up/down lifecycle
./scripts/demo_fleet_up.sh \
  --stack demo-fleet \
  --inventory-file .data/demo-fleet-target-inventory.json \
  --api-base-url "$MAPPO_API_BASE_URL"

./scripts/demo_fleet_down.sh \
  --stack demo-fleet \
  --inventory-file .data/demo-fleet-target-inventory.json \
  --api-base-url "$MAPPO_API_BASE_URL"
```

Run backend locally:

```bash
./mvnw -pl backend spring-boot:run
```

Pulumi IaC projects are now Java-based:
- `/Users/cvonderheid/workspace/mappo/infra/pulumi`
- `/Users/cvonderheid/workspace/mappo/infra/demo-fleet`

## Backend Stack

- Java 21
- Spring Boot (REST API)
- jOOQ (data access)
- Flyway (migrations)
- Azure Java SDK (`azure-identity`, `azure-resourcemanager`)

Backend module location:
- `/Users/cvonderheid/workspace/mappo/backend`

## Database

Flyway migrations are in:
- `/Users/cvonderheid/workspace/mappo/backend/src/main/resources/db/migration`

Environment variables:
- `MAPPO_JDBC_DATABASE_URL` (preferred)
- `MAPPO_DATABASE_URL` (legacy compatibility alias)
- `MAPPO_DB_USER`
- `MAPPO_DB_PASSWORD`
- `MAPPO_MARKETPLACE_INGEST_TOKEN`
- `MAPPO_AZURE_TENANT_ID`
- `MAPPO_AZURE_CLIENT_ID`
- `MAPPO_AZURE_CLIENT_SECRET`

## API Surface (Java)

- `GET /healthz`
- `GET /api/v1/health`
- `GET /api/v1/targets`
- `GET /api/v1/releases`
- `POST /api/v1/releases`
- `GET /api/v1/runs`
- `GET /api/v1/runs/{runId}`
- `POST /api/v1/runs`
- `POST /api/v1/runs/{runId}/resume`
- `POST /api/v1/runs/{runId}/retry-failed`
- `GET /api/v1/admin/onboarding`
- `POST /api/v1/admin/onboarding/events`
- `GET /api/v1/admin/onboarding/forwarder-logs`
- `POST /api/v1/admin/onboarding/forwarder-logs`
- `PATCH /api/v1/admin/onboarding/registrations/{targetId}`
- `DELETE /api/v1/admin/onboarding/registrations/{targetId}`

## Quality Commands

Maven baseline:

```bash
./mvnw test
./mvnw verify
```
