import { FormEvent, useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import ProjectFlowDiagram, { type ProjectFlowSection } from "@/features/project/ProjectFlowDiagram";
import ProjectSettingsChecklistCard from "@/features/project/settings/ProjectSettingsChecklistCard";
import ProjectSettingsCreateDrawer from "@/features/project/settings/ProjectSettingsCreateDrawer";
import ProjectSettingsSectionContent from "@/features/project/settings/ProjectSettingsSectionContent";
import ProjectSettingsSummaryCard from "@/features/project/settings/ProjectSettingsSummaryCard";
import {
  DEPLOYMENT_DRIVER_LABELS,
  DEPLOYMENT_METHODS_BY_SYSTEM,
  PROJECT_SECTIONS,
  PROJECT_THEME_OPTIONS,
  RELEASE_SOURCE_TYPE_LABELS,
  RUNTIME_HEALTH_LABELS,
  applyDeploymentDriverSelection,
  asNumberString,
  asRecord,
  buildCreateRequest,
  buildPatchRequest,
  emptyProjectDraft,
  firstDriverForDeploymentSystem,
  normalizeDeploymentDriver,
  normalizeDeploymentSystem,
  parseOptionalNumber,
  projectToDraft,
  readStringField,
  releaseArtifactSourceForDriver,
  type DraftValidationIssue,
  type ProjectDraft,
  type ProjectSettingsPageProps,
  type ProjectTab,
  type ValidationScope,
} from "@/features/project/settings/shared";
import { useProjectSettingsDiscovery } from "@/features/project/settings/useProjectSettingsDiscovery";
import FieldHelpTooltip from "@/features/shared/FieldHelpTooltip";
import { listProjectAudit } from "@/lib/api";
import { DEFAULT_THEME_KEY, PROJECT_THEMES, type ProjectThemeKey } from "@/lib/project-theme";

export default function ProjectSettingsPage({
  project,
  selectedProjectId,
  targets,
  projectReleaseCount,
  onCreateProject,
  onPatchProject,
  onDeleteProject,
  onValidateProject,
  onDiscoverAdoBranches,
  onDiscoverAdoPipelines,
  onDiscoverAdoRepositories,
}: ProjectSettingsPageProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState<ProjectTab>("general");
  const [draft, setDraft] = useState<ProjectDraft>(() => projectToDraft(project));
  const [isSaving, setIsSaving] = useState(false);
  const [isDeleting, setIsDeleting] = useState(false);
  const [isValidating, setIsValidating] = useState(false);
  const [createDrawerOpen, setCreateDrawerOpen] = useState(false);
  const [createSubmitting, setCreateSubmitting] = useState(false);
  const [hasPersistedConfigurationUpdate, setHasPersistedConfigurationUpdate] = useState(false);
  const [createDraft, setCreateDraft] = useState<ProjectDraft>(() => emptyProjectDraft());

  useEffect(() => {
    setDraft(projectToDraft(project));
  }, [project, selectedProjectId]);

  useEffect(() => {
    let cancelled = false;
    const projectId = project?.id?.trim() ?? "";
    if (projectId === "") {
      setHasPersistedConfigurationUpdate(false);
      return;
    }
    setHasPersistedConfigurationUpdate(false);
    void listProjectAudit(projectId, { page: 0, size: 1, action: "updated" })
      .then((page) => {
        if (!cancelled) {
          setHasPersistedConfigurationUpdate((page.items ?? []).length > 0);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setHasPersistedConfigurationUpdate(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [project?.id]);

  useEffect(() => {
    const params = new URLSearchParams(location.search);
    if (params.get("new") !== "1") {
      return;
    }
    setCreateDraft(emptyProjectDraft());
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
    const requiredReleaseArtifactSource = releaseArtifactSourceForDriver(draft.deploymentDriver);
    if (draft.releaseArtifactSource === requiredReleaseArtifactSource) {
      return;
    }
    setDraft((current) => ({
      ...current,
      releaseArtifactSource: requiredReleaseArtifactSource,
    }));
  }, [draft.deploymentDriver, draft.releaseArtifactSource]);

  useEffect(() => {
    if (draft.deploymentDriver !== "pipeline_trigger" || draft.driver.pipelineSystem === "azure_devops") {
      return;
    }
    setDraft((current) => ({
      ...current,
      driver: { ...current.driver, pipelineSystem: "azure_devops" },
    }));
  }, [draft.deploymentDriver, draft.driver.pipelineSystem]);

  const discovery = useProjectSettingsDiscovery({
    project,
    selectedProjectId,
    draft,
    setDraft,
    onDiscoverAdoBranches,
    onDiscoverAdoPipelines,
    onDiscoverAdoRepositories,
  });

  const targetCount = targets.length;
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
        discovery.selectedProviderConnection
        && (discovery.selectedProviderConnection.provider ?? "").toLowerCase() !== "azure_devops"
      ) {
        issues.push({
          id: "driver-provider-connection-provider",
          tab: "deployment-driver",
          fieldId: "driver-provider-connection-id",
          message: "Deployment Driver: linked deployment connection must use Azure DevOps.",
        });
      } else if (discovery.selectedProviderConnectionRequiresVerification) {
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
  }, [discovery.selectedProviderConnection, discovery.selectedProviderConnectionRequiresVerification, draft]);

  const releaseSourceConfigured = discovery.selectedReleaseIngestEndpoint !== null;
  const deploymentConfigured =
    draft.deploymentDriver === "pipeline_trigger"
      ? draft.providerConnectionId.trim() !== ""
        && draft.driver.organization.trim() !== ""
        && draft.driver.project.trim() !== ""
        && draft.driver.repository.trim() !== ""
        && draft.driver.pipelineId.trim() !== ""
      : draft.deploymentDriver === "azure_deployment_stack" || draft.deploymentDriver === "azure_template_spec";
  const runtimeHealthConfigured =
    draft.runtime.path.trim() !== ""
    && parseOptionalNumber(draft.runtime.expectedStatus) !== undefined
    && parseOptionalNumber(draft.runtime.timeoutMs) !== undefined;

  const persistedDriverConfig = asRecord(project?.deploymentDriverConfig);
  const persistedRuntimeConfig = asRecord(project?.runtimeHealthProviderConfig);
  const persistedRuntimeExpectedStatus =
    readStringField(persistedRuntimeConfig, "expectedStatus")
    || asNumberString(persistedRuntimeConfig.expectedStatus);
  const persistedRuntimeTimeoutMs =
    readStringField(persistedRuntimeConfig, "timeoutMs")
    || asNumberString(persistedRuntimeConfig.timeoutMs);
  const persistedReleaseSourceConfigured = (project?.releaseIngestEndpointId ?? "").trim() !== "";
  const persistedDeploymentConfigured =
    hasPersistedConfigurationUpdate
    && (
      project?.deploymentDriver === "pipeline_trigger"
        ? (project.providerConnectionId ?? "").trim() !== ""
          && readStringField(persistedDriverConfig, "organization") !== ""
          && readStringField(persistedDriverConfig, "project") !== ""
          && readStringField(persistedDriverConfig, "repository") !== ""
          && readStringField(persistedDriverConfig, "pipelineId") !== ""
        : project?.deploymentDriver === "azure_deployment_stack"
          || project?.deploymentDriver === "azure_template_spec"
    );
  const persistedRuntimeHealthConfigured =
    hasPersistedConfigurationUpdate
    && readStringField(persistedRuntimeConfig, "path") !== ""
    && parseOptionalNumber(persistedRuntimeExpectedStatus) !== undefined
    && parseOptionalNumber(persistedRuntimeTimeoutMs) !== undefined;

  const setupValidationIssues = useMemo(() => {
    const issues: DraftValidationIssue[] = [...draftValidationIssues];
    if (!releaseSourceConfigured) {
      issues.push({
        id: "release-source-required",
        tab: "release-ingest",
        fieldId: "release-ingest-endpoint-id",
        message: "Release Source: choose the release source MAPPO should listen to for this project.",
      });
    }
    if (!deploymentConfigured) {
      issues.push({
        id: "deployment-behavior-required",
        tab: "deployment-driver",
        fieldId: "deployment-method",
        message: "Deployment Driver: choose how MAPPO should roll releases out for this project.",
      });
    }
    if (!runtimeHealthConfigured) {
      issues.push({
        id: "runtime-health-required",
        tab: "runtime-health",
        fieldId: "runtime-path",
        message: "Runtime Health: configure how MAPPO should verify deployed targets.",
      });
    }
    return issues;
  }, [deploymentConfigured, draftValidationIssues, releaseSourceConfigured, runtimeHealthConfigured]);

  const configComplete =
    project !== null
    && persistedReleaseSourceConfigured
    && persistedDeploymentConfigured
    && persistedRuntimeHealthConfigured;
  const canPersist = project !== null && draftValidationIssues.length === 0;
  const canCreateProject = createDraft.name.trim() !== "";
  const activeSectionLabel =
    PROJECT_SECTIONS.find((section) => section.key === activeTab)?.label ?? "Project";
  const releaseSourceLabel =
    draft.deploymentDriver === "pipeline_trigger"
      ? RELEASE_SOURCE_TYPE_LABELS.external_deployment_inputs
      : RELEASE_SOURCE_TYPE_LABELS[draft.releaseArtifactSource];
  const selectedPipelineName = useMemo(() => {
    const currentValue = draft.driver.pipelineId.trim();
    if (currentValue === "") {
      return "";
    }
    return discovery.discoveredPipelines.find((pipeline) => pipeline.id === currentValue)?.name ?? currentValue;
  }, [discovery.discoveredPipelines, draft.driver.pipelineId]);
  const deploymentMethodOptions = DEPLOYMENT_METHODS_BY_SYSTEM[discovery.selectedDeploymentSystem];
  const selectedDiscoveredAdoProjectName =
    discovery.selectedDiscoveredAdoProject?.name || discovery.resolvedAdoProject || "";

  async function saveProjectConfig(): Promise<void> {
    if (!project?.id || draftValidationIssues.length > 0) {
      return;
    }
    setIsSaving(true);
    try {
      await onPatchProject(project.id, buildPatchRequest(draft));
      setHasPersistedConfigurationUpdate(true);
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
    if (setupValidationIssues.length > 0) {
      toast.warning("Validation has setup findings.");
      focusValidationIssue(setupValidationIssues[0]);
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
      setCreateDraft(emptyProjectDraft());
      setHasPersistedConfigurationUpdate(false);
    } catch (error) {
      toast.error((error as Error).message);
    } finally {
      setCreateSubmitting(false);
    }
  }

  function updateDraft<K extends keyof ProjectDraft>(key: K, value: ProjectDraft[K]): void {
    setDraft((current) => ({ ...current, [key]: value }));
  }

  function updateDeploymentSystem(system: typeof discovery.selectedDeploymentSystem): void {
    const normalizedSystem = normalizeDeploymentSystem(system);
    if (!normalizedSystem) {
      return;
    }
    setDraft((current) => applyDeploymentDriverSelection(current, firstDriverForDeploymentSystem(normalizedSystem)));
  }

  function updateDeploymentMethod(method: ProjectDraft["deploymentDriver"]): void {
    const normalizedMethod = normalizeDeploymentDriver(method);
    if (!normalizedMethod) {
      return;
    }
    setDraft((current) => applyDeploymentDriverSelection(current, normalizedMethod));
  }

  function updateCreateDrawerOpen(open: boolean): void {
    setCreateDrawerOpen(open);
    if (open) {
      setCreateDraft(emptyProjectDraft());
    }
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

  async function deleteCurrentProject(): Promise<void> {
    if (!project?.id) {
      return;
    }
    const confirmed = window.confirm(
      `Delete project ${project.name || project.id}? This removes its runs, targets, releases, and registration history.`
    );
    if (!confirmed) {
      return;
    }
    setIsDeleting(true);
    try {
      await onDeleteProject(project.id);
      toast.success(`Deleted project ${project.name || project.id}.`);
      navigate("/projects");
    } catch (error) {
      toast.error((error as Error).message);
    } finally {
      setIsDeleting(false);
    }
  }

  return (
    <section className="space-y-4">
      <ProjectSettingsChecklistCard
        hasProject={project !== null}
        configComplete={configComplete}
        targetCount={targetCount}
        projectReleaseCount={projectReleaseCount}
      />

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
            {project?.id ? (
              <Button
                type="button"
                variant="outline"
                className="border-destructive/60 text-destructive hover:bg-destructive/10 hover:text-destructive"
                onClick={() => void deleteCurrentProject()}
                disabled={isDeleting}
              >
                {isDeleting ? "Deleting..." : "Delete Project"}
              </Button>
            ) : null}
          </div>
        </CardHeader>
        <CardContent className="space-y-4">
          {setupValidationIssues.length > 0 ? (
            <div className="rounded-md border border-amber-400/60 bg-amber-500/10 p-3">
              <p className="mb-2 text-sm font-semibold text-amber-200">Inline validation</p>
              <ul className="space-y-1 text-xs text-amber-100">
                {setupValidationIssues.map((issue) => (
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

          <Card className="border-border/70 bg-background/50">
            <CardHeader className="pb-3">
              <CardTitle className="text-sm uppercase tracking-[0.08em]">Project basics</CardTitle>
              <p className="text-xs text-muted-foreground">
                Operator-facing project name and visual theme. These apply across the control plane and are always editable here.
              </p>
            </CardHeader>
            <CardContent>
              <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
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
                <div className="space-y-1">
                  <div className="flex items-center gap-1">
                    <Label htmlFor="project-theme">Project theme</Label>
                    <FieldHelpTooltip content="Visual theme used for this project in the control plane shell and project pages." />
                  </div>
                  <Select
                    value={draft.themeKey}
                    onValueChange={(value) => updateDraft("themeKey", value as ProjectThemeKey)}
                  >
                    <SelectTrigger id="project-theme">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      {PROJECT_THEME_OPTIONS.map((theme) => (
                        <SelectItem key={theme.key} value={theme.key}>
                          {theme.name}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                  <p className="text-xs text-muted-foreground">
                    {PROJECT_THEMES[draft.themeKey]?.description ?? PROJECT_THEMES[DEFAULT_THEME_KEY].description}
                  </p>
                </div>
              </div>
            </CardContent>
          </Card>

          <ProjectFlowDiagram
            projectName={draft.name.trim() || project?.name || "Project"}
            releaseSourceProvider={discovery.effectiveReleaseSystem}
            releaseSourceName={discovery.selectedReleaseIngestEndpoint?.name || discovery.selectedReleaseIngestEndpoint?.id || "No linked release source"}
            releaseSourceTypeLabel={discovery.releaseSourceTypeLabel}
            releaseSourceRecord={discovery.selectedReleaseIngestEndpoint}
            releaseSourceConfigured={releaseSourceConfigured}
            deploymentSystem={discovery.selectedDeploymentSystem}
            deploymentMethodLabel={DEPLOYMENT_DRIVER_LABELS[draft.deploymentDriver]}
            deploymentConnectionName={discovery.selectedProviderConnection?.name || discovery.selectedProviderConnection?.id || ""}
            azureDevOpsProjectName={selectedDiscoveredAdoProjectName}
            repositoryName={draft.driver.repository.trim()}
            pipelineName={selectedPipelineName}
            branchName={draft.driver.branch.trim()}
            deploymentConfigured={deploymentConfigured}
            targetCount={targetCount}
            projectReleaseCount={projectReleaseCount}
            targets={targets}
            runtimeHealthConfigured={runtimeHealthConfigured}
            runtimeHealthLabel={RUNTIME_HEALTH_LABELS[draft.runtimeHealthProvider] ?? "HTTP endpoint"}
            runtimeHealthPath={draft.runtime.path}
            runtimeHealthExpectedStatus={draft.runtime.expectedStatus}
            runtimeHealthTimeoutMs={draft.runtime.timeoutMs}
            activeSection={activeTab as ProjectFlowSection}
            onSectionSelect={(section) => setActiveTab(section)}
          />

          <div className="grid gap-4 xl:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
            <div className="min-w-0">
              <Card className="border-border/70 bg-background/50">
                <CardHeader className="pb-3">
                  <CardTitle>{activeSectionLabel}</CardTitle>
                  <p className="text-xs text-muted-foreground">
                    Select a node in the project flow above to configure that part of the setup.
                  </p>
                </CardHeader>
                <CardContent className="space-y-3">
                  <ProjectSettingsSectionContent
                    activeTab={activeTab}
                    draft={draft}
                    setDraft={setDraft}
                    targetCount={targetCount}
                    availableReleaseSystems={discovery.availableReleaseSystems}
                    effectiveReleaseSystem={discovery.effectiveReleaseSystem}
                    releaseSourceTypeLabel={discovery.releaseSourceTypeLabel}
                    releaseIngestEndpointOptions={discovery.releaseIngestEndpointOptions}
                    selectedReleaseIngestEndpoint={discovery.selectedReleaseIngestEndpoint}
                    isLoadingReleaseIngestEndpoints={discovery.isLoadingReleaseIngestEndpoints}
                    selectedDeploymentSystem={discovery.selectedDeploymentSystem}
                    deploymentMethodOptions={deploymentMethodOptions}
                    providerConnectionOptions={discovery.providerConnectionOptions}
                    isLoadingProviderConnections={discovery.isLoadingProviderConnections}
                    verifiedPipelineProviderConnections={discovery.verifiedPipelineProviderConnections}
                    selectedProviderConnection={discovery.selectedProviderConnection}
                    selectedProviderConnectionRequiresVerification={discovery.selectedProviderConnectionRequiresVerification}
                    isVerifiedAzureDevOpsConnection={discovery.isVerifiedAzureDevOpsConnection}
                    cachedProviderConnectionProjects={discovery.cachedProviderConnectionProjects}
                    canSelectAzureDevOpsProject={discovery.canSelectAzureDevOpsProject}
                    hasSelectedAzureDevOpsProject={discovery.hasSelectedAzureDevOpsProject}
                    canDiscoverAzureDevOpsProjectResources={discovery.canDiscoverAzureDevOpsProjectResources}
                    selectedDiscoveredAdoProjectId={discovery.selectedDiscoveredAdoProjectId}
                    selectedProviderConnectionDiscoveryUrl={discovery.selectedProviderConnectionDiscoveryUrl}
                    selectedDiscoveredRepositoryId={discovery.selectedDiscoveredRepositoryId}
                    selectedDiscoveredBranchRef={discovery.selectedDiscoveredBranchRef}
                    selectedDiscoveredPipelineId={discovery.selectedDiscoveredPipelineId}
                    discoveredRepositories={discovery.discoveredRepositories}
                    discoveredBranches={discovery.discoveredBranches}
                    discoveredPipelines={discovery.discoveredPipelines}
                    isDiscoveringRepositories={discovery.isDiscoveringRepositories}
                    isDiscoveringBranches={discovery.isDiscoveringBranches}
                    isDiscoveringPipelines={discovery.isDiscoveringPipelines}
                    repositoryDiscoveryError={discovery.repositoryDiscoveryError}
                    branchDiscoveryError={discovery.branchDiscoveryError}
                    pipelineDiscoveryError={discovery.pipelineDiscoveryError}
                    hasSingleCachedAdoProject={discovery.hasSingleCachedAdoProject}
                    hasSingleDiscoveredRepository={discovery.hasSingleDiscoveredRepository}
                    hasSingleDiscoveredBranch={discovery.hasSingleDiscoveredBranch}
                    hasSingleDiscoveredPipeline={discovery.hasSingleDiscoveredPipeline}
                    hasSavedPipelineOutsideDiscovery={discovery.hasSavedPipelineOutsideDiscovery}
                    isValidating={isValidating}
                    canValidateCredentials={Boolean(project?.id)}
                    onSelectReleaseSystem={(value) => discovery.setSelectedReleaseSystem(value)}
                    onUpdateReleaseIngestEndpoint={(value) => updateDraft("releaseIngestEndpointId", value)}
                    onRefreshReleaseSources={() => {
                      void discovery.refreshReleaseIngestEndpointOptions();
                    }}
                    onUpdateDeploymentSystem={updateDeploymentSystem}
                    onUpdateDeploymentMethod={updateDeploymentMethod}
                    onRefreshConnections={() => {
                      void discovery.refreshProviderConnectionOptions();
                    }}
                    onDiscoverRepositories={() => {
                      void discovery.discoverAdoRepositories();
                    }}
                    onDiscoverBranches={() => {
                      void discovery.discoverAdoBranches();
                    }}
                    onDiscoverPipelines={() => {
                      void discovery.discoverAdoPipelines();
                    }}
                    onRunCredentialsValidation={() => {
                      void runValidation(["credentials"]);
                    }}
                  />
                </CardContent>
              </Card>
            </div>

            <ProjectSettingsSummaryCard
              releaseSourceConfigured={releaseSourceConfigured}
              effectiveReleaseSystem={discovery.effectiveReleaseSystem}
              releaseSourceLabel={releaseSourceLabel}
              selectedReleaseIngestEndpoint={discovery.selectedReleaseIngestEndpoint}
              deploymentConfigured={deploymentConfigured}
              selectedDeploymentSystem={discovery.selectedDeploymentSystem}
              draft={draft}
              selectedProviderConnection={discovery.selectedProviderConnection}
              selectedDiscoveredAdoProjectName={selectedDiscoveredAdoProjectName}
              discoveredPipelines={discovery.discoveredPipelines}
              runtimeHealthConfigured={runtimeHealthConfigured}
            />
          </div>
        </CardContent>
      </Card>

      <ProjectSettingsCreateDrawer
        open={createDrawerOpen}
        createDraft={createDraft}
        canCreateProject={canCreateProject}
        createSubmitting={createSubmitting}
        onOpenChange={updateCreateDrawerOpen}
        onSubmit={(event) => {
          void handleCreateProject(event);
        }}
        onUpdateName={(name) => {
          setCreateDraft((current) => ({ ...current, name }));
        }}
        onUpdateTheme={(themeKey) => {
          setCreateDraft((current) => ({ ...current, themeKey }));
        }}
      />
    </section>
  );
}
