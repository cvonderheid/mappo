# Extending MAPPO

## Add a New Deployment Stage
1. Extend stage enum/state model in backend domain schemas.
2. Update transition validation rules.
3. Update per-target timeline persistence and API response models.
4. Add regression tests for valid/invalid transitions.

## Add a New Rollout Strategy
1. Add strategy definition and validation in run-creation contracts.
2. Implement deterministic target ordering and wave expansion logic.
3. Add stop-policy interaction tests (percent and absolute thresholds).
4. Add run summary and UI rendering coverage.

## Add a New Verification Check Type
1. Define check contract and timeout/retry rules.
2. Implement executor adapter with structured error mapping.
3. Persist check outcomes in per-target stage details.
4. Add operator-facing messages and troubleshooting links.

## Add a New Execution Adapter
1. Implement the execution adapter in the Java orchestration/service layer under `/Users/cvonderheid/workspace/mappo/backend/src/main/java/com/mappo/controlplane`.
2. Keep emitted stage events deterministic (`started`/`completed`) with explicit correlation IDs.
3. Wire adapter selection through the backend settings/config path (do not fork orchestration logic).
4. Add adapter behavior tests and run backend verify plus frontend contract checks.

## Quality Gate
Run before merging non-trivial changes:
```bash
./mvnw -pl backend verify
./mvnw -pl frontend generate-sources
./mvnw -pl frontend compile
./mvnw -pl frontend test
./mvnw -pl frontend package
```
