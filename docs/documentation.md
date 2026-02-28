# MAPPO Documentation

## Local Development

### Prerequisites
- Python 3.11+
- GNU Make

### Process commands
```bash
make workflow-discipline-check
make docs-consistency-check
make golden-principles-check
make check-no-demo-leak
make phase1-gate-fast
make phase1-gate-full
```

### Managed app demo commands
```bash
make managed-demo-refresh SUBSCRIPTION_IDS="<provider-sub>,<customer-sub>"
# optional if you run discover/import manually:
make bootstrap-releases
make dev-backend-azure
make dev-frontend
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
