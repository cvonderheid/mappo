# MAPPO Active Backlog

## Next demo / operator UX
- Finish provider-first project configuration flow:
  - Release Source should choose provider first, then only show provider-relevant options.
  - Deployment Driver should choose deployment provider/system first, then show only provider-relevant deployment methods.
- Remove remaining Azure DevOps-only controls from the direct Azure rollout path.
- Make `Check for new releases` a direct action instead of opening a configuration drawer.
- Add project deletion from the UI.
- Add a manual `Check health` action on Fleet.
- Add a visual project flow diagram using factual provider/deployment icons only.
- Rename or restructure the current `Managed App` page so project registration history and global integration plumbing are not mixed.

## Data and configuration cleanup
- Move project-scoped defaults out of normal Target editing; keep per-target overrides advanced-only.
- Remove publisher-owned `project_id` from release manifests permanently and keep MAPPO-side routing internal.
- Support first-class selectable secret references for multiple accounts of the same provider type.

## Pre-production work
- Remove non-essential seeded data from Flyway migrations.
- Cut a clean baseline migration before a production release.
- Reevaluate string IDs versus numeric surrogate keys.
- Continue backend package cleanup by bounded context.
