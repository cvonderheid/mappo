import type { Dispatch, SetStateAction } from "react";

import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import FieldHelpTooltip from "@/features/shared/FieldHelpTooltip";
import type {
  ProjectAdoBranch,
  ProjectAdoPipeline,
  ProjectAdoRepository,
  ProviderConnection,
  ReleaseIngestEndpoint,
} from "@/lib/types";
import type { DeploymentSystem, ProjectDraft, ProjectTab, ReleaseSystem } from "@/features/project/settings/shared";
import ProjectDeploymentSettingsSection from "@/features/project/settings/ProjectDeploymentSettingsSection";
import ProjectReleaseSettingsSection from "@/features/project/settings/ProjectReleaseSettingsSection";

type ProjectSettingsSectionContentProps = {
  activeTab: ProjectTab;
  draft: ProjectDraft;
  setDraft: Dispatch<SetStateAction<ProjectDraft>>;
  targetCount: number;
  availableReleaseSystems: ReleaseSystem[];
  effectiveReleaseSystem: ReleaseSystem;
  releaseSourceTypeLabel: string;
  releaseIngestEndpointOptions: ReleaseIngestEndpoint[];
  selectedReleaseIngestEndpoint: ReleaseIngestEndpoint | null;
  isLoadingReleaseIngestEndpoints: boolean;
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
  onSelectReleaseSystem: (value: ReleaseSystem) => void;
  onUpdateReleaseIngestEndpoint: (value: string) => void;
  onRefreshReleaseSources: () => void;
  onUpdateDeploymentSystem: (system: DeploymentSystem) => void;
  onUpdateDeploymentMethod: (method: ProjectDraft["deploymentDriver"]) => void;
  onRefreshConnections: () => void;
  onDiscoverRepositories: () => void;
  onDiscoverBranches: () => void;
  onDiscoverPipelines: () => void;
  onRunCredentialsValidation: () => void;
};

export default function ProjectSettingsSectionContent(props: ProjectSettingsSectionContentProps) {
  const {
    activeTab,
    draft,
    setDraft,
    targetCount,
    availableReleaseSystems,
    effectiveReleaseSystem,
    releaseSourceTypeLabel,
    releaseIngestEndpointOptions,
    selectedReleaseIngestEndpoint,
    isLoadingReleaseIngestEndpoints,
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
    onSelectReleaseSystem,
    onUpdateReleaseIngestEndpoint,
    onRefreshReleaseSources,
    onUpdateDeploymentSystem,
    onUpdateDeploymentMethod,
    onRefreshConnections,
    onDiscoverRepositories,
    onDiscoverBranches,
    onDiscoverPipelines,
    onRunCredentialsValidation,
  } = props;

  if (activeTab === "general") {
    return (
      <div className="space-y-3">
        <div className="rounded-md border border-border/70 bg-background/60 p-3">
          <p className="text-sm font-semibold text-foreground">MAPPO project record</p>
          <p className="mt-2 text-xs text-muted-foreground">
            Project name and theme are configured in Project basics above. Select another node in the flow to configure
            release source, deployment behavior, targets, or runtime health.
          </p>
        </div>
      </div>
    );
  }

  if (activeTab === "release-ingest") {
    return (
      <ProjectReleaseSettingsSection
        draft={draft}
        availableReleaseSystems={availableReleaseSystems}
        effectiveReleaseSystem={effectiveReleaseSystem}
        releaseSourceTypeLabel={releaseSourceTypeLabel}
        releaseIngestEndpointOptions={releaseIngestEndpointOptions}
        selectedReleaseIngestEndpoint={selectedReleaseIngestEndpoint}
        isLoadingReleaseIngestEndpoints={isLoadingReleaseIngestEndpoints}
        onSelectReleaseSystem={onSelectReleaseSystem}
        onUpdateReleaseIngestEndpoint={onUpdateReleaseIngestEndpoint}
        onRefreshReleaseSources={onRefreshReleaseSources}
      />
    );
  }

  if (activeTab === "deployment-driver") {
    return (
      <ProjectDeploymentSettingsSection
        draft={draft}
        setDraft={setDraft}
        selectedDeploymentSystem={selectedDeploymentSystem}
        deploymentMethodOptions={deploymentMethodOptions}
        providerConnectionOptions={providerConnectionOptions}
        isLoadingProviderConnections={isLoadingProviderConnections}
        verifiedPipelineProviderConnections={verifiedPipelineProviderConnections}
        selectedProviderConnection={selectedProviderConnection}
        selectedProviderConnectionRequiresVerification={selectedProviderConnectionRequiresVerification}
        isVerifiedAzureDevOpsConnection={isVerifiedAzureDevOpsConnection}
        cachedProviderConnectionProjects={cachedProviderConnectionProjects}
        canSelectAzureDevOpsProject={canSelectAzureDevOpsProject}
        hasSelectedAzureDevOpsProject={hasSelectedAzureDevOpsProject}
        canDiscoverAzureDevOpsProjectResources={canDiscoverAzureDevOpsProjectResources}
        selectedDiscoveredAdoProjectId={selectedDiscoveredAdoProjectId}
        selectedProviderConnectionDiscoveryUrl={selectedProviderConnectionDiscoveryUrl}
        selectedDiscoveredRepositoryId={selectedDiscoveredRepositoryId}
        selectedDiscoveredBranchRef={selectedDiscoveredBranchRef}
        selectedDiscoveredPipelineId={selectedDiscoveredPipelineId}
        discoveredRepositories={discoveredRepositories}
        discoveredBranches={discoveredBranches}
        discoveredPipelines={discoveredPipelines}
        isDiscoveringRepositories={isDiscoveringRepositories}
        isDiscoveringBranches={isDiscoveringBranches}
        isDiscoveringPipelines={isDiscoveringPipelines}
        repositoryDiscoveryError={repositoryDiscoveryError}
        branchDiscoveryError={branchDiscoveryError}
        pipelineDiscoveryError={pipelineDiscoveryError}
        hasSingleCachedAdoProject={hasSingleCachedAdoProject}
        hasSingleDiscoveredRepository={hasSingleDiscoveredRepository}
        hasSingleDiscoveredBranch={hasSingleDiscoveredBranch}
        hasSingleDiscoveredPipeline={hasSingleDiscoveredPipeline}
        hasSavedPipelineOutsideDiscovery={hasSavedPipelineOutsideDiscovery}
        isValidating={isValidating}
        canValidateCredentials={canValidateCredentials}
        onUpdateDeploymentSystem={onUpdateDeploymentSystem}
        onUpdateDeploymentMethod={onUpdateDeploymentMethod}
        onRefreshConnections={onRefreshConnections}
        onDiscoverRepositories={onDiscoverRepositories}
        onDiscoverBranches={onDiscoverBranches}
        onDiscoverPipelines={onDiscoverPipelines}
        onRunCredentialsValidation={onRunCredentialsValidation}
      />
    );
  }

  if (activeTab === "targets") {
    return (
      <div className="space-y-3">
        <div className="rounded-md border border-border/70 bg-background/60 p-3">
          <p className="text-sm font-semibold text-foreground">Targets are managed from Project → Targets</p>
          <p className="mt-2 text-xs text-muted-foreground">
            This project currently has {targetCount} registered target{targetCount === 1 ? "" : "s"}. Use the Targets
            page to add, import, refresh, edit, or remove target registrations.
          </p>
        </div>
        <div className="grid grid-cols-1 gap-3 md:grid-cols-2">
          <div className="rounded-md border border-border/70 bg-background/40 p-3">
            <p className="text-xs uppercase tracking-[0.08em] text-muted-foreground">Registration model</p>
            <p className="mt-2 text-sm text-foreground">Project-scoped targets</p>
          </div>
          <div className="rounded-md border border-border/70 bg-background/40 p-3">
            <p className="text-xs uppercase tracking-[0.08em] text-muted-foreground">Registered targets</p>
            <p className="mt-2 text-sm text-foreground">{targetCount}</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="space-y-3">
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
    </div>
  );
}
