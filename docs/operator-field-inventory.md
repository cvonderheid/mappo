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

### Release Source
| Field | Classification | Decision |
|---|---|---|
| Release source | required | Keep; auto-locked to `Webhook / Pipeline Event` for `Pipeline Trigger` driver. |
| Linked release source | required (pipeline trigger) | Keep; selects webhook verification and routing from global release-source config. |
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
| Deployment connection | required (pipeline trigger) | Keep; selects the verified Azure DevOps auth/context from Admin → Deployment Connections. |
| Azure DevOps project | required (pipeline trigger) | Keep as discovered dropdown populated from the selected deployment connection. |
| Azure DevOps repo | required (pipeline trigger) | Keep as discovered dropdown populated from the selected Azure DevOps project. |
| Azure DevOps pipeline | required (pipeline trigger) | Keep as discovered dropdown populated from the selected Azure DevOps project; store the selected pipeline ID internally after selection. |
| Branch | optional | Keep; default `main`. |
| Azure service connection | required (pipeline trigger) | Keep; selected from Azure DevOps project-scoped discovery. |
| Organization/project freeform typing | legacy/remove | Removed from operator input when deployment connection + project discovery are available. |
| PAT source mode selector | legacy/remove | Removed from operator input. |
| PAT literal value input | legacy/remove | Removed from operator input. |
| Provider API secret reference wiring | auto/internal | Owned by the linked Admin → Deployment Connections record. |

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

## Admin -> Deployment Connections
| Field | Classification | Decision |
|---|---|---|
| Connection ID | required | Keep. |
| Name | required | Keep. |
| Deployment system | required | Keep. |
| Enabled | required | Keep. |
| Azure DevOps URL | required (Azure DevOps) | Keep; operator can paste any org/project/repository URL and MAPPO normalizes it. |
| Azure DevOps PAT source | required (Azure DevOps) | Keep, but only as backend-default or env-var source; verify it before save. |
| Literal PAT entry | legacy/remove | Removed from operator input. |
| Verified Azure DevOps root URL | auto/internal (display) | Keep visible read-only after verification. |
| Discovered Azure DevOps projects | auto/internal (display) | Keep visible read-only after verification. |

## Admin -> Release Sources
| Field | Classification | Decision |
|---|---|---|
| Release source ID | required | Keep. |
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
