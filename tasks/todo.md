# MAPPO Active Backlog

## Operator UX Cleanup
- Merge `Fleet` into `Targets`.
  - Keep one operator page named `Targets`.
  - Move useful Fleet filtering/table capabilities into Targets.
  - Remove `Fleet` from the left navigation.
- Fix event-driven release-source behavior on Project -> Releases.
  - `Check for new releases` should only apply to manifest-backed/pollable sources.
  - Pipeline/webhook-driven sources should explain that releases arrive from the external event.
  - Remove incorrect GitHub-specific errors for Azure DevOps event sources.
- Replace operator-facing `external_deployment_inputs` wording with `Pipeline release event` or equivalent.
- Audit all flow-card arrows and contract popovers.
  - Move the project-flow webhook/event contract so it appears between `Release Source` and `MAPPO`, not between `Outside MAPPO` and `Release Source`.
  - Only make arrows clickable when they show meaningful request, payload, probe, or contract details between the two connected steps.
  - Ensure arrow direction and placement match the real operator/system flow.
- Verify a brand-new project cannot show `Configuration complete` until release source, deployment behavior, and runtime health are meaningfully configured.
- Polish Admin -> Release Sources and Admin -> Deployment Connections flow cards.
  - Remove raw numeric IDs from operator-facing cards.
  - Use status colors only for real status, not decorative emphasis.
  - Keep card layout and action placement consistent with the rest of the app.
- Improve deployment failure messaging for Azure DevOps pipeline failures.
  - Point operators to the ADO run, service connection authorization, and Azure RBAC checks.
- Rename or restructure the current `Managed App` page so project registration history and global integration plumbing are not mixed.

## Data And Configuration Cleanup
- Confirm all operator-created records use system-generated IDs.
  - Operators should not type database identifiers for projects, secret references, release sources, deployment connections, or targets.
- Validate first-class selectable secret references with multiple accounts of the same provider type.
- Decide whether imported targets need short operator-friendly generated names when the import payload omits `targetId`.

## Handoff Documentation
- Update the Azure DevOps operator setup guide with the validated pipeline-to-pipeline walkthrough.
  - Release-readiness pipeline emits the release event to MAPPO.
  - MAPPO starts a separate deployment pipeline.
  - The deployment pipeline owns its Azure service connection.
  - MAPPO currently starts one pipeline run per selected target.
- Document common ADO deployment failures.
  - Old/deleted subscription on the service connection.
  - Service connection not authorized for the pipeline.
  - Service principal missing RBAC on the target subscription/resource group.
