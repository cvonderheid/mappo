# MAPPO Backend

Spring Boot + jOOQ backend for MAPPO.

## Run locally

```bash
./mvnw -pl backend spring-boot:run
```

## Build/test

```bash
./mvnw -pl backend test
./mvnw -pl backend package
```

## Environment

- `MAPPO_JDBC_DATABASE_URL` (preferred)
- `MAPPO_DATABASE_URL` (legacy compatibility alias)
- `MAPPO_DB_USER`
- `MAPPO_DB_PASSWORD`
- `MAPPO_MARKETPLACE_INGEST_TOKEN`
- `MAPPO_AZURE_TENANT_ID`
- `MAPPO_AZURE_MANAGED_IDENTITY_CLIENT_ID` for hosted user-assigned managed identity selection
