# MAPPO Deployment Runbook

## Local build and test
```bash
./mvnw clean install
./mvnw verify
```

Useful module-level commands:
```bash
./mvnw -pl backend test
./mvnw -pl frontend compile
./mvnw -pl frontend test
```

## Local runtime
Build first, then start the local stack:
```bash
./mvnw clean install
docker compose -f infra/docker-compose.yml up --build
```

Current local runtime shape:
- backend runs from the prebuilt local image
- frontend runs in hot-reload mode

## Publish artifacts only
Use this when you want to publish runtime artifacts without mutating Azure.

Required environment:
- `MAPPO_IMAGE_PREFIX`
- Docker credentials that can push to that registry

Command:
```bash
./mvnw deploy \
  -Ddocker.image.prefix="$MAPPO_IMAGE_PREFIX" \
  -Dmappo.image.tag="<image-tag>"
```

## Publish and roll out to Azure
Use this when you want to publish artifacts and then apply the hosted runtime update.

Required environment:
- `MAPPO_IMAGE_PREFIX`
- `MAPPO_DOCKER_USERNAME`
- `MAPPO_DOCKER_PASSWORD`
- `PULUMI_CONFIG_PASSPHRASE`
- Azure CLI already authenticated

Command:
```bash
./mvnw -Pazure deploy \
  -Ddocker.image.prefix="$MAPPO_IMAGE_PREFIX" \
  -Dmappo.image.tag="<image-tag>" \
  -Dpulumi.stack="<stack>"
```

The `azure` profile performs:
- Pulumi apply
- runtime Container Apps prepare/apply
- Flyway migration job execution
- frontend/backend update
- marketplace forwarder deployment

## OpenAPI and frontend client contract
Backend OpenAPI export:
```bash
./mvnw -pl backend verify
```

Frontend type generation and verification:
```bash
./mvnw -pl frontend generate-sources
./mvnw -pl frontend compile
./mvnw -pl frontend test
```

Important files:
- OpenAPI: `/Users/cvonderheid/workspace/mappo/backend/target/openapi/openapi.json`
- generated frontend schema: `/Users/cvonderheid/workspace/mappo/frontend/src/lib/api/generated/schema.ts`

## Publisher release flow
The customer workload release catalog is managed in:
- `/Users/cvonderheid/workspace/mappo-managed-app`

Typical operator/demo sequence:
1. create/publish a new release in `mappo-managed-app`
2. push the repo changes
3. in MAPPO, open the project's Releases page
4. click `Check for new releases`
5. preview and start the deployment from Project -> Deployments
