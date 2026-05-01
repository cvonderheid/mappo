import { DEFAULT_THEME_KEY, PROJECT_THEMES, type ProjectThemeKey } from "@/lib/project-theme";
import type {
  DiscoverProjectAdoBranchesRequest,
  DiscoverProjectAdoRepositoriesRequest,
  DiscoverProjectAdoPipelinesRequest,
  ProjectAdoBranch,
  ProjectAdoBranchDiscoveryResult,
  ProjectAdoPipeline,
  ProjectAdoPipelineDiscoveryResult,
  ProjectAdoRepository,
  ProjectAdoRepositoryDiscoveryResult,
  ProjectAccessStrategyConfigRequest,
  ProjectConfigurationPatchRequest,
  ProjectCreateRequest,
  ProjectDefinition,
  ProjectDeploymentDriverConfigRequest,
  ProjectReleaseArtifactSourceConfigRequest,
  ProjectRuntimeHealthProviderConfigRequest,
  ProjectValidationRequest,
  ProjectValidationResult,
  ProviderConnection,
  ProviderConnectionAdoProject,
  ReleaseIngestEndpoint,
  Target,
} from "@/lib/types";

export type ProjectSettingsPageProps = {
  project: ProjectDefinition | null;
  projects: ProjectDefinition[];
  selectedProjectId: string;
  targets: Target[];
  projectReleaseCount: number;
  onCreateProject: (request: ProjectCreateRequest) => Promise<ProjectDefinition>;
  onPatchProject: (projectId: string, request: ProjectConfigurationPatchRequest) => Promise<ProjectDefinition>;
  onDeleteProject: (projectId: string) => Promise<void>;
  onValidateProject: (projectId: string, request: ProjectValidationRequest) => Promise<ProjectValidationResult>;
  onDiscoverAdoBranches: (
    projectId: string,
    request: DiscoverProjectAdoBranchesRequest
  ) => Promise<ProjectAdoBranchDiscoveryResult>;
  onDiscoverAdoPipelines: (
    projectId: string,
    request: DiscoverProjectAdoPipelinesRequest
  ) => Promise<ProjectAdoPipelineDiscoveryResult>;
  onDiscoverAdoRepositories: (
    projectId: string,
    request: DiscoverProjectAdoRepositoriesRequest
  ) => Promise<ProjectAdoRepositoryDiscoveryResult>;
};

export type ProjectTab =
  | "general"
  | "release-ingest"
  | "deployment-driver"
  | "targets"
  | "runtime-health";

export type ValidationScope = "credentials" | "webhook" | "target_contract";
export type ReleaseSystem = "github" | "azure_devops";
export type DeploymentSystem = "azure" | "azure_devops";

export type ProjectDraft = {
  id: string;
  name: string;
  themeKey: ProjectThemeKey;
  releaseIngestEndpointId: string;
  providerConnectionId: string;
  accessStrategy: "simulator" | "azure_workload_rbac" | "lighthouse_delegated_access";
  deploymentDriver: "azure_deployment_stack" | "azure_template_spec" | "pipeline_trigger";
  releaseArtifactSource: "blob_arm_template" | "template_spec_resource" | "external_deployment_inputs";
  runtimeHealthProvider: "azure_container_app_http" | "http_endpoint";
  access: {
    managingTenantId: string;
    managingPrincipalClientId: string;
  };
  driver: {
    pipelineSystem: string;
    organization: string;
    project: string;
    repository: string;
    pipelineId: string;
    branch: string;
    supportsExternalExecutionHandle: boolean;
    supportsExternalLogs: boolean;
    supportsPreview: boolean;
    previewMode: string;
  };
  runtime: {
    path: string;
    expectedStatus: string;
    timeoutMs: string;
  };
};

export type DraftValidationIssue = {
  id: string;
  tab: ProjectTab;
  fieldId: string;
  message: string;
};

export const PROJECT_SECTIONS: { key: ProjectTab; label: string }[] = [
  { key: "general", label: "General" },
  { key: "release-ingest", label: "Release Source" },
  { key: "deployment-driver", label: "Deployment" },
  { key: "targets", label: "Targets" },
  { key: "runtime-health", label: "Runtime Health" },
];

export const PROJECT_THEME_OPTIONS = Object.values(PROJECT_THEMES);

export const RELEASE_SYSTEM_ORDER: ReleaseSystem[] = ["github", "azure_devops"];
export const DEPLOYMENT_SYSTEM_ORDER: DeploymentSystem[] = ["azure", "azure_devops"];

export const RELEASE_SYSTEM_LABELS: Record<ReleaseSystem, string> = {
  github: "GitHub",
  azure_devops: "Azure DevOps",
};

export const DEPLOYMENT_SYSTEM_LABELS: Record<DeploymentSystem, string> = {
  azure: "Azure",
  azure_devops: "Azure DevOps",
};

export const RELEASE_SOURCE_TYPE_LABELS: Record<ProjectDraft["releaseArtifactSource"], string> = {
  blob_arm_template: "Managed app release manifest",
  external_deployment_inputs: "Pipeline release event",
  template_spec_resource: "Template Spec release reference",
};

export const DEFAULT_TEMPLATE_URI_FIELD = "templateUri";
export const DEFAULT_TEMPLATE_SPEC_VERSION_FIELD = "templateSpecVersion";
export const DEFAULT_EXTERNAL_INPUT_SOURCE_SYSTEM = "azure_devops";
export const DEFAULT_EXTERNAL_INPUT_DESCRIPTOR_PATH = "pipelineInputs";
export const DEFAULT_EXTERNAL_INPUT_VERSION_FIELD = "artifactVersion";

export const DEPLOYMENT_DRIVER_LABELS: Record<ProjectDraft["deploymentDriver"], string> = {
  azure_deployment_stack: "MAPPO Azure API",
  azure_template_spec: "MAPPO Azure API (template spec)",
  pipeline_trigger: "Pipeline-driven rollout",
};

export const DEPLOYMENT_METHODS_BY_SYSTEM: Record<DeploymentSystem, ProjectDraft["deploymentDriver"][]> = {
  azure: ["azure_deployment_stack", "azure_template_spec"],
  azure_devops: ["pipeline_trigger"],
};

export const ACCESS_STRATEGY_LABELS: Record<ProjectDraft["accessStrategy"], string> = {
  azure_workload_rbac: "MAPPO deploys with its Azure permissions",
  lighthouse_delegated_access: "Deploy through Azure Lighthouse delegation",
  simulator: "Simulation only (no Azure changes)",
};

export const RUNTIME_HEALTH_LABELS: Record<ProjectDraft["runtimeHealthProvider"], string> = {
  azure_container_app_http: "HTTP endpoint",
  http_endpoint: "HTTP Endpoint",
};

export const DEPLOYMENT_DRIVER_HELP: Record<ProjectDraft["deploymentDriver"], string> = {
  azure_deployment_stack:
    "MAPPO uses its Azure credentials and Azure SDK/ARM calls to update each selected target.",
  azure_template_spec:
    "MAPPO uses its Azure credentials and Azure SDK/ARM calls to update each selected target from a template-spec-backed release.",
  pipeline_trigger:
    "MAPPO asks an external CI/CD system to deploy each selected target instead of calling Azure directly.",
};

export const ACCESS_STRATEGY_HELP: Record<ProjectDraft["accessStrategy"], string> = {
  azure_workload_rbac:
    "MAPPO uses its own Azure identity and RBAC permissions when it deploys this project.",
  lighthouse_delegated_access:
    "MAPPO reaches customer Azure subscriptions through Azure Lighthouse delegation before it deploys.",
  simulator:
    "MAPPO records and validates runs without making live Azure changes.",
};

export function deriveAzureDevOpsAccountUrl(value: string): string {
  const normalized = value.trim();
  if (normalized === "") {
    return "";
  }
  try {
    const parsed = new URL(normalized);
    const host = parsed.hostname.toLowerCase();
    if (host === "dev.azure.com") {
      const [organization] = parsed.pathname.split("/").filter(Boolean);
      return organization ? `${parsed.protocol}//${parsed.host}/${organization}` : "";
    }
    if (host.endsWith(".visualstudio.com")) {
      return `${parsed.protocol}//${parsed.host}`;
    }
    return "";
  } catch {
    return "";
  }
}

export function resolveProviderConnectionAccountUrl(
  connection: ProviderConnection | null | undefined
): string {
  const persisted = (connection?.organizationUrl ?? "").trim();
  if (persisted !== "") {
    return deriveAzureDevOpsAccountUrl(persisted) || persisted;
  }
  for (const project of connection?.discoveredProjects ?? []) {
    const derived = deriveAzureDevOpsAccountUrl(project.webUrl ?? "");
    if (derived !== "") {
      return derived;
    }
  }
  return "";
}

export function normalizeReleaseSystem(value: string | null | undefined): ReleaseSystem | null {
  const normalized = (value ?? "").trim().toLowerCase();
  if (normalized === "github") {
    return "github";
  }
  if (normalized === "azure_devops") {
    return "azure_devops";
  }
  return null;
}

export function deriveDeploymentSystem(driver: ProjectDraft["deploymentDriver"]): DeploymentSystem {
  return driver === "pipeline_trigger" ? "azure_devops" : "azure";
}

export function normalizeDeploymentSystem(value: string | null | undefined): DeploymentSystem | null {
  const normalized = (value ?? "").trim().toLowerCase().replaceAll("-", "_").replaceAll(" ", "_");
  if (normalized === "azure") {
    return "azure";
  }
  if (normalized === "azure_devops") {
    return "azure_devops";
  }
  return null;
}

export function normalizeDeploymentDriver(value: string | null | undefined): ProjectDraft["deploymentDriver"] | null {
  const normalized = (value ?? "").trim().toLowerCase().replaceAll("-", "_").replaceAll(" ", "_");
  if (normalized === "azure_deployment_stack" || normalized === "direct_azure_rollout") {
    return "azure_deployment_stack";
  }
  if (
    normalized === "azure_template_spec"
    || normalized === "direct_azure_rollout_(template_spec)"
    || normalized === "direct_azure_rollout_template_spec"
  ) {
    return "azure_template_spec";
  }
  if (normalized === "pipeline_trigger" || normalized === "pipeline_driven_rollout") {
    return "pipeline_trigger";
  }
  return null;
}

export function releaseArtifactSourceForDriver(
  driver: ProjectDraft["deploymentDriver"]
): ProjectDraft["releaseArtifactSource"] {
  switch (driver) {
    case "azure_deployment_stack":
      return "blob_arm_template";
    case "azure_template_spec":
      return "template_spec_resource";
    case "pipeline_trigger":
      return "external_deployment_inputs";
  }
}

export function defaultReleaseSystemForDriver(driver: ProjectDraft["deploymentDriver"]): ReleaseSystem {
  return driver === "pipeline_trigger" ? "azure_devops" : "github";
}

export function firstDriverForDeploymentSystem(
  system: DeploymentSystem
): ProjectDraft["deploymentDriver"] {
  return DEPLOYMENT_METHODS_BY_SYSTEM[system]?.[0] ?? "azure_deployment_stack";
}

export function applyDeploymentDriverSelection(
  current: ProjectDraft,
  nextDriver: ProjectDraft["deploymentDriver"]
): ProjectDraft {
  const pipelineDriver = nextDriver === "pipeline_trigger";
  return {
    ...current,
    deploymentDriver: nextDriver,
    releaseArtifactSource: releaseArtifactSourceForDriver(nextDriver),
    providerConnectionId: pipelineDriver ? current.providerConnectionId : "",
    driver: {
      ...current.driver,
      pipelineSystem: "azure_devops",
      organization: pipelineDriver ? current.driver.organization : "",
      project: pipelineDriver ? current.driver.project : "",
      repository: pipelineDriver ? current.driver.repository : "",
      pipelineId: pipelineDriver ? current.driver.pipelineId : "",
      branch: pipelineDriver ? current.driver.branch : "main",
    },
  };
}

export function asRecord(value: unknown): Record<string, unknown> {
  if (value !== null && typeof value === "object" && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }
  return {};
}

export function readStringField(object: Record<string, unknown>, key: string): string {
  const value = object[key];
  return typeof value === "string" ? value.trim() : "";
}

export function asString(value: unknown, fallback = ""): string {
  return typeof value === "string" ? value : fallback;
}

export function asBoolean(value: unknown, fallback = false): boolean {
  return typeof value === "boolean" ? value : fallback;
}

export function asNumberString(value: unknown, fallback = ""): string {
  if (typeof value === "number" && Number.isFinite(value)) {
    return String(value);
  }
  return fallback;
}

export function projectToDraft(project: ProjectDefinition | null): ProjectDraft {
  const accessConfig = asRecord(project?.accessStrategyConfig);
  const driverConfig = asRecord(project?.deploymentDriverConfig);
  const releaseConfig = asRecord(project?.releaseArtifactSourceConfig);
  const runtimeConfig = asRecord(project?.runtimeHealthProviderConfig);

  return {
    id: asString(project?.id),
    name: asString(project?.name),
    themeKey: ((project?.themeKey as ProjectThemeKey | undefined) ?? DEFAULT_THEME_KEY),
    releaseIngestEndpointId: asString(project?.releaseIngestEndpointId),
    providerConnectionId: asString(project?.providerConnectionId),
    accessStrategy: (project?.accessStrategy ?? "azure_workload_rbac") as ProjectDraft["accessStrategy"],
    deploymentDriver: (project?.deploymentDriver ?? "azure_deployment_stack") as ProjectDraft["deploymentDriver"],
    releaseArtifactSource: (project?.releaseArtifactSource ?? "blob_arm_template") as ProjectDraft["releaseArtifactSource"],
    runtimeHealthProvider: (project?.runtimeHealthProvider ?? "azure_container_app_http") as ProjectDraft["runtimeHealthProvider"],
    access: {
      managingTenantId: asString(accessConfig.managingTenantId),
      managingPrincipalClientId: asString(accessConfig.managingPrincipalClientId),
    },
    driver: {
      pipelineSystem: asString(driverConfig.pipelineSystem, "azure_devops"),
      organization: asString(driverConfig.organization),
      project: asString(driverConfig.project),
      repository: asString(driverConfig.repository),
      pipelineId: asString(driverConfig.pipelineId),
      branch: asString(driverConfig.branch, "main"),
      supportsExternalExecutionHandle: asBoolean(driverConfig.supportsExternalExecutionHandle, true),
      supportsExternalLogs: asBoolean(driverConfig.supportsExternalLogs, true),
      supportsPreview: asBoolean(driverConfig.supportsPreview, project?.deploymentDriver === "azure_deployment_stack"),
      previewMode: asString(driverConfig.previewMode, "arm_what_if"),
    },
    runtime: {
      path: asString(runtimeConfig.path, "/"),
      expectedStatus: asNumberString(runtimeConfig.expectedStatus, "200"),
      timeoutMs: asNumberString(runtimeConfig.timeoutMs, "5000"),
    },
  };
}

export function emptyProjectDraft(): ProjectDraft {
  return projectToDraft({
    id: "",
    name: "",
    themeKey: DEFAULT_THEME_KEY,
    accessStrategy: "azure_workload_rbac",
    deploymentDriver: "azure_deployment_stack",
    releaseArtifactSource: "blob_arm_template",
    runtimeHealthProvider: "azure_container_app_http",
  });
}

export function parseOptionalNumber(value: string): number | undefined {
  if (value.trim() === "") {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

export function buildPatchRequest(draft: ProjectDraft): ProjectConfigurationPatchRequest {
  const effectiveReleaseArtifactSource = releaseArtifactSourceForDriver(draft.deploymentDriver);

  let authModel = "rbac";
  let requiresAzureCredential = true;
  let requiresTargetExecutionMetadata = true;
  let requiresDelegation = false;
  if (draft.accessStrategy === "lighthouse_delegated_access") {
    authModel = "delegated_access";
    requiresDelegation = true;
  }
  if (draft.accessStrategy === "simulator") {
    authModel = "simulator";
    requiresAzureCredential = false;
    requiresTargetExecutionMetadata = false;
  }
  if (draft.deploymentDriver === "pipeline_trigger") {
    authModel = "pipeline_owned";
    requiresAzureCredential = false;
    requiresTargetExecutionMetadata = true;
  }

  const accessStrategyConfig: ProjectAccessStrategyConfigRequest = {
    authModel,
    requiresAzureCredential,
    requiresTargetExecutionMetadata,
    requiresDelegation,
  };
  if (draft.access.managingTenantId.trim() !== "") {
    accessStrategyConfig.managingTenantId = draft.access.managingTenantId.trim();
  }
  if (draft.access.managingPrincipalClientId.trim() !== "") {
    accessStrategyConfig.managingPrincipalClientId = draft.access.managingPrincipalClientId.trim();
  }

  const deploymentDriverConfig: ProjectDeploymentDriverConfigRequest = {
    supportsExternalExecutionHandle: draft.driver.supportsExternalExecutionHandle,
    supportsExternalLogs: draft.driver.supportsExternalLogs,
    supportsPreview: draft.driver.supportsPreview,
  };
  if (draft.driver.previewMode.trim() !== "") {
    deploymentDriverConfig.previewMode = draft.driver.previewMode.trim();
  }
  if (draft.deploymentDriver === "pipeline_trigger") {
    deploymentDriverConfig.pipelineSystem = draft.driver.pipelineSystem.trim() || "azure_devops";
    deploymentDriverConfig.organization = draft.driver.organization.trim() || undefined;
    deploymentDriverConfig.project = draft.driver.project.trim() || undefined;
    deploymentDriverConfig.repository = draft.driver.repository.trim() || undefined;
    deploymentDriverConfig.pipelineId = draft.driver.pipelineId.trim() || undefined;
    deploymentDriverConfig.branch = draft.driver.branch.trim() || undefined;
  }

  const releaseArtifactSourceConfig: ProjectReleaseArtifactSourceConfigRequest = {};
  if (effectiveReleaseArtifactSource === "blob_arm_template") {
    releaseArtifactSourceConfig.templateUriField = DEFAULT_TEMPLATE_URI_FIELD;
  }
  if (effectiveReleaseArtifactSource === "external_deployment_inputs") {
    releaseArtifactSourceConfig.sourceSystem = DEFAULT_EXTERNAL_INPUT_SOURCE_SYSTEM;
    releaseArtifactSourceConfig.descriptorPath = DEFAULT_EXTERNAL_INPUT_DESCRIPTOR_PATH;
    releaseArtifactSourceConfig.versionField = DEFAULT_EXTERNAL_INPUT_VERSION_FIELD;
  }
  if (effectiveReleaseArtifactSource === "template_spec_resource") {
    releaseArtifactSourceConfig.versionRefField = DEFAULT_TEMPLATE_SPEC_VERSION_FIELD;
  }

  const runtimeHealthProviderConfig: ProjectRuntimeHealthProviderConfigRequest = {
    path: draft.runtime.path.trim() || "/",
    expectedStatus: parseOptionalNumber(draft.runtime.expectedStatus),
    timeoutMs: parseOptionalNumber(draft.runtime.timeoutMs),
  };

  return {
    name: draft.name.trim(),
    themeKey: draft.themeKey,
    releaseIngestEndpointId: draft.releaseIngestEndpointId.trim() || undefined,
    providerConnectionId:
      draft.deploymentDriver === "pipeline_trigger"
        ? draft.providerConnectionId.trim() || undefined
        : undefined,
    accessStrategy: draft.accessStrategy,
    accessStrategyConfig,
    deploymentDriver: draft.deploymentDriver,
    deploymentDriverConfig,
    releaseArtifactSource: effectiveReleaseArtifactSource,
    releaseArtifactSourceConfig,
    runtimeHealthProvider: draft.runtimeHealthProvider,
    runtimeHealthProviderConfig,
  };
}

export function buildCreateRequest(draft: ProjectDraft): ProjectCreateRequest {
  const patchPayload = buildPatchRequest(draft);
  return {
    name: draft.name.trim(),
    themeKey: patchPayload.themeKey,
    releaseIngestEndpointId: patchPayload.releaseIngestEndpointId,
    providerConnectionId: patchPayload.providerConnectionId,
    accessStrategy: patchPayload.accessStrategy ?? "azure_workload_rbac",
    accessStrategyConfig: patchPayload.accessStrategyConfig,
    deploymentDriver: patchPayload.deploymentDriver ?? "azure_deployment_stack",
    deploymentDriverConfig: patchPayload.deploymentDriverConfig,
    releaseArtifactSource: patchPayload.releaseArtifactSource ?? "blob_arm_template",
    releaseArtifactSourceConfig: patchPayload.releaseArtifactSourceConfig,
    runtimeHealthProvider: patchPayload.runtimeHealthProvider ?? "azure_container_app_http",
    runtimeHealthProviderConfig: patchPayload.runtimeHealthProviderConfig,
  };
}

export function normalizeDiscoveryError(message: string, providerLabel: string): string {
  const normalizedMessage = message.toLowerCase();
  if (normalizedMessage.includes("pat could not be resolved")) {
    return `${providerLabel} access is not configured correctly for the selected deployment connection. Open Admin → Deployment Connections, edit that connection, and verify the Azure DevOps API credential there.`;
  }
  if (
    normalizedMessage.includes("azure devops url is required")
    || normalizedMessage.includes("account url is required")
    || normalizedMessage.includes("project or repo url")
    || normalizedMessage.includes("verified azure devops url")
  ) {
    return `${providerLabel} projects are not available yet because the selected deployment connection has no verified Azure DevOps account scope. Open Admin → Deployment Connections, paste any Azure DevOps project or repository URL for that account, and verify it first.`;
  }
  return message;
}

export type ProjectSettingsDiscoveryState = {
  releaseIngestEndpoints: ReleaseIngestEndpoint[];
  providerConnections: ProviderConnection[];
  isLoadingReleaseIngestEndpoints: boolean;
  isLoadingProviderConnections: boolean;
  selectedReleaseSystem: ReleaseSystem;
  setSelectedReleaseSystem: (value: ReleaseSystem) => void;
  selectedProviderConnection: ProviderConnection | null;
  selectedProviderConnectionRequiresVerification: boolean;
  selectedProviderConnectionDiscoveryUrl: string;
  verifiedPipelineProviderConnections: ProviderConnection[];
  providerConnectionOptions: ProviderConnection[];
  releaseIngestEndpointOptions: ReleaseIngestEndpoint[];
  selectedReleaseIngestEndpoint: ReleaseIngestEndpoint | null;
  availableReleaseSystems: ReleaseSystem[];
  effectiveReleaseSystem: ReleaseSystem;
  releaseSourceTypeLabel: string;
  selectedDeploymentSystem: DeploymentSystem;
  selectedDiscoveredAdoProjectId: string;
  selectedDiscoveredAdoProject: ProviderConnectionAdoProject | null;
  cachedProviderConnectionProjects: ProviderConnectionAdoProject[];
  resolvedAdoOrganization: string;
  resolvedAdoProject: string;
  canSelectAzureDevOpsProject: boolean;
  hasSelectedAzureDevOpsProject: boolean;
  canDiscoverAzureDevOpsProjectResources: boolean;
  discoveredBranches: ProjectAdoBranch[];
  discoveredRepositories: ProjectAdoRepository[];
  discoveredPipelines: ProjectAdoPipeline[];
  isDiscoveringBranches: boolean;
  isDiscoveringRepositories: boolean;
  isDiscoveringPipelines: boolean;
  branchDiscoveryError: string;
  repositoryDiscoveryError: string;
  pipelineDiscoveryError: string;
  selectedDiscoveredPipelineId: string;
  selectedDiscoveredRepositoryId: string;
  selectedDiscoveredRepository: ProjectAdoRepository | null;
  selectedDiscoveredBranchRef: string;
  hasSavedPipelineOutsideDiscovery: boolean;
  hasSingleCachedAdoProject: boolean;
  hasSingleDiscoveredBranch: boolean;
  hasSingleDiscoveredRepository: boolean;
  hasSingleDiscoveredPipeline: boolean;
  isVerifiedAzureDevOpsConnection: (connection: ProviderConnection | null | undefined) => boolean;
  refreshReleaseIngestEndpointOptions: (silent?: boolean) => Promise<void>;
  refreshProviderConnectionOptions: (silent?: boolean) => Promise<void>;
  discoverAdoBranches: (options?: { silent?: boolean }) => Promise<void>;
  discoverAdoPipelines: (options?: { silent?: boolean }) => Promise<void>;
  discoverAdoRepositories: (options?: { silent?: boolean }) => Promise<void>;
};
