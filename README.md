# MAPPO

MAPPO is a multi-tenant deployment control plane for Azure Managed Apps.

## Build System

This repository now uses Maven as the primary workflow runner for build, test, code generation, and packaging.

Core commands:

```bash
./mvnw -v
./mvnw -pl backend compile
./mvnw -pl backend test
./mvnw verify
```

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
  --github-repo <owner>/<repo> \
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
