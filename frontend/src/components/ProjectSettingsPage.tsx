import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { toast } from "sonner";

import FieldHelpTooltip from "@/components/FieldHelpTooltip";
import { Badge } from "@/components/ui/badge";
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Drawer,
  DrawerClose,
  DrawerContent,
  DrawerDescription,
  DrawerFooter,
  DrawerHeader,
  DrawerTitle,
} from "@/components/ui/drawer";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import {
  listProviderConnections,
  listReleaseIngestEndpoints,
} from "@/lib/api";
import type {
  DiscoverProjectAdoRepositoriesRequest,
  DiscoverProjectAdoServiceConnectionsRequest,
  DiscoverProjectAdoPipelinesRequest,
  ProjectAdoPipeline,
  ProjectAdoPipelineDiscoveryResult,
  ProjectAdoRepository,
  ProjectAdoRepositoryDiscoveryResult,
  ProjectAdoServiceConnection,
  ProjectAdoServiceConnectionDiscoveryResult,
  ProjectConfigurationPatchRequest,
  ProjectCreateRequest,
  ProjectDefinition,
  ProviderConnection,
  ReleaseIngestEndpoint,
  ProjectValidationRequest,
  ProjectValidationResult,
  Target,
} from "@/lib/types";

type ProjectSettingsPageProps = {
  project: ProjectDefinition | null;
  projects: ProjectDefinition[];
  selectedProjectId: string;
  targets: Target[];
  projectReleaseCount: number;
  onCreateProject: (request: ProjectCreateRequest) => Promise<ProjectDefinition>;
  onPatchProject: (projectId: string, request: ProjectConfigurationPatchRequest) => Promise<ProjectDefinition>;
  onValidateProject: (projectId: string, request: ProjectValidationRequest) => Promise<ProjectValidationResult>;
  onDiscoverAdoPipelines: (
    projectId: string,
    request: DiscoverProjectAdoPipelinesRequest
  ) => Promise<ProjectAdoPipelineDiscoveryResult>;
  onDiscoverAdoRepositories: (
    projectId: string,
    request: DiscoverProjectAdoRepositoriesRequest
  ) => Promise<ProjectAdoRepositoryDiscoveryResult>;
  onDiscoverAdoServiceConnections: (
    projectId: string,
    request: DiscoverProjectAdoServiceConnectionsRequest
  ) => Promise<ProjectAdoServiceConnectionDiscoveryResult>;
};

type ProjectTab =
  | "general"
  | "release-ingest"
  | "deployment-driver"
  | "runtime-health";

type ValidationScope = "credentials" | "webhook" | "target_contract";
type ReleaseSystem = "github" | "azure_devops";
type DeploymentSystem = "azure" | "azure_devops";

type ProjectDraft = {
  id: string;
  name: string;
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
    azureServiceConnectionName: string;
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

type DraftValidationIssue = {
  id: string;
  tab: ProjectTab;
  fieldId: string;
  message: string;
};

const PROJECT_TABS: { key: ProjectTab; label: string }[] = [
  { key: "general", label: "General" },
  { key: "release-ingest", label: "Release Source" },
  { key: "deployment-driver", label: "Deployment Driver" },
  { key: "runtime-health", label: "Runtime Health" },
];

const RELEASE_SYSTEM_ORDER: ReleaseSystem[] = ["github", "azure_devops"];
const DEPLOYMENT_SYSTEM_ORDER: DeploymentSystem[] = ["azure", "azure_devops"];

const RELEASE_SYSTEM_LABELS: Record<ReleaseSystem, string> = {
  github: "GitHub",
  azure_devops: "Azure DevOps",
};

const DEPLOYMENT_SYSTEM_LABELS: Record<DeploymentSystem, string> = {
  azure: "Azure",
  azure_devops: "Azure DevOps",
};

const RELEASE_SOURCE_OPTIONS: Array<{
  value: ProjectDraft["releaseArtifactSource"];
  label: string;
}> = [
  { value: "blob_arm_template", label: "Blob-hosted ARM Template" },
  { value: "external_deployment_inputs", label: "Release Event" },
];

const RELEASE_SOURCE_TYPE_LABELS: Record<ProjectDraft["releaseArtifactSource"], string> = {
  blob_arm_template: "Managed app release manifest",
  external_deployment_inputs: "Webhook / Pipeline Event",
  template_spec_resource: "Template Spec release reference",
};

const DEFAULT_TEMPLATE_URI_FIELD = "templateUri";
const DEFAULT_TEMPLATE_SPEC_VERSION_FIELD = "templateSpecVersion";
const DEFAULT_EXTERNAL_INPUT_SOURCE_SYSTEM = "azure_devops";
const DEFAULT_EXTERNAL_INPUT_DESCRIPTOR_PATH = "pipelineInputs";
const DEFAULT_EXTERNAL_INPUT_VERSION_FIELD = "artifactVersion";

function deriveAzureDevOpsAccountUrl(value: string): string {
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

function resolveProviderConnectionAccountUrl(
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

function normalizeReleaseSystem(value: string | null | undefined): ReleaseSystem | null {
  const normalized = (value ?? "").trim().toLowerCase();
  if (normalized === "github") {
    return "github";
  }
  if (normalized === "azure_devops") {
    return "azure_devops";
  }
  return null;
}

function deriveDeploymentSystem(driver: ProjectDraft["deploymentDriver"]): DeploymentSystem {
  return driver === "pipeline_trigger" ? "azure_devops" : "azure";
}

function driverForDeploymentSystem(system: DeploymentSystem): ProjectDraft["deploymentDriver"] {
  return system === "azure_devops" ? "pipeline_trigger" : "azure_deployment_stack";
}

const DEPLOYMENT_DRIVER_LABELS: Record<ProjectDraft["deploymentDriver"], string> = {
  azure_deployment_stack: "Direct Azure rollout",
  azure_template_spec: "Direct Azure rollout (template spec)",
  pipeline_trigger: "Pipeline-driven rollout",
};

const ACCESS_STRATEGY_LABELS: Record<ProjectDraft["accessStrategy"], string> = {
  azure_workload_rbac: "MAPPO deploys with its Azure permissions",
  lighthouse_delegated_access: "Deploy through Azure Lighthouse delegation",
  simulator: "Simulation only (no Azure changes)",
};

const RUNTIME_HEALTH_LABELS: Record<ProjectDraft["runtimeHealthProvider"], string> = {
  azure_container_app_http: "HTTP endpoint",
  http_endpoint: "HTTP Endpoint",
};

const DEPLOYMENT_DRIVER_HELP: Record<ProjectDraft["deploymentDriver"], string> = {
  azure_deployment_stack:
    "MAPPO updates each selected target directly in Azure using that target's Deployment Stack.",
  azure_template_spec:
    "MAPPO updates each selected target directly in Azure using that target's template-spec-backed release.",
  pipeline_trigger:
    "MAPPO asks an external CI/CD system to deploy each selected target instead of calling Azure directly.",
};

const ACCESS_STRATEGY_HELP: Record<ProjectDraft["accessStrategy"], string> = {
  azure_workload_rbac:
    "MAPPO uses its own Azure identity and RBAC permissions when it deploys this project.",
  lighthouse_delegated_access:
    "MAPPO reaches customer Azure subscriptions through Azure Lighthouse delegation before it deploys.",
  simulator:
    "MAPPO records and validates runs without making live Azure changes.",
};

function asRecord(value: unknown): Record<string, unknown> {
  if (value !== null && typeof value === "object" && !Array.isArray(value)) {
    return value as Record<string, unknown>;
  }
  return {};
}

function readStringField(object: Record<string, unknown>, key: string): string {
  const value = object[key];
  return typeof value === "string" ? value.trim() : "";
}

function asString(value: unknown, fallback = ""): string {
  return typeof value === "string" ? value : fallback;
}

function asBoolean(value: unknown, fallback = false): boolean {
  return typeof value === "boolean" ? value : fallback;
}

function asNumberString(value: unknown, fallback = ""): string {
  if (typeof value === "number" && Number.isFinite(value)) {
    return String(value);
  }
  return fallback;
}

function projectToDraft(project: ProjectDefinition | null): ProjectDraft {
  const accessConfig = asRecord(project?.accessStrategyConfig);
  const driverConfig = asRecord(project?.deploymentDriverConfig);
  const releaseConfig = asRecord(project?.releaseArtifactSourceConfig);
  const runtimeConfig = asRecord(project?.runtimeHealthProviderConfig);

  return {
    id: asString(project?.id),
    name: asString(project?.name),
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
      azureServiceConnectionName: asString(driverConfig.azureServiceConnectionName),
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

function parseOptionalNumber(value: string): number | undefined {
  if (value.trim() === "") {
    return undefined;
  }
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

function buildPatchRequest(draft: ProjectDraft): ProjectConfigurationPatchRequest {
  const effectiveReleaseArtifactSource: ProjectDraft["releaseArtifactSource"] =
    draft.deploymentDriver === "pipeline_trigger"
      ? "external_deployment_inputs"
      : draft.releaseArtifactSource;

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

  const accessStrategyConfig: Record<string, unknown> = {
    authModel,
    requiresAzureCredential,
    requiresTargetExecutionMetadata,
    requiresDelegation,
  };
  if (draft.deploymentDriver === "pipeline_trigger" && draft.driver.azureServiceConnectionName.trim() !== "") {
    accessStrategyConfig.azureServiceConnectionName = draft.driver.azureServiceConnectionName.trim();
  }
  if (draft.access.managingTenantId.trim() !== "") {
    accessStrategyConfig.managingTenantId = draft.access.managingTenantId.trim();
  }
  if (draft.access.managingPrincipalClientId.trim() !== "") {
    accessStrategyConfig.managingPrincipalClientId = draft.access.managingPrincipalClientId.trim();
  }

  const deploymentDriverConfig: Record<string, unknown> = {
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
    deploymentDriverConfig.azureServiceConnectionName =
      draft.driver.azureServiceConnectionName.trim() || undefined;
  }

  const releaseArtifactSourceConfig: Record<string, unknown> = {};
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

  const runtimeHealthProviderConfig: Record<string, unknown> = {
    path: draft.runtime.path.trim() || "/",
    expectedStatus: parseOptionalNumber(draft.runtime.expectedStatus),
    timeoutMs: parseOptionalNumber(draft.runtime.timeoutMs),
  };

  return {
    name: draft.name.trim(),
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

function buildCreateRequest(draft: ProjectDraft): ProjectCreateRequest {
  const patchPayload = buildPatchRequest(draft);
  return {
    id: draft.id.trim(),
    name: draft.name.trim(),
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

export default function ProjectSettingsPage({
  project,
  projects,
  selectedProjectId,
  targets,
  projectReleaseCount,
  onCreateProject,
  onPatchProject,
  onValidateProject,
  onDiscoverAdoPipelines,
  onDiscoverAdoRepositories,
  onDiscoverAdoServiceConnections,
}: ProjectSettingsPageProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<ProjectTab>("general");
  const [draft, setDraft] = useState<ProjectDraft>(() => projectToDraft(project));
  const [isSaving, setIsSaving] = useState(false);
  const [isValidating, setIsValidating] = useState(false);
  const [createDrawerOpen, setCreateDrawerOpen] = useState(false);
  const [createSubmitting, setCreateSubmitting] = useState(false);
  const [isDiscoveringRepositories, setIsDiscoveringRepositories] = useState(false);
  const [isDiscoveringPipelines, setIsDiscoveringPipelines] = useState(false);
  const [isDiscoveringServiceConnections, setIsDiscoveringServiceConnections] = useState(false);
  const [repositoryDiscoveryError, setRepositoryDiscoveryError] = useState("");
  const [pipelineDiscoveryError, setPipelineDiscoveryError] = useState("");
  const [serviceConnectionDiscoveryError, setServiceConnectionDiscoveryError] = useState("");
  const [discoveredRepositories, setDiscoveredRepositories] = useState<ProjectAdoRepository[]>([]);
  const [discoveredPipelines, setDiscoveredPipelines] = useState<ProjectAdoPipeline[]>([]);
  const [discoveredServiceConnections, setDiscoveredServiceConnections] = useState<ProjectAdoServiceConnection[]>([]);
  const [releaseIngestEndpoints, setReleaseIngestEndpoints] = useState<ReleaseIngestEndpoint[]>([]);
  const [providerConnections, setProviderConnections] = useState<ProviderConnection[]>([]);
  const [isLoadingReleaseIngestEndpoints, setIsLoadingReleaseIngestEndpoints] = useState(false);
  const [isLoadingProviderConnections, setIsLoadingProviderConnections] = useState(false);
  const [selectedReleaseSystem, setSelectedReleaseSystem] = useState<ReleaseSystem>("github");
  const repositoryDiscoveryKeyRef = useRef("");
  const pipelineDiscoveryKeyRef = useRef("");
  const serviceConnectionDiscoveryKeyRef = useRef("");
  const [createDraft, setCreateDraft] = useState<ProjectDraft>(() =>
    projectToDraft({
      id: "",
      name: "",
      accessStrategy: "azure_workload_rbac",
      deploymentDriver: "azure_deployment_stack",
      releaseArtifactSource: "blob_arm_template",
      runtimeHealthProvider: "azure_container_app_http",
    })
  );

  useEffect(() => {
    const nextDraft = projectToDraft(project);
    setDraft(nextDraft);
    setSelectedReleaseSystem(
      nextDraft.deploymentDriver === "pipeline_trigger" ? "azure_devops" : "github"
    );
    setDiscoveredRepositories([]);
    setDiscoveredPipelines([]);
    setDiscoveredServiceConnections([]);
    setRepositoryDiscoveryError("");
    setPipelineDiscoveryError("");
    setServiceConnectionDiscoveryError("");
    repositoryDiscoveryKeyRef.current = "";
    pipelineDiscoveryKeyRef.current = "";
    serviceConnectionDiscoveryKeyRef.current = "";
  }, [project, selectedProjectId]);

  async function refreshReleaseIngestEndpointOptions(silent = false): Promise<void> {
    if (!silent) {
      setIsLoadingReleaseIngestEndpoints(true);
    }
    try {
      const endpoints = await listReleaseIngestEndpoints();
      setReleaseIngestEndpoints(endpoints ?? []);
    } catch (error) {
      toast.error((error as Error).message);
    } finally {
      setIsLoadingReleaseIngestEndpoints(false);
    }
  }

  async function refreshProviderConnectionOptions(silent = false): Promise<void> {
    if (!silent) {
      setIsLoadingProviderConnections(true);
    }
    try {
      const connections = await listProviderConnections();
      setProviderConnections(connections ?? []);
    } catch (error) {
      toast.error((error as Error).message);
    } finally {
      setIsLoadingProviderConnections(false);
    }
  }

  useEffect(() => {
    void refreshReleaseIngestEndpointOptions(true);
    void refreshProviderConnectionOptions(true);
  }, []);

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    if (params.get("new") !== "1") {
      return;
    }
    setCreateDrawerOpen(true);
    params.delete("new");
    const nextSearch = params.toString();
    void navigate(
      {
        pathname: location.pathname,
        search: nextSearch ? `?${nextSearch}` : "",
      },
      { replace: true }
    );
  }, [location.pathname, location.search, navigate]);

  useEffect(() => {
    if (draft.deploymentDriver !== "pipeline_trigger") {
      return;
    }
    if (draft.releaseArtifactSource === "external_deployment_inputs") {
      return;
    }
    setDraft((current) => ({
      ...current,
      releaseArtifactSource: "external_deployment_inputs",
    }));
  }, [
    draft.deploymentDriver,
    draft.releaseArtifactSource,
  ]);

  useEffect(() => {
    if (draft.deploymentDriver !== "pipeline_trigger") {
      return;
    }
    if (draft.driver.pipelineSystem === "azure_devops") {
      return;
    }
    setDraft((current) => ({
      ...current,
      driver: { ...current.driver, pipelineSystem: "azure_devops" },
    }));
  }, [draft.deploymentDriver, draft.driver.pipelineSystem]);

  const targetCount = targets.length;
  const sortedReleaseIngestEndpoints = useMemo(
    () =>
      [...releaseIngestEndpoints].sort((left, right) => {
        const leftName = `${left.name ?? left.id ?? ""}`.toLowerCase();
        const rightName = `${right.name ?? right.id ?? ""}`.toLowerCase();
        return leftName.localeCompare(rightName);
      }),
    [releaseIngestEndpoints]
  );
  const sortedProviderConnections = useMemo(
    () =>
      [...providerConnections].sort((left, right) => {
        const leftName = `${left.name ?? left.id ?? ""}`.toLowerCase();
        const rightName = `${right.name ?? right.id ?? ""}`.toLowerCase();
        return leftName.localeCompare(rightName);
      }),
    [providerConnections]
  );
  const selectedProviderConnection = useMemo(() => {
    const connectionId = draft.providerConnectionId.trim();
    if (connectionId === "") {
      return null;
    }
    return (
      providerConnections.find((connection) => (connection.id ?? "").trim() === connectionId) ?? null
    );
  }, [providerConnections, draft.providerConnectionId]);
  const isVerifiedAzureDevOpsConnection = useCallback(
    (connection: ProviderConnection | null | undefined) =>
      (connection?.provider ?? "").toLowerCase() === "azure_devops" &&
      (connection?.enabled ?? true) &&
      resolveProviderConnectionAccountUrl(connection) !== "" &&
      (connection?.discoveredProjects?.length ?? 0) > 0,
    []
  );
  const pipelineProviderConnections = useMemo(
    () =>
      sortedProviderConnections.filter(
        (connection) =>
          (connection.provider ?? "").toLowerCase() === "azure_devops" &&
          (connection.enabled ?? true)
      ),
    [sortedProviderConnections]
  );
  const verifiedPipelineProviderConnections = useMemo(
    () =>
      pipelineProviderConnections.filter((connection) =>
        isVerifiedAzureDevOpsConnection(connection)
      ),
    [isVerifiedAzureDevOpsConnection, pipelineProviderConnections]
  );
  const selectedProviderConnectionIsAzureDevOps =
    (selectedProviderConnection?.provider ?? "").toLowerCase() === "azure_devops";
  const selectedProviderConnectionDiscoveryUrl = resolveProviderConnectionAccountUrl(
    selectedProviderConnection
  );
  const cachedProviderConnectionProjects = useMemo(
    () =>
      [...(selectedProviderConnection?.discoveredProjects ?? [])].sort((left, right) =>
        `${left.name ?? ""}`.localeCompare(`${right.name ?? ""}`, undefined, { sensitivity: "base" })
      ),
    [selectedProviderConnection]
  );
  const selectedProviderConnectionRequiresVerification =
    draft.deploymentDriver === "pipeline_trigger" &&
    draft.providerConnectionId.trim() !== "" &&
    selectedProviderConnectionIsAzureDevOps &&
    !isVerifiedAzureDevOpsConnection(selectedProviderConnection);
  const isPipelineDriver = draft.deploymentDriver === "pipeline_trigger";
  const selectedDeploymentSystem = deriveDeploymentSystem(draft.deploymentDriver);
  const selectedDiscoveredAdoProjectId = useMemo(() => {
    const currentValue = draft.driver.project.trim();
    if (currentValue === "") {
      return "__none";
    }
    const matching = cachedProviderConnectionProjects.find((projectOption) => projectOption.name === currentValue);
    return matching ? matching.id : "__none";
  }, [cachedProviderConnectionProjects, draft.driver.project]);
  const selectedDiscoveredAdoProject = useMemo(() => {
    if (selectedDiscoveredAdoProjectId === "__none") {
      return null;
    }
    return cachedProviderConnectionProjects.find((projectOption) => projectOption.id === selectedDiscoveredAdoProjectId) ?? null;
  }, [cachedProviderConnectionProjects, selectedDiscoveredAdoProjectId]);
  const resolvedAdoOrganization = draft.driver.organization.trim();
  const resolvedAdoProject = draft.driver.project.trim();
  const canSelectAzureDevOpsProject =
    draft.deploymentDriver === "pipeline_trigger" &&
    draft.providerConnectionId.trim() !== "" &&
    !selectedProviderConnectionRequiresVerification &&
    cachedProviderConnectionProjects.length > 0;
  const hasSelectedAzureDevOpsProject = resolvedAdoOrganization !== "" && resolvedAdoProject !== "";
  const canDiscoverAzureDevOpsProjectResources =
    canSelectAzureDevOpsProject &&
    hasSelectedAzureDevOpsProject;
  const providerConnectionOptions = useMemo(() => {
    if (draft.deploymentDriver !== "pipeline_trigger") {
      return sortedProviderConnections;
    }
    const base = [...pipelineProviderConnections];
    if (
      selectedProviderConnection &&
      !isVerifiedAzureDevOpsConnection(selectedProviderConnection) &&
      !base.some((connection) => (connection.id ?? "").trim() === (selectedProviderConnection.id ?? "").trim())
    ) {
      base.unshift(selectedProviderConnection);
    }
    return base;
  }, [
    draft.deploymentDriver,
    sortedProviderConnections,
    pipelineProviderConnections,
    selectedProviderConnection,
    isVerifiedAzureDevOpsConnection,
  ]);
  const selectedReleaseIngestEndpoint = useMemo(() => {
    const endpointId = draft.releaseIngestEndpointId.trim();
    if (endpointId === "") {
      return null;
    }
    return (
      releaseIngestEndpoints.find((endpoint) => (endpoint.id ?? "").trim() === endpointId) ?? null
    );
  }, [releaseIngestEndpoints, draft.releaseIngestEndpointId]);
  const pipelineReleaseIngestEndpoints = useMemo(() => {
    return sortedReleaseIngestEndpoints.filter(
      (endpoint) => (endpoint.provider ?? "").toLowerCase() === "azure_devops"
    );
  }, [sortedReleaseIngestEndpoints]);
  const selectedReleaseIngestEndpointIsAzureDevOps =
    (selectedReleaseIngestEndpoint?.provider ?? "").toLowerCase() === "azure_devops";
  const availableReleaseSystems = useMemo(() => {
    const discovered = new Set<ReleaseSystem>();
    sortedReleaseIngestEndpoints.forEach((endpoint) => {
      const provider = normalizeReleaseSystem(endpoint.provider);
      if (!provider) {
        return;
      }
      if (draft.deploymentDriver === "pipeline_trigger" && provider !== "azure_devops") {
        return;
      }
      discovered.add(provider);
    });
    if (draft.deploymentDriver === "pipeline_trigger") {
      discovered.add("azure_devops");
    }
    if (discovered.size === 0) {
      discovered.add(draft.deploymentDriver === "pipeline_trigger" ? "azure_devops" : "github");
    }
    return RELEASE_SYSTEM_ORDER.filter((provider) => discovered.has(provider));
  }, [draft.deploymentDriver, sortedReleaseIngestEndpoints]);
  const effectiveReleaseSystem = useMemo<ReleaseSystem>(() => {
    if (draft.deploymentDriver === "pipeline_trigger") {
      return "azure_devops";
    }
    if (availableReleaseSystems.includes(selectedReleaseSystem)) {
      return selectedReleaseSystem;
    }
    return availableReleaseSystems[0] ?? "github";
  }, [availableReleaseSystems, draft.deploymentDriver, selectedReleaseSystem]);
  const releaseSourceTypeLabel = draft.deploymentDriver === "pipeline_trigger"
    ? RELEASE_SOURCE_TYPE_LABELS.external_deployment_inputs
    : RELEASE_SOURCE_TYPE_LABELS[draft.releaseArtifactSource];
  const releaseIngestEndpointOptions = useMemo(() => {
    if (draft.deploymentDriver !== "pipeline_trigger") {
      return sortedReleaseIngestEndpoints.filter(
        (endpoint) => normalizeReleaseSystem(endpoint.provider) === effectiveReleaseSystem
      );
    }
    const base = [...pipelineReleaseIngestEndpoints];
    if (
      selectedReleaseIngestEndpoint &&
      !selectedReleaseIngestEndpointIsAzureDevOps &&
      !base.some((endpoint) => (endpoint.id ?? "").trim() === (selectedReleaseIngestEndpoint.id ?? "").trim())
    ) {
      base.unshift(selectedReleaseIngestEndpoint);
    }
    return base;
  }, [
    draft.deploymentDriver,
    effectiveReleaseSystem,
    sortedReleaseIngestEndpoints,
    pipelineReleaseIngestEndpoints,
    selectedReleaseIngestEndpoint,
    selectedReleaseIngestEndpointIsAzureDevOps,
  ]);
  const selectedDiscoveredPipelineId = useMemo(() => {
    if (draft.driver.pipelineId.trim() === "") {
      return "__none";
    }
    return discoveredPipelines.some((pipeline) => pipeline.id === draft.driver.pipelineId.trim())
      ? draft.driver.pipelineId.trim()
      : "__none";
  }, [discoveredPipelines, draft.driver.pipelineId]);
  const selectedDiscoveredRepositoryId = useMemo(() => {
    const currentValue = draft.driver.repository.trim();
    if (currentValue === "") {
      return "__none";
    }
    const matching = discoveredRepositories.find((repository) =>
      repository.name === currentValue || repository.id === currentValue
    );
    return matching ? matching.id : "__none";
  }, [discoveredRepositories, draft.driver.repository]);
  const selectedDiscoveredServiceConnectionId = useMemo(() => {
    const currentValue = draft.driver.azureServiceConnectionName.trim();
    if (currentValue === "") {
      return "__none";
    }
    const matching = discoveredServiceConnections.find((connection) =>
      connection.name === currentValue || connection.id === currentValue
    );
    return matching ? matching.id : "__none";
  }, [discoveredServiceConnections, draft.driver.azureServiceConnectionName]);
  const hasSingleCachedAdoProject = cachedProviderConnectionProjects.length === 1;
  const hasSingleDiscoveredRepository = discoveredRepositories.length === 1;
  const hasSingleDiscoveredPipeline = discoveredPipelines.length === 1;
  const hasSingleDiscoveredServiceConnection = discoveredServiceConnections.length === 1;

  useEffect(() => {
    setDiscoveredRepositories([]);
    setDiscoveredPipelines([]);
    setDiscoveredServiceConnections([]);
    setRepositoryDiscoveryError("");
    setPipelineDiscoveryError("");
    setServiceConnectionDiscoveryError("");
    repositoryDiscoveryKeyRef.current = "";
    pipelineDiscoveryKeyRef.current = "";
    serviceConnectionDiscoveryKeyRef.current = "";
  }, [cachedProviderConnectionProjects, draft.providerConnectionId]);

  useEffect(() => {
    setDiscoveredRepositories([]);
    setDiscoveredPipelines([]);
    setDiscoveredServiceConnections([]);
    setRepositoryDiscoveryError("");
    setPipelineDiscoveryError("");
    setServiceConnectionDiscoveryError("");
    repositoryDiscoveryKeyRef.current = "";
    pipelineDiscoveryKeyRef.current = "";
    serviceConnectionDiscoveryKeyRef.current = "";
  }, [draft.driver.organization, draft.driver.project]);

  useEffect(() => {
    if (
      draft.deploymentDriver !== "pipeline_trigger" ||
      draft.providerConnectionId.trim() === "" ||
      draft.driver.project.trim() !== "" ||
      cachedProviderConnectionProjects.length !== 1
    ) {
      return;
    }
    const onlyProject = cachedProviderConnectionProjects[0];
    if (!onlyProject?.name) {
      return;
    }
    const resolvedOrganization =
      deriveAzureDevOpsAccountUrl(onlyProject.webUrl ?? "") || selectedProviderConnectionDiscoveryUrl;
    if (resolvedOrganization === "") {
      return;
    }
    setDraft((current) => ({
      ...current,
      driver: {
        ...current.driver,
        organization: resolvedOrganization,
        project: onlyProject.name,
      },
    }));
  }, [
    cachedProviderConnectionProjects,
    draft.deploymentDriver,
    draft.driver.project,
    draft.providerConnectionId,
    selectedProviderConnectionDiscoveryUrl,
  ]);

  useEffect(() => {
    if (draft.deploymentDriver !== "pipeline_trigger") {
      return;
    }
    if (draft.providerConnectionId.trim() === "") {
      return;
    }
    if (draft.driver.project.trim() === "") {
      return;
    }
    const matchingProject = cachedProviderConnectionProjects.find(
      (projectOption) => projectOption.name === draft.driver.project.trim()
    );
    if (matchingProject) {
      return;
    }
    setDraft((current) => ({
      ...current,
      driver: {
        ...current.driver,
        organization: "",
        project: "",
        repository: "",
        pipelineId: "",
        azureServiceConnectionName: "",
      },
    }));
  }, [
    cachedProviderConnectionProjects,
    draft.deploymentDriver,
    draft.driver.project,
    draft.providerConnectionId,
  ]);

  useEffect(() => {
    if (draft.deploymentDriver !== "pipeline_trigger") {
      return;
    }
    if (draft.providerConnectionId.trim() !== "") {
      return;
    }
    if (verifiedPipelineProviderConnections.length !== 1) {
      return;
    }
    const onlyConnectionId = (verifiedPipelineProviderConnections[0]?.id ?? "").trim();
    if (onlyConnectionId === "") {
      return;
    }
    setDraft((current) => {
      if (current.providerConnectionId.trim() !== "") {
        return current;
      }
      return {
        ...current,
        providerConnectionId: onlyConnectionId,
      };
    });
  }, [
    draft.deploymentDriver,
    draft.providerConnectionId,
    verifiedPipelineProviderConnections,
  ]);

  useEffect(() => {
    if (draft.releaseIngestEndpointId.trim() !== "") {
      return;
    }
    if (releaseIngestEndpointOptions.length !== 1) {
      return;
    }
    const onlyEndpointId = (releaseIngestEndpointOptions[0]?.id ?? "").trim();
    if (onlyEndpointId === "") {
      return;
    }
    setDraft((current) => {
      if (current.releaseIngestEndpointId.trim() !== "") {
        return current;
      }
      return {
        ...current,
        releaseIngestEndpointId: onlyEndpointId,
      };
    });
  }, [draft.releaseIngestEndpointId, releaseIngestEndpointOptions]);

  useEffect(() => {
    if (draft.deploymentDriver === "pipeline_trigger" && selectedReleaseSystem !== "azure_devops") {
      setSelectedReleaseSystem("azure_devops");
    }
  }, [draft.deploymentDriver, selectedReleaseSystem]);

  useEffect(() => {
    const linkedProvider = normalizeReleaseSystem(selectedReleaseIngestEndpoint?.provider);
    if (linkedProvider && linkedProvider !== selectedReleaseSystem) {
      setSelectedReleaseSystem(linkedProvider);
    }
  }, [selectedReleaseIngestEndpoint, selectedReleaseSystem]);

  useEffect(() => {
    const linkedProvider = normalizeReleaseSystem(selectedReleaseIngestEndpoint?.provider);
    if (!linkedProvider || linkedProvider === effectiveReleaseSystem) {
      return;
    }
    setDraft((current) => ({
      ...current,
      releaseIngestEndpointId: "",
    }));
  }, [effectiveReleaseSystem, selectedReleaseIngestEndpoint]);

function normalizeDiscoveryError(message: string, providerLabel: string): string {
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

  const canUseManualServiceConnectionName =
    draft.deploymentDriver === "pipeline_trigger"
    && draft.providerConnectionId.trim() !== ""
    && resolvedAdoOrganization !== ""
    && resolvedAdoProject !== ""
    && discoveredServiceConnections.length === 0;

  const draftValidationIssues = useMemo(() => {
    const issues: DraftValidationIssue[] = [];
    if (draft.name.trim() === "") {
      issues.push({
        id: "project-name-required",
        tab: "general",
        fieldId: "project-name",
        message: "Project display name is required.",
      });
    }
    if (draft.deploymentDriver === "pipeline_trigger") {
      if (draft.providerConnectionId.trim() === "") {
        issues.push({
          id: "driver-provider-connection-required",
          tab: "deployment-driver",
          fieldId: "driver-provider-connection-id",
          message: "Deployment Driver: linked Azure DevOps deployment connection is required.",
        });
      } else if (
        selectedProviderConnection &&
        (selectedProviderConnection.provider ?? "").toLowerCase() !== "azure_devops"
      ) {
        issues.push({
          id: "driver-provider-connection-provider",
          tab: "deployment-driver",
          fieldId: "driver-provider-connection-id",
          message: "Deployment Driver: linked deployment connection must use Azure DevOps.",
        });
      } else if (selectedProviderConnectionRequiresVerification) {
        issues.push({
          id: "driver-provider-connection-scope",
          tab: "deployment-driver",
          fieldId: "driver-provider-connection-id",
          message:
            "Deployment Driver: selected deployment connection must be verified in Admin → Deployment Connections before Azure DevOps projects can be selected.",
        });
      } else if (draft.driver.organization.trim() === "" || draft.driver.project.trim() === "") {
        issues.push({
          id: "driver-project-required",
          tab: "deployment-driver",
          fieldId: "driver-project-select",
          message: "Deployment Driver: choose an Azure DevOps project.",
        });
      } else {
        if (draft.driver.repository.trim() === "") {
          issues.push({
            id: "driver-repository-required",
            tab: "deployment-driver",
            fieldId: "driver-repository-select",
            message: "Deployment Driver: choose an Azure DevOps repository.",
          });
        }
        if (draft.driver.pipelineId.trim() === "") {
          issues.push({
            id: "driver-pipeline-id-required",
            tab: "deployment-driver",
            fieldId: "driver-pipeline-select",
            message: "Deployment Driver: choose an Azure DevOps pipeline.",
          });
        }
        if (draft.driver.azureServiceConnectionName.trim() === "") {
          issues.push({
            id: "driver-service-connection-required",
            tab: "deployment-driver",
            fieldId: "driver-service-connection",
            message: canUseManualServiceConnectionName
              ? "Deployment Driver: enter the Azure service connection name that the pipeline uses."
              : "Deployment Driver: choose the Azure service connection that pipeline uses.",
          });
        }
      }
    }
    if (parseOptionalNumber(draft.runtime.expectedStatus) === undefined) {
      issues.push({
        id: "runtime-status-number",
        tab: "runtime-health",
        fieldId: "runtime-status-code",
        message: "Runtime Health: expected status must be a number.",
      });
    }
    if (parseOptionalNumber(draft.runtime.timeoutMs) === undefined) {
      issues.push({
        id: "runtime-timeout-number",
        tab: "runtime-health",
        fieldId: "runtime-timeout",
        message: "Runtime Health: timeout (ms) must be a number.",
      });
    }
    return issues;
  }, [
    canUseManualServiceConnectionName,
    draft,
    selectedProviderConnection,
    selectedProviderConnectionRequiresVerification,
  ]);

  const configComplete = project !== null && draftValidationIssues.length === 0;
  const canPersist = project !== null && draftValidationIssues.length === 0;
  const canCreateProject = createDraft.id.trim() !== "" && createDraft.name.trim() !== "";
  const releaseSourceLabel = releaseSourceTypeLabel;
  const createSelectedDeploymentSystem = deriveDeploymentSystem(createDraft.deploymentDriver);
  const createSelectedReleaseSystem: ReleaseSystem =
    createDraft.deploymentDriver === "pipeline_trigger" || createDraft.releaseArtifactSource === "external_deployment_inputs"
      ? "azure_devops"
      : "github";

  async function saveProjectConfig(): Promise<void> {
    if (!project?.id || draftValidationIssues.length > 0) {
      return;
    }
    setIsSaving(true);
    try {
      await onPatchProject(project.id, buildPatchRequest(draft));
      toast.success("Project configuration saved.");
    } catch (error) {
      toast.error((error as Error).message);
    } finally {
      setIsSaving(false);
    }
  }

  async function runValidation(scopes: ValidationScope[]): Promise<void> {
    if (!project?.id) {
      return;
    }
    setIsValidating(true);
    try {
      const response = await onValidateProject(project.id, { scopes });
      if (response.valid) {
        toast.success("Validation passed.");
      } else {
        toast.warning("Validation completed with findings.");
      }
    } catch (error) {
      toast.error((error as Error).message);
    } finally {
      setIsValidating(false);
    }
  }

  async function discoverAdoPipelines(options?: { silent?: boolean }): Promise<void> {
    if (!project?.id) {
      return;
    }
    if (selectedProviderConnectionRequiresVerification) {
      const message =
        "Open Admin → Deployment Connections, verify the selected connection, and select an Azure DevOps project before MAPPO can browse pipelines.";
      setPipelineDiscoveryError(message);
      if (!options?.silent) {
        toast.error(message);
      }
      return;
    }
    const resolvedOrganization = resolvedAdoOrganization || undefined;
    const resolvedProject = resolvedAdoProject || undefined;
    setIsDiscoveringPipelines(true);
    setPipelineDiscoveryError("");
    try {
      const response = await onDiscoverAdoPipelines(project.id, {
        organization: resolvedOrganization,
        project: resolvedProject,
        providerConnectionId: draft.providerConnectionId.trim() || undefined,
      });
      const pipelines = [...(response.pipelines ?? [])].sort((a, b) =>
        `${a.name ?? ""}`.localeCompare(`${b.name ?? ""}`, undefined, { sensitivity: "base" })
      );
      setDiscoveredPipelines(pipelines);
      const currentPipelineId = draft.driver.pipelineId.trim();
      const hasMatchingSelectedPipeline =
        currentPipelineId !== "" && pipelines.some((pipeline) => pipeline.id === currentPipelineId);
      if (!hasMatchingSelectedPipeline && currentPipelineId === "" && pipelines.length === 1) {
        setDraft((current) => ({
          ...current,
          driver: { ...current.driver, pipelineId: pipelines[0]?.id ?? "" },
        }));
      }
      if (!options?.silent) {
        toast.success(
          `Discovered ${pipelines.length} pipeline${pipelines.length === 1 ? "" : "s"} from ${response.organization}/${response.project}.`
        );
      }
    } catch (error) {
      const message = (error as Error).message;
      const normalizedMessage = normalizeDiscoveryError(message, "Azure DevOps");
      setPipelineDiscoveryError(normalizedMessage);
      if (!options?.silent) {
        toast.error(`Unable to load Azure DevOps pipelines. ${normalizedMessage}`);
      }
    } finally {
      setIsDiscoveringPipelines(false);
    }
  }

  async function discoverAdoRepositories(options?: { silent?: boolean }): Promise<void> {
    if (!project?.id) {
      return;
    }
    if (selectedProviderConnectionRequiresVerification) {
      const message =
        "Open Admin → Deployment Connections, verify the selected connection, and select an Azure DevOps project before MAPPO can browse repositories.";
      setRepositoryDiscoveryError(message);
      if (!options?.silent) {
        toast.error(message);
      }
      return;
    }
    const resolvedOrganization = resolvedAdoOrganization || undefined;
    const resolvedProject = resolvedAdoProject || undefined;
    setIsDiscoveringRepositories(true);
    setRepositoryDiscoveryError("");
    try {
      const response = await onDiscoverAdoRepositories(project.id, {
        organization: resolvedOrganization,
        project: resolvedProject,
        providerConnectionId: draft.providerConnectionId.trim() || undefined,
      });
      const repositories = [...(response.repositories ?? [])].sort((a, b) =>
        `${a.name ?? ""}`.localeCompare(`${b.name ?? ""}`, undefined, { sensitivity: "base" })
      );
      setDiscoveredRepositories(repositories);
      const currentRepositoryValue = draft.driver.repository.trim();
      const selectedRepository = repositories.find(
        (repository) =>
          repository.name === currentRepositoryValue || repository.id === currentRepositoryValue
      );
      const onlyRepository = repositories.length === 1 ? repositories[0] : null;
      const repositoryToApply = selectedRepository ?? (currentRepositoryValue === "" ? onlyRepository : null);
      if (repositoryToApply) {
        setDraft((current) => ({
          ...current,
          driver: {
            ...current.driver,
            repository:
              current.driver.repository.trim() === "" ? (repositoryToApply?.name ?? "") : current.driver.repository,
            branch:
              current.driver.branch.trim() === "" || current.driver.branch.trim() === "main"
                ? (repositoryToApply?.defaultBranch?.replace(/^refs\/heads\//, "") ?? current.driver.branch)
                : current.driver.branch,
          },
        }));
      }
      if (!options?.silent) {
        toast.success(
          `Discovered ${repositories.length} repositor${repositories.length === 1 ? "y" : "ies"} from ${response.organization}/${response.project}.`
        );
      }
    } catch (error) {
      const message = (error as Error).message;
      const normalizedMessage = normalizeDiscoveryError(message, "Azure DevOps");
      setRepositoryDiscoveryError(normalizedMessage);
      if (!options?.silent) {
        toast.error(`Unable to load Azure DevOps repositories. ${normalizedMessage}`);
      }
    } finally {
      setIsDiscoveringRepositories(false);
    }
  }

  async function discoverAdoServiceConnections(options?: { silent?: boolean }): Promise<void> {
    if (!project?.id) {
      return;
    }
    if (selectedProviderConnectionRequiresVerification) {
      const message =
        "Open Admin → Deployment Connections, verify the selected connection, and select an Azure DevOps project before MAPPO can browse service connections.";
      setServiceConnectionDiscoveryError(message);
      if (!options?.silent) {
        toast.error(message);
      }
      return;
    }
    const resolvedOrganization = resolvedAdoOrganization || undefined;
    const resolvedProject = resolvedAdoProject || undefined;
    setIsDiscoveringServiceConnections(true);
    setServiceConnectionDiscoveryError("");
    try {
      const response = await onDiscoverAdoServiceConnections(project.id, {
        organization: resolvedOrganization,
        project: resolvedProject,
        providerConnectionId: draft.providerConnectionId.trim() || undefined,
      });
      const serviceConnections = [...(response.serviceConnections ?? [])].sort((a, b) =>
        `${a.name ?? ""}`.localeCompare(`${b.name ?? ""}`, undefined, { sensitivity: "base" })
      );
      setDiscoveredServiceConnections(serviceConnections);
      const currentServiceConnectionName = draft.driver.azureServiceConnectionName.trim();
      const hasMatchingSelectedServiceConnection =
        currentServiceConnectionName !== ""
        && serviceConnections.some(
          (serviceConnection) =>
            serviceConnection.name === currentServiceConnectionName
            || serviceConnection.id === currentServiceConnectionName
        );
      if (
        !hasMatchingSelectedServiceConnection
        && currentServiceConnectionName === ""
        && serviceConnections.length === 1
      ) {
        setDraft((current) => ({
          ...current,
          driver: {
            ...current.driver,
            azureServiceConnectionName: serviceConnections[0]?.name ?? "",
          },
        }));
      }
      if (!options?.silent) {
        toast.success(
          `Discovered ${serviceConnections.length} service connection${serviceConnections.length === 1 ? "" : "s"} from ${response.organization}/${response.project}.`
        );
      }
    } catch (error) {
      const message = (error as Error).message;
      const normalizedMessage = normalizeDiscoveryError(message, "Azure DevOps");
      setServiceConnectionDiscoveryError(normalizedMessage);
      if (!options?.silent) {
        toast.error(`Unable to load Azure service connections. ${normalizedMessage}`);
      }
    } finally {
      setIsDiscoveringServiceConnections(false);
    }
  }

  useEffect(() => {
    if (
      draft.deploymentDriver !== "pipeline_trigger" ||
      draft.driver.pipelineSystem !== "azure_devops" ||
      draft.providerConnectionId.trim() === "" ||
      selectedProviderConnectionRequiresVerification ||
      selectedProviderConnectionDiscoveryUrl === "" ||
      !hasSelectedAzureDevOpsProject
    ) {
      return;
    }

    const discoveryKey = `${draft.providerConnectionId.trim()}|${resolvedAdoOrganization}|${resolvedAdoProject}`;

    if (repositoryDiscoveryKeyRef.current !== discoveryKey) {
      repositoryDiscoveryKeyRef.current = discoveryKey;
      void discoverAdoRepositories({ silent: true });
    }
    if (pipelineDiscoveryKeyRef.current !== discoveryKey) {
      pipelineDiscoveryKeyRef.current = discoveryKey;
      void discoverAdoPipelines({ silent: true });
    }
    if (serviceConnectionDiscoveryKeyRef.current !== discoveryKey) {
      serviceConnectionDiscoveryKeyRef.current = discoveryKey;
      void discoverAdoServiceConnections({ silent: true });
    }
  }, [
    draft.deploymentDriver,
    draft.driver.pipelineSystem,
    draft.providerConnectionId,
    hasSelectedAzureDevOpsProject,
    selectedProviderConnectionDiscoveryUrl,
    selectedProviderConnectionRequiresVerification,
    resolvedAdoOrganization,
    resolvedAdoProject,
  ]);

  async function handleCreateProject(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    if (!canCreateProject) {
      return;
    }
    setCreateSubmitting(true);
    try {
      const created = await onCreateProject(buildCreateRequest(createDraft));
      toast.success(`Created project ${created.name ?? created.id}.`);
      setCreateDrawerOpen(false);
      setCreateDraft(
        projectToDraft({
          id: "",
          name: "",
          accessStrategy: "azure_workload_rbac",
          deploymentDriver: "azure_deployment_stack",
          releaseArtifactSource: "blob_arm_template",
          runtimeHealthProvider: "azure_container_app_http",
        })
      );
    } catch (error) {
      toast.error((error as Error).message);
    } finally {
      setCreateSubmitting(false);
    }
  }

  function updateDraft<K extends keyof ProjectDraft>(key: K, value: ProjectDraft[K]): void {
    setDraft((current) => ({ ...current, [key]: value }));
  }

  function updateCreateDraft<K extends keyof ProjectDraft>(key: K, value: ProjectDraft[K]): void {
    setCreateDraft((current) => ({ ...current, [key]: value }));
  }

  function updateCreateDeploymentSystem(system: DeploymentSystem): void {
    setCreateDraft((current) => {
      const nextDriver = driverForDeploymentSystem(system);
      return {
        ...current,
        deploymentDriver: nextDriver,
        releaseArtifactSource:
          nextDriver === "pipeline_trigger"
            ? "external_deployment_inputs"
            : current.releaseArtifactSource === "external_deployment_inputs"
              ? "blob_arm_template"
              : current.releaseArtifactSource,
      };
    });
  }

  function updateCreateReleaseSystem(system: ReleaseSystem): void {
    setCreateDraft((current) => ({
      ...current,
      releaseArtifactSource:
        system === "azure_devops" ? "external_deployment_inputs" : "blob_arm_template",
    }));
  }

  function focusValidationIssue(issue: DraftValidationIssue): void {
    setActiveTab(issue.tab);
    window.setTimeout(() => {
      const element = document.getElementById(issue.fieldId);
      if (!element) {
        return;
      }
      element.scrollIntoView({ behavior: "smooth", block: "center" });
      if ("focus" in element && typeof element.focus === "function") {
        element.focus();
      }
    }, 50);
  }

  return (
    <section className="space-y-4">
      <Card className="glass-card animate-fade-up [animation-delay:30ms] [animation-fill-mode:forwards]">
        <CardHeader className="pb-2">
          <CardTitle className="text-base">Project Setup Checklist</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3 pt-0 text-sm">
          <div className="grid gap-2 md:grid-cols-2 xl:grid-cols-5">
            <div className="rounded-md border border-border/70 bg-background/40 p-2">
              <p className="text-xs font-medium">Project created</p>
              <Badge className="mt-1" variant={project ? "default" : "secondary"}>
                {project ? "Complete" : "Pending"}
              </Badge>
            </div>
            <div className="rounded-md border border-border/70 bg-background/40 p-2">
              <p className="text-xs font-medium">Configuration complete</p>
              <Badge className="mt-1" variant={configComplete ? "default" : "secondary"}>
                {project ? (configComplete ? "Complete" : "Needs attention") : "Pending"}
              </Badge>
            </div>
            <div className="rounded-md border border-border/70 bg-background/40 p-2">
              <p className="text-xs font-medium">Targets registered</p>
              <Badge className="mt-1" variant={targetCount > 0 ? "default" : "secondary"}>
                {targetCount > 0 ? "Complete" : "Pending"}
              </Badge>
            </div>
            <div className="rounded-md border border-border/70 bg-background/40 p-2">
              <p className="text-xs font-medium">Release available</p>
              <Badge className="mt-1" variant={projectReleaseCount > 0 ? "default" : "secondary"}>
                {projectReleaseCount > 0 ? "Complete" : "Pending"}
              </Badge>
            </div>
            <div className="rounded-md border border-border/70 bg-background/40 p-2">
              <p className="text-xs font-medium">Ready to deploy</p>
              <Badge
                className="mt-1"
                variant={configComplete && targetCount > 0 && projectReleaseCount > 0 ? "default" : "secondary"}
              >
                {configComplete && targetCount > 0 && projectReleaseCount > 0 ? "Complete" : "Blocked"}
              </Badge>
            </div>
          </div>

        </CardContent>
      </Card>

      <Card className="glass-card animate-fade-up [animation-delay:40ms] [animation-fill-mode:forwards]">
        <CardHeader className="flex flex-col gap-3 md:flex-row md:items-start md:justify-between">
          <div className="space-y-2">
            <CardTitle>Project Settings</CardTitle>
            <p className="text-sm text-muted-foreground">
              Configure release sources, deployment behavior, runtime health, and project defaults.
            </p>
          </div>
          <div className="sticky top-2 z-20 flex flex-wrap items-center gap-2 rounded-md border border-border/60 bg-background/80 p-2 backdrop-blur">
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                void runValidation(["credentials", "webhook", "target_contract"]);
              }}
              disabled={!project?.id || isValidating}
            >
              {isValidating ? "Validating..." : "Validate"}
            </Button>
            <Button type="button" onClick={() => void saveProjectConfig()} disabled={!canPersist || isSaving}>
              {isSaving ? "Saving..." : "Save"}
            </Button>
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          {draftValidationIssues.length > 0 ? (
            <div className="rounded-md border border-amber-400/60 bg-amber-500/10 p-3">
              <p className="mb-2 text-sm font-semibold text-amber-200">Inline validation</p>
              <ul className="space-y-1 text-xs text-amber-100">
                {draftValidationIssues.map((issue) => (
                  <li key={issue.id} className="flex flex-wrap items-center justify-between gap-2 rounded-sm border border-amber-200/20 px-2 py-1">
                    <span>{issue.message}</span>
                    <Button
                      type="button"
                      size="sm"
                      variant="outline"
                      className="h-7 border-amber-200/40 bg-amber-500/10 text-[11px] hover:bg-amber-500/20"
                      onClick={() => focusValidationIssue(issue)}
                    >
                      Go to field
                    </Button>
                  </li>
                ))}
              </ul>
            </div>
          ) : null}

          <div className="grid gap-4 xl:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
            <div className="min-w-0">
              <Tabs value={activeTab} onValueChange={(value) => setActiveTab(value as ProjectTab)} className="space-y-4">
                <TabsList className="flex h-auto w-full flex-wrap justify-start gap-1 bg-background/70 p-1">
                  {PROJECT_TABS.map((tab) => (
                    <TabsTrigger
                      key={tab.key}
                      value={tab.key}
                      className="h-auto min-w-[120px] whitespace-normal px-3 py-2 text-center text-xs leading-tight sm:min-w-[140px]"
                    >
                      {tab.label}
                    </TabsTrigger>
                  ))}
                </TabsList>

            <TabsContent value="general" className="space-y-3">
              <h3 className="text-sm font-semibold uppercase tracking-[0.08em] text-muted-foreground">General</h3>
              <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                <div className="space-y-1">
                  <Label htmlFor="project-id">Project ID</Label>
                  <Input id="project-id" value={draft.id} disabled />
                </div>
                <div className="space-y-1">
                  <div className="flex items-center gap-1">
                    <Label htmlFor="project-name">Project display name</Label>
                    <FieldHelpTooltip content="Friendly name shown to operators in project selector and page headers." />
                  </div>
                  <Input
                    id="project-name"
                    value={draft.name}
                    onChange={(event) => updateDraft("name", event.target.value)}
                    placeholder="Customer Managed App Orchestrator"
                  />
                </div>
              </div>
            </TabsContent>

            <TabsContent value="release-ingest" className="space-y-3">
              <h3 className="text-sm font-semibold uppercase tracking-[0.08em] text-muted-foreground">Release Source</h3>
              <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                <div className="space-y-1">
                  <div className="flex items-center gap-1">
                    <Label htmlFor="release-system">Release provider</Label>
                    <FieldHelpTooltip content="Which external system tells MAPPO about new versions for this project. MAPPO filters the linked release sources below to this provider." />
                  </div>
                  <Select
                    value={effectiveReleaseSystem}
                    onValueChange={(value) => {
                      const nextSystem = value as ReleaseSystem;
                      setSelectedReleaseSystem(nextSystem);
                    }}
                    disabled={draft.deploymentDriver === "pipeline_trigger" || availableReleaseSystems.length <= 1}
                  >
                    <SelectTrigger id="release-system">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {availableReleaseSystems.map((provider) => (
                        <SelectItem key={provider} value={provider}>
                          {RELEASE_SYSTEM_LABELS[provider]}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <p className="text-xs text-muted-foreground">
                    {draft.deploymentDriver === "pipeline_trigger"
                      ? "Pipeline-driven rollout always listens for Azure DevOps release events."
                      : availableReleaseSystems.length <= 1
                        ? `This project currently has one release provider option: ${RELEASE_SYSTEM_LABELS[effectiveReleaseSystem]}.`
                        : "Choose the provider MAPPO should check for new versions for this project."}
                  </p>
                </div>
                <div className="space-y-1">
                  <div className="flex items-center gap-1">
                    <Label htmlFor="release-source-type">Release source type</Label>
                    <FieldHelpTooltip content="How the selected provider tells MAPPO about deployable versions for this project." />
                  </div>
                  <Input id="release-source-type" value={releaseSourceTypeLabel} disabled />
                  <p className="text-xs text-muted-foreground">
                    {draft.deploymentDriver === "pipeline_trigger"
                      ? "MAPPO learns about versions from an inbound webhook or pipeline event."
                      : "MAPPO reads deployable versions from the selected provider's release manifest."}
                  </p>
                </div>
                <div className="space-y-1 md:col-span-2">
                  <div className="flex items-center gap-1">
                    <Label htmlFor="release-ingest-endpoint-id">
                      Linked release source
                    </Label>
                    <FieldHelpTooltip content="Pick the global release source from Admin > Release Sources. MAPPO uses it to decide which inbound webhook or release notifications are trusted for this project." />
                  </div>
                  <div className="flex flex-col gap-2 md:flex-row">
                    <Select
                      value={draft.releaseIngestEndpointId.trim() === "" ? "__none" : draft.releaseIngestEndpointId}
                      onValueChange={(value) =>
                        updateDraft("releaseIngestEndpointId", value === "__none" ? "" : value)
                      }
                    >
                      <SelectTrigger id="release-ingest-endpoint-id" className="md:flex-1">
                        <SelectValue placeholder="Select release source" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="__none">No linked release source</SelectItem>
                        {releaseIngestEndpointOptions
                          .filter((endpoint) => (endpoint.id ?? "").trim() !== "")
                          .map((endpoint) => (
                          <SelectItem key={endpoint.id ?? endpoint.name} value={endpoint.id ?? ""}>
                            {endpoint.name || endpoint.id}
                            {" ("}
                            {endpoint.id}
                            {")"}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    <Button
                      type="button"
                      variant="outline"
                      onClick={() => {
                        void refreshReleaseIngestEndpointOptions();
                      }}
                      disabled={isLoadingReleaseIngestEndpoints}
                    >
                      {isLoadingReleaseIngestEndpoints ? "Reloading..." : "Reload release sources"}
                    </Button>
                  </div>
                  {draft.releaseIngestEndpointId.trim() !== "" && selectedReleaseIngestEndpoint ? (
                    <div className="rounded-md border border-border/60 bg-background/40 px-3 py-2 text-xs text-muted-foreground">
                      Using <span className="font-medium text-foreground">{selectedReleaseIngestEndpoint.name || selectedReleaseIngestEndpoint.id}</span>{" "}
                      from <span className="font-medium text-foreground">Admin → Release Sources</span>.
                    </div>
                  ) : null}
                  {releaseIngestEndpointOptions.length === 0 ? (
                    <p className="text-xs text-muted-foreground">
                      No {RELEASE_SYSTEM_LABELS[effectiveReleaseSystem]} release sources are available yet. Create one in{" "}
                      <span className="font-medium text-foreground">Admin → Release Sources</span>.
                    </p>
                  ) : draft.releaseIngestEndpointId.trim() === "" ? (
                    <p className="text-xs text-muted-foreground">
                      Select the {RELEASE_SYSTEM_LABELS[effectiveReleaseSystem]} release source MAPPO should trust for this project.
                    </p>
                  ) : null}
                </div>
              </div>
            </TabsContent>

            <TabsContent value="deployment-driver" className="space-y-3">
              <h3 className="text-sm font-semibold uppercase tracking-[0.08em] text-muted-foreground">Deployment Driver</h3>
              <div className="space-y-3">
                <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                  <div className="space-y-1">
                    <div className="flex items-center gap-1">
                      <Label htmlFor="deployment-system">Deployment system</Label>
                      <FieldHelpTooltip content="Which external system or Azure control plane MAPPO uses for this project's rollouts." />
                    </div>
                    <Select
                      value={selectedDeploymentSystem}
                      onValueChange={(value) => {
                        const nextSystem = value as DeploymentSystem;
                        const nextDriver = driverForDeploymentSystem(nextSystem);
                        setDraft((current) => ({
                          ...current,
                          deploymentDriver: nextDriver,
                          releaseArtifactSource:
                            nextDriver === "pipeline_trigger"
                              ? "external_deployment_inputs"
                              : current.releaseArtifactSource === "external_deployment_inputs"
                                ? "blob_arm_template"
                                : current.releaseArtifactSource,
                          providerConnectionId: nextDriver === "pipeline_trigger" ? current.providerConnectionId : "",
                          driver: {
                            ...current.driver,
                            organization: nextDriver === "pipeline_trigger" ? current.driver.organization : "",
                            project: nextDriver === "pipeline_trigger" ? current.driver.project : "",
                            repository: nextDriver === "pipeline_trigger" ? current.driver.repository : "",
                            pipelineId: nextDriver === "pipeline_trigger" ? current.driver.pipelineId : "",
                            azureServiceConnectionName:
                              nextDriver === "pipeline_trigger" ? current.driver.azureServiceConnectionName : "",
                            branch: nextDriver === "pipeline_trigger" ? current.driver.branch : "main",
                          },
                        }));
                      }}
                    >
                      <SelectTrigger id="deployment-system">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="azure">
                          {DEPLOYMENT_SYSTEM_LABELS.azure}
                        </SelectItem>
                        <SelectItem value="azure_devops">
                          {DEPLOYMENT_SYSTEM_LABELS.azure_devops}
                        </SelectItem>
                      </SelectContent>
                    </Select>
                    <p className="text-xs text-muted-foreground">
                      {selectedDeploymentSystem === "azure"
                        ? "MAPPO deploys directly into each selected Azure target."
                        : "MAPPO triggers an Azure DevOps pipeline for each selected target."}
                    </p>
                  </div>
                  <div className="space-y-1">
                    <div className="flex items-center gap-1">
                      <Label htmlFor="deployment-method">Deployment method</Label>
                      <FieldHelpTooltip content={DEPLOYMENT_DRIVER_HELP[draft.deploymentDriver]} />
                    </div>
                    <Input id="deployment-method" value={DEPLOYMENT_DRIVER_LABELS[draft.deploymentDriver]} disabled />
                    <p className="text-xs text-muted-foreground">
                      {DEPLOYMENT_DRIVER_HELP[draft.deploymentDriver]}
                    </p>
                  </div>
                  {isPipelineDriver ? (
                    <div className="space-y-1 md:col-span-2">
                      <div className="flex items-center gap-1">
                        <Label htmlFor="driver-provider-connection-id">Deployment connection</Label>
                        <FieldHelpTooltip content="Verified Azure DevOps API connection from Admin > Deployment Connections. This owns authentication and browse scope; Project Config only chooses which discovered Azure DevOps project, repository, pipeline, and service connection this MAPPO project should use." />
                      </div>
                      <div className="flex flex-col gap-2">
                        <Select
                          value={draft.providerConnectionId.trim() === "" ? "__none" : draft.providerConnectionId}
                          onValueChange={(value) =>
                            updateDraft("providerConnectionId", value === "__none" ? "" : value)
                          }
                        >
                          <SelectTrigger id="driver-provider-connection-id">
                            <SelectValue placeholder="Select deployment connection" />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="__none">Select deployment connection</SelectItem>
                            {providerConnectionOptions
                              .filter((connection) => (connection.id ?? "").trim() !== "")
                              .map((connection) => (
                                <SelectItem key={connection.id ?? connection.name} value={connection.id ?? ""}>
                                  {connection.name || connection.id}
                                  {(connection.provider ?? "").toLowerCase() !== "azure_devops"
                                    ? " (incompatible provider)"
                                    : isVerifiedAzureDevOpsConnection(connection)
                                      ? ""
                                      : " (needs verification)"}
                                </SelectItem>
                              ))}
                          </SelectContent>
                        </Select>
                        <div className="flex items-center gap-2">
                          <Button
                            type="button"
                            variant="outline"
                            size="sm"
                            onClick={() => {
                              void refreshProviderConnectionOptions();
                            }}
                            disabled={isLoadingProviderConnections}
                          >
                            {isLoadingProviderConnections ? "Refreshing..." : "Refresh connections"}
                          </Button>
                          <span className="text-xs text-muted-foreground">
                            Configure in <span className="font-medium text-foreground">Admin → Deployment Connections</span>.
                          </span>
                        </div>
                        {verifiedPipelineProviderConnections.length === 0 ? (
                          <p className="rounded-md border border-amber-400/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-100">
                            No verified Azure DevOps deployment connections are available yet. Open Admin → Deployment Connections, add one, and verify it.
                          </p>
                        ) : null}
                        {selectedProviderConnectionRequiresVerification ? (
                          <p className="rounded-md border border-amber-400/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-100">
                            The selected deployment connection still needs verification before MAPPO can load Azure DevOps projects. Open Admin → Deployment Connections, edit{" "}
                            <span className="font-medium text-foreground">
                              {selectedProviderConnection?.name || selectedProviderConnection?.id}
                            </span>
                            , then verify it.
                          </p>
                        ) : null}
                      </div>
                    </div>
                  ) : null}
                </div>

                {isPipelineDriver ? (
                  <div className="space-y-3">
                      <div className="rounded-md border border-border/70 bg-background/40 p-3 text-xs text-muted-foreground">
                      <p>
                        Pipeline Trigger deploys through <span className="font-medium text-foreground">Azure DevOps</span>. First choose a verified <span className="font-medium text-foreground">Deployment connection</span>. That admin-managed connection owns Azure DevOps authentication and account scope; this screen only asks you to choose the Azure DevOps project resources MAPPO should use.
                      </p>
                    </div>

                    {!canSelectAzureDevOpsProject ? (
                      <div className="rounded-md border border-dashed border-border/70 bg-background/40 p-3 text-xs text-muted-foreground">
                        <p>
                          {draft.providerConnectionId.trim() === ""
                            ? (
                                <>
                                  Select a <span className="font-medium text-foreground">Deployment connection</span> first.
                                </>
                              )
                            : selectedProviderConnectionRequiresVerification
                              ? (
                                  <>
                                    Verify the selected <span className="font-medium text-foreground">Deployment connection</span> in Admin before configuring this project.
                                  </>
                                )
                              : (
                                  <>
                                    No Azure DevOps projects are available for the selected <span className="font-medium text-foreground">Deployment connection</span>. Open Admin → Deployment Connections and verify access for that connection first.
                                  </>
                                )}
                        </p>
                      </div>
                    ) : null}

                    {canSelectAzureDevOpsProject ? (
                    <div className="rounded-md border border-border/70 bg-background/60 p-3">
                        <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
                          <div>
                            <h4 className="text-sm font-semibold text-foreground">Azure DevOps Project</h4>
                            <p className="mt-1 text-xs text-muted-foreground">
                              Choose the Azure DevOps project this MAPPO project deploys through. MAPPO reads this list from the selected deployment connection after it has been verified in Admin → Deployment Connections.
                            </p>
                          </div>
                          <Badge variant="secondary">
                            {cachedProviderConnectionProjects.length} project{cachedProviderConnectionProjects.length === 1 ? "" : "s"} available
                          </Badge>
                        </div>
                        <div className="mt-3 flex flex-col gap-2 md:flex-row md:items-end">
                          <div className="flex-1 space-y-1">
                            <div className="flex items-center gap-1">
                              <Label htmlFor="driver-project-select">Azure DevOps Project</Label>
                              <FieldHelpTooltip content="Azure DevOps project discovered through the selected deployment connection. Manage which Azure DevOps account MAPPO can browse in Admin → Deployment Connections." />
                            </div>
                            {cachedProviderConnectionProjects.length > 0 ? (
                              <Select
                                value={selectedDiscoveredAdoProjectId}
                                onValueChange={(value) => {
                                  if (value === "__none") {
                                    setDraft((current) => ({
                                      ...current,
                                      driver: {
                                        ...current.driver,
                                        organization: "",
                                        project: "",
                                        repository: "",
                                        pipelineId: "",
                                        azureServiceConnectionName: "",
                                      },
                                    }));
                                    return;
                                  }
                                  const selectedProject = cachedProviderConnectionProjects.find(
                                    (projectOption) => projectOption.id === value
                                  );
                                  if (!selectedProject) {
                                    return;
                                  }
                                  const resolvedOrganization =
                                    deriveAzureDevOpsAccountUrl(selectedProject.webUrl ?? "") ||
                                    selectedProviderConnectionDiscoveryUrl;
                                  if (resolvedOrganization === "") {
                                    return;
                                  }
                                  setDraft((current) => ({
                                    ...current,
                                    driver: {
                                      ...current.driver,
                                      organization: resolvedOrganization,
                                      project: selectedProject.name,
                                      repository: "",
                                      pipelineId: "",
                                      azureServiceConnectionName: "",
                                    },
                                  }));
                                }}
                              >
                                <SelectTrigger id="driver-project-select">
                                  <SelectValue placeholder="Select discovered Azure DevOps project" />
                                </SelectTrigger>
                                <SelectContent>
                                  <SelectItem value="__none">Select Azure DevOps project</SelectItem>
                                  {cachedProviderConnectionProjects.map((projectOption) => (
                                    <SelectItem key={projectOption.id} value={projectOption.id}>
                                      {projectOption.name}
                                    </SelectItem>
                                  ))}
                                </SelectContent>
                              </Select>
                            ) : (
                              <p
                                id="driver-project-select"
                                className="rounded-md border border-dashed border-border/60 px-3 py-2 text-xs text-muted-foreground"
                                >
                                  {draft.providerConnectionId.trim() === ""
                                    ? "Select a deployment connection above first."
                                    : selectedProviderConnectionRequiresVerification
                                      ? "Open Admin → Deployment Connections and verify the selected connection first."
                                      : "This deployment connection is verified, but MAPPO does not have any Azure DevOps projects cached for it yet. Open Admin → Deployment Connections, re-verify that connection, and load its Azure DevOps projects there."}
                              </p>
                            )}
                          </div>
                        </div>
                        <p className="mt-3 text-xs text-muted-foreground">
                          The selected <span className="font-medium text-foreground">Deployment connection</span> defines which Azure DevOps account MAPPO can browse. This screen only uses Azure DevOps projects already verified and loaded in Admin → Deployment Connections.
                        </p>
                        {hasSingleCachedAdoProject && draft.driver.project.trim() !== "" ? (
                          <p className="mt-2 rounded-md border border-emerald-400/30 bg-emerald-500/10 px-3 py-2 text-xs text-emerald-100">
                            MAPPO auto-selected the only Azure DevOps project this deployment connection can access.
                          </p>
                        ) : null}
                      </div>
                    ) : null}

                    {canSelectAzureDevOpsProject && !hasSelectedAzureDevOpsProject ? (
                      <div className="rounded-md border border-dashed border-border/70 bg-background/40 p-3 text-xs text-muted-foreground">
                        <p>
                          Select an <span className="font-medium text-foreground">Azure DevOps project</span> above to continue with repository, pipeline, and service connection setup.
                        </p>
                      </div>
                    ) : null}

                    {canDiscoverAzureDevOpsProjectResources ? (
                      <>
                        <div className="rounded-md border border-border/70 bg-background/60 p-3">
                          <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
                            <div>
                              <h4 className="text-sm font-semibold text-foreground">Azure DevOps Repository</h4>
                              <p className="mt-1 text-xs text-muted-foreground">
                                Select the default repository for this MAPPO project. MAPPO loads repositories from the selected Azure DevOps project automatically and auto-selects the only match when there is one.
                              </p>
                            </div>
                            <Button
                              id="driver-repository-discovery-action"
                              type="button"
                              variant="outline"
                              disabled={isDiscoveringRepositories || !canDiscoverAzureDevOpsProjectResources}
                              onClick={() => {
                                void discoverAdoRepositories();
                              }}
                            >
                              {isDiscoveringRepositories
                                ? "Loading..."
                                : repositoryDiscoveryError
                                  ? "Retry"
                                  : discoveredRepositories.length > 0
                                    ? "Reload repositories"
                                    : "Load repositories"}
                            </Button>
                          </div>
                          <p className="mt-2 text-xs text-muted-foreground">
                            MAPPO starts this automatically after you choose an Azure DevOps project. Use <span className="font-medium text-foreground">Reload repositories</span> only if that project changed recently.
                          </p>
                          <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2">
                            <div className="space-y-1">
                              <div className="flex items-center gap-1">
                                <Label htmlFor="driver-repository-select">Azure DevOps Repository</Label>
                                <FieldHelpTooltip content="Repository MAPPO should treat as this project's default Azure DevOps repo context. MAPPO discovers it from the selected Azure DevOps project." />
                              </div>
                              {discoveredRepositories.length > 0 ? (
                                <Select
                                  value={selectedDiscoveredRepositoryId}
                                  onValueChange={(value) => {
                                    if (value === "__none") {
                                      setDraft((current) => ({
                                        ...current,
                                        driver: { ...current.driver, repository: "" },
                                      }));
                                      return;
                                    }
                                    const selected = discoveredRepositories.find((repository) => repository.id === value);
                                    if (!selected) {
                                      return;
                                    }
                                    setDraft((current) => ({
                                      ...current,
                                      driver: {
                                        ...current.driver,
                                        repository: selected.name,
                                        branch:
                                          current.driver.branch.trim() === "" || current.driver.branch.trim() === "main"
                                            ? selected.defaultBranch || current.driver.branch
                                            : current.driver.branch,
                                      },
                                    }));
                                  }}
                                >
                                  <SelectTrigger id="driver-repository-select">
                                    <SelectValue placeholder="Select discovered repository" />
                                  </SelectTrigger>
                                  <SelectContent>
                                    <SelectItem value="__none">Select discovered repository</SelectItem>
                                    {discoveredRepositories.map((repository) => (
                                      <SelectItem key={repository.id} value={repository.id}>
                                        {repository.name}
                                      </SelectItem>
                                    ))}
                                  </SelectContent>
                                </Select>
                              ) : (
                                <p
                                  id="driver-repository-select"
                                  className="rounded-md border border-dashed border-border/60 px-3 py-2 text-xs text-muted-foreground"
                                >
                                  {isDiscoveringRepositories
                                    ? "Loading Azure DevOps repositories from the selected project..."
                                : "MAPPO has not loaded any repositories from Azure DevOps for this project yet. If the selected Azure DevOps project definitely has repositories, use Reload repositories above."}
                                </p>
                              )}
                            </div>
                            <div className="space-y-1">
                              <div className="flex items-center gap-1">
                                <Label htmlFor="driver-branch">Branch</Label>
                                <FieldHelpTooltip content="Default Git branch/ref MAPPO passes when it queues the Azure DevOps pipeline run." />
                              </div>
                              <Input
                                id="driver-branch"
                                value={draft.driver.branch}
                                onChange={(event) =>
                                  setDraft((current) => ({
                                    ...current,
                                    driver: { ...current.driver, branch: event.target.value },
                                  }))
                                }
                              />
                              {draft.driver.repository.trim() !== "" ? (
                                <p className="text-xs text-muted-foreground">
                                  Selected repo: <span className="font-medium text-foreground">{draft.driver.repository}</span>
                                </p>
                              ) : null}
                              {hasSingleDiscoveredRepository && draft.driver.repository.trim() !== "" ? (
                                <p className="text-xs text-emerald-300">
                                  MAPPO auto-selected the only repository returned by Azure DevOps.
                                </p>
                              ) : null}
                            </div>
                          </div>
                          {repositoryDiscoveryError ? (
                            <p className="mt-3 rounded-md border border-amber-400/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-100">
                              {repositoryDiscoveryError}
                            </p>
                          ) : null}
                        </div>

                        <div className="rounded-md border border-border/70 bg-background/60 p-3">
                          <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
                            <div>
                              <h4 className="text-sm font-semibold text-foreground">Azure DevOps Pipeline</h4>
                              <p className="mt-1 text-xs text-muted-foreground">
                                Choose the pipeline MAPPO should trigger when operators start a deployment run. MAPPO loads pipelines from the selected Azure DevOps project automatically and auto-selects the only match when there is one.
                              </p>
                            </div>
                            <Button
                              id="driver-pipeline-discovery-action"
                              type="button"
                              variant="outline"
                              disabled={isDiscoveringPipelines || !canDiscoverAzureDevOpsProjectResources}
                              onClick={() => {
                                void discoverAdoPipelines();
                              }}
                            >
                              {isDiscoveringPipelines
                                ? "Loading..."
                                : pipelineDiscoveryError
                                  ? "Retry"
                                  : discoveredPipelines.length > 0
                                    ? "Reload pipelines"
                                    : "Load pipelines"}
                            </Button>
                          </div>
                          <p className="mt-2 text-xs text-muted-foreground">
                            MAPPO starts this automatically after you choose an Azure DevOps project. Use <span className="font-medium text-foreground">Reload pipelines</span> only if that project changed recently.
                          </p>
                          <div className="mt-3 space-y-1">
                            <div className="flex items-center gap-1">
                              <Label htmlFor="driver-pipeline-select">Azure DevOps Pipeline</Label>
                              <FieldHelpTooltip content="Pipeline MAPPO queues when an operator starts a deployment run for this project." />
                            </div>
                            {discoveredPipelines.length > 0 ? (
                              <Select
                                value={selectedDiscoveredPipelineId}
                                onValueChange={(value) => {
                                  if (value === "__none") {
                                    setDraft((current) => ({
                                      ...current,
                                      driver: { ...current.driver, pipelineId: "" },
                                    }));
                                    return;
                                  }
                                  setDraft((current) => ({
                                    ...current,
                                    driver: { ...current.driver, pipelineId: value },
                                  }));
                                }}
                              >
                                <SelectTrigger id="driver-pipeline-select">
                                  <SelectValue placeholder="Select discovered pipeline" />
                                </SelectTrigger>
                                <SelectContent>
                                  <SelectItem value="__none">Select discovered pipeline</SelectItem>
                                  {discoveredPipelines.map((pipeline) => (
                                    <SelectItem key={`${pipeline.id}-${pipeline.name}`} value={pipeline.id}>
                                      {pipeline.name}
                                    </SelectItem>
                                  ))}
                                </SelectContent>
                              </Select>
                            ) : (
                              <p className="rounded-md border border-dashed border-border/60 px-3 py-2 text-xs text-muted-foreground">
                                {isDiscoveringPipelines
                                  ? "Loading Azure DevOps pipelines from the selected project..."
                                : "MAPPO has not loaded any pipelines from Azure DevOps for this project yet. If the selected Azure DevOps project definitely has pipelines, use Reload pipelines above."}
                              </p>
                            )}
                            {hasSingleDiscoveredPipeline && draft.driver.pipelineId.trim() !== "" ? (
                              <p className="text-xs text-emerald-300">
                                MAPPO auto-selected the only pipeline returned by Azure DevOps.
                              </p>
                            ) : null}
                          </div>
                          {pipelineDiscoveryError ? (
                            <p className="mt-3 rounded-md border border-amber-400/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-100">
                              {pipelineDiscoveryError}
                            </p>
                          ) : null}
                        </div>

                        <div className="rounded-md border border-border/70 bg-background/60 p-3">
                          <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
                            <div>
                              <h4 className="text-sm font-semibold text-foreground">Azure Service Connection</h4>
                              <p className="mt-1 text-xs text-muted-foreground">
                                Choose the Azure service connection the selected pipeline uses to authenticate to Azure. MAPPO loads service connections from the selected Azure DevOps project automatically and auto-selects the only match when there is one.
                              </p>
                            </div>
                            <Button
                              type="button"
                              variant="outline"
                              disabled={isDiscoveringServiceConnections || !canDiscoverAzureDevOpsProjectResources}
                              onClick={() => {
                                void discoverAdoServiceConnections();
                              }}
                            >
                              {isDiscoveringServiceConnections
                                ? "Loading..."
                                : serviceConnectionDiscoveryError
                                  ? "Retry"
                                  : discoveredServiceConnections.length > 0
                                    ? "Reload service connections"
                                    : "Load service connections"}
                            </Button>
                          </div>
                          <p className="mt-2 text-xs text-muted-foreground">
                            MAPPO starts this automatically after you choose an Azure DevOps project. Use <span className="font-medium text-foreground">Reload service connections</span> only if that project changed recently. If Azure DevOps still returns none, use the manual fallback below.
                          </p>
                          <div className="mt-3 space-y-1">
                            <div className="flex items-center gap-1">
                                <Label htmlFor="driver-service-connection">Azure Service Connection</Label>
                                <FieldHelpTooltip content="Azure DevOps service connection the selected pipeline uses when it deploys into Azure for this MAPPO project." />
                              </div>
                            {discoveredServiceConnections.length > 0 ? (
                              <Select
                                value={selectedDiscoveredServiceConnectionId}
                                onValueChange={(value) => {
                                  if (value === "__none") {
                                    setDraft((current) => ({
                                      ...current,
                                      driver: { ...current.driver, azureServiceConnectionName: "" },
                                    }));
                                    return;
                                  }
                                  const selected = discoveredServiceConnections.find((connection) => connection.id === value);
                                  if (!selected) {
                                    return;
                                  }
                                  setDraft((current) => ({
                                    ...current,
                                    driver: {
                                      ...current.driver,
                                      azureServiceConnectionName: selected.name,
                                    },
                                  }));
                                }}
                              >
                                <SelectTrigger id="driver-service-connection">
                                  <SelectValue placeholder="Select discovered service connection" />
                                </SelectTrigger>
                                <SelectContent>
                                  <SelectItem value="__none">Select discovered service connection</SelectItem>
                                  {discoveredServiceConnections.map((connection) => (
                                    <SelectItem key={connection.id} value={connection.id}>
                                      {connection.name}
                                    </SelectItem>
                                  ))}
                                </SelectContent>
                              </Select>
                            ) : (
                              <>
                                <p className="rounded-md border border-dashed border-border/60 px-3 py-2 text-xs text-muted-foreground">
                                  {isDiscoveringServiceConnections
                                    ? "Loading Azure service connections from the selected project..."
                                  : "Azure DevOps did not return any service connections for this project. If that is expected in your environment, use the manual fallback below. Otherwise use Reload service connections above."}
                                </p>
                                {canUseManualServiceConnectionName ? (
                                  <Accordion type="single" collapsible className="rounded-md border border-border/60 bg-background/40 px-3">
                                    <AccordionItem value="manual-service-connection" className="border-none">
                                      <AccordionTrigger className="py-2 text-sm font-medium text-foreground hover:no-underline">
                                        Manual fallback
                                      </AccordionTrigger>
                                      <AccordionContent className="space-y-2 pt-1">
                                        <p className="text-xs text-muted-foreground">
                                          Use this only if Azure DevOps does not expose service connections to MAPPO. Enter the Azure DevOps service connection name exactly as it appears in the Azure DevOps project.
                                        </p>
                                        <Input
                                          id="driver-service-connection"
                                          value={draft.driver.azureServiceConnectionName}
                                          onChange={(event) =>
                                            setDraft((current) => ({
                                              ...current,
                                              driver: {
                                                ...current.driver,
                                                azureServiceConnectionName: event.target.value,
                                              },
                                            }))
                                          }
                                          placeholder="For example mappo-ado-demo-rg-contributor"
                                        />
                                      </AccordionContent>
                                    </AccordionItem>
                                  </Accordion>
                                ) : null}
                              </>
                            )}
                            {hasSingleDiscoveredServiceConnection && draft.driver.azureServiceConnectionName.trim() !== "" ? (
                              <p className="text-xs text-emerald-300">
                                MAPPO auto-selected the only service connection returned by Azure DevOps.
                              </p>
                            ) : null}
                          </div>
                          {serviceConnectionDiscoveryError ? (
                            <p className="mt-3 rounded-md border border-amber-400/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-100">
                              {serviceConnectionDiscoveryError}
                            </p>
                          ) : null}
                        </div>
                      </>
                    ) : null}
                  </div>
                ) : (
                  <div className="space-y-3">
                    <div className="rounded-md border border-border/70 bg-background/40 p-3">
                      <p className="text-sm font-semibold text-foreground">Direct Azure rollout</p>
                      <p className="mt-2 text-xs text-muted-foreground">
                        MAPPO updates each selected target directly in Azure using that target&apos;s Deployment Stack.
                      </p>
                      <p className="mt-2 text-xs text-muted-foreground">
                        No deployment connection is required for this mode because MAPPO talks to Azure directly.
                      </p>
                    </div>
                    <div className="flex justify-end">
                      <Button
                        type="button"
                        variant="outline"
                        onClick={() => {
                          void runValidation(["credentials"]);
                        }}
                        disabled={!project?.id || isValidating}
                      >
                        Check Azure access
                      </Button>
                    </div>
                  </div>
                )}

              </div>
            </TabsContent>

            <TabsContent value="runtime-health" className="space-y-3">
              <h3 className="text-sm font-semibold uppercase tracking-[0.08em] text-muted-foreground">Runtime Health</h3>
              <div className="rounded-md border border-border/70 bg-background/60 p-3">
                <p className="text-sm font-semibold text-foreground">Health check type</p>
                <p className="mt-2 text-sm text-foreground">HTTP endpoint</p>
                <p className="mt-2 text-xs text-muted-foreground">
                  MAPPO checks a target by calling its HTTP health endpoint and verifying the expected status code.
                </p>
              </div>
              <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                <div className="space-y-1">
                  <div className="flex items-center gap-1">
                    <Label htmlFor="runtime-path">Path</Label>
                    <FieldHelpTooltip content="HTTP path to check on each target runtime endpoint (for example /health)." />
                  </div>
                  <Input
                    id="runtime-path"
                    value={draft.runtime.path}
                    onChange={(event) =>
                      setDraft((current) => ({
                        ...current,
                        runtime: { ...current.runtime, path: event.target.value },
                      }))
                    }
                  />
                </div>
                <div className="space-y-1">
                  <div className="flex items-center gap-1">
                    <Label htmlFor="runtime-status-code">Expected status</Label>
                    <FieldHelpTooltip content="Expected HTTP status code for healthy response (usually 200)." />
                  </div>
                  <Input
                    id="runtime-status-code"
                    value={draft.runtime.expectedStatus}
                    onChange={(event) =>
                      setDraft((current) => ({
                        ...current,
                        runtime: { ...current.runtime, expectedStatus: event.target.value },
                      }))
                    }
                  />
                </div>
                <div className="space-y-1">
                  <div className="flex items-center gap-1">
                    <Label htmlFor="runtime-timeout">Timeout ms</Label>
                    <FieldHelpTooltip content="Max wait time (milliseconds) for runtime health response before marking check as failed." />
                  </div>
                  <Input
                    id="runtime-timeout"
                    value={draft.runtime.timeoutMs}
                    onChange={(event) =>
                      setDraft((current) => ({
                        ...current,
                        runtime: { ...current.runtime, timeoutMs: event.target.value },
                      }))
                    }
                  />
                </div>
              </div>
            </TabsContent>

              </Tabs>
            </div>
            <Card className="h-fit border-border/70 bg-background/50 xl:sticky xl:top-4">
              <CardHeader className="pb-2">
                <CardTitle className="text-sm uppercase tracking-[0.08em]">Setup Summary</CardTitle>
                <p className="text-xs text-muted-foreground">Operator-facing summary of the current project configuration.</p>
              </CardHeader>
              <CardContent className="pt-0">
                <div className="space-y-4 text-xs">
                  <div className="space-y-2">
                    <p className="text-[11px] uppercase tracking-[0.08em] text-muted-foreground">Release source</p>
                    <dl className="space-y-1">
                      <div className="flex justify-between gap-3">
                        <dt className="text-muted-foreground">Provider</dt>
                        <dd className="text-right text-foreground">{RELEASE_SYSTEM_LABELS[effectiveReleaseSystem]}</dd>
                      </div>
                      <div className="flex justify-between gap-3">
                        <dt className="text-muted-foreground">Type</dt>
                        <dd className="text-right text-foreground">{releaseSourceLabel}</dd>
                      </div>
                      <div className="flex justify-between gap-3">
                        <dt className="text-muted-foreground">Linked source</dt>
                        <dd className="text-right text-foreground">
                          {selectedReleaseIngestEndpoint?.name || selectedReleaseIngestEndpoint?.id || "Not linked"}
                        </dd>
                      </div>
                    </dl>
                  </div>

                  <div className="space-y-2">
                    <p className="text-[11px] uppercase tracking-[0.08em] text-muted-foreground">Deployment</p>
                    <dl className="space-y-1">
                      <div className="flex justify-between gap-3">
                        <dt className="text-muted-foreground">System</dt>
                        <dd className="text-right text-foreground">{DEPLOYMENT_SYSTEM_LABELS[selectedDeploymentSystem]}</dd>
                      </div>
                      <div className="flex justify-between gap-3">
                        <dt className="text-muted-foreground">Method</dt>
                        <dd className="text-right text-foreground">{DEPLOYMENT_DRIVER_LABELS[draft.deploymentDriver]}</dd>
                      </div>
                      {draft.deploymentDriver === "pipeline_trigger" ? (
                        <>
                          <div className="flex justify-between gap-3">
                            <dt className="text-muted-foreground">Deployment connection</dt>
                            <dd className="text-right text-foreground">
                              {selectedProviderConnection?.name || selectedProviderConnection?.id || "Not linked"}
                            </dd>
                          </div>
                          <div className="flex justify-between gap-3">
                            <dt className="text-muted-foreground">Azure DevOps project</dt>
                            <dd className="text-right text-foreground">
                              {selectedDiscoveredAdoProject?.name || resolvedAdoProject || "Not selected"}
                            </dd>
                          </div>
                          <div className="flex justify-between gap-3">
                            <dt className="text-muted-foreground">Repository</dt>
                            <dd className="text-right text-foreground">{draft.driver.repository || "Not selected"}</dd>
                          </div>
                          <div className="flex justify-between gap-3">
                            <dt className="text-muted-foreground">Pipeline</dt>
                            <dd className="text-right text-foreground">
                              {discoveredPipelines.find((pipeline) => pipeline.id === draft.driver.pipelineId)?.name ||
                                draft.driver.pipelineId ||
                                "Not selected"}
                            </dd>
                          </div>
                          <div className="flex justify-between gap-3">
                            <dt className="text-muted-foreground">Service connection</dt>
                            <dd className="text-right text-foreground">
                              {draft.driver.azureServiceConnectionName || "Not selected"}
                            </dd>
                          </div>
                          <div className="flex justify-between gap-3">
                            <dt className="text-muted-foreground">Branch</dt>
                            <dd className="text-right text-foreground">{draft.driver.branch || "main"}</dd>
                          </div>
                        </>
                      ) : null}
                    </dl>
                  </div>

                  <div className="space-y-2">
                    <p className="text-[11px] uppercase tracking-[0.08em] text-muted-foreground">Access and health</p>
                    <dl className="space-y-1">
                      <div className="flex justify-between gap-3">
                        <dt className="text-muted-foreground">Access model</dt>
                        <dd className="text-right text-foreground">{ACCESS_STRATEGY_LABELS[draft.accessStrategy]}</dd>
                      </div>
                      <div className="flex justify-between gap-3">
                        <dt className="text-muted-foreground">Runtime check</dt>
                        <dd className="text-right text-foreground">{RUNTIME_HEALTH_LABELS[draft.runtimeHealthProvider]}</dd>
                      </div>
                      <div className="flex justify-between gap-3">
                        <dt className="text-muted-foreground">Health path</dt>
                        <dd className="text-right text-foreground">{draft.runtime.path || "/"}</dd>
                      </div>
                      <div className="flex justify-between gap-3">
                        <dt className="text-muted-foreground">Expected status</dt>
                        <dd className="text-right text-foreground">{draft.runtime.expectedStatus || "200"}</dd>
                      </div>
                      <div className="flex justify-between gap-3">
                        <dt className="text-muted-foreground">Timeout</dt>
                        <dd className="text-right text-foreground">{draft.runtime.timeoutMs || "5000"} ms</dd>
                      </div>
                    </dl>
                  </div>

                </div>
              </CardContent>
            </Card>
          </div>
        </CardContent>
      </Card>

      <Drawer direction="top" open={createDrawerOpen} onOpenChange={setCreateDrawerOpen}>
        <DrawerContent className="glass-card">
          <DrawerHeader>
            <DrawerTitle>Create Project</DrawerTitle>
            <DrawerDescription>
              Add a new project profile without scripts. You can refine driver/access/runtime details after creation.
            </DrawerDescription>
          </DrawerHeader>
          <div className="max-h-[70vh] overflow-y-auto px-4 pb-2">
            <form id="project-create-form" className="grid grid-cols-1 gap-3 md:grid-cols-2" onSubmit={handleCreateProject}>
              <div className="space-y-1">
                <Label htmlFor="create-project-id">Project ID</Label>
                <Input
                  id="create-project-id"
                  value={createDraft.id}
                  onChange={(event) => updateCreateDraft("id", event.target.value)}
                  placeholder="azure-appservice-ado-pipeline"
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="create-project-name">Project name</Label>
                <Input
                  id="create-project-name"
                  value={createDraft.name}
                  onChange={(event) => updateCreateDraft("name", event.target.value)}
                  placeholder="Azure App Service ADO Pipeline"
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="create-access-strategy">Access strategy</Label>
                <Input
                  id="create-access-strategy"
                  value={ACCESS_STRATEGY_LABELS.azure_workload_rbac}
                  disabled
                />
                <p className="text-xs text-muted-foreground">
                  New projects start with MAPPO using its Azure permissions. You can review this later in Project → Config.
                </p>
              </div>
              <div className="space-y-1">
                <Label htmlFor="create-deployment-system">Deployment system</Label>
                <Select
                  value={createSelectedDeploymentSystem}
                  onValueChange={(value) =>
                    updateCreateDeploymentSystem(value as DeploymentSystem)
                  }
                >
                  <SelectTrigger id="create-deployment-system">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="azure">{DEPLOYMENT_SYSTEM_LABELS.azure}</SelectItem>
                    <SelectItem value="azure_devops">{DEPLOYMENT_SYSTEM_LABELS.azure_devops}</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1">
                <Label htmlFor="create-deployment-method">Deployment method</Label>
                <Input
                  id="create-deployment-method"
                  value={DEPLOYMENT_DRIVER_LABELS[createDraft.deploymentDriver]}
                  disabled
                />
                <p className="text-xs text-muted-foreground">
                  {DEPLOYMENT_DRIVER_HELP[createDraft.deploymentDriver]}
                </p>
              </div>
              <div className="space-y-1">
                <Label htmlFor="create-release-provider">Release provider</Label>
                <Select
                  value={createSelectedReleaseSystem}
                  onValueChange={(value) =>
                    updateCreateReleaseSystem(value as ReleaseSystem)
                  }
                  disabled={createDraft.deploymentDriver === "pipeline_trigger"}
                >
                  <SelectTrigger id="create-release-provider">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="github">{RELEASE_SYSTEM_LABELS.github}</SelectItem>
                    <SelectItem value="azure_devops">{RELEASE_SYSTEM_LABELS.azure_devops}</SelectItem>
                  </SelectContent>
                </Select>
                <p className="text-xs text-muted-foreground">
                  {createDraft.deploymentDriver === "pipeline_trigger"
                    ? "Pipeline-driven rollout always expects Azure DevOps release events."
                    : "Choose which provider MAPPO should check for new versions for this project."}
                </p>
              </div>
              <div className="space-y-1">
                <Label htmlFor="create-release-type">Release source type</Label>
                <Input
                  id="create-release-type"
                  value={RELEASE_SOURCE_TYPE_LABELS[createDraft.releaseArtifactSource]}
                  disabled
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="create-runtime-provider">Runtime health check</Label>
                <Input
                  id="create-runtime-provider"
                  value={RUNTIME_HEALTH_LABELS.http_endpoint}
                  disabled
                />
                <p className="text-xs text-muted-foreground">
                  New projects start with a standard HTTP endpoint health check. You can refine the path and status code later in Project → Config.
                </p>
              </div>
            </form>
          </div>
          <DrawerFooter>
            <Button form="project-create-form" type="submit" disabled={!canCreateProject || createSubmitting}>
              {createSubmitting ? "Creating..." : "Create Project"}
            </Button>
            <DrawerClose asChild>
              <Button variant="outline">Cancel</Button>
            </DrawerClose>
          </DrawerFooter>
        </DrawerContent>
      </Drawer>
    </section>
  );
}
