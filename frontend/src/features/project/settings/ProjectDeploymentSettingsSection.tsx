import type { Dispatch, SetStateAction } from "react";

import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import FieldHelpTooltip from "@/features/shared/FieldHelpTooltip";
import type {
  ProjectAdoBranch,
  ProjectAdoPipeline,
  ProjectAdoRepository,
  ProviderConnection,
} from "@/lib/types";
import type { DeploymentSystem, ProjectDraft } from "@/features/project/settings/shared";
import {
  DEPLOYMENT_DRIVER_HELP,
  DEPLOYMENT_DRIVER_LABELS,
  DEPLOYMENT_SYSTEM_LABELS,
} from "@/features/project/settings/shared";

type ProjectDeploymentSettingsSectionProps = {
  draft: ProjectDraft;
  setDraft: Dispatch<SetStateAction<ProjectDraft>>;
  selectedDeploymentSystem: DeploymentSystem;
  deploymentMethodOptions: ProjectDraft["deploymentDriver"][];
  providerConnectionOptions: ProviderConnection[];
  isLoadingProviderConnections: boolean;
  verifiedPipelineProviderConnections: ProviderConnection[];
  selectedProviderConnection: ProviderConnection | null;
  selectedProviderConnectionRequiresVerification: boolean;
  isVerifiedAzureDevOpsConnection: (connection: ProviderConnection | null | undefined) => boolean;
  cachedProviderConnectionProjects: ProviderConnection["discoveredProjects"] extends infer T
    ? T extends Array<infer Item>
      ? Item[]
      : never[]
    : never[];
  canSelectAzureDevOpsProject: boolean;
  hasSelectedAzureDevOpsProject: boolean;
  canDiscoverAzureDevOpsProjectResources: boolean;
  selectedDiscoveredAdoProjectId: string;
  selectedProviderConnectionDiscoveryUrl: string;
  selectedDiscoveredRepositoryId: string;
  selectedDiscoveredBranchRef: string;
  selectedDiscoveredPipelineId: string;
  discoveredRepositories: ProjectAdoRepository[];
  discoveredBranches: ProjectAdoBranch[];
  discoveredPipelines: ProjectAdoPipeline[];
  isDiscoveringRepositories: boolean;
  isDiscoveringBranches: boolean;
  isDiscoveringPipelines: boolean;
  repositoryDiscoveryError: string;
  branchDiscoveryError: string;
  pipelineDiscoveryError: string;
  hasSingleCachedAdoProject: boolean;
  hasSingleDiscoveredRepository: boolean;
  hasSingleDiscoveredBranch: boolean;
  hasSingleDiscoveredPipeline: boolean;
  hasSavedPipelineOutsideDiscovery: boolean;
  isValidating: boolean;
  canValidateCredentials: boolean;
  onUpdateDeploymentSystem: (system: DeploymentSystem) => void;
  onUpdateDeploymentMethod: (method: ProjectDraft["deploymentDriver"]) => void;
  onRefreshConnections: () => void;
  onDiscoverRepositories: () => void;
  onDiscoverBranches: () => void;
  onDiscoverPipelines: () => void;
  onRunCredentialsValidation: () => void;
};

export default function ProjectDeploymentSettingsSection({
  draft,
  setDraft,
  selectedDeploymentSystem,
  deploymentMethodOptions,
  providerConnectionOptions,
  isLoadingProviderConnections,
  verifiedPipelineProviderConnections,
  selectedProviderConnection,
  selectedProviderConnectionRequiresVerification,
  isVerifiedAzureDevOpsConnection,
  cachedProviderConnectionProjects,
  canSelectAzureDevOpsProject,
  hasSelectedAzureDevOpsProject,
  canDiscoverAzureDevOpsProjectResources,
  selectedDiscoveredAdoProjectId,
  selectedProviderConnectionDiscoveryUrl,
  selectedDiscoveredRepositoryId,
  selectedDiscoveredBranchRef,
  selectedDiscoveredPipelineId,
  discoveredRepositories,
  discoveredBranches,
  discoveredPipelines,
  isDiscoveringRepositories,
  isDiscoveringBranches,
  isDiscoveringPipelines,
  repositoryDiscoveryError,
  branchDiscoveryError,
  pipelineDiscoveryError,
  hasSingleCachedAdoProject,
  hasSingleDiscoveredRepository,
  hasSingleDiscoveredBranch,
  hasSingleDiscoveredPipeline,
  hasSavedPipelineOutsideDiscovery,
  isValidating,
  canValidateCredentials,
  onUpdateDeploymentSystem,
  onUpdateDeploymentMethod,
  onRefreshConnections,
  onDiscoverRepositories,
  onDiscoverBranches,
  onDiscoverPipelines,
  onRunCredentialsValidation,
}: ProjectDeploymentSettingsSectionProps) {
  const isPipelineDriver = draft.deploymentDriver === "pipeline_trigger";

  return (
    <div className="space-y-3">
      <div className="space-y-3">
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          <div className="space-y-1">
            <div className="flex items-center gap-1">
              <Label htmlFor="deployment-system">Deployment system</Label>
              <FieldHelpTooltip content="Which external system or Azure control plane MAPPO uses for this project's rollouts." />
            </div>
            <Select
              value={selectedDeploymentSystem}
              onValueChange={(value) => onUpdateDeploymentSystem(value as DeploymentSystem)}
            >
              <SelectTrigger id="deployment-system">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="azure">{DEPLOYMENT_SYSTEM_LABELS.azure}</SelectItem>
                <SelectItem value="azure_devops">{DEPLOYMENT_SYSTEM_LABELS.azure_devops}</SelectItem>
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
            <Select
              value={draft.deploymentDriver}
              onValueChange={(value) => onUpdateDeploymentMethod(value as ProjectDraft["deploymentDriver"])}
              disabled={deploymentMethodOptions.length <= 1}
            >
              <SelectTrigger id="deployment-method">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {deploymentMethodOptions.map((method) => (
                  <SelectItem key={method} value={method}>
                    {DEPLOYMENT_DRIVER_LABELS[method]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <p className="text-xs text-muted-foreground">
              {deploymentMethodOptions.length <= 1
                ? `${DEPLOYMENT_SYSTEM_LABELS[selectedDeploymentSystem]} currently supports one deployment method in MAPPO.`
                : "Choose how MAPPO should deploy through the selected deployment system."}
            </p>
          </div>
          {isPipelineDriver ? (
            <div className="space-y-1 md:col-span-2">
              <div className="flex items-center gap-1">
                <Label htmlFor="driver-provider-connection-id">Deployment connection</Label>
                <FieldHelpTooltip content="Verified Azure DevOps API connection from Admin > Deployment Connections. This owns authentication and browse scope; Project Config only chooses which discovered Azure DevOps project, repository, and pipeline this MAPPO project should use." />
              </div>
              <div className="flex flex-col gap-2">
                <Select
                  value={draft.providerConnectionId.trim() === "" ? "__none" : draft.providerConnectionId}
                  onValueChange={(value) =>
                    setDraft((current) => ({
                      ...current,
                      providerConnectionId: value === "__none" ? "" : value,
                    }))
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
                    onClick={onRefreshConnections}
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
                    No verified Azure DevOps deployment connections are available yet. Open Admin → Deployment Connections,
                    add one, and verify it.
                  </p>
                ) : null}
                {selectedProviderConnectionRequiresVerification ? (
                  <p className="rounded-md border border-amber-400/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-100">
                    The selected deployment connection still needs verification before MAPPO can load Azure DevOps
                    projects. Open Admin → Deployment Connections, edit{" "}
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
                Pipeline Trigger deploys through <span className="font-medium text-foreground">Azure DevOps</span>.
                First choose a verified <span className="font-medium text-foreground">Deployment connection</span>. That
                admin-managed connection owns Azure DevOps authentication and account scope; this screen only asks you to
                choose the Azure DevOps project resources MAPPO should use.
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
                            Verify the selected <span className="font-medium text-foreground">Deployment connection</span>{" "}
                            in Admin before configuring this project.
                          </>
                        )
                      : (
                          <>
                            No Azure DevOps projects are available for the selected{" "}
                            <span className="font-medium text-foreground">Deployment connection</span>. Open Admin →
                            Deployment Connections and verify access for that connection first.
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
                      Choose the Azure DevOps project this MAPPO project deploys through. MAPPO reads this list from the
                      selected deployment connection after it has been verified in Admin → Deployment Connections.
                    </p>
                  </div>
                  <Badge variant="secondary">
                    {cachedProviderConnectionProjects.length} project
                    {cachedProviderConnectionProjects.length === 1 ? "" : "s"} available
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
                            deriveAzureDevOpsAccountUrl(selectedProject.webUrl ?? "")
                            || selectedProviderConnectionDiscoveryUrl;
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
                  The selected <span className="font-medium text-foreground">Deployment connection</span> defines which
                  Azure DevOps account MAPPO can browse. This screen only uses Azure DevOps projects already verified and
                  loaded in Admin → Deployment Connections.
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
                  Select an <span className="font-medium text-foreground">Azure DevOps project</span> above to continue
                  with repository and pipeline setup.
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
                        Select the default repository for this MAPPO project. MAPPO loads repositories from the selected
                        Azure DevOps project automatically and auto-selects the only match when there is one.
                      </p>
                    </div>
                    <Button
                      id="driver-repository-discovery-action"
                      type="button"
                      variant="outline"
                      disabled={isDiscoveringRepositories || !canDiscoverAzureDevOpsProjectResources}
                      onClick={onDiscoverRepositories}
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
                    MAPPO starts this automatically after you choose an Azure DevOps project. Use{" "}
                    <span className="font-medium text-foreground">Reload repositories</span> only if that project changed
                    recently.
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
                      <div className="flex items-center justify-between gap-2">
                        <div className="flex items-center gap-1">
                          <Label htmlFor="driver-branch-select">Branch</Label>
                          <FieldHelpTooltip content="Git branch/ref MAPPO passes when it queues the Azure DevOps pipeline run. MAPPO loads branches from the selected repository automatically and keeps a manual fallback only when Azure DevOps does not return any." />
                        </div>
                        <Button
                          id="driver-branch-discovery-action"
                          type="button"
                          variant="outline"
                          size="sm"
                          disabled={isDiscoveringBranches || draft.driver.repository.trim() === ""}
                          onClick={onDiscoverBranches}
                        >
                          {isDiscoveringBranches
                            ? "Loading..."
                            : branchDiscoveryError
                              ? "Retry"
                              : discoveredBranches.length > 0
                                ? "Reload branches"
                                : "Load branches"}
                        </Button>
                      </div>
                      {discoveredBranches.length > 0 ? (
                        <Select
                          value={selectedDiscoveredBranchRef}
                          onValueChange={(value) => {
                            if (value === "__none") {
                              setDraft((current) => ({
                                ...current,
                                driver: { ...current.driver, branch: "" },
                              }));
                              return;
                            }
                            const selected = discoveredBranches.find(
                              (branch) => branch.refName === value || branch.name === value
                            );
                            if (!selected) {
                              return;
                            }
                            setDraft((current) => ({
                              ...current,
                              driver: { ...current.driver, branch: selected.name },
                            }));
                          }}
                        >
                          <SelectTrigger id="driver-branch-select">
                            <SelectValue placeholder="Select discovered branch" />
                          </SelectTrigger>
                          <SelectContent>
                            <SelectItem value="__none">Select discovered branch</SelectItem>
                            {discoveredBranches.map((branch) => (
                              <SelectItem key={branch.refName} value={branch.refName}>
                                {branch.name}
                              </SelectItem>
                            ))}
                          </SelectContent>
                        </Select>
                      ) : (
                        <>
                          <p
                            id="driver-branch-select"
                            className="rounded-md border border-dashed border-border/60 px-3 py-2 text-xs text-muted-foreground"
                          >
                            {draft.driver.repository.trim() === ""
                              ? "Choose a repository first so MAPPO knows which Azure DevOps branches to load."
                              : isDiscoveringBranches
                                ? "Loading Azure DevOps branches from the selected repository..."
                                : "MAPPO has not loaded any branches from Azure DevOps for this repository yet. Use Load branches above. If Azure DevOps still returns none, use the manual fallback below."}
                          </p>
                          <Accordion type="single" collapsible className="rounded-md border border-border/60 bg-background/40 px-3">
                            <AccordionItem value="manual-branch" className="border-none">
                              <AccordionTrigger className="py-2 text-sm font-medium text-foreground hover:no-underline">
                                Manual fallback
                              </AccordionTrigger>
                              <AccordionContent className="space-y-2 pt-1">
                                <p className="text-xs text-muted-foreground">
                                  Use this only if Azure DevOps does not return repository branches to MAPPO. Enter the
                                  branch name exactly as the pipeline expects it.
                                </p>
                                <Input
                                  id="driver-branch"
                                  value={draft.driver.branch}
                                  onChange={(event) =>
                                    setDraft((current) => ({
                                      ...current,
                                      driver: { ...current.driver, branch: event.target.value },
                                    }))
                                  }
                                  placeholder="For example main"
                                />
                              </AccordionContent>
                            </AccordionItem>
                          </Accordion>
                        </>
                      )}
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
                      {hasSingleDiscoveredBranch && draft.driver.branch.trim() !== "" ? (
                        <p className="text-xs text-emerald-300">
                          MAPPO auto-selected the only branch returned by Azure DevOps.
                        </p>
                      ) : null}
                    </div>
                  </div>
                  {repositoryDiscoveryError ? (
                    <p className="mt-3 rounded-md border border-amber-400/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-100">
                      {repositoryDiscoveryError}
                    </p>
                  ) : null}
                  {branchDiscoveryError ? (
                    <p className="mt-3 rounded-md border border-amber-400/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-100">
                      {branchDiscoveryError}
                    </p>
                  ) : null}
                </div>

                <div className="rounded-md border border-border/70 bg-background/60 p-3">
                  <div className="flex flex-col gap-2 md:flex-row md:items-start md:justify-between">
                    <div>
                      <h4 className="text-sm font-semibold text-foreground">Azure DevOps Pipeline</h4>
                      <p className="mt-1 text-xs text-muted-foreground">
                        Choose the pipeline MAPPO should trigger when operators start a deployment run. MAPPO loads
                        pipelines from the selected Azure DevOps project automatically and auto-selects the only match when
                        there is one.
                      </p>
                    </div>
                    <Button
                      id="driver-pipeline-discovery-action"
                      type="button"
                      variant="outline"
                      disabled={isDiscoveringPipelines || !canDiscoverAzureDevOpsProjectResources}
                      onClick={onDiscoverPipelines}
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
                    MAPPO starts this automatically after you choose an Azure DevOps project. Use{" "}
                    <span className="font-medium text-foreground">Reload pipelines</span> only if that project changed
                    recently.
                  </p>
                  <div className="mt-3 space-y-1">
                    <div className="flex items-center gap-1">
                      <Label htmlFor="driver-pipeline-select">Azure DevOps Pipeline</Label>
                      <FieldHelpTooltip content="Pipeline MAPPO queues when an operator starts a deployment run for this project." />
                    </div>
                    {discoveredPipelines.length > 0 || draft.driver.pipelineId.trim() !== "" ? (
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
                          {hasSavedPipelineOutsideDiscovery ? (
                            <SelectItem value={draft.driver.pipelineId.trim()}>
                              Saved pipeline ID {draft.driver.pipelineId.trim()}
                            </SelectItem>
                          ) : null}
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
                    {hasSavedPipelineOutsideDiscovery ? (
                      <p className="text-xs text-muted-foreground">
                        Saved pipeline ID <span className="font-medium text-foreground">{draft.driver.pipelineId.trim()}</span>{" "}
                        will resolve to a name after MAPPO reloads Azure DevOps pipelines.
                      </p>
                    ) : null}
                  </div>
                  {pipelineDiscoveryError ? (
                    <p className="mt-3 rounded-md border border-amber-400/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-100">
                      {pipelineDiscoveryError}
                    </p>
                  ) : null}
                </div>
              </>
            ) : null}
          </div>
        ) : (
          <div className="space-y-3">
            <div className="rounded-md border border-border/70 bg-background/40 p-3">
              <p className="text-sm font-semibold text-foreground">MAPPO Azure API</p>
              <p className="mt-2 text-xs text-muted-foreground">
                MAPPO uses its Azure credentials and Azure SDK/ARM calls to update each selected target.
              </p>
              <p className="mt-2 text-xs text-muted-foreground">
                No deployment connection is required for this mode because MAPPO owns the Azure API call.
              </p>
            </div>
            <div className="flex justify-end">
              <Button
                type="button"
                variant="outline"
                onClick={onRunCredentialsValidation}
                disabled={!canValidateCredentials || isValidating}
              >
                Check Azure access
              </Button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

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
