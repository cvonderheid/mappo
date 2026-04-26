# MAPPO Developer Guide

## Repo layout
- `./backend`: Spring Boot API, orchestration, persistence, Azure integrations
- `./frontend`: React UI
- `./tooling`: repo checks and workflow helpers
- `./infra/pulumi`: control-plane IaC
- `./infra/demo/targets-azure-delivery`: direct Azure delivery demo targets IaC
- `./infra/demo/targets-pipeline-delivery`: pipeline delivery demo targets IaC
- `./delivery`: Maven-driven rollout orchestration for the hosted runtime

## Development rules
- Backend OpenAPI is authoritative.
- Frontend generated types must come from that OpenAPI artifact.
- Prefer deterministic tests for regressions.
- Prefer operator-facing wording over backend nouns in the UI.
- Remove stale docs and stale UI paths instead of preserving dead compatibility in a pre-production system.

## Testing workflow
Typical checks:
```bash
./mvnw test
./mvnw verify
npm --prefix frontend run typecheck
npm --prefix frontend run test -- --run
```

Targeted checks:
```bash
./mvnw -pl backend -am test -DskipITs
./mvnw -pl tooling exec:java@docs-consistency-check
```

## OpenAPI and frontend types
- OpenAPI export: `./backend/target/openapi/openapi.json`
- Frontend generated schema: `./frontend/src/lib/api/generated/schema.ts`
- Frontend client generation uses `openapi-typescript`

## Debugging discipline
1. reproduce the failure clearly
2. identify whether the issue is config, runtime, or operator wording
3. add or update deterministic tests where practical
4. update living docs only if the behavior or workflow actually changed

## Documentation rule
The docs directory should only contain current living documentation. Sprint plans, one-off audits, and obsolete runbooks should be deleted or folded into the current docs.
