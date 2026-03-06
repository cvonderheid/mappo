# MAPPO Backend (Java)

Spring Boot + jOOQ backend for MAPPO.

## Run locally

```bash
./mvnw -pl backend-java spring-boot:run
```

## Build/test

```bash
./mvnw -pl backend-java test
./mvnw -pl backend-java package
```

## Environment

- `MAPPO_JDBC_DATABASE_URL` (preferred), or `MAPPO_DATABASE_URL` (SQLAlchemy-style URL is auto-converted)
- `MAPPO_DB_USER`
- `MAPPO_DB_PASSWORD`
- `MAPPO_MARKETPLACE_INGEST_TOKEN`
- `MAPPO_AZURE_TENANT_ID`
- `MAPPO_AZURE_CLIENT_ID`
- `MAPPO_AZURE_CLIENT_SECRET`
