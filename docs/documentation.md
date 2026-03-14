# MAPPO Documentation

## Local Development

### Prerequisites
- Java 21+
- Maven 3.9+
- PostgreSQL 16+

### Maven workflow

```bash
# full build (includes backend local runtime image: mappo-backend:local)
./mvnw clean install

# compile backend
./mvnw -pl backend compile

# run tests
./mvnw -pl backend test

# full verification for current modules
./mvnw verify

# run backend API
./mvnw -pl backend spring-boot:run
```

### Local compose runtime

```bash
# 1) build everything first (especially backend image)
./mvnw clean install

# 2) run local stack (backend image + hot-reload frontend)
docker compose -f infra/docker-compose.yml up --build
```

Notes:
- `backend` service in compose uses the prebuilt `mappo-backend:local` image.
- `frontend` service is still hot-reload (`npm run dev`).

### Backend module
- `/Users/cvonderheid/workspace/mappo/backend`

### Database migrations
- `/Users/cvonderheid/workspace/mappo/backend/src/main/resources/db/migration`
- Flyway runs automatically on backend startup.

### Environment
- `MAPPO_JDBC_DATABASE_URL` (preferred)
- `MAPPO_DATABASE_URL` (legacy compatibility alias)
- `MAPPO_DB_USER`
- `MAPPO_DB_PASSWORD`
- `MAPPO_MARKETPLACE_INGEST_TOKEN`
- `MAPPO_AZURE_TENANT_ID`
- `MAPPO_AZURE_CLIENT_ID`
- `MAPPO_AZURE_CLIENT_SECRET`

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
- Release: versioned deployment artifact plus rollout metadata.
- Deployment Run: one rollout execution over selected targets.

## Bug-to-Test Loop
1. Reproduce the issue and capture expected vs actual.
2. Add or extend a deterministic failing test.
3. Implement the fix.
4. Keep the test as regression coverage.
