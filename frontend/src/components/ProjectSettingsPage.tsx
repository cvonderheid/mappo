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
import type {
  DiscoverProjectAdoPipelinesRequest,
  ListProjectAuditQuery,
  PageMetadata,
  ProjectAdoPipeline,
  ProjectAdoPipelineDiscoveryResult,
  ProjectConfigurationAuditAction,
  ProjectConfigurationAuditPage,
  ProjectConfigurationPatchRequest,
  ProjectCreateRequest,
  ProjectDefinition,
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
  accessStrategy: "simulator" | "azure_workload_rbac" | "lighthouse_delegated_access";
  deploymentDriver: "azure_deployment_stack" | "azure_template_spec" | "pipeline_trigger";
  releaseArtifactSource: "blob_arm_template" | "template_spec_resource" | "external_deployment_inputs";
  runtimeHealthProvider: "azure_container_app_http" | "http_endpoint";
  access: {
    authModel: string;
    requiresAzureCredential: boolean;
    requiresTargetExecutionMetadata: boolean;
    azureServiceConnectionName: string;
    managingTenantId: string;
    managingPrincipalClientId: string;
    requiresDelegation: boolean;
  };
  driver: {
    pipelineSystem: string;
    organization: string;
    project: string;
    pipelineId: string;
    branch: string;
    azureServiceConnectionName: string;
    personalAccessTokenRef: string;
    supportsExternalExecutionHandle: boolean;
    supportsExternalLogs: boolean;
    supportsPreview: boolean;
    previewMode: string;
  };
  release: {
    descriptor: string;
    templateUriField: string;
    sourceSystem: string;
    descriptorPath: string;
    versionField: string;
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
    accessStrategy: (project?.accessStrategy ?? "azure_workload_rbac") as ProjectDraft["accessStrategy"],
    deploymentDriver: (project?.deploymentDriver ?? "azure_deployment_stack") as ProjectDraft["deploymentDriver"],
    releaseArtifactSource: (project?.releaseArtifactSource ?? "blob_arm_template") as ProjectDraft["releaseArtifactSource"],
    runtimeHealthProvider: (project?.runtimeHealthProvider ?? "azure_container_app_http") as ProjectDraft["runtimeHealthProvider"],
    access: {
      authModel: asString(accessConfig.authModel, "rbac"),
      requiresAzureCredential: asBoolean(accessConfig.requiresAzureCredential, true),
      requiresTargetExecutionMetadata: asBoolean(accessConfig.requiresTargetExecutionMetadata, true),
      azureServiceConnectionName: asString(accessConfig.azureServiceConnectionName),
      managingTenantId: asString(accessConfig.managingTenantId),
      managingPrincipalClientId: asString(accessConfig.managingPrincipalClientId),
      requiresDelegation: asBoolean(accessConfig.requiresDelegation, false),
    },
    driver: {
      pipelineSystem: asString(driverConfig.pipelineSystem, "azure_devops"),
      organization: asString(driverConfig.organization),
      project: asString(driverConfig.project),
      pipelineId: asString(driverConfig.pipelineId),
      branch: asString(driverConfig.branch, "main"),
      azureServiceConnectionName: asString(driverConfig.azureServiceConnectionName),
      personalAccessTokenRef: asString(
        driverConfig.personalAccessTokenRef,
        "mappo.azure-devops.personal-access-token"
      ),
      supportsExternalExecutionHandle: asBoolean(driverConfig.supportsExternalExecutionHandle, true),
      supportsExternalLogs: asBoolean(driverConfig.supportsExternalLogs, true),
      supportsPreview: asBoolean(driverConfig.supportsPreview, project?.deploymentDriver === "azure_deployment_stack"),
      previewMode: asString(driverConfig.previewMode, "arm_what_if"),
    },
    release: {
      descriptor: asString(releaseConfig.descriptor, "managed-app-release"),
      templateUriField: asString(releaseConfig.templateUriField, "templateUri"),
      sourceSystem: asString(releaseConfig.sourceSystem, "github"),
      descriptorPath: asString(releaseConfig.descriptorPath, "releases/manifest.json"),
      versionField: asString(releaseConfig.versionField, "releaseVersion"),
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
  const accessStrategyConfig: Record<string, unknown> = {
    authModel: draft.access.authModel.trim() || undefined,
    requiresAzureCredential: draft.access.requiresAzureCredential,
    requiresTargetExecutionMetadata: draft.access.requiresTargetExecutionMetadata,
  };
  if (draft.access.azureServiceConnectionName.trim() !== "") {
    accessStrategyConfig.azureServiceConnectionName = draft.access.azureServiceConnectionName.trim();
  }
  if (draft.access.managingTenantId.trim() !== "") {
    accessStrategyConfig.managingTenantId = draft.access.managingTenantId.trim();
  }
  if (draft.access.managingPrincipalClientId.trim() !== "") {
    accessStrategyConfig.managingPrincipalClientId = draft.access.managingPrincipalClientId.trim();
  }
  accessStrategyConfig.requiresDelegation = draft.access.requiresDelegation;

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
    deploymentDriverConfig.personalAccessTokenRef =
      draft.driver.personalAccessTokenRef.trim() || undefined;
  }

  const releaseArtifactSourceConfig: Record<string, unknown> = {
    descriptor: draft.release.descriptor.trim() || undefined,
  };
  if (draft.releaseArtifactSource === "blob_arm_template") {
    releaseArtifactSourceConfig.templateUriField = draft.release.templateUriField.trim() || "templateUri";
  }
  if (draft.releaseArtifactSource === "external_deployment_inputs") {
    releaseArtifactSourceConfig.sourceSystem = draft.release.sourceSystem.trim() || "azure_devops";
    releaseArtifactSourceConfig.descriptorPath = draft.release.descriptorPath.trim() || undefined;
    releaseArtifactSourceConfig.versionField = draft.release.versionField.trim() || undefined;
  }
  if (draft.releaseArtifactSource === "template_spec_resource") {
    releaseArtifactSourceConfig.versionRefField = draft.release.versionRefField.trim() || "templateSpecVersion";
  }

  const runtimeHealthProviderConfig: Record<string, unknown> = {
    path: draft.runtime.path.trim() || "/",
    expectedStatus: parseOptionalNumber(draft.runtime.expectedStatus),
    timeoutMs: parseOptionalNumber(draft.runtime.timeoutMs),
  };

  return {
    name: draft.name.trim(),
    accessStrategy: draft.accessStrategy,
    accessStrategyConfig,
    deploymentDriver: draft.deploymentDriver,
    deploymentDriverConfig,
    releaseArtifactSource: draft.releaseArtifactSource,
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
  const [discoveredPipelines, setDiscoveredPipelines] = useState<ProjectAdoPipeline[]>([]);
  const [pipelineNameContains, setPipelineNameContains] = useState("");
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
    setDraft(projectToDraft(project));
    setValidationResult(null);
    setAuditPage(null);
    setAuditPageIndex(0);
    setDiscoveredPipelines([]);
    setPipelineNameContains("");
  }, [project, selectedProjectId]);

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

  const capabilities = DRIVER_CAPABILITIES[draft.deploymentDriver];
  const targetContract = TARGET_CONTRACTS[draft.deploymentDriver];
  const targetCount = targets.length;
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

  const draftErrors = useMemo(() => {
    const errors: string[] = [];
    if (draft.name.trim() === "") {
      errors.push("Project display name is required.");
    }
    if (draft.deploymentDriver === "pipeline_trigger") {
      if (draft.driver.organization.trim() === "") {
        errors.push("Deployment Driver: ADO organization is required for pipeline_trigger.");
      }
      if (draft.driver.project.trim() === "") {
        errors.push("Deployment Driver: ADO project is required for pipeline_trigger.");
      }
      if (draft.driver.pipelineId.trim() === "") {
        errors.push("Deployment Driver: pipelineId is required for pipeline_trigger.");
      }
      if (draft.driver.azureServiceConnectionName.trim() === "") {
        errors.push("Deployment Driver: azureServiceConnectionName is required for pipeline_trigger.");
      }
      if (draft.driver.personalAccessTokenRef.trim() === "") {
        errors.push("Deployment Driver: personalAccessTokenRef is required for pipeline_trigger.");
      }
    }
    if (parseOptionalNumber(draft.runtime.expectedStatus) === undefined) {
      errors.push("Runtime Health: expectedStatus must be a number.");
    }
    if (parseOptionalNumber(draft.runtime.timeoutMs) === undefined) {
      errors.push("Runtime Health: timeoutMs must be a number.");
    }
    return errors;
  }, [draft]);

  const normalizedPayloadPreview = useMemo(
    () => JSON.stringify(buildPatchRequest(draft), null, 2),
    [draft]
  );

  const canPersist = project !== null && draftErrors.length === 0;
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
    if (!project?.id || draftErrors.length > 0) {
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
    setIsDiscoveringPipelines(true);
    try {
      const response = await onDiscoverAdoPipelines(project.id, {
        organization: draft.driver.organization.trim() || undefined,
        project: draft.driver.project.trim() || undefined,
        personalAccessTokenRef: draft.driver.personalAccessTokenRef.trim() || undefined,
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
      toast.error((error as Error).message);
    } finally {
      setIsDiscoveringPipelines(false);
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
          {draftErrors.length > 0 ? (
            <div className="rounded-md border border-amber-400/60 bg-amber-500/10 p-3">
              <p className="mb-2 text-sm font-semibold text-amber-200">Inline validation</p>
              <ul className="list-disc space-y-1 pl-4 text-xs text-amber-100">
                {draftErrors.map((error) => (
                  <li key={error}>{error}</li>
                ))}
              </ul>
            </div>
          ) : null}

          <div className="grid gap-4 xl:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
            <div className="min-w-0">
              <Tabs value={activeTab} onValueChange={(value) => setActiveTab(value as ProjectTab)} className="space-y-4">
                <TabsList className="grid h-auto w-full grid-cols-2 gap-1 bg-background/70 p-1 md:grid-cols-3 xl:grid-cols-4">
                  {PROJECT_TABS.map((tab) => (
                    <TabsTrigger
                      key={tab.key}
                      value={tab.key}
                      className="min-w-0 whitespace-normal px-3 text-xs leading-tight"
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
                  <Label htmlFor="project-name">Project display name</Label>
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
                    <Label htmlFor="release-source-type">Release artifact source</Label>
                    <FieldHelpTooltip content="Defines how releases are interpreted. Use External Deployment Inputs for pipeline-produced version payloads." />
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
                      <SelectItem value="blob_arm_template">Blob ARM Template</SelectItem>
                      <SelectItem value="external_deployment_inputs">External Deployment Inputs</SelectItem>
                      <SelectItem value="template_spec_resource">Template Spec Resource</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1">
                  <div className="flex items-center gap-1">
                    <Label htmlFor="release-descriptor">Descriptor</Label>
                    <FieldHelpTooltip content="Logical release descriptor key used when matching incoming release payloads for this project." />
                  </div>
                  <Input
                    id="release-descriptor"
                    value={draft.release.descriptor}
                    onChange={(event) =>
                      setDraft((current) => ({
                        ...current,
                        release: { ...current.release, descriptor: event.target.value },
                      }))
                    }
                  />
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
                {draft.releaseArtifactSource === "external_deployment_inputs" ? (
                  <>
                    <div className="space-y-1">
                      <div className="flex items-center gap-1">
                        <Label htmlFor="release-source-system">Source system</Label>
                        <FieldHelpTooltip content="Identifier for the external release system (for example azure_devops or github)." />
                      </div>
                      <Input
                        id="release-source-system"
                        value={draft.release.sourceSystem}
                        onChange={(event) =>
                          setDraft((current) => ({
                            ...current,
                            release: { ...current.release, sourceSystem: event.target.value },
                          }))
                        }
                      />
                    </div>
                    <div className="space-y-1">
                      <div className="flex items-center gap-1">
                        <Label htmlFor="release-descriptor-path">Descriptor path</Label>
                        <FieldHelpTooltip content="JSON path key where release descriptor metadata is read from external release payloads." />
                      </div>
                      <Input
                        id="release-descriptor-path"
                        value={draft.release.descriptorPath}
                        onChange={(event) =>
                          setDraft((current) => ({
                            ...current,
                            release: { ...current.release, descriptorPath: event.target.value },
                          }))
                        }
                      />
                    </div>
                    <div className="space-y-1">
                      <div className="flex items-center gap-1">
                        <Label htmlFor="release-version-field">Version field</Label>
                        <FieldHelpTooltip content="JSON field name that carries the deployable version value from external release payloads." />
                      </div>
                      <Input
                        id="release-version-field"
                        value={draft.release.versionField}
                        onChange={(event) =>
                          setDraft((current) => ({
                            ...current,
                            release: { ...current.release, versionField: event.target.value },
                          }))
                        }
                      />
                    </div>
                  </>
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
              <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
                <div className="space-y-1">
                  <div className="flex items-center gap-1">
                    <Label htmlFor="driver-type">Deployment driver</Label>
                    <FieldHelpTooltip content="Execution engine for this project. Pipeline Trigger delegates deployment to CI/CD pipeline runs." />
                  </div>
                  <Select
                    value={draft.deploymentDriver}
                    onValueChange={(value) =>
                      updateDraft("deploymentDriver", value as ProjectDraft["deploymentDriver"])
                    }
                  >
                    <SelectTrigger id="driver-type">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="azure_deployment_stack">Azure Deployment Stack</SelectItem>
                      <SelectItem value="pipeline_trigger">Pipeline Trigger</SelectItem>
                      <SelectItem value="azure_template_spec">Azure Template Spec</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                {draft.deploymentDriver === "pipeline_trigger" ? (
                  <>
                    <div className="space-y-1">
                      <div className="flex items-center gap-1">
                        <Label htmlFor="driver-pipeline-system">Pipeline system</Label>
                        <FieldHelpTooltip content="Pipeline provider. Azure DevOps is currently supported for pipeline-trigger projects." />
                      </div>
                      <Select
                        value={draft.driver.pipelineSystem || "azure_devops"}
                        onValueChange={(value) =>
                          setDraft((current) => ({
                            ...current,
                            driver: { ...current.driver, pipelineSystem: value },
                          }))
                        }
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
                        <Label htmlFor="driver-organization">Organization</Label>
                        <FieldHelpTooltip content="Azure DevOps organization URL, for example https://dev.azure.com/your-org." />
                      </div>
                      <Input
                        id="driver-organization"
                        value={draft.driver.organization}
                        onChange={(event) =>
                          setDraft((current) => ({
                            ...current,
                            driver: { ...current.driver, organization: event.target.value },
                          }))
                        }
                        placeholder="https://dev.azure.com/org"
                      />
                    </div>
                    <div className="space-y-1">
                      <div className="flex items-center gap-1">
                        <Label htmlFor="driver-project">Project</Label>
                        <FieldHelpTooltip content="Azure DevOps project name that contains the deployment pipeline." />
                      </div>
                      <Input
                        id="driver-project"
                        value={draft.driver.project}
                        onChange={(event) =>
                          setDraft((current) => ({
                            ...current,
                            driver: { ...current.driver, project: event.target.value },
                          }))
                        }
                      />
                    </div>
                    <div className="space-y-1">
                      <div className="flex items-center gap-1">
                        <Label htmlFor="driver-pipeline-discovery">Discover pipeline by name</Label>
                        <FieldHelpTooltip content="Fetch available pipelines from Azure DevOps using organization/project and PAT reference, then pick one to auto-fill Pipeline ID." />
                      </div>
                      <div className="flex flex-col gap-2 md:flex-row">
                        <Input
                          id="driver-pipeline-discovery"
                          value={pipelineNameContains}
                          onChange={(event) => setPipelineNameContains(event.target.value)}
                          placeholder="Optional name filter (for example deploy)"
                          className="md:flex-1"
                        />
                        <Button
                          type="button"
                          variant="outline"
                          disabled={isDiscoveringPipelines}
                          onClick={() => {
                            void discoverAdoPipelines();
                          }}
                        >
                          {isDiscoveringPipelines ? "Discovering..." : "Discover Pipelines"}
                        </Button>
                      </div>
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
                        <FieldHelpTooltip content="Azure DevOps pipeline definition ID. This is auto-filled when you select a discovered pipeline, but you can override it manually." />
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
                    <div className="space-y-1">
                      <div className="flex items-center gap-1">
                        <Label htmlFor="driver-branch">Branch</Label>
                        <FieldHelpTooltip content="Default branch ref MAPPO passes when queueing pipeline runs." />
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
                    <div className="space-y-1">
                      <div className="flex items-center gap-1">
                        <Label htmlFor="driver-service-connection">Azure Service Connection</Label>
                        <FieldHelpTooltip content="Name of the ADO service connection used by the pipeline to authenticate to Azure." />
                      </div>
                      <Input
                        id="driver-service-connection"
                        value={draft.driver.azureServiceConnectionName}
                        onChange={(event) =>
                          setDraft((current) => ({
                            ...current,
                            driver: { ...current.driver, azureServiceConnectionName: event.target.value },
                          }))
                        }
                      />
                    </div>
                    <div className="space-y-1 md:col-span-2">
                      <div className="flex items-center gap-1">
                        <Label htmlFor="driver-pat-ref">PAT secret reference</Label>
                        <FieldHelpTooltip content="How MAPPO resolves the Azure DevOps PAT. Leave default to use mappo.azure-devops.personal-access-token, or use env:MY_PAT_VAR, or literal:actual-token." />
                      </div>
                      <Input
                        id="driver-pat-ref"
                        value={draft.driver.personalAccessTokenRef}
                        onChange={(event) =>
                          setDraft((current) => ({
                            ...current,
                            driver: { ...current.driver, personalAccessTokenRef: event.target.value },
                          }))
                        }
                        placeholder="mappo.azure-devops.personal-access-token"
                      />
                    </div>
                  </>
                ) : null}
                <div className="space-y-2 md:col-span-2">
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
                  <Label htmlFor="access-strategy">Access strategy</Label>
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
                <div className="space-y-1">
                  <Label htmlFor="access-auth-model">Auth model</Label>
                  <Input
                    id="access-auth-model"
                    value={draft.access.authModel}
                    onChange={(event) =>
                      setDraft((current) => ({
                        ...current,
                        access: { ...current.access, authModel: event.target.value },
                      }))
                    }
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="access-managing-tenant">Managing tenant ID</Label>
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
                  <Label htmlFor="access-managing-principal">Managing principal client ID</Label>
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
                <div className="space-y-1">
                  <Label htmlFor="access-service-connection">Service connection name</Label>
                  <Input
                    id="access-service-connection"
                    value={draft.access.azureServiceConnectionName}
                    onChange={(event) =>
                      setDraft((current) => ({
                        ...current,
                        access: { ...current.access, azureServiceConnectionName: event.target.value },
                      }))
                    }
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="access-requires-azure-creds">Requires Azure credential</Label>
                  <Select
                    value={draft.access.requiresAzureCredential ? "true" : "false"}
                    onValueChange={(value) =>
                      setDraft((current) => ({
                        ...current,
                        access: { ...current.access, requiresAzureCredential: value === "true" },
                      }))
                    }
                  >
                    <SelectTrigger id="access-requires-azure-creds">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="true">true</SelectItem>
                      <SelectItem value="false">false</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1">
                  <Label htmlFor="access-requires-target-metadata">Requires target metadata</Label>
                  <Select
                    value={draft.access.requiresTargetExecutionMetadata ? "true" : "false"}
                    onValueChange={(value) =>
                      setDraft((current) => ({
                        ...current,
                        access: { ...current.access, requiresTargetExecutionMetadata: value === "true" },
                      }))
                    }
                  >
                    <SelectTrigger id="access-requires-target-metadata">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="true">true</SelectItem>
                      <SelectItem value="false">false</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1">
                  <Label htmlFor="access-requires-delegation">Requires delegation</Label>
                  <Select
                    value={draft.access.requiresDelegation ? "true" : "false"}
                    onValueChange={(value) =>
                      setDraft((current) => ({
                        ...current,
                        access: { ...current.access, requiresDelegation: value === "true" },
                      }))
                    }
                  >
                    <SelectTrigger id="access-requires-delegation">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="true">true</SelectItem>
                      <SelectItem value="false">false</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
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
                  <Label htmlFor="runtime-provider">Runtime health provider</Label>
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
                  <Label htmlFor="runtime-path">Path</Label>
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
                  <Label htmlFor="runtime-status-code">Expected status</Label>
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
                  <Label htmlFor="runtime-timeout">Timeout ms</Label>
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
                  <Label htmlFor="validation-target">Target for contract check</Label>
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
                <Label htmlFor="create-release-source">Release artifact source</Label>
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
                    <SelectItem value="blob_arm_template">Blob ARM Template</SelectItem>
                    <SelectItem value="external_deployment_inputs">External Deployment Inputs</SelectItem>
                    <SelectItem value="template_spec_resource">Template Spec Resource</SelectItem>
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
