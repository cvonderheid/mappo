import { FormEvent, useEffect, useMemo, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { toast } from "sonner";

import DataTablePagination from "@/components/DataTablePagination";
import FieldHelpTooltip from "@/components/FieldHelpTooltip";
import { Badge } from "@/components/ui/badge";
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
  discoverProviderConnectionAdoProjects,
  listProviderConnections,
  listReleaseIngestEndpoints,
} from "@/lib/api";
import type {
  DiscoverProjectAdoRepositoriesRequest,
  DiscoverProjectAdoServiceConnectionsRequest,
  DiscoverProjectAdoPipelinesRequest,
  ListProjectAuditQuery,
  PageMetadata,
  ProjectAdoPipeline,
  ProjectAdoPipelineDiscoveryResult,
  ProjectAdoRepository,
  ProjectAdoRepositoryDiscoveryResult,
  ProjectAdoServiceConnection,
  ProjectAdoServiceConnectionDiscoveryResult,
  ProjectConfigurationAuditAction,
  ProjectConfigurationAuditPage,
  ProjectConfigurationPatchRequest,
  ProjectCreateRequest,
  ProjectDefinition,
  ProviderConnection,
  ProviderConnectionAdoProject,
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
  onListProjectAudit: (
    projectId: string,
    query: ListProjectAuditQuery
  ) => Promise<ProjectConfigurationAuditPage>;
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
  | "access-identity"
  | "target-contract"
  | "runtime-health"
  | "validation"
  | "audit";

type ValidationScope = "credentials" | "webhook" | "target_contract";

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
  release: {
    templateUriField: string;
    versionRefField: string;
  };
  runtime: {
    path: string;
    expectedStatus: string;
    timeoutMs: string;
  };
};

type AuditItem = NonNullable<ProjectConfigurationAuditPage["items"]>[number];
type ValidationFinding = NonNullable<ProjectValidationResult["findings"]>[number];

type TargetContractField = {
  key: string;
  label: string;
  description: string;
};

type DraftValidationIssue = {
  id: string;
  tab: ProjectTab;
  fieldId: string;
  message: string;
};

const PROJECT_TABS: { key: ProjectTab; label: string }[] = [
  { key: "general", label: "General" },
  { key: "release-ingest", label: "Release Ingest" },
  { key: "deployment-driver", label: "Deployment Driver" },
  { key: "access-identity", label: "Access & Identity" },
  { key: "target-contract", label: "Target Contract" },
  { key: "runtime-health", label: "Runtime Health" },
  { key: "validation", label: "Validation" },
  { key: "audit", label: "Audit" },
];

const RELEASE_SOURCE_OPTIONS: Array<{
  value: ProjectDraft["releaseArtifactSource"];
  label: string;
}> = [
  { value: "blob_arm_template", label: "Blob-hosted ARM Template" },
  { value: "external_deployment_inputs", label: "Webhook / Pipeline Event" },
];

const DEFAULT_EXTERNAL_INPUT_SOURCE_SYSTEM = "azure_devops";
const DEFAULT_EXTERNAL_INPUT_DESCRIPTOR_PATH = "pipelineInputs";
const DEFAULT_EXTERNAL_INPUT_VERSION_FIELD = "artifactVersion";

const DRIVER_CAPABILITIES: Record<ProjectDraft["deploymentDriver"], { preview: boolean; cancel: boolean; externalLogs: boolean; externalHandle: boolean }> = {
  azure_deployment_stack: {
    preview: true,
    cancel: false,
    externalLogs: true,
    externalHandle: true,
  },
  azure_template_spec: {
    preview: false,
    cancel: false,
    externalLogs: true,
    externalHandle: false,
  },
  pipeline_trigger: {
    preview: false,
    cancel: false,
    externalLogs: true,
    externalHandle: true,
  },
};

const TARGET_CONTRACTS: Record<ProjectDraft["deploymentDriver"], { required: TargetContractField[]; optional: TargetContractField[] }> = {
  azure_deployment_stack: {
    required: [
      {
        key: "managedResourceGroupId",
        label: "Managed resource group",
        description: "Resource group MAPPO updates when it applies the deployment stack.",
      },
      {
        key: "deploymentStackName",
        label: "Deployment stack name",
        description: "Deployment Stack resource name MAPPO should update in the target subscription.",
      },
      {
        key: "containerAppResourceId",
        label: "Container App resource ID",
        description: "Runtime resource MAPPO probes after deployment to confirm health.",
      },
    ],
    optional: [
      {
        key: "registryAuthMode",
        label: "Registry auth mode",
        description: "How the target authenticates to the container registry, if the deployment needs registry credentials.",
      },
      {
        key: "registryServer",
        label: "Registry server",
        description: "Container registry hostname used by the target workload.",
      },
      {
        key: "registryUsername",
        label: "Registry username",
        description: "Username value for registry authentication when username/password auth is used.",
      },
      {
        key: "registryPasswordSecretName",
        label: "Registry password secret name",
        description: "Secret name that stores the registry password in the target environment.",
      },
    ],
  },
  azure_template_spec: {
    required: [
      {
        key: "managedResourceGroupId",
        label: "Managed resource group",
        description: "Resource group MAPPO updates when it applies the template spec release.",
      },
      {
        key: "containerAppResourceId",
        label: "Container App resource ID",
        description: "Runtime resource MAPPO probes after deployment to confirm health.",
      },
    ],
    optional: [
      {
        key: "managedApplicationId",
        label: "Managed application ID",
        description: "Marketplace managed application resource that owns the target deployment.",
      },
      {
        key: "registryAuthMode",
        label: "Registry auth mode",
        description: "How the target authenticates to the container registry, if needed.",
      },
      {
        key: "registryServer",
        label: "Registry server",
        description: "Container registry hostname used by the target workload.",
      },
    ],
  },
  pipeline_trigger: {
    required: [
      {
        key: "executionConfig.resourceGroup",
        label: "Target resource group",
        description: "Azure resource group containing the App Service deployment target.",
      },
      {
        key: "executionConfig.appServiceName",
        label: "App Service name",
        description: "App Service that the Azure DevOps pipeline should deploy into.",
      },
    ],
    optional: [
      {
        key: "executionConfig.slotName",
        label: "Deployment slot",
        description: "Optional App Service slot name if releases should target a slot instead of production.",
      },
      {
        key: "executionConfig.healthPath",
        label: "Health path override",
        description: "Optional per-target runtime health path if it differs from the project default.",
      },
      {
        key: "executionConfig.pipelineVariables",
        label: "Pipeline variables",
        description: "Optional extra Azure DevOps pipeline variables for this target.",
      },
    ],
  },
};

const DEPLOYMENT_DRIVER_LABELS: Record<ProjectDraft["deploymentDriver"], string> = {
  azure_deployment_stack: "Azure Deployment Stack",
  azure_template_spec: "Azure Template Spec",
  pipeline_trigger: "Pipeline Trigger",
};

const ACCESS_STRATEGY_LABELS: Record<ProjectDraft["accessStrategy"], string> = {
  azure_workload_rbac: "Azure Workload RBAC",
  lighthouse_delegated_access: "Lighthouse Delegated Access",
  simulator: "Simulator",
};

const RUNTIME_HEALTH_LABELS: Record<ProjectDraft["runtimeHealthProvider"], string> = {
  azure_container_app_http: "Azure Container App HTTP",
  http_endpoint: "HTTP Endpoint",
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
    release: {
      templateUriField: asString(releaseConfig.templateUriField, "templateUri"),
      versionRefField: asString(releaseConfig.versionRefField, "templateSpecVersion"),
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
    releaseArtifactSourceConfig.templateUriField = draft.release.templateUriField.trim() || "templateUri";
  }
  if (effectiveReleaseArtifactSource === "external_deployment_inputs") {
    releaseArtifactSourceConfig.sourceSystem = DEFAULT_EXTERNAL_INPUT_SOURCE_SYSTEM;
    releaseArtifactSourceConfig.descriptorPath = DEFAULT_EXTERNAL_INPUT_DESCRIPTOR_PATH;
    releaseArtifactSourceConfig.versionField = DEFAULT_EXTERNAL_INPUT_VERSION_FIELD;
  }
  if (effectiveReleaseArtifactSource === "template_spec_resource") {
    releaseArtifactSourceConfig.versionRefField = draft.release.versionRefField.trim() || "templateSpecVersion";
  }

  const runtimeHealthProviderConfig: Record<string, unknown> = {
    path: draft.runtime.path.trim() || "/",
    expectedStatus: parseOptionalNumber(draft.runtime.expectedStatus),
    timeoutMs: parseOptionalNumber(draft.runtime.timeoutMs),
  };

  return {
    name: draft.name.trim(),
    releaseIngestEndpointId: draft.releaseIngestEndpointId.trim() || undefined,
    providerConnectionId: draft.providerConnectionId.trim() || undefined,
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
  onListProjectAudit,
  onDiscoverAdoPipelines,
  onDiscoverAdoRepositories,
  onDiscoverAdoServiceConnections,
}: ProjectSettingsPageProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<ProjectTab>("general");
  const [draft, setDraft] = useState<ProjectDraft>(() => projectToDraft(project));
  const [isSaving, setIsSaving] = useState(false);
  const [isPublishing, setIsPublishing] = useState(false);
  const [isValidating, setIsValidating] = useState(false);
  const [validationTargetId, setValidationTargetId] = useState<string>("");
  const [validationResult, setValidationResult] = useState<ProjectValidationResult | null>(null);
  const [auditPage, setAuditPage] = useState<ProjectConfigurationAuditPage | null>(null);
  const [auditPageIndex, setAuditPageIndex] = useState<number>(0);
  const [auditPageSize, setAuditPageSize] = useState<number>(10);
  const [auditActionFilter, setAuditActionFilter] = useState<ProjectConfigurationAuditAction | "all">("all");
  const [auditLoading, setAuditLoading] = useState(false);
  const [createDrawerOpen, setCreateDrawerOpen] = useState(false);
  const [createSubmitting, setCreateSubmitting] = useState(false);
  const [isDiscoveringRepositories, setIsDiscoveringRepositories] = useState(false);
  const [isDiscoveringPipelines, setIsDiscoveringPipelines] = useState(false);
  const [isDiscoveringServiceConnections, setIsDiscoveringServiceConnections] = useState(false);
  const [isDiscoveringAdoProjects, setIsDiscoveringAdoProjects] = useState(false);
  const [repositoryDiscoveryError, setRepositoryDiscoveryError] = useState("");
  const [pipelineDiscoveryError, setPipelineDiscoveryError] = useState("");
  const [serviceConnectionDiscoveryError, setServiceConnectionDiscoveryError] = useState("");
  const [adoProjectDiscoveryError, setAdoProjectDiscoveryError] = useState("");
  const [discoveredRepositories, setDiscoveredRepositories] = useState<ProjectAdoRepository[]>([]);
  const [discoveredPipelines, setDiscoveredPipelines] = useState<ProjectAdoPipeline[]>([]);
  const [discoveredServiceConnections, setDiscoveredServiceConnections] = useState<ProjectAdoServiceConnection[]>([]);
  const [discoveredAdoProjects, setDiscoveredAdoProjects] = useState<ProviderConnectionAdoProject[]>([]);
  const [releaseIngestEndpoints, setReleaseIngestEndpoints] = useState<ReleaseIngestEndpoint[]>([]);
  const [providerConnections, setProviderConnections] = useState<ProviderConnection[]>([]);
  const [isLoadingReleaseIngestEndpoints, setIsLoadingReleaseIngestEndpoints] = useState(false);
  const [isLoadingProviderConnections, setIsLoadingProviderConnections] = useState(false);
  const adoProjectDiscoveryKeyRef = useRef("");
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
    setValidationResult(null);
    setAuditPage(null);
    setAuditPageIndex(0);
    setDiscoveredRepositories([]);
    setDiscoveredPipelines([]);
    setDiscoveredServiceConnections([]);
    setDiscoveredAdoProjects([]);
    setRepositoryDiscoveryError("");
    setPipelineDiscoveryError("");
    setServiceConnectionDiscoveryError("");
    setAdoProjectDiscoveryError("");
    adoProjectDiscoveryKeyRef.current = "";
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
    if (!validationTargetId && targets.length > 0) {
      setValidationTargetId(targets[0]?.id ?? "");
    }
  }, [targets, validationTargetId]);

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

  useEffect(() => {
    if (draft.deploymentDriver !== "pipeline_trigger") {
      return;
    }
    if (draft.providerConnectionId.trim() !== "") {
      return;
    }
    const firstEnabledAdoConnection = providerConnections.find(
      (connection) =>
        (connection.provider ?? "").toLowerCase() === "azure_devops" &&
        (connection.enabled ?? true) &&
        (connection.id ?? "").trim() !== ""
    );
    if (!firstEnabledAdoConnection?.id) {
      return;
    }
    setDraft((current) => ({
      ...current,
      providerConnectionId: firstEnabledAdoConnection.id ?? "",
    }));
  }, [draft.deploymentDriver, draft.providerConnectionId, providerConnections]);

  useEffect(() => {
    if (draft.deploymentDriver !== "pipeline_trigger") {
      return;
    }
    if (draft.releaseIngestEndpointId.trim() !== "") {
      return;
    }
    const firstEnabledAdoEndpoint = releaseIngestEndpoints.find(
      (endpoint) =>
        (endpoint.provider ?? "").toLowerCase() === "azure_devops" &&
        (endpoint.enabled ?? true) &&
        (endpoint.id ?? "").trim() !== ""
    );
    if (!firstEnabledAdoEndpoint?.id) {
      return;
    }
    setDraft((current) => ({
      ...current,
      releaseIngestEndpointId: firstEnabledAdoEndpoint.id ?? "",
    }));
  }, [draft.deploymentDriver, draft.releaseIngestEndpointId, releaseIngestEndpoints]);

  const capabilities = DRIVER_CAPABILITIES[draft.deploymentDriver];
  const targetContract = TARGET_CONTRACTS[draft.deploymentDriver];
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
  const pipelineProviderConnections = useMemo(
    () =>
      sortedProviderConnections.filter(
        (connection) =>
          (connection.provider ?? "").toLowerCase() === "azure_devops" &&
          (connection.enabled ?? true)
      ),
    [sortedProviderConnections]
  );
  const selectedProviderConnectionIsAzureDevOps =
    (selectedProviderConnection?.provider ?? "").toLowerCase() === "azure_devops";
  const selectedDiscoveredAdoProjectId = useMemo(() => {
    const currentValue = draft.driver.project.trim();
    if (currentValue === "") {
      return "__none";
    }
    const matching = discoveredAdoProjects.find((projectOption) => projectOption.name === currentValue);
    return matching ? matching.id : "__none";
  }, [discoveredAdoProjects, draft.driver.project]);
  const selectedDiscoveredAdoProject = useMemo(() => {
    if (selectedDiscoveredAdoProjectId === "__none") {
      return null;
    }
    return discoveredAdoProjects.find((projectOption) => projectOption.id === selectedDiscoveredAdoProjectId) ?? null;
  }, [discoveredAdoProjects, selectedDiscoveredAdoProjectId]);
  const resolvedAdoOrganization = draft.driver.organization.trim();
  const resolvedAdoProject = draft.driver.project.trim();
  const selectedAdoProjectUrl = useMemo(() => {
    if (selectedDiscoveredAdoProject?.webUrl?.trim()) {
      return selectedDiscoveredAdoProject.webUrl.trim();
    }
    if (resolvedAdoOrganization !== "" && resolvedAdoProject !== "") {
      return `${resolvedAdoOrganization}/${encodeURIComponent(resolvedAdoProject)}`;
    }
    return "";
  }, [resolvedAdoOrganization, resolvedAdoProject, selectedDiscoveredAdoProject]);
  const providerConnectionOptions = useMemo(() => {
    if (draft.deploymentDriver !== "pipeline_trigger") {
      return sortedProviderConnections;
    }
    const base = [...pipelineProviderConnections];
    if (
      selectedProviderConnection &&
      !selectedProviderConnectionIsAzureDevOps &&
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
    selectedProviderConnectionIsAzureDevOps,
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
  const releaseIngestEndpointOptions = useMemo(() => {
    if (draft.deploymentDriver !== "pipeline_trigger") {
      return sortedReleaseIngestEndpoints;
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
    sortedReleaseIngestEndpoints,
    pipelineReleaseIngestEndpoints,
    selectedReleaseIngestEndpoint,
    selectedReleaseIngestEndpointIsAzureDevOps,
  ]);
  const auditMetadata: PageMetadata = auditPage?.page ?? {
    page: auditPageIndex,
    size: auditPageSize,
    totalItems: 0,
    totalPages: 0,
  };
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

  useEffect(() => {
    setDiscoveredAdoProjects([]);
    setAdoProjectDiscoveryError("");
    adoProjectDiscoveryKeyRef.current = "";
    setDiscoveredRepositories([]);
    setDiscoveredPipelines([]);
    setDiscoveredServiceConnections([]);
    setRepositoryDiscoveryError("");
    setPipelineDiscoveryError("");
    setServiceConnectionDiscoveryError("");
    repositoryDiscoveryKeyRef.current = "";
    pipelineDiscoveryKeyRef.current = "";
    serviceConnectionDiscoveryKeyRef.current = "";
  }, [draft.providerConnectionId]);

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

  function normalizeDiscoveryError(message: string, providerLabel: string): string {
    const normalizedMessage = message.toLowerCase();
    if (normalizedMessage.includes("pat could not be resolved")) {
      return `${providerLabel} PAT could not be resolved. Open Admin → Provider Connections and verify the credential source for the selected connection.`;
    }
    if (normalizedMessage.includes("organization url is required on the provider connection")) {
      return `${providerLabel} project discovery is blocked because the selected Provider Connection is missing its Azure DevOps URL. Open Admin → Provider Connections and paste an Azure DevOps organization, project, or repo URL for that connection.`;
    }
    return message;
  }

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
          message: "Deployment Driver: linked Azure DevOps provider connection is required.",
        });
      } else if (
        selectedProviderConnection &&
        (selectedProviderConnection.provider ?? "").toLowerCase() !== "azure_devops"
      ) {
        issues.push({
          id: "driver-provider-connection-provider",
          tab: "deployment-driver",
          fieldId: "driver-provider-connection-id",
          message: "Deployment Driver: linked provider connection must use Azure DevOps.",
        });
      }
      if (draft.driver.organization.trim() === "" || draft.driver.project.trim() === "") {
        issues.push({
          id: "driver-project-required",
          tab: "deployment-driver",
          fieldId: "driver-project-select",
          message: "Deployment Driver: Azure DevOps project selection is required.",
        });
      }
      if (draft.driver.repository.trim() === "") {
        issues.push({
          id: "driver-repository-required",
          tab: "deployment-driver",
          fieldId: "driver-repository-select",
          message: "Deployment Driver: Azure DevOps repository is required.",
        });
      }
      if (draft.driver.pipelineId.trim() === "") {
        issues.push({
          id: "driver-pipeline-id-required",
          tab: "deployment-driver",
          fieldId: "driver-pipeline-select",
          message: "Deployment Driver: Azure DevOps pipeline is required.",
        });
      }
      if (draft.driver.azureServiceConnectionName.trim() === "") {
        issues.push({
          id: "driver-service-connection-required",
          tab: "deployment-driver",
          fieldId: "driver-service-connection",
          message: "Deployment Driver: Azure service connection is required.",
        });
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
  }, [draft, selectedProviderConnection]);

  const normalizedPayloadPreview = useMemo(
    () =>
      JSON.stringify(
        buildPatchRequest(draft),
        null,
        2
      ),
    [draft]
  );

  const configComplete = project !== null && draftValidationIssues.length === 0;
  const canPersist = project !== null && draftValidationIssues.length === 0;
  const canCreateProject = createDraft.id.trim() !== "" && createDraft.name.trim() !== "";
  const releaseSourceLabel =
    draft.deploymentDriver === "pipeline_trigger"
      ? "Webhook / Pipeline Event"
      : RELEASE_SOURCE_OPTIONS.find((option) => option.value === draft.releaseArtifactSource)?.label ??
        draft.releaseArtifactSource;

  async function refreshAudit(): Promise<void> {
    if (!project?.id) {
      setAuditPage(null);
      return;
    }
    setAuditLoading(true);
    try {
      const response = await onListProjectAudit(project.id, {
        page: auditPageIndex,
        size: auditPageSize,
        action: auditActionFilter === "all" ? undefined : auditActionFilter,
      });
      setAuditPage(response);
    } catch (error) {
      toast.error((error as Error).message);
    } finally {
      setAuditLoading(false);
    }
  }

  useEffect(() => {
    if (activeTab !== "audit") {
      return;
    }
    void refreshAudit();
  }, [activeTab, auditActionFilter, auditPageIndex, auditPageSize, project?.id]);

  async function persistDraft(mode: "save" | "publish"): Promise<void> {
    if (!project?.id || draftValidationIssues.length > 0) {
      return;
    }
    if (mode === "save") {
      setIsSaving(true);
    } else {
      setIsPublishing(true);
    }
    try {
      await onPatchProject(project.id, buildPatchRequest(draft));
      toast.success(mode === "save" ? "Project draft saved." : "Project configuration published.");
    } catch (error) {
      toast.error((error as Error).message);
    } finally {
      if (mode === "save") {
        setIsSaving(false);
      } else {
        setIsPublishing(false);
      }
    }
  }

  async function runValidation(scopes: ValidationScope[]): Promise<void> {
    if (!project?.id) {
      return;
    }
    setIsValidating(true);
    try {
      const response = await onValidateProject(project.id, {
        scopes,
        targetId: scopes.includes("target_contract") ? validationTargetId || undefined : undefined,
      });
      setValidationResult(response);
      if (response.valid) {
        toast.success("Validation passed.");
      } else {
        toast.warning("Validation completed with findings.");
      }
      setActiveTab("validation");
    } catch (error) {
      toast.error((error as Error).message);
    } finally {
      setIsValidating(false);
    }
  }

  async function discoverAdoProjects(options?: { silent?: boolean }): Promise<void> {
    const providerConnectionId = draft.providerConnectionId.trim();
    if (providerConnectionId === "") {
      return;
    }
    setIsDiscoveringAdoProjects(true);
    setAdoProjectDiscoveryError("");
    try {
      const response = await discoverProviderConnectionAdoProjects(providerConnectionId);
      const projects = [...(response.projects ?? [])].sort((left, right) =>
        `${left.name ?? ""}`.localeCompare(`${right.name ?? ""}`, undefined, { sensitivity: "base" })
      );
      setDiscoveredAdoProjects(projects);
      setDraft((current) => {
        const currentProjectName = current.driver.project.trim();
        const currentSelection =
          projects.find((projectOption) => projectOption.name === currentProjectName) ??
          (currentProjectName === "" && projects.length === 1 ? projects[0] : undefined);
        if (!currentSelection) {
          return current;
        }
        if (
          current.driver.organization.trim() === response.organizationUrl &&
          current.driver.project.trim() === currentSelection.name
        ) {
          return current;
        }
        return {
          ...current,
          driver: {
            ...current.driver,
            organization: response.organizationUrl,
            project: currentSelection.name,
          },
        };
      });
      if (!options?.silent) {
        toast.success(
          `Discovered ${projects.length} Azure DevOps project${projects.length === 1 ? "" : "s"} for ${selectedProviderConnection?.name || providerConnectionId}.`
        );
      }
    } catch (error) {
      const message = normalizeDiscoveryError((error as Error).message, "Azure DevOps");
      setAdoProjectDiscoveryError(message);
      if (!options?.silent) {
        toast.error(message);
      }
    } finally {
      setIsDiscoveringAdoProjects(false);
    }
  }

  async function discoverAdoPipelines(options?: { silent?: boolean }): Promise<void> {
    if (!project?.id) {
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
      if (pipelines.length > 0 && draft.driver.pipelineId.trim() === "") {
        setDraft((current) => ({
          ...current,
          driver: { ...current.driver, pipelineId: pipelines[0]?.id ?? "" },
        }));
      }
      if (!options?.silent) {
        toast.success(
          `Discovered ${pipelines.length} pipeline${pipelines.length === 1 ? "" : "s"} in ${response.organization}/${response.project}.`
        );
      }
    } catch (error) {
      const message = (error as Error).message;
      const normalizedMessage = normalizeDiscoveryError(message, "Azure DevOps");
      setPipelineDiscoveryError(normalizedMessage);
      if (!options?.silent) {
        toast.error(normalizedMessage);
      }
    } finally {
      setIsDiscoveringPipelines(false);
    }
  }

  async function discoverAdoRepositories(options?: { silent?: boolean }): Promise<void> {
    if (!project?.id) {
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
      const selectedRepository =
        repositories.find(
          (repository) =>
            repository.name === draft.driver.repository.trim() || repository.id === draft.driver.repository.trim()
        ) ?? repositories[0];
      if (selectedRepository) {
        setDraft((current) => ({
          ...current,
          driver: {
            ...current.driver,
            repository:
              current.driver.repository.trim() === "" ? (selectedRepository.name ?? "") : current.driver.repository,
            branch:
              current.driver.branch.trim() === "" || current.driver.branch.trim() === "main"
                ? (selectedRepository.defaultBranch?.replace(/^refs\/heads\//, "") ?? current.driver.branch)
                : current.driver.branch,
          },
        }));
      }
      if (!options?.silent) {
        toast.success(
          `Discovered ${repositories.length} repositor${repositories.length === 1 ? "y" : "ies"} in ${response.organization}/${response.project}.`
        );
      }
    } catch (error) {
      const message = (error as Error).message;
      const normalizedMessage = normalizeDiscoveryError(message, "Azure DevOps");
      setRepositoryDiscoveryError(normalizedMessage);
      if (!options?.silent) {
        toast.error(normalizedMessage);
      }
    } finally {
      setIsDiscoveringRepositories(false);
    }
  }

  async function discoverAdoServiceConnections(options?: { silent?: boolean }): Promise<void> {
    if (!project?.id) {
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
      if (serviceConnections.length > 0 && draft.driver.azureServiceConnectionName.trim() === "") {
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
          `Discovered ${serviceConnections.length} service connection${serviceConnections.length === 1 ? "" : "s"} in ${response.organization}/${response.project}.`
        );
      }
    } catch (error) {
      const message = (error as Error).message;
      const normalizedMessage = normalizeDiscoveryError(message, "Azure DevOps");
      setServiceConnectionDiscoveryError(normalizedMessage);
      if (!options?.silent) {
        toast.error(normalizedMessage);
      }
    } finally {
      setIsDiscoveringServiceConnections(false);
    }
  }

  useEffect(() => {
    if (
      activeTab !== "deployment-driver" ||
      draft.deploymentDriver !== "pipeline_trigger" ||
      draft.driver.pipelineSystem !== "azure_devops" ||
      draft.providerConnectionId.trim() === ""
    ) {
      return;
    }

    const discoveryKey = draft.providerConnectionId.trim();
    if (adoProjectDiscoveryKeyRef.current === discoveryKey) {
      return;
    }
    adoProjectDiscoveryKeyRef.current = discoveryKey;
    void discoverAdoProjects({ silent: true });
  }, [
    activeTab,
    draft.deploymentDriver,
    draft.driver.pipelineSystem,
    draft.providerConnectionId,
  ]);

  useEffect(() => {
    if (
      activeTab !== "deployment-driver" ||
      draft.deploymentDriver !== "pipeline_trigger" ||
      draft.driver.pipelineSystem !== "azure_devops" ||
      draft.providerConnectionId.trim() === "" ||
      resolvedAdoOrganization === "" ||
      resolvedAdoProject === ""
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
    activeTab,
    draft.deploymentDriver,
    draft.driver.pipelineSystem,
    draft.providerConnectionId,
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
          <div className="grid gap-2 md:grid-cols-2">
            <div className="rounded-md border border-border/70 bg-background/50 p-2">
              <p className="text-[11px] uppercase tracking-[0.08em] text-muted-foreground">Selected project</p>
              <p className="mt-1 text-sm font-medium">{project?.name ?? "No project selected"}</p>
              {project?.id ? <p className="font-mono text-[11px] text-muted-foreground">{project.id}</p> : null}
            </div>
            <div className="rounded-md border border-border/70 bg-background/50 p-2">
              <p className="text-[11px] uppercase tracking-[0.08em] text-muted-foreground">Current progress</p>
              <p className="mt-1 text-sm text-muted-foreground">
                Targets: <span className="font-medium text-foreground">{targetCount}</span>
                {" · "}
                Releases: <span className="font-medium text-foreground">{projectReleaseCount}</span>
              </p>
            </div>
          </div>

          <div className="grid gap-2 md:grid-cols-2 xl:grid-cols-5">
            <div className="rounded-md border border-border/70 bg-background/40 p-2">
              <p className="text-xs font-medium">Project selected</p>
              <Badge className="mt-1" variant={project ? "default" : "secondary"}>
                {project ? "Done" : "Pending"}
              </Badge>
            </div>
            <div className="rounded-md border border-border/70 bg-background/40 p-2">
              <p className="text-xs font-medium">Config complete</p>
              <Badge className="mt-1" variant={configComplete ? "default" : "secondary"}>
                {project ? (configComplete ? "Done" : "Needs input") : "Pending"}
              </Badge>
            </div>
            <div className="rounded-md border border-border/70 bg-background/40 p-2">
              <p className="text-xs font-medium">Target onboarded</p>
              <Badge className="mt-1" variant={targetCount > 0 ? "default" : "secondary"}>
                {targetCount > 0 ? "Done" : "Pending"}
              </Badge>
            </div>
            <div className="rounded-md border border-border/70 bg-background/40 p-2">
              <p className="text-xs font-medium">Release ingested</p>
              <Badge className="mt-1" variant={projectReleaseCount > 0 ? "default" : "secondary"}>
                {projectReleaseCount > 0 ? "Done" : "Pending"}
              </Badge>
            </div>
            <div className="rounded-md border border-border/70 bg-background/40 p-2">
              <p className="text-xs font-medium">Ready to deploy</p>
              <Badge
                className="mt-1"
                variant={configComplete && targetCount > 0 && projectReleaseCount > 0 ? "default" : "secondary"}
              >
                {configComplete && targetCount > 0 && projectReleaseCount > 0 ? "Ready" : "Blocked"}
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
              Configure release ingest, deployment driver, identity model, runtime health, validation, and audit history.
            </p>
            {project?.id ? (
              <p className="font-mono text-xs text-muted-foreground">Selected project: {project.id}</p>
            ) : (
              <p className="font-mono text-xs text-muted-foreground">No project selected.</p>
            )}
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
            <Button type="button" variant="outline" onClick={() => void persistDraft("save")} disabled={!canPersist || isSaving}>
              {isSaving ? "Saving..." : "Save Draft"}
            </Button>
            <Button type="button" onClick={() => void persistDraft("publish")} disabled={!canPersist || isPublishing}>
              {isPublishing ? "Publishing..." : "Publish Config"}
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
                <TabsList className="grid h-auto w-full grid-cols-2 gap-1 bg-background/70 p-1 sm:grid-cols-4 xl:grid-cols-8">
                  {PROJECT_TABS.map((tab) => (
                    <TabsTrigger
                      key={tab.key}
                      value={tab.key}
                      className="h-auto whitespace-normal px-3 py-2 text-center text-xs leading-tight"
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
              <h3 className="text-sm font-semibold uppercase tracking-[0.08em] text-muted-foreground">Release Ingest</h3>
              <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                {draft.deploymentDriver === "pipeline_trigger" ? (
                  <div className="space-y-1">
                    <div className="flex items-center gap-1">
                      <Label htmlFor="release-source-derived">Release source</Label>
                      <FieldHelpTooltip content="Pipeline Trigger projects always ingest releases from webhook or pipeline events. Operators do not need to choose this manually." />
                    </div>
                    <div
                      id="release-source-derived"
                      className="rounded-md border border-border/70 bg-background/60 px-3 py-2 text-sm text-foreground"
                    >
                      Webhook / Pipeline Event
                    </div>
                    <p className="text-xs text-muted-foreground">
                      Derived automatically from <span className="font-medium text-foreground">Pipeline Trigger</span>.
                    </p>
                  </div>
                ) : (
                  <div className="space-y-1">
                    <div className="flex items-center gap-1">
                      <Label htmlFor="release-source-type">Release source</Label>
                      <FieldHelpTooltip content="Where MAPPO reads deployable versions from for this project." />
                    </div>
                    <Select
                      value={draft.releaseArtifactSource}
                      onValueChange={(value) =>
                        updateDraft("releaseArtifactSource", value as ProjectDraft["releaseArtifactSource"])
                      }
                    >
                      <SelectTrigger id="release-source-type">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        {RELEASE_SOURCE_OPTIONS.map((option) => (
                          <SelectItem key={option.value} value={option.value}>
                            {option.label}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>
                )}
                <div className="space-y-1 md:col-span-2">
                  <div className="flex items-center gap-1">
                    <Label htmlFor="release-ingest-endpoint-id">Linked release ingest endpoint</Label>
                    <FieldHelpTooltip content="Pick a global release-ingest endpoint from Admin > Release Ingest. That endpoint controls webhook auth and routing for this project." />
                  </div>
                  <div className="flex flex-col gap-2 md:flex-row">
                    <Select
                      value={draft.releaseIngestEndpointId.trim() === "" ? "__none" : draft.releaseIngestEndpointId}
                      onValueChange={(value) =>
                        updateDraft("releaseIngestEndpointId", value === "__none" ? "" : value)
                      }
                    >
                      <SelectTrigger id="release-ingest-endpoint-id" className="md:flex-1">
                        <SelectValue placeholder="Select endpoint" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="__none">No linked endpoint</SelectItem>
                        {releaseIngestEndpointOptions
                          .filter((endpoint) => (endpoint.id ?? "").trim() !== "")
                          .map((endpoint) => (
                          <SelectItem key={endpoint.id ?? endpoint.name} value={endpoint.id ?? ""}>
                            {endpoint.name || endpoint.id}
                            {draft.deploymentDriver === "pipeline_trigger" &&
                            (endpoint.provider ?? "").toLowerCase() !== "azure_devops"
                              ? " (incompatible provider)"
                              : ""}
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
                      {isLoadingReleaseIngestEndpoints ? "Refreshing..." : "Refresh"}
                    </Button>
                  </div>
                  {draft.releaseIngestEndpointId.trim() !== "" && selectedReleaseIngestEndpoint ? (
                    <p className="text-xs text-muted-foreground">
                      Using <span className="font-medium text-foreground">{selectedReleaseIngestEndpoint.name || selectedReleaseIngestEndpoint.id}</span>{" "}
                      from <span className="font-medium text-foreground">Admin → Release Ingest</span>.
                    </p>
                  ) : null}
                  {releaseIngestEndpointOptions.length === 0 ? (
                    <p className="text-xs text-muted-foreground">
                      No compatible endpoints found. Create one in{" "}
                      <span className="font-medium text-foreground">Admin → Release Ingest</span>.
                    </p>
                  ) : draft.releaseIngestEndpointId.trim() === "" ? (
                    <p className="text-xs text-muted-foreground">
                      Select the global release-ingest endpoint MAPPO should trust for this project.
                    </p>
                  ) : null}
                </div>
                {draft.releaseArtifactSource === "blob_arm_template" ? (
                  <div className="space-y-1">
                    <div className="flex items-center gap-1">
                      <Label htmlFor="release-template-uri-field">Template URI Field</Label>
                      <FieldHelpTooltip content="Payload field that contains the ARM template URI for blob-based release sources." />
                    </div>
                    <Input
                      id="release-template-uri-field"
                      value={draft.release.templateUriField}
                      onChange={(event) =>
                        setDraft((current) => ({
                          ...current,
                          release: { ...current.release, templateUriField: event.target.value },
                        }))
                      }
                    />
                  </div>
                ) : null}
                {draft.releaseArtifactSource === "template_spec_resource" ? (
                  <div className="space-y-1">
                    <div className="flex items-center gap-1">
                      <Label htmlFor="release-version-ref-field">Version Ref Field</Label>
                      <FieldHelpTooltip content="Payload field that points to the template spec version reference for template spec sources." />
                    </div>
                    <Input
                      id="release-version-ref-field"
                      value={draft.release.versionRefField}
                      onChange={(event) =>
                        setDraft((current) => ({
                          ...current,
                          release: { ...current.release, versionRefField: event.target.value },
                        }))
                      }
                    />
                  </div>
                ) : null}
              </div>
            </TabsContent>

            <TabsContent value="deployment-driver" className="space-y-3">
              <h3 className="text-sm font-semibold uppercase tracking-[0.08em] text-muted-foreground">Deployment Driver</h3>
              <div className="space-y-3">
                <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
                  <div className="space-y-1">
                    <div className="flex items-center gap-1">
                      <Label htmlFor="driver-type">Deployment driver</Label>
                      <FieldHelpTooltip content="Execution engine for this project. Pipeline Trigger delegates deployment to a CI/CD system instead of calling Azure resource APIs directly." />
                    </div>
                    <Select
                      value={draft.deploymentDriver}
                      onValueChange={(value) => {
                        const nextDriver = value as ProjectDraft["deploymentDriver"];
                        setDraft((current) => ({
                          ...current,
                          deploymentDriver: nextDriver,
                          releaseArtifactSource:
                            nextDriver === "pipeline_trigger"
                              ? "external_deployment_inputs"
                              : current.releaseArtifactSource,
                        }));
                      }}
                    >
                      <SelectTrigger id="driver-type">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="azure_deployment_stack">Azure Deployment Stack</SelectItem>
                        <SelectItem value="pipeline_trigger">Pipeline Trigger</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="space-y-1">
                    <div className="flex items-center gap-1">
                      <Label htmlFor="driver-pipeline-system">Pipeline system</Label>
                      <FieldHelpTooltip content="Pipeline provider MAPPO will talk to when the deployment driver is Pipeline Trigger." />
                    </div>
                    <Select
                      value={draft.driver.pipelineSystem || "azure_devops"}
                      onValueChange={(value) =>
                        setDraft((current) => ({
                          ...current,
                          driver: { ...current.driver, pipelineSystem: value },
                        }))
                      }
                      disabled={draft.deploymentDriver !== "pipeline_trigger"}
                    >
                      <SelectTrigger id="driver-pipeline-system">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="azure_devops">Azure DevOps</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>
                  <div className="space-y-1">
                    <div className="flex items-center gap-1">
                      <Label htmlFor="driver-provider-connection-id">Provider connection</Label>
                      <FieldHelpTooltip content="Admin-managed Azure DevOps API connection that MAPPO uses to discover projects, repositories, pipelines, and service connections for this project." />
                    </div>
                    <div className="flex flex-col gap-2">
                      <Select
                        value={draft.providerConnectionId.trim() === "" ? "__none" : draft.providerConnectionId}
                        onValueChange={(value) =>
                          updateDraft("providerConnectionId", value === "__none" ? "" : value)
                        }
                        disabled={draft.deploymentDriver !== "pipeline_trigger"}
                      >
                        <SelectTrigger id="driver-provider-connection-id">
                          <SelectValue placeholder="Select Azure DevOps connection" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="__none">No linked Azure DevOps connection</SelectItem>
                          {providerConnectionOptions
                            .filter((connection) => (connection.id ?? "").trim() !== "")
                            .map((connection) => (
                              <SelectItem key={connection.id ?? connection.name} value={connection.id ?? ""}>
                                {connection.name || connection.id}
                                {draft.deploymentDriver === "pipeline_trigger" &&
                                (connection.provider ?? "").toLowerCase() !== "azure_devops"
                                  ? " (incompatible provider)"
                                  : ""}
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
                          disabled={isLoadingProviderConnections || draft.deploymentDriver !== "pipeline_trigger"}
                        >
                          {isLoadingProviderConnections ? "Refreshing..." : "Refresh"}
                        </Button>
                        <span className="text-xs text-muted-foreground">
                          Manage in <span className="font-medium text-foreground">Admin → Provider Connections</span>.
                        </span>
                      </div>
                    </div>
                  </div>
                </div>

                {draft.deploymentDriver === "pipeline_trigger" ? (
                  <div className="space-y-3">
                    <div className="rounded-md border border-border/70 bg-background/60 p-3">
                      <h4 className="text-sm font-semibold text-foreground">Azure DevOps Project</h4>
                      <p className="mt-1 text-xs text-muted-foreground">
                        Choose the Azure DevOps project that this MAPPO project deploys through. MAPPO discovers the
                        list from the selected Azure DevOps connection so operators do not need to reconstruct organization
                        and project details manually.
                      </p>
                      <div className="mt-3 flex flex-col gap-2 md:flex-row md:items-end">
                        <div className="flex-1 space-y-1">
                          <div className="flex items-center gap-1">
                            <Label htmlFor="driver-project-select">Azure DevOps Project</Label>
                            <FieldHelpTooltip content="Discovered Azure DevOps project. MAPPO loads this list from the selected Provider Connection by calling the Azure DevOps Projects API with that connection's PAT." />
                          </div>
                          {discoveredAdoProjects.length > 0 ? (
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
                                const selectedProject = discoveredAdoProjects.find(
                                  (projectOption) => projectOption.id === value
                                );
                                if (!selectedProject || !selectedProviderConnection?.organizationUrl?.trim()) {
                                  return;
                                }
                                setDraft((current) => ({
                                  ...current,
                                  driver: {
                                    ...current.driver,
                                    organization: selectedProviderConnection.organizationUrl!.trim(),
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
                                {discoveredAdoProjects.map((projectOption) => (
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
                              No Azure DevOps projects loaded yet. Choose a Provider Connection and click{" "}
                              <span className="font-medium text-foreground">Refresh Projects</span>.
                            </p>
                          )}
                        </div>
                        <Button
                          id="driver-project-discovery-action"
                          type="button"
                          variant="outline"
                          disabled={isDiscoveringAdoProjects || draft.providerConnectionId.trim() === ""}
                          onClick={() => {
                            void discoverAdoProjects();
                          }}
                        >
                          {isDiscoveringAdoProjects
                            ? "Discovering..."
                            : discoveredAdoProjects.length > 0
                              ? "Refresh Projects"
                              : "Discover Projects"}
                        </Button>
                      </div>
                      <div className="mt-3 grid grid-cols-1 gap-2 md:grid-cols-2">
                        <div className="rounded-md border border-border/60 bg-background/50 px-2 py-1 text-xs text-muted-foreground">
                          <p className="text-[11px] uppercase tracking-[0.08em] text-muted-foreground">Organization URL</p>
                          <p className="mt-1 font-mono text-foreground">
                            {resolvedAdoOrganization || "Waiting for a discovered project"}
                          </p>
                        </div>
                        <div className="rounded-md border border-border/60 bg-background/50 px-2 py-1 text-xs text-muted-foreground">
                          <p className="text-[11px] uppercase tracking-[0.08em] text-muted-foreground">Project URL</p>
                          <p className="mt-1 font-mono text-foreground">
                            {selectedAdoProjectUrl || "Waiting for a discovered project"}
                          </p>
                        </div>
                      </div>
                      <p className="mt-2 text-xs text-muted-foreground">
                        {selectedProviderConnection ? (
                          <>
                            Discovery and trigger calls will use Azure DevOps connection{" "}
                            <span className="font-medium text-foreground">
                              {selectedProviderConnection.name || selectedProviderConnection.id}
                            </span>
                            .
                          </>
                        ) : (
                          <>
                            Select an Azure DevOps connection above before discovering project resources.
                          </>
                        )}
                      </p>
                      {adoProjectDiscoveryError ? (
                        <p className="mt-3 rounded-md border border-amber-400/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-100">
                          {adoProjectDiscoveryError}
                        </p>
                      ) : null}
                    </div>

                    <div className="rounded-md border border-border/70 bg-background/60 p-3">
                      <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
                        <div>
                          <h4 className="text-sm font-semibold text-foreground">Azure DevOps Repo</h4>
                          <p className="mt-1 text-xs text-muted-foreground">
                            Select the repository MAPPO should treat as this project’s default Azure DevOps repo context.
                          </p>
                        </div>
                        <Button
                          id="driver-repository-discovery-action"
                          type="button"
                          variant="outline"
                          disabled={
                            isDiscoveringRepositories ||
                            resolvedAdoOrganization === "" ||
                            resolvedAdoProject === "" ||
                            draft.providerConnectionId.trim() === ""
                          }
                          onClick={() => {
                            void discoverAdoRepositories();
                          }}
                        >
                          {isDiscoveringRepositories
                            ? "Discovering..."
                            : discoveredRepositories.length > 0
                              ? "Refresh Repositories"
                              : "Discover Repositories"}
                        </Button>
                      </div>
                      <p className="mt-2 text-xs text-muted-foreground">
                        MAPPO loads repositories from the selected Azure DevOps project using the linked Provider Connection.
                      </p>
                      <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2">
                        <div className="space-y-1">
                          <div className="flex items-center gap-1">
                            <Label htmlFor="driver-repository-select">Azure DevOps Repo</Label>
                            <FieldHelpTooltip content="Repository selected for this project. MAPPO stores the repository name as part of the project definition so pipeline runs stay tied to the same Azure DevOps repo context." />
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
                              No repositories loaded yet. MAPPO will load them from the selected Azure DevOps project after you click{" "}
                              <span className="font-medium text-foreground">Discover Repositories</span>.
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
                            Discover and select the Azure DevOps pipeline MAPPO should trigger for this project.
                          </p>
                        </div>
                        <Button
                          id="driver-pipeline-discovery-action"
                          type="button"
                          variant="outline"
                          disabled={
                            isDiscoveringPipelines ||
                            resolvedAdoOrganization === "" ||
                            resolvedAdoProject === "" ||
                            draft.providerConnectionId.trim() === ""
                          }
                          onClick={() => {
                            void discoverAdoPipelines();
                          }}
                        >
                          {isDiscoveringPipelines
                            ? "Discovering..."
                            : discoveredPipelines.length > 0
                              ? "Refresh Pipelines"
                              : "Discover Pipelines"}
                        </Button>
                      </div>
                      <p className="mt-2 text-xs text-muted-foreground">
                        Pipelines are loaded from the selected Azure DevOps project. MAPPO stores the selected pipeline ID for deployment runs.
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
                            No pipelines loaded yet. MAPPO will load them from the selected Azure DevOps project after you click{" "}
                            <span className="font-medium text-foreground">Discover Pipelines</span>.
                          </p>
                        )}
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
                            Select the Azure service connection name that the Azure DevOps pipeline uses to authenticate
                            to the target Azure subscription.
                          </p>
                        </div>
                        <Button
                          type="button"
                          variant="outline"
                          disabled={
                            isDiscoveringServiceConnections ||
                            resolvedAdoOrganization === "" ||
                            resolvedAdoProject === "" ||
                            draft.providerConnectionId.trim() === ""
                          }
                          onClick={() => {
                            void discoverAdoServiceConnections();
                          }}
                        >
                          {isDiscoveringServiceConnections
                            ? "Discovering..."
                            : discoveredServiceConnections.length > 0
                              ? "Refresh Service Connections"
                              : "Discover Service Connections"}
                        </Button>
                      </div>
                      <p className="mt-2 text-xs text-muted-foreground">
                        Service connections are loaded from the same Azure DevOps project and should match the pipeline’s Azure authentication entry.
                      </p>
                      <div className="mt-3 space-y-1">
                        <div className="flex items-center gap-1">
                          <Label htmlFor="driver-service-connection">Azure Service Connection</Label>
                          <FieldHelpTooltip content="Service connection name that the Azure DevOps pipeline expects when MAPPO asks it to deploy into a target subscription." />
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
                          <p
                            id="driver-service-connection"
                            className="rounded-md border border-dashed border-border/60 px-3 py-2 text-xs text-muted-foreground"
                          >
                            No service connections loaded yet. MAPPO will load them from the selected Azure DevOps project after you click{" "}
                            <span className="font-medium text-foreground">Discover Service Connections</span>.
                          </p>
                        )}
                      </div>
                      {serviceConnectionDiscoveryError ? (
                        <p className="mt-3 rounded-md border border-amber-400/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-100">
                          {serviceConnectionDiscoveryError}
                        </p>
                      ) : null}
                    </div>
                  </div>
                ) : (
                  <div className="rounded-md border border-border/70 bg-background/40 p-3 text-xs text-muted-foreground">
                    <p>
                      Pipeline settings appear when Deployment driver is set to{" "}
                      <span className="font-medium text-foreground">Pipeline Trigger</span>.
                    </p>
                  </div>
                )}

                <div className="space-y-2">
                  <p className="text-xs uppercase tracking-[0.08em] text-muted-foreground">Capability matrix</p>
                  <div className="flex flex-wrap gap-2">
                    <Badge variant={capabilities.preview ? "default" : "outline"}>
                      {capabilities.preview ? "Supports Preview" : "No Preview"}
                    </Badge>
                    <Badge variant={capabilities.cancel ? "default" : "outline"}>
                      {capabilities.cancel ? "Supports Cancel" : "No Cancel"}
                    </Badge>
                    <Badge variant={capabilities.externalLogs ? "default" : "outline"}>
                      {capabilities.externalLogs ? "External Logs" : "No External Logs"}
                    </Badge>
                    <Badge variant={capabilities.externalHandle ? "default" : "outline"}>
                      {capabilities.externalHandle ? "External Handle" : "No External Handle"}
                    </Badge>
                  </div>
                </div>
              </div>
            </TabsContent>

            <TabsContent value="access-identity" className="space-y-3">
              <h3 className="text-sm font-semibold uppercase tracking-[0.08em] text-muted-foreground">Access & Identity</h3>
              <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                <div className="space-y-1">
                  <div className="flex items-center gap-1">
                    <Label htmlFor="access-strategy">Access model</Label>
                    <FieldHelpTooltip content="How MAPPO authenticates to deploy for this project. Most operators should use Azure Workload RBAC." />
                  </div>
                  <Select
                    value={draft.accessStrategy}
                    onValueChange={(value) =>
                      updateDraft("accessStrategy", value as ProjectDraft["accessStrategy"])
                    }
                  >
                    <SelectTrigger id="access-strategy">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="azure_workload_rbac">Azure Workload RBAC</SelectItem>
                      <SelectItem value="lighthouse_delegated_access">Lighthouse Delegated Access</SelectItem>
                      <SelectItem value="simulator">Simulator</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="md:col-span-2 rounded-md border border-border/70 bg-background/60 p-3 text-xs text-muted-foreground">
                  <p>
                    MAPPO sets internal auth flags from this access model automatically. You only need to provide
                    extra identity values when using delegated models.
                  </p>
                </div>
                {draft.accessStrategy === "lighthouse_delegated_access" ? (
                  <>
                    <div className="space-y-1">
                      <div className="flex items-center gap-1">
                        <Label htmlFor="access-managing-tenant">Managing tenant ID (optional)</Label>
                        <FieldHelpTooltip content="Publisher/managing tenant ID used for delegated access patterns." />
                      </div>
                      <Input
                        id="access-managing-tenant"
                        value={draft.access.managingTenantId}
                        onChange={(event) =>
                          setDraft((current) => ({
                            ...current,
                            access: { ...current.access, managingTenantId: event.target.value },
                          }))
                        }
                      />
                    </div>
                    <div className="space-y-1">
                      <div className="flex items-center gap-1">
                        <Label htmlFor="access-managing-principal">Managing principal client ID (optional)</Label>
                        <FieldHelpTooltip content="Client/application ID of the managing principal for delegated access." />
                      </div>
                      <Input
                        id="access-managing-principal"
                        value={draft.access.managingPrincipalClientId}
                        onChange={(event) =>
                          setDraft((current) => ({
                            ...current,
                            access: { ...current.access, managingPrincipalClientId: event.target.value },
                          }))
                        }
                      />
                    </div>
                  </>
                ) : null}
              </div>
            </TabsContent>

            <TabsContent value="target-contract" className="space-y-3">
              <h3 className="text-sm font-semibold uppercase tracking-[0.08em] text-muted-foreground">Target Contract</h3>
              <div className="rounded-md border border-border/70 bg-background/60 p-3">
                <p className="text-sm font-semibold">Required onboarding fields</p>
                <div className="mt-3 grid gap-3 md:grid-cols-2">
                  {targetContract.required.map((field) => (
                    <div key={field.key} className="rounded-md border border-border/60 bg-background/50 p-3">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <p className="text-sm font-medium text-foreground">{field.label}</p>
                        <Badge>{field.key}</Badge>
                      </div>
                      <p className="mt-2 text-xs text-muted-foreground">{field.description}</p>
                    </div>
                  ))}
                </div>
              </div>
              <div className="rounded-md border border-border/70 bg-background/60 p-3">
                <p className="text-sm font-semibold">Optional onboarding fields</p>
                <div className="mt-3 grid gap-3 md:grid-cols-2">
                  {targetContract.optional.map((field) => (
                    <div key={field.key} className="rounded-md border border-border/60 bg-background/50 p-3">
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <p className="text-sm font-medium text-foreground">{field.label}</p>
                        <Badge variant="secondary">{field.key}</Badge>
                      </div>
                      <p className="mt-2 text-xs text-muted-foreground">{field.description}</p>
                    </div>
                  ))}
                </div>
              </div>
            </TabsContent>

            <TabsContent value="runtime-health" className="space-y-3">
              <h3 className="text-sm font-semibold uppercase tracking-[0.08em] text-muted-foreground">Runtime Health</h3>
              <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                <div className="space-y-1">
                  <div className="flex items-center gap-1">
                    <Label htmlFor="runtime-provider">Runtime health provider</Label>
                    <FieldHelpTooltip content="Health check strategy MAPPO uses to classify runtime status for targets in this project." />
                  </div>
                  <Select
                    value={draft.runtimeHealthProvider}
                    onValueChange={(value) =>
                      updateDraft("runtimeHealthProvider", value as ProjectDraft["runtimeHealthProvider"])
                    }
                  >
                    <SelectTrigger id="runtime-provider">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="azure_container_app_http">Azure Container App HTTP</SelectItem>
                      <SelectItem value="http_endpoint">HTTP Endpoint</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
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

            <TabsContent value="validation" className="space-y-3">
              <h3 className="text-sm font-semibold uppercase tracking-[0.08em] text-muted-foreground">Validation</h3>
              <div className="grid gap-3 lg:grid-cols-3">
                <div className="rounded-md border border-border/70 bg-background/60 p-3">
                  <p className="text-sm font-semibold text-foreground">Provider connection access</p>
                  <p className="mt-2 text-xs text-muted-foreground">
                    Confirms MAPPO can authenticate to the deployment system configured for this project.
                  </p>
                  <Button
                    type="button"
                    variant="outline"
                    className="mt-3"
                    disabled={!project?.id || isValidating}
                    onClick={() => {
                      void runValidation(["credentials"]);
                    }}
                  >
                    Verify Access
                  </Button>
                </div>
                <div className="rounded-md border border-border/70 bg-background/60 p-3">
                  <p className="text-sm font-semibold text-foreground">Release ingest</p>
                  <p className="mt-2 text-xs text-muted-foreground">
                    Confirms the linked release ingest endpoint is ready to receive release events for this project.
                  </p>
                  <Button
                    type="button"
                    variant="outline"
                    className="mt-3"
                    disabled={!project?.id || isValidating}
                    onClick={() => {
                      void runValidation(["webhook"]);
                    }}
                  >
                    Verify Release Ingest
                  </Button>
                </div>
                <div className="rounded-md border border-border/70 bg-background/60 p-3">
                  <p className="text-sm font-semibold text-foreground">Target contract</p>
                  <p className="mt-2 text-xs text-muted-foreground">
                    Confirms an onboarded target includes the metadata required by this deployment driver.
                  </p>
                  {targets.length > 0 ? (
                    <div className="mt-3 space-y-2">
                      <div className="space-y-1">
                        <div className="flex items-center gap-1">
                          <Label htmlFor="validation-target">Target for contract check</Label>
                          <FieldHelpTooltip content="Target used to validate the required onboarding metadata contract." />
                        </div>
                        <Select value={validationTargetId} onValueChange={setValidationTargetId}>
                          <SelectTrigger id="validation-target">
                            <SelectValue placeholder="Select target" />
                          </SelectTrigger>
                          <SelectContent>
                            {targets.map((target) => (
                              <SelectItem key={target.id} value={target.id ?? ""}>
                                {target.id}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      </div>
                      <Button
                        type="button"
                        variant="outline"
                        disabled={!project?.id || isValidating}
                        onClick={() => {
                          void runValidation(["target_contract"]);
                        }}
                      >
                        Verify Target Contract
                      </Button>
                    </div>
                  ) : (
                    <p className="mt-3 rounded-md border border-dashed border-border/60 px-3 py-2 text-xs text-muted-foreground">
                      Onboard at least one target before MAPPO can verify this project’s target metadata contract.
                    </p>
                  )}
                </div>
              </div>
              {validationResult ? (
                <div className="space-y-2 rounded-md border border-border/70 bg-background/60 p-3">
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge variant={validationResult.valid ? "default" : "destructive"}>
                      {validationResult.valid ? "VALID" : "ACTION REQUIRED"}
                    </Badge>
                    <span className="text-xs text-muted-foreground">
                      {validationResult.validatedAt ?? "Validation timestamp unavailable"}
                    </span>
                  </div>
                  <table className="w-full text-left text-xs">
                    <thead className="text-muted-foreground">
                      <tr>
                        <th className="py-1">Scope</th>
                        <th className="py-1">Status</th>
                        <th className="py-1">Code</th>
                        <th className="py-1">Message</th>
                      </tr>
                    </thead>
                    <tbody>
                      {(validationResult.findings ?? []).map((finding: ValidationFinding, index) => (
                        <tr key={`${finding.code}-${index}`} className="border-t border-border/50">
                          <td className="py-1">{finding.scope}</td>
                          <td className="py-1">
                            <Badge variant={finding.status === "fail" ? "destructive" : finding.status === "warning" ? "secondary" : "default"}>
                              {finding.status}
                            </Badge>
                          </td>
                          <td className="py-1 font-mono">{finding.code}</td>
                          <td className="py-1">{finding.message}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : (
                <p className="text-xs text-muted-foreground">
                  Run validation to confirm credentials, webhook configuration, and target contract readiness.
                </p>
              )}
            </TabsContent>

            <TabsContent value="audit" className="space-y-3">
              <h3 className="text-sm font-semibold uppercase tracking-[0.08em] text-muted-foreground">Audit</h3>
              <div className="flex flex-wrap items-center gap-2">
                <div className="flex items-center gap-1">
                  <span className="text-xs text-muted-foreground">Action filter</span>
                  <FieldHelpTooltip content="Filter project configuration audit history by mutation type." />
                </div>
                <Select
                  value={auditActionFilter}
                  onValueChange={(value) => {
                    setAuditActionFilter(value as ProjectConfigurationAuditAction | "all");
                    setAuditPageIndex(0);
                  }}
                >
                  <SelectTrigger className="h-9 w-[180px] bg-background/90">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="all">All actions</SelectItem>
                    <SelectItem value="created">created</SelectItem>
                    <SelectItem value="updated">updated</SelectItem>
                  </SelectContent>
                </Select>
                <Button type="button" variant="outline" onClick={() => void refreshAudit()} disabled={auditLoading}>
                  {auditLoading ? "Refreshing..." : "Refresh Audit"}
                </Button>
              </div>
              <div className="rounded-md border border-border/70 bg-background/60 p-3">
                {(auditPage?.items?.length ?? 0) === 0 ? (
                  <p className="text-xs text-muted-foreground">No audit events found for current filters.</p>
                ) : (
                  <div className="space-y-2">
                    {(auditPage?.items ?? []).map((item: AuditItem) => (
                      <details key={item.id} className="rounded border border-border/60 bg-background/50 p-2">
                        <summary className="cursor-pointer text-xs">
                          <span className="font-mono">{item.id}</span>
                          <span className="mx-2">·</span>
                          <Badge variant="secondary">{item.action}</Badge>
                          <span className="mx-2">·</span>
                          <span>{item.changeSummary}</span>
                          <span className="mx-2">·</span>
                          <span className="text-muted-foreground">{item.createdAt}</span>
                        </summary>
                        <div className="mt-2 grid grid-cols-1 gap-2 md:grid-cols-2">
                          <div>
                            <p className="mb-1 text-[11px] uppercase tracking-[0.08em] text-muted-foreground">Before</p>
                            <pre className="max-h-40 overflow-auto rounded bg-background p-2 text-[11px]">
                              {JSON.stringify(item.beforeSnapshot ?? {}, null, 2)}
                            </pre>
                          </div>
                          <div>
                            <p className="mb-1 text-[11px] uppercase tracking-[0.08em] text-muted-foreground">After</p>
                            <pre className="max-h-40 overflow-auto rounded bg-background p-2 text-[11px]">
                              {JSON.stringify(item.afterSnapshot ?? {}, null, 2)}
                            </pre>
                          </div>
                        </div>
                      </details>
                    ))}
                    <DataTablePagination
                      page={auditMetadata.page ?? 0}
                      pageSize={auditMetadata.size ?? auditPageSize}
                      totalItems={auditMetadata.totalItems ?? 0}
                      totalPages={auditMetadata.totalPages ?? 0}
                      onPageChange={setAuditPageIndex}
                      onPageSizeChange={(size) => {
                        setAuditPageSize(size);
                        setAuditPageIndex(0);
                      }}
                      noun="events"
                    />
                  </div>
                )}
              </div>
            </TabsContent>
              </Tabs>
            </div>
            <Card className="h-fit border-border/70 bg-background/50 xl:sticky xl:top-4">
              <CardHeader className="pb-2">
                <CardTitle className="text-sm uppercase tracking-[0.08em]">Configuration Summary</CardTitle>
                <p className="text-xs text-muted-foreground">Updates live as project settings are edited.</p>
              </CardHeader>
              <CardContent className="pt-0">
                <div className="space-y-4 text-xs">
                  <div className="space-y-2">
                    <p className="text-[11px] uppercase tracking-[0.08em] text-muted-foreground">Release ingest</p>
                    <dl className="space-y-1">
                      <div className="flex justify-between gap-3">
                        <dt className="text-muted-foreground">Source</dt>
                        <dd className="text-right text-foreground">{releaseSourceLabel}</dd>
                      </div>
                      <div className="flex justify-between gap-3">
                        <dt className="text-muted-foreground">Endpoint</dt>
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
                        <dt className="text-muted-foreground">Driver</dt>
                        <dd className="text-right text-foreground">{DEPLOYMENT_DRIVER_LABELS[draft.deploymentDriver]}</dd>
                      </div>
                      {draft.deploymentDriver === "pipeline_trigger" ? (
                        <>
                          <div className="flex justify-between gap-3">
                            <dt className="text-muted-foreground">Pipeline system</dt>
                            <dd className="text-right text-foreground">
                              {draft.driver.pipelineSystem === "azure_devops" ? "Azure DevOps" : draft.driver.pipelineSystem}
                            </dd>
                          </div>
                          <div className="flex justify-between gap-3">
                            <dt className="text-muted-foreground">Provider connection</dt>
                            <dd className="text-right text-foreground">
                              {selectedProviderConnection?.name || selectedProviderConnection?.id || "Not linked"}
                            </dd>
                          </div>
                          <div className="flex justify-between gap-3">
                            <dt className="text-muted-foreground">Azure DevOps project</dt>
                            <dd className="text-right text-foreground">{resolvedAdoProject || "Not selected"}</dd>
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

                  <details className="rounded-md border border-border/60 bg-background/50 p-3">
                    <summary className="cursor-pointer text-sm font-medium text-foreground">Technical payload</summary>
                    <pre className="mt-3 max-h-[40vh] overflow-auto text-[11px] text-muted-foreground">
                      {normalizedPayloadPreview}
                    </pre>
                  </details>
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
                <Select
                  value={createDraft.accessStrategy}
                  onValueChange={(value) =>
                    updateCreateDraft("accessStrategy", value as ProjectDraft["accessStrategy"])
                  }
                >
                  <SelectTrigger id="create-access-strategy">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="azure_workload_rbac">Azure Workload RBAC</SelectItem>
                    <SelectItem value="lighthouse_delegated_access">Lighthouse Delegated Access</SelectItem>
                    <SelectItem value="simulator">Simulator</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1">
                <Label htmlFor="create-driver">Deployment driver</Label>
                <Select
                  value={createDraft.deploymentDriver}
                  onValueChange={(value) =>
                    updateCreateDraft("deploymentDriver", value as ProjectDraft["deploymentDriver"])
                  }
                >
                  <SelectTrigger id="create-driver">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="azure_deployment_stack">Azure Deployment Stack</SelectItem>
                    <SelectItem value="pipeline_trigger">Pipeline Trigger</SelectItem>
                    <SelectItem value="azure_template_spec">Azure Template Spec</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1">
                <Label htmlFor="create-release-source">Release source</Label>
                <Select
                  value={createDraft.releaseArtifactSource}
                  onValueChange={(value) =>
                    updateCreateDraft(
                      "releaseArtifactSource",
                      value as ProjectDraft["releaseArtifactSource"]
                    )
                  }
                >
                  <SelectTrigger id="create-release-source">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {RELEASE_SOURCE_OPTIONS.map((option) => (
                      <SelectItem key={option.value} value={option.value}>
                        {option.label}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1">
                <Label htmlFor="create-runtime-provider">Runtime health provider</Label>
                <Select
                  value={createDraft.runtimeHealthProvider}
                  onValueChange={(value) =>
                    updateCreateDraft(
                      "runtimeHealthProvider",
                      value as ProjectDraft["runtimeHealthProvider"]
                    )
                  }
                >
                  <SelectTrigger id="create-runtime-provider">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="azure_container_app_http">Azure Container App HTTP</SelectItem>
                    <SelectItem value="http_endpoint">HTTP Endpoint</SelectItem>
                  </SelectContent>
                </Select>
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
