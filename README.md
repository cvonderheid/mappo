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

# publish MAPPO runtime artifacts only; image tag defaults to <project-version>-<git-sha>
./mvnw deploy \
  -Ddocker.image.prefix="$MAPPO_IMAGE_PREFIX"
```

Notes:
- `deploy` publishes backend, frontend, and Flyway images and packages the forwarder artifact, but does not mutate Azure runtime state.
- Maven does not run Pulumi or mutate Azure. Infrastructure changes are applied directly from the relevant Pulumi project.
- The Docker image tag defaults to the Maven project version plus the 12-character Git commit, for example `1.0.0-SNAPSHOT-c9225249259d`. Override `-Dmappo.image.tag=...` only for an intentional one-off publish.

Runtime infrastructure workflow:

```bash
cd infra/pulumi
pulumi up --stack "<stack>"
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
- backend OpenAPI export: `./backend/target/openapi/openapi.json`
- frontend generated schema: `./frontend/src/lib/api/generated/schema.ts`

Operational automation runs directly through `scripts/` and Pulumi:

```bash
# release ingest from repo/file
./scripts/release_ingest_from_repo.sh \
  --api-base-url "$MAPPO_API_BASE_URL" \
  --github-repo owner/release-catalog \
  --github-path releases/releases.manifest.json \
  --github-ref main

# azure delivery demo targets up/down lifecycle
./scripts/targets_azure_delivery_up.sh \
  --stack targets-azure-delivery \
  --inventory-file .data/targets-azure-delivery-inventory.json \
  --api-base-url "$MAPPO_API_BASE_URL"

./scripts/targets_azure_delivery_down.sh \
  --stack targets-azure-delivery \
  --inventory-file .data/targets-azure-delivery-inventory.json \
  --api-base-url "$MAPPO_API_BASE_URL"
```

Run backend locally:

```bash
./mvnw -pl backend spring-boot:run
```

Pulumi IaC projects are now Java-based:
- `./infra/pulumi`
- `./infra/demo/targets-azure-delivery`
- `./infra/demo/targets-pipeline-delivery`

## Backend Stack

- Java 21
- Spring Boot (REST API)
- jOOQ (data access)
- Flyway (migrations)
- Azure Java SDK (`azure-identity`, `azure-resourcemanager`)

Backend module location:
- `./backend`

## Database

Flyway migrations are in:
- `./backend/src/main/resources/db/migration`

Environment variables:
- Use `./mappo.env.example` as the consolidated template.
- Copy it to `.data/mappo.env`, fill in real values, and source it before running local/demo scripts.

## API Surface (Java)

Primary operator-facing routes:

- `GET /healthz`
- `GET /api/v1/health`
- `GET /docs`
- `GET /api/v1/openapi.json`
- `GET /api/v1/targets/page`
- `GET /api/v1/releases`
- `POST /api/v1/releases`
- `GET /api/v1/runs`
- `GET /api/v1/runs/{runId}`
- `POST /api/v1/runs`
- `POST /api/v1/runs/{runId}/resume`
- `POST /api/v1/runs/{runId}/retry-failed`
- `POST /api/v1/runs/preview`
- `GET /api/v1/events/stream`
- `GET /api/v1/admin/onboarding/registrations`
- `GET /api/v1/admin/onboarding/events`
- `GET /api/v1/admin/onboarding/forwarder-logs/page`
- `POST /api/v1/admin/onboarding/forwarder-logs`
- `PATCH /api/v1/admin/onboarding/registrations/{targetId}`
- `DELETE /api/v1/admin/onboarding/registrations/{targetId}`
- `GET /api/v1/admin/releases/webhook-deliveries`
- `POST /api/v1/admin/releases/webhooks/github`

## Quality Commands

Maven baseline:

```bash
./mvnw test
./mvnw verify
```
