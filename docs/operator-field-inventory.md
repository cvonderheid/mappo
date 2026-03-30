# Operator Field Inventory (UX Cleanup)

Date: 2026-03-22
Owner: MAPPO UI/UX cleanup pass

## Classification Legend
- `required`: operator must provide this value for a valid setup.
- `optional`: operator may provide this when needed.
- `auto/internal`: value is fixed or derived by MAPPO; not operator input.
- `legacy/remove`: old or low-value input removed from operator UX.

## Project -> Config

### General
| Field | Classification | Decision |
|---|---|---|
| Project ID | required (read-only in edit) | Keep visible for identity and support. |
| Project display name | required | Keep editable. |

### Release Ingest
| Field | Classification | Decision |
|---|---|---|
| Release source | required | Keep; auto-locked to `Webhook / Pipeline Event` for `Pipeline Trigger` driver. |
| Linked release ingest endpoint | required (pipeline trigger) | Keep; selects provider/auth/routing from global endpoint config and is the only PAT source for Azure DevOps discovery/trigger. |
| Template URI field (blob source) | optional/advanced | Keep for blob template projects only. |
| Incoming release event type | auto/internal | Removed from operator input. |
| Release event provider (auto-derived) | auto/internal | Removed from operator input. |
| Source system (`azure_devops`) | auto/internal | Removed from operator input; fixed in payload builder. |
| Descriptor path (`pipelineInputs`) | auto/internal | Removed from operator input; fixed in payload builder. |
| Version field (`artifactVersion`) | auto/internal | Removed from operator input; fixed in payload builder. |

### Deployment Driver
| Field | Classification | Decision |
|---|---|---|
| Deployment driver | required | Keep; controls available execution model. |
| Pipeline system | required (single supported value) | Keep as constrained dropdown (`Azure DevOps`) for now. |
| Organization URL | required (pipeline trigger) | Keep with explicit URL guidance. |
| Project | required (pipeline trigger) | Keep. |
| Repository URL helper | optional | Keep; auto-parses org/project from URL. |
| Discover pipeline by name | optional | Keep helper workflow. |
| Pipeline ID | required (pipeline trigger) | Keep; can be auto-filled via discovery. |
| Branch | optional | Keep; default `main`. |
| Discover service connection by name | optional | Keep helper workflow. |
| Azure service connection | required (pipeline trigger) | Keep; can be selected from discovery. |
| PAT source mode selector | legacy/remove | Removed from operator input. |
| PAT literal value input | legacy/remove | Removed from operator input. |
| Provider API secret reference wiring | auto/internal | Fixed to backend-managed secret ref on the linked Azure DevOps release-ingest endpoint: `mappo.azure-devops.personal-access-token`. |

### Access & Identity
| Field | Classification | Decision |
|---|---|---|
| Access model | required | Keep. |
| Managing tenant ID | optional (delegated models) | Keep only for `Lighthouse Delegated Access`. |
| Managing principal client ID | optional (delegated models) | Keep only for `Lighthouse Delegated Access`. |
| Authentication mode key | legacy/remove | Removed from operator input; derived by MAPPO. |
| Requires Azure credential | legacy/remove | Removed from operator input; derived by MAPPO. |
| Requires target metadata | legacy/remove | Removed from operator input; derived by MAPPO. |
| Requires delegation | legacy/remove | Removed from operator input; derived by MAPPO. |
| Access-level service connection fallback | legacy/remove | Removed from operator input; pipeline driver owns service connection. |

### Target Contract
| Field | Classification | Decision |
|---|---|---|
| Required metadata keys | auto/internal (display) | Keep visible read-only contract. |
| Optional metadata keys | auto/internal (display) | Keep visible read-only contract. |

### Runtime Health
| Field | Classification | Decision |
|---|---|---|
| Runtime health provider | required | Keep. |
| Path | required | Keep. |
| Expected status | required | Keep with numeric validation. |
| Timeout ms | required | Keep with numeric validation. |

### Validation
| Field | Classification | Decision |
|---|---|---|
| Target for contract check | optional (required for target-contract scope) | Keep. |
| Test credentials / webhook / target contract actions | operational action | Keep. |

### Audit
| Field | Classification | Decision |
|---|---|---|
| Action filter | optional | Keep. |
| Refresh audit | operational action | Keep. |
| Before/After snapshots | operational evidence | Keep. |

## Admin -> Release Ingest Endpoint Form
| Field | Classification | Decision |
|---|---|---|
| Endpoint ID | required | Keep. |
| Name | required | Keep. |
| Provider | required | Keep. |
| Enabled | required | Keep. |
| Webhook secret reference | optional | Keep as plain secret ref. |
| Provider default secret reference | auto/internal (display hint) | Keep as helper text. |
| Secret source mode (`provider_default` / `literal`) | legacy/remove | Removed from operator input. |
| Literal webhook secret entry | legacy/remove | Removed from operator input. |
| Repo filter | optional | Keep (GitHub only). |
| Branch filter | optional | Keep. |
| Pipeline filter | optional | Keep (ADO only). |
| Manifest path | optional | Keep (GitHub only). |
| Source config JSON | optional/advanced | Keep for now; future candidate for typed fields by provider. |

## Targets / Onboarding (Current State)
| Field Group | Classification | Decision |
|---|---|---|
| Core identity (`projectId`, `tenantId`, `subscriptionId`, target/display) | required | Keep. |
| Driver-specific required fields (pipeline app/rg or managed-app resource IDs) | required | Keep. |
| Commercial tags (`group`, `region`, `environment`, `tier`) | optional/operational | Keep. |
| Execution config raw JSON | optional/advanced | Keep for now; candidate for progressive disclosure. |
| Ingest token | optional/security | Keep, but only used when ingest token validation is enabled. |

## Cleanup Rules Going Forward
1. If a field is `auto/internal`, it must not appear as editable operator input.
2. If a field is `legacy/remove`, remove UI immediately (no soft-deprecation banners).
3. If a field remains `optional/advanced`, attach a tooltip with exact source/meaning.
4. Prefer discovery/selectors over free-text IDs whenever an API exists to populate options.
