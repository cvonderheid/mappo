import { FormEvent, useEffect, useMemo, useState } from "react";
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
import { listProviderConnections, listReleaseIngestEndpoints } from "@/lib/api";
import type {
  DiscoverProjectAdoServiceConnectionsRequest,
  DiscoverProjectAdoPipelinesRequest,
  ListProjectAuditQuery,
  PageMetadata,
  ProjectAdoPipeline,
  ProjectAdoPipelineDiscoveryResult,
  ProjectAdoServiceConnection,
  ProjectAdoServiceConnectionDiscoveryResult,
  ProjectConfigurationAuditAction,
  ProjectConfigurationAuditPage,
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
  onListProjectAudit: (
    projectId: string,
    query: ListProjectAuditQuery
  ) => Promise<ProjectConfigurationAuditPage>;
  onDiscoverAdoPipelines: (
    projectId: string,
    request: DiscoverProjectAdoPipelinesRequest
  ) => Promise<ProjectAdoPipelineDiscoveryResult>;
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

const TARGET_CONTRACTS: Record<ProjectDraft["deploymentDriver"], { required: string[]; optional: string[] }> = {
  azure_deployment_stack: {
    required: ["managedResourceGroupId", "deploymentStackName", "containerAppResourceId"],
    optional: ["registryAuthMode", "registryServer", "registryUsername", "registryPasswordSecretName"],
  },
  azure_template_spec: {
    required: ["managedResourceGroupId", "containerAppResourceId"],
    optional: ["managedApplicationId", "registryAuthMode", "registryServer"],
  },
  pipeline_trigger: {
    required: ["executionConfig.resourceGroup", "executionConfig.appServiceName"],
    optional: ["executionConfig.slotName", "executionConfig.healthPath", "executionConfig.pipelineVariables"],
  },
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

type ParsedAzureDevOpsRepositoryContext = {
  organizationUrl: string;
  projectName: string;
  repositoryName: string;
};

function parseAzureDevOpsRepositoryUrl(value: string): ParsedAzureDevOpsRepositoryContext | null {
  const candidate = value.trim();
  if (candidate === "") {
    return null;
  }
  try {
    const url = new URL(candidate);
    const hostname = url.hostname.toLowerCase();
    const segments = url.pathname.split("/").filter((segment) => segment !== "");
    const gitIndex = segments.findIndex((segment) => segment.toLowerCase() === "_git");
    const repoName = gitIndex >= 0 && segments[gitIndex + 1] ? decodeURIComponent(segments[gitIndex + 1]!) : "";

    if (hostname === "dev.azure.com") {
      const organization = segments[0];
      const project = segments[1];
      if (!organization || !project) {
        return null;
      }
      return {
        organizationUrl: `https://dev.azure.com/${organization}`,
        projectName: decodeURIComponent(project),
        repositoryName: repoName,
      };
    }

    if (hostname.endsWith(".visualstudio.com")) {
      const organization = hostname.replace(/\.visualstudio\.com$/i, "");
      const project = segments[0];
      if (!organization || !project) {
        return null;
      }
      return {
        organizationUrl: `https://dev.azure.com/${organization}`,
        projectName: decodeURIComponent(project),
        repositoryName: repoName,
      };
    }
  } catch {
    return null;
  }
  return null;
}

function normalizeAzureDevOpsOrganizationUrl(value: string): string {
  const trimmed = value.trim();
  if (trimmed === "") {
    return "";
  }
  try {
    const url = new URL(trimmed);
    if (url.hostname.toLowerCase() === "dev.azure.com") {
      const segments = url.pathname.split("/").filter((segment) => segment !== "");
      if (segments[0]) {
        return `https://dev.azure.com/${segments[0]}`;
      }
    }
  } catch {
    // Keep user input untouched if it isn't a URL yet.
  }
  return trimmed;
}

function buildAzureDevOpsProjectUrl(organizationUrl: string, projectName: string): string {
  const normalizedOrganization = normalizeAzureDevOpsOrganizationUrl(organizationUrl);
  const normalizedProject = projectName.trim();
  if (normalizedOrganization === "" || normalizedProject === "") {
    return "";
  }
  return `${normalizedOrganization}/${encodeURIComponent(normalizedProject)}`;
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
  const [isDiscoveringPipelines, setIsDiscoveringPipelines] = useState(false);
  const [isDiscoveringServiceConnections, setIsDiscoveringServiceConnections] = useState(false);
  const [discoveredPipelines, setDiscoveredPipelines] = useState<ProjectAdoPipeline[]>([]);
  const [discoveredServiceConnections, setDiscoveredServiceConnections] = useState<ProjectAdoServiceConnection[]>([]);
  const [releaseIngestEndpoints, setReleaseIngestEndpoints] = useState<ReleaseIngestEndpoint[]>([]);
  const [providerConnections, setProviderConnections] = useState<ProviderConnection[]>([]);
  const [isLoadingReleaseIngestEndpoints, setIsLoadingReleaseIngestEndpoints] = useState(false);
  const [isLoadingProviderConnections, setIsLoadingProviderConnections] = useState(false);
  const [pipelineNameContains, setPipelineNameContains] = useState("");
  const [serviceConnectionNameContains, setServiceConnectionNameContains] = useState("");
  const [adoRepositoryUrl, setAdoRepositoryUrl] = useState("");
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
    setDiscoveredPipelines([]);
    setDiscoveredServiceConnections([]);
    setPipelineNameContains("");
    setServiceConnectionNameContains("");
    setAdoRepositoryUrl(buildAzureDevOpsProjectUrl(nextDraft.driver.organization, nextDraft.driver.project));
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

  const capabilities = DRIVER_CAPABILITIES[draft.deploymentDriver];
  const targetContract = TARGET_CONTRACTS[draft.deploymentDriver];
  const targetCount = targets.length;
  const derivedAdoContext = useMemo(
    () => parseAzureDevOpsRepositoryUrl(adoRepositoryUrl),
    [adoRepositoryUrl]
  );
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
  const selectedDiscoveredServiceConnectionId = useMemo(() => {
    const currentValue = draft.driver.azureServiceConnectionName.trim();
    if (currentValue === "") {
      return "__none";
    }
    const matching = discoveredServiceConnections.find((connection) =>
      connection.name === currentValue || connection.id === currentValue
    );
    return matching ? matching.id : "__custom";
  }, [discoveredServiceConnections, draft.driver.azureServiceConnectionName]);

  useEffect(() => {
    if (draft.deploymentDriver !== "pipeline_trigger") {
      return;
    }
    if (!derivedAdoContext) {
      return;
    }
    setDraft((current) => {
      const nextOrganization = derivedAdoContext.organizationUrl;
      const nextProject = derivedAdoContext.projectName;
      if (
        current.driver.organization.trim() === nextOrganization &&
        current.driver.project.trim() === nextProject
      ) {
        return current;
      }
      return {
        ...current,
        driver: {
          ...current.driver,
          organization: nextOrganization,
          project: nextProject,
        },
      };
    });
    if (
      derivedAdoContext.repositoryName.trim() !== "" &&
      pipelineNameContains.trim() === ""
    ) {
      setPipelineNameContains(derivedAdoContext.repositoryName);
    }
  }, [derivedAdoContext, draft.deploymentDriver, pipelineNameContains]);

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
          id: "driver-project-url-required",
          tab: "deployment-driver",
          fieldId: "driver-project-url",
          message: "Deployment Driver: Azure DevOps Project URL is required.",
        });
      }
      if (draft.driver.pipelineId.trim() === "") {
        issues.push({
          id: "driver-pipeline-id-required",
          tab: "deployment-driver",
          fieldId: "driver-pipeline-id",
          message: "Deployment Driver: Pipeline ID is required.",
        });
      }
      if (draft.driver.azureServiceConnectionName.trim() === "") {
        issues.push({
          id: "driver-service-connection-required",
          tab: "deployment-driver",
          fieldId: "driver-service-connection",
          message: "Deployment Driver: Azure service connection name is required.",
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

  const canPersist = project !== null && draftValidationIssues.length === 0;
  const canCreateProject = createDraft.id.trim() !== "" && createDraft.name.trim() !== "";

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

  async function discoverAdoPipelines(): Promise<void> {
    if (!project?.id) {
      return;
    }
    const resolvedOrganization =
      normalizeAzureDevOpsOrganizationUrl(draft.driver.organization) ||
      derivedAdoContext?.organizationUrl ||
      undefined;
    const resolvedProject = draft.driver.project.trim() || derivedAdoContext?.projectName || undefined;
    setIsDiscoveringPipelines(true);
    try {
      const response = await onDiscoverAdoPipelines(project.id, {
        organization: resolvedOrganization,
        project: resolvedProject,
        providerConnectionId: draft.providerConnectionId.trim() || undefined,
        nameContains: pipelineNameContains.trim() || undefined,
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
      toast.success(
        `Discovered ${pipelines.length} pipeline${pipelines.length === 1 ? "" : "s"} in ${response.organization}/${response.project}.`
      );
    } catch (error) {
      const message = (error as Error).message;
      if (message.toLowerCase().includes("pat could not be resolved")) {
        toast.error(
          "Azure DevOps PAT could not be resolved. Configure an Azure DevOps PAT reference in Admin → Provider Connections."
        );
      } else {
        toast.error(message);
      }
    } finally {
      setIsDiscoveringPipelines(false);
    }
  }

  async function discoverAdoServiceConnections(): Promise<void> {
    if (!project?.id) {
      return;
    }
    const resolvedOrganization =
      normalizeAzureDevOpsOrganizationUrl(draft.driver.organization) ||
      derivedAdoContext?.organizationUrl ||
      undefined;
    const resolvedProject = draft.driver.project.trim() || derivedAdoContext?.projectName || undefined;
    setIsDiscoveringServiceConnections(true);
    try {
      const response = await onDiscoverAdoServiceConnections(project.id, {
        organization: resolvedOrganization,
        project: resolvedProject,
        providerConnectionId: draft.providerConnectionId.trim() || undefined,
        nameContains: serviceConnectionNameContains.trim() || undefined,
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
      toast.success(
        `Discovered ${serviceConnections.length} service connection${serviceConnections.length === 1 ? "" : "s"} in ${response.organization}/${response.project}.`
      );
    } catch (error) {
      const message = (error as Error).message;
      if (message.toLowerCase().includes("pat could not be resolved")) {
        toast.error(
          "Azure DevOps PAT could not be resolved. Configure an Azure DevOps PAT reference in Admin → Provider Connections."
        );
      } else {
        toast.error(message);
      }
    } finally {
      setIsDiscoveringServiceConnections(false);
    }
  }

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
              <p className="text-xs font-medium">Config section available</p>
              <Badge className="mt-1" variant={project ? "default" : "secondary"}>
                {project ? "Done" : "Pending"}
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
              <Badge className="mt-1" variant={targetCount > 0 && projectReleaseCount > 0 ? "default" : "secondary"}>
                {targetCount > 0 && projectReleaseCount > 0 ? "Ready" : "Blocked"}
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
                <TabsList className="flex h-auto w-full flex-wrap justify-start gap-1 bg-background/70 p-1">
                  {PROJECT_TABS.map((tab) => (
                    <TabsTrigger
                      key={tab.key}
                      value={tab.key}
                      className="flex-none whitespace-nowrap px-3 text-xs"
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
                <div className="space-y-1">
                  <div className="flex items-center gap-1">
                    <Label htmlFor="release-source-type">Release source</Label>
                    <FieldHelpTooltip content="Where MAPPO reads deployable versions from. Pipeline Trigger projects always use webhook/pipeline release events." />
                  </div>
                  <Select
                    value={draft.releaseArtifactSource}
                    onValueChange={(value) =>
                      updateDraft("releaseArtifactSource", value as ProjectDraft["releaseArtifactSource"])
                    }
                    disabled={draft.deploymentDriver === "pipeline_trigger"}
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
                  {draft.deploymentDriver === "pipeline_trigger" ? (
                    <p className="text-xs text-muted-foreground">
                      Pipeline Trigger requires <span className="font-medium text-foreground">Webhook / Pipeline Event</span>.
                    </p>
                  ) : null}
                </div>
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
                  {releaseIngestEndpointOptions.length === 0 ? (
                    <p className="text-xs text-muted-foreground">
                      No compatible endpoints found. Create one in{" "}
                      <span className="font-medium text-foreground">Admin → Release Ingest</span>.
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
                      <FieldHelpTooltip content="Execution engine for this project. Pipeline Trigger delegates deployment to CI/CD pipeline runs." />
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
                      <FieldHelpTooltip content="Pipeline provider MAPPO will call when Deployment driver is Pipeline Trigger." />
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
                      <FieldHelpTooltip content="Admin-managed API credential profile used by this deployment driver. For Azure DevOps, this holds the PAT secret reference and optional organization scope." />
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
                          <SelectValue placeholder="Select provider connection" />
                        </SelectTrigger>
                        <SelectContent>
                          <SelectItem value="__none">No linked provider connection</SelectItem>
                          {providerConnectionOptions
                            .filter((connection) => (connection.id ?? "").trim() !== "")
                            .map((connection) => (
                              <SelectItem key={connection.id ?? connection.name} value={connection.id ?? ""}>
                                {connection.name || connection.id}
                                {draft.deploymentDriver === "pipeline_trigger" &&
                                (connection.provider ?? "").toLowerCase() !== "azure_devops"
                                  ? " (incompatible provider)"
                                  : ""}
                                {" ("}
                                {connection.id}
                                {")"}
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
                          Configure in <span className="font-medium text-foreground">Admin → Provider Connections</span>.
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
                        Set the Azure DevOps project context first. MAPPO derives organization and project from this URL.
                      </p>
                      <div className="mt-3 space-y-1">
                        <div className="flex items-center gap-1">
                          <Label htmlFor="driver-project-url">Azure DevOps Project URL</Label>
                          <FieldHelpTooltip content="Full Azure DevOps URL for the project or repo. Examples: https://dev.azure.com/<org>/<project> or https://dev.azure.com/<org>/<project>/_git/<repo>." />
                        </div>
                        <Input
                          id="driver-project-url"
                          value={adoRepositoryUrl}
                          onChange={(event) => setAdoRepositoryUrl(event.target.value)}
                          placeholder="https://dev.azure.com/pg123/demo-app-service"
                        />
                      </div>
                      <div className="mt-2 rounded-md border border-border/60 bg-background/50 px-2 py-1 text-xs text-muted-foreground">
                        {derivedAdoContext ? (
                          <>
                            Derived organization:{" "}
                            <span className="font-mono text-foreground">{derivedAdoContext.organizationUrl}</span>
                            {" · "}
                            derived project:{" "}
                            <span className="font-mono text-foreground">{derivedAdoContext.projectName}</span>
                          </>
                        ) : (
                          "Enter a valid Azure DevOps project URL so MAPPO can derive organization and project automatically."
                        )}
                      </div>
                    </div>

                    <div className="rounded-md border border-border/70 bg-background/60 p-3">
                      <h4 className="text-sm font-semibold text-foreground">Azure DevOps Repo</h4>
                      <p className="mt-1 text-xs text-muted-foreground">
                        Configure repo-level defaults used when MAPPO queues a pipeline run.
                      </p>
                      <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-1">
                        <div className="space-y-1">
                          <div className="flex items-center gap-1">
                            <Label htmlFor="driver-branch">Branch</Label>
                            <FieldHelpTooltip content="Default branch/ref MAPPO passes when queueing pipeline runs." />
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
                        </div>
                      </div>
                    </div>

                    <div className="rounded-md border border-border/70 bg-background/60 p-3">
                      <h4 className="text-sm font-semibold text-foreground">Azure DevOps Pipeline</h4>
                      <p className="mt-1 text-xs text-muted-foreground">
                        Discover and select the Azure DevOps pipeline MAPPO should trigger.
                      </p>
                      <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2">
                        <div className="space-y-1">
                          <div className="flex items-center gap-1">
                            <Label htmlFor="driver-pipeline-discovery">Pipeline name filter (optional)</Label>
                            <FieldHelpTooltip content="Used only for pipeline discovery to narrow the result list. Leave blank to list all pipelines in the Azure DevOps project." />
                          </div>
                          <Input
                            id="driver-pipeline-discovery"
                            value={pipelineNameContains}
                            onChange={(event) => setPipelineNameContains(event.target.value)}
                            placeholder="Optional (for example demo-app-service)"
                          />
                        </div>
                        <div className="space-y-1">
                          <div className="flex items-center gap-1">
                            <Label htmlFor="driver-pipeline-discovery-action">Discover pipelines</Label>
                            <FieldHelpTooltip content="Fetch available pipelines from Azure DevOps using the derived project context and linked Provider Connection PAT reference." />
                          </div>
                          <Button
                            id="driver-pipeline-discovery-action"
                            type="button"
                            variant="outline"
                            disabled={isDiscoveringPipelines}
                            onClick={() => {
                              void discoverAdoPipelines();
                            }}
                          >
                            {isDiscoveringPipelines ? "Discovering..." : "Discover Pipelines"}
                          </Button>
                          {discoveredPipelines.length > 0 ? (
                            <Select
                              value={selectedDiscoveredPipelineId}
                              onValueChange={(value) => {
                                if (value === "__none") {
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
                                    {pipeline.name} ({pipeline.id})
                                  </SelectItem>
                                ))}
                              </SelectContent>
                            </Select>
                          ) : (
                            <p className="text-xs text-muted-foreground">
                              No discovered pipelines yet. Click <span className="font-medium text-foreground">Discover Pipelines</span>.
                            </p>
                          )}
                        </div>
                        <div className="space-y-1">
                          <div className="flex items-center gap-1">
                            <Label htmlFor="driver-pipeline-id">Pipeline ID</Label>
                            <FieldHelpTooltip content="Azure DevOps pipeline definition ID. Use Discover Pipelines to auto-fill this field, or enter it manually." />
                          </div>
                          <Input
                            id="driver-pipeline-id"
                            value={draft.driver.pipelineId}
                            onChange={(event) =>
                              setDraft((current) => ({
                                ...current,
                                driver: { ...current.driver, pipelineId: event.target.value },
                              }))
                            }
                            placeholder="For example: 1"
                          />
                        </div>
                      </div>
                    </div>

                    <div className="rounded-md border border-border/70 bg-background/60 p-3">
                      <h4 className="text-sm font-semibold text-foreground">Azure Service Connection</h4>
                      <p className="mt-1 text-xs text-muted-foreground">
                        MAPPO passes the selected Azure Service Connection name to the deployment trigger so the
                        pipeline can authenticate to Azure in the target subscription.
                      </p>
                      <div className="mt-3 grid grid-cols-1 gap-3 md:grid-cols-2">
                        <div className="space-y-1">
                          <div className="flex items-center gap-1">
                            <Label htmlFor="driver-service-connection-discovery">Service connection name filter (optional)</Label>
                            <FieldHelpTooltip content="Used only for service-connection discovery to narrow results. Leave blank to list all service connections in the Azure DevOps project." />
                          </div>
                          <div className="flex flex-col gap-2 md:flex-row">
                            <Input
                              id="driver-service-connection-discovery"
                              value={serviceConnectionNameContains}
                              onChange={(event) => setServiceConnectionNameContains(event.target.value)}
                              placeholder="Optional name filter (for example contributor)"
                              className="md:flex-1"
                            />
                            <Button
                              type="button"
                              variant="outline"
                              disabled={isDiscoveringServiceConnections}
                              onClick={() => {
                                void discoverAdoServiceConnections();
                              }}
                            >
                              {isDiscoveringServiceConnections ? "Discovering..." : "Discover Service Connections"}
                            </Button>
                          </div>
                        </div>
                        <div className="space-y-1">
                          <div className="flex items-center gap-1">
                            <Label htmlFor="driver-service-connection">Azure Service Connection</Label>
                            <FieldHelpTooltip content="Service connection name used by the pipeline for Azure authentication. Discover from Azure DevOps Project Settings > Service connections or enter manually." />
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
                                if (value === "__custom") {
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
                                    {connection.name} ({connection.type || "unknown"})
                                  </SelectItem>
                                ))}
                                <SelectItem value="__custom">Manual entry</SelectItem>
                              </SelectContent>
                            </Select>
                          ) : (
                            <Input
                              id="driver-service-connection"
                              value={draft.driver.azureServiceConnectionName}
                              onChange={(event) =>
                                setDraft((current) => ({
                                  ...current,
                                  driver: { ...current.driver, azureServiceConnectionName: event.target.value },
                                }))
                              }
                              placeholder="For example: mappo-ado-demo-rg-contributor"
                            />
                          )}
                          {selectedDiscoveredServiceConnectionId === "__custom" ? (
                            <Input
                              id="driver-service-connection-custom"
                              value={draft.driver.azureServiceConnectionName}
                              onChange={(event) =>
                                setDraft((current) => ({
                                  ...current,
                                  driver: { ...current.driver, azureServiceConnectionName: event.target.value },
                                }))
                              }
                              placeholder="Type service connection name manually"
                            />
                          ) : null}
                        </div>
                        <div className="space-y-1 md:col-span-2">
                          <p className="text-xs text-muted-foreground">
                            {selectedProviderConnection ? (
                              <>
                                Azure DevOps API auth is resolved from linked provider connection{" "}
                                <span className="font-mono text-foreground">
                                  {selectedProviderConnection.id || "(unknown)"}
                                </span>{" "}
                                using PAT secret reference{" "}
                                <span className="font-mono text-foreground">
                                  {selectedProviderConnection.personalAccessTokenRef ||
                                    "mappo.azure-devops.personal-access-token"}
                                </span>
                                .
                              </>
                            ) : (
                              <>
                                Link an Azure DevOps provider connection above so MAPPO can resolve API credentials
                                for pipeline/service-connection discovery and trigger calls.
                              </>
                            )}
                          </p>
                        </div>
                      </div>
                    </div>
                  </div>
                ) : (
                  <div className="rounded-md border border-border/70 bg-background/40 p-3 text-xs text-muted-foreground">
                    <p>
                      Pipeline configuration is shown when Deployment driver is set to{" "}
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
                <p className="text-sm font-semibold">Required metadata keys</p>
                <div className="mt-2 flex flex-wrap gap-2">
                  {targetContract.required.map((key) => (
                    <Badge key={key}>{key}</Badge>
                  ))}
                </div>
              </div>
              <div className="rounded-md border border-border/70 bg-background/60 p-3">
                <p className="text-sm font-semibold">Optional metadata keys</p>
                <div className="mt-2 flex flex-wrap gap-2">
                  {targetContract.optional.map((key) => (
                    <Badge key={key} variant="secondary">
                      {key}
                    </Badge>
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
              <div className="flex flex-wrap items-end gap-2 rounded-md border border-border/70 bg-background/60 p-3">
                <div className="min-w-[240px] flex-1 space-y-1">
                  <div className="flex items-center gap-1">
                    <Label htmlFor="validation-target">Target for contract check</Label>
                    <FieldHelpTooltip content="Target used to validate required metadata contract. Select one of the onboarded targets." />
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
                    void runValidation(["credentials"]);
                  }}
                >
                  Test Credentials
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  disabled={!project?.id || isValidating}
                  onClick={() => {
                    void runValidation(["webhook"]);
                  }}
                >
                  Test Webhook
                </Button>
                <Button
                  type="button"
                  variant="outline"
                  disabled={!project?.id || isValidating}
                  onClick={() => {
                    void runValidation(["target_contract"]);
                  }}
                >
                  Test Target Contract
                </Button>
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
                <CardTitle className="text-sm uppercase tracking-[0.08em]">Payload Preview</CardTitle>
                <p className="text-xs text-muted-foreground">Updates live as project settings are edited.</p>
              </CardHeader>
              <CardContent className="pt-0">
                <pre className="max-h-[70vh] overflow-auto rounded bg-background p-2 text-[11px]">
                  {normalizedPayloadPreview}
                </pre>
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
