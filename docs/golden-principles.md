# Golden Principles (Mechanical Checks)

These principles are enforced by scripts and gate targets, not convention only.

## Principles
1. Determinism first
- The same run inputs + release + target selection should produce stable stage ordering and event timelines.

2. Source-of-truth consistency
- Task files, plans, and docs must not contradict each other.

3. No demo leakage in production paths
- Demo-specific strings/shortcuts must not exist in runtime production paths.

4. Contract-before-change
- Runtime behavior changes require plan/docs updates and matching verification.

5. Legibility is required
- Operators must be able to explain what happened, why it failed, and what to retry.

## Local Commands
- `python3 scripts/workflow_discipline_check.py`
- `python3 scripts/docs_consistency_check.py`
- `python3 scripts/golden_principles_check.py`
- `python3 scripts/check_no_demo_leak.py`
- `./mvnw -pl backend test`
- `./mvnw -pl frontend compile`
