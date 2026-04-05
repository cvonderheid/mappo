# MAPPO Product And Workflow Lessons

These are the durable rules that still apply.

## Operator UX
- Separate **Release Sources** (inbound release notifications) from **Deployment Connections** (outbound authenticated access to deployment systems).
- Discover values whenever the external system can provide them. Do not make operators type IDs, URLs, or enum-shaped strings when MAPPO can verify and load them.
- If a field does not change runtime behavior, it is metadata, not configuration.
- Do not expose raw backend enum names or storage values in the UI.
- Action pages should own creation paths; history pages should not pretend to be the main place to start work.
- Validation should live next to the thing it validates.

## Product boundaries
- Publisher release manifests should describe release artifacts and versions, not MAPPO-internal project routing.
- Project-scoped infrastructure defaults should not appear as normal per-target editing unless an override is truly needed.
- Keep direct Azure rollout language explicit: MAPPO updates each selected target directly in Azure; Azure does not fan out a single global deployment for MAPPO.
- Only show resource/runtime icons for facts MAPPO explicitly stores. Do not infer final app kinds in operator visuals.

## Repo hygiene
- Keep docs small and current. Delete sprint plans, one-off audits, and obsolete runbooks once the living docs absorb anything still true.
- Keep backlog files short and current. Historical work logs do not belong in an active todo file.
