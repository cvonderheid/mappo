# Regression Intake

## Goal
Turn failures into deterministic, owner-routed regression work within one business day.

## Workflow
1. Capture failing behavior and stable reproduction steps.
2. Add failing deterministic test first (or in same PR with clear failing assertion history).
3. Implement fix and verify through relevant phase gate.
4. Record recurring pattern in `tasks/lessons.md` when systemic.

## SLA Targets
- Triage owner assignment: < 2 hours.
- Regression test merged: < 1 business day.
