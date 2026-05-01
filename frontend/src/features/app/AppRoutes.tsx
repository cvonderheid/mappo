import { Suspense, type FormEvent } from "react";
import { Navigate, Route, Routes, type NavigateFunction } from "react-router-dom";

import AppRouteLoadingFallback from "@/features/app/AppRouteLoadingFallback";
import DeploymentRunDetailRoute from "@/features/app/DeploymentRunDetailRoute";
import {
  DemoPanel,
  DeploymentsPage,
  ForwarderLogsPage,
  ProjectSettingsPage,
  ProviderConnectionsConfigPage,
  ReleaseIngestConfigPage,
  ReleasesPage,
  SecretReferencesConfigPage,
  TargetsPage,
} from "@/features/app/lazyRoutes";
import type {
  MarketplaceEventIngestRequest,
  MarketplaceEventIngestResponse,
  ProjectAdoBranchDiscoveryResult,
  ProjectAdoPipelineDiscoveryResult,
  ProjectAdoRepositoryDiscoveryResult,
  ProjectConfigurationPatchRequest,
  ProjectCreateRequest,
  ProjectDefinition,
  ProjectValidationRequest,
  ProjectValidationResult,
  Release,
  ReleaseManifestIngestRequest,
  ReleaseManifestIngestResponse,
  RunDetail,
  RunPreview,
  RunStatus,
  RunSummary,
  Target,
  TargetRegistrationRecord,
  UpdateTargetRegistrationRequest,
} from "@/lib/types";
import type { StartRunFormState } from "@/lib/deployment-form";

type AppRoutesProps = {
  errorMessage: string;
  formState: StartRunFormState;
  isSubmitting: boolean;
  isPreviewing: boolean;
  previewElapsedSeconds: number;
  previewErrorMessage: string;
  previewProgressPercent: number;
  previewTargetCount: number;
  releases: Release[];
  runPreview: RunPreview | null;
  runs: RunSummary[];
  runPage: number;
  runPageSize: number;
  runTotalItems: number;
  runTotalPages: number;
  runIdFilter: string;
  runReleaseFilter: string;
  runStatusFilter: RunStatus | "";
  selectedRelease: Release | null;
  selectedReleaseId: string;
  selectedTargetIds: string[];
  targetGroupFilter: string;
  targets: Target[];
  controlsOpen: boolean;
  runDetail: RunDetail | null;
  projects: ProjectDefinition[];
  selectedProject: ProjectDefinition | null;
  selectedProjectId: string;
  latestRelease: Release | null;
  projectTargets: Target[];
  projectReleases: Release[];
  registrations: TargetRegistrationRecord[];
  refreshKey: number;
  targetRefreshKey?: number;
  adminErrorMessage: string;
  adminIsSubmitting: boolean;
  adminResult: MarketplaceEventIngestResponse | null;
  releaseIngestIsSubmitting: boolean;
  navigate: NavigateFunction;
  onFormStateChange: (value: StartRunFormState) => void;
  onOpenRun: (runId: string) => void;
  onReleaseChange: (releaseId: string) => void;
  onCloneRun: (runId: string) => void;
  onRetryFailed: (runId: string) => void;
  onRunIdFilterChange: (value: string) => void;
  onRunReleaseFilterChange: (value: string) => void;
  onRunStatusFilterChange: (value: string) => void;
  onRunsPageChange: (page: number) => void;
  onRunsPageSizeChange: (size: number) => void;
  onResumeRun: (runId: string) => void;
  onSelectedTargetIdsChange: (value: string[]) => void;
  onStartRun: (event: FormEvent<HTMLFormElement>) => Promise<void>;
  onControlsOpenChange: (open: boolean) => void;
  onTargetGroupFilterChange: (value: string) => void;
  onRunActionsMenuOpenChange: (open: boolean) => void;
  onPreviewRun: () => Promise<void>;
  onCancelPreview: () => void;
  onRunChange: (runId: string) => void;
  onIngestMarketplaceEvent: (request: MarketplaceEventIngestRequest, ingestToken?: string) => Promise<void>;
  onRegisterOperatorTarget: (request: MarketplaceEventIngestRequest) => Promise<void>;
  onUpdateTargetRegistration: (targetId: string, request: UpdateTargetRegistrationRequest) => Promise<void>;
  onDeleteTargetRegistration: (targetId: string) => Promise<void>;
  onRefreshRegistrations: () => Promise<void>;
  onIngestManagedAppReleases: (request?: ReleaseManifestIngestRequest) => Promise<ReleaseManifestIngestResponse>;
  onCreateProject: (request: ProjectCreateRequest) => Promise<ProjectDefinition>;
  onPatchProject: (projectId: string, request: ProjectConfigurationPatchRequest) => Promise<ProjectDefinition>;
  onDeleteProject: (projectId: string) => Promise<void>;
  onValidateProject: (projectId: string, request: ProjectValidationRequest) => Promise<ProjectValidationResult>;
  onDiscoverAdoBranches: (
    projectId: string,
    request: {
      organization?: string;
      project?: string;
      providerConnectionId?: string;
      repositoryId?: string;
      repository?: string;
      nameContains?: string;
    }
  ) => Promise<ProjectAdoBranchDiscoveryResult>;
  onDiscoverAdoRepositories: (
    projectId: string,
    request: {
      organization?: string;
      project?: string;
      providerConnectionId?: string;
      nameContains?: string;
    }
  ) => Promise<ProjectAdoRepositoryDiscoveryResult>;
  onDiscoverAdoPipelines: (
    projectId: string,
    request: {
      organization?: string;
      project?: string;
      providerConnectionId?: string;
      nameContains?: string;
    }
  ) => Promise<ProjectAdoPipelineDiscoveryResult>;
};

export default function AppRoutes({
  errorMessage,
  formState,
  isSubmitting,
  isPreviewing,
  previewElapsedSeconds,
  previewErrorMessage,
  previewProgressPercent,
  previewTargetCount,
  releases,
  runPreview,
  runs,
  runPage,
  runPageSize,
  runTotalItems,
  runTotalPages,
  runIdFilter,
  runReleaseFilter,
  runStatusFilter,
  selectedRelease,
  selectedReleaseId,
  selectedTargetIds,
  targetGroupFilter,
  targets,
  controlsOpen,
  runDetail,
  projects,
  selectedProject,
  selectedProjectId,
  latestRelease,
  projectTargets,
  projectReleases,
  registrations,
  refreshKey,
  targetRefreshKey,
  adminErrorMessage,
  adminIsSubmitting,
  adminResult,
  releaseIngestIsSubmitting,
  navigate,
  onFormStateChange,
  onOpenRun,
  onReleaseChange,
  onCloneRun,
  onRetryFailed,
  onRunIdFilterChange,
  onRunReleaseFilterChange,
  onRunStatusFilterChange,
  onRunsPageChange,
  onRunsPageSizeChange,
  onResumeRun,
  onSelectedTargetIdsChange,
  onStartRun,
  onControlsOpenChange,
  onTargetGroupFilterChange,
  onRunActionsMenuOpenChange,
  onPreviewRun,
  onCancelPreview,
  onRunChange,
  onIngestMarketplaceEvent,
  onRegisterOperatorTarget,
  onUpdateTargetRegistration,
  onDeleteTargetRegistration,
  onRefreshRegistrations,
  onIngestManagedAppReleases,
  onCreateProject,
  onPatchProject,
  onDeleteProject,
  onValidateProject,
  onDiscoverAdoBranches,
  onDiscoverAdoRepositories,
  onDiscoverAdoPipelines,
}: AppRoutesProps) {
  return (
    <Suspense fallback={<AppRouteLoadingFallback />}>
      <Routes>
        <Route path="/" element={<Navigate to="/targets" replace />} />
        <Route path="/fleet" element={<Navigate to="/targets" replace />} />
        <Route
          path="/projects"
          element={
            <ProjectSettingsPage
              project={selectedProject}
              projects={projects}
              selectedProjectId={selectedProjectId}
              targets={projectTargets}
              projectReleaseCount={projectReleases.length}
              onCreateProject={onCreateProject}
              onPatchProject={onPatchProject}
              onDeleteProject={onDeleteProject}
              onValidateProject={onValidateProject}
              onDiscoverAdoBranches={onDiscoverAdoBranches}
              onDiscoverAdoRepositories={onDiscoverAdoRepositories}
              onDiscoverAdoPipelines={onDiscoverAdoPipelines}
            />
          }
        />
        <Route
          path="/secret-references"
          element={<SecretReferencesConfigPage selectedProjectId={selectedProjectId} />}
        />
        <Route
          path="/deployment-connections"
          element={<ProviderConnectionsConfigPage selectedProjectId={selectedProjectId} />}
        />
        <Route
          path="/release-sources"
          element={<ReleaseIngestConfigPage selectedProjectId={selectedProjectId} />}
        />
        <Route
          path="/deployments"
          element={
            <DeploymentsPage
              errorMessage={errorMessage}
              formState={formState}
              isSubmitting={isSubmitting}
              isPreviewing={isPreviewing}
              previewElapsedSeconds={previewElapsedSeconds}
              previewErrorMessage={previewErrorMessage}
              previewProgressPercent={previewProgressPercent}
              previewTargetCount={previewTargetCount}
              releases={projectReleases}
              runPreview={runPreview}
              runs={runs}
              runPage={runPage}
              runPageSize={runPageSize}
              runTotalItems={runTotalItems}
              runTotalPages={runTotalPages}
              runIdFilter={runIdFilter}
              runReleaseFilter={runReleaseFilter}
              runStatusFilter={runStatusFilter}
              selectedRelease={selectedRelease}
              selectedReleaseId={selectedReleaseId}
              selectedTargetIds={selectedTargetIds}
              targetGroupFilter={targetGroupFilter}
              targets={targets}
              controlsOpen={controlsOpen}
              onFormStateChange={onFormStateChange}
              onOpenRun={onOpenRun}
              onReleaseChange={onReleaseChange}
              onCloneRun={onCloneRun}
              onRetryFailed={onRetryFailed}
              onRunIdFilterChange={onRunIdFilterChange}
              onRunReleaseFilterChange={onRunReleaseFilterChange}
              onRunStatusFilterChange={onRunStatusFilterChange}
              onRunsPageChange={onRunsPageChange}
              onRunsPageSizeChange={onRunsPageSizeChange}
              onResumeRun={onResumeRun}
              onSelectedTargetIdsChange={onSelectedTargetIdsChange}
              onStartRun={onStartRun}
              onControlsOpenChange={onControlsOpenChange}
              onTargetGroupFilterChange={onTargetGroupFilterChange}
              onRunActionsMenuOpenChange={onRunActionsMenuOpenChange}
              onPreviewRun={onPreviewRun}
              onCancelPreview={onCancelPreview}
            />
          }
        />
        <Route
          path="/deployments/:runId"
          element={<DeploymentRunDetailRoute errorMessage={errorMessage} runDetail={runDetail} onRunChange={onRunChange} />}
        />
        <Route
          path="/demo"
          element={
            <DemoPanel
              adminErrorMessage={adminErrorMessage}
              adminIsSubmitting={adminIsSubmitting}
              adminResult={adminResult}
              projects={projects}
              releases={releases}
              registrations={registrations}
              onIngestMarketplaceEvent={onRegisterOperatorTarget}
              onRefreshRegistrations={onRefreshRegistrations}
            />
          }
        />
        <Route
          path="/targets"
          element={
            <TargetsPage
              adminErrorMessage={adminErrorMessage}
              adminIsSubmitting={adminIsSubmitting}
              adminResult={adminResult}
              projects={projects}
              latestRelease={latestRelease}
              selectedProjectId={selectedProjectId}
              registrations={registrations}
              refreshKey={refreshKey}
              targetRefreshKey={targetRefreshKey}
              onIngestMarketplaceEvent={onRegisterOperatorTarget}
              onUpdateTargetRegistration={onUpdateTargetRegistration}
              onDeleteTargetRegistration={onDeleteTargetRegistration}
              onRefreshRegistrations={onRefreshRegistrations}
              viewMode="targets"
            />
          }
        />
        <Route
          path="/onboarding"
          element={
            <TargetsPage
              adminErrorMessage={adminErrorMessage}
              adminIsSubmitting={adminIsSubmitting}
              adminResult={adminResult}
              projects={projects}
              latestRelease={latestRelease}
              selectedProjectId={selectedProjectId}
              registrations={registrations}
              refreshKey={refreshKey}
              onIngestMarketplaceEvent={onIngestMarketplaceEvent}
              onUpdateTargetRegistration={onUpdateTargetRegistration}
              onDeleteTargetRegistration={onDeleteTargetRegistration}
              onRefreshRegistrations={onRefreshRegistrations}
              viewMode="onboarding"
            />
          }
        />
        <Route
          path="/releases"
          element={
            <ReleasesPage
              selectedProjectId={selectedProjectId}
              selectedProject={selectedProject}
              releases={projectReleases}
              releaseIngestIsSubmitting={releaseIngestIsSubmitting}
              refreshKey={refreshKey}
              onIngestManagedAppReleases={onIngestManagedAppReleases}
            />
          }
        />
        <Route path="/forwarder-logs" element={<ForwarderLogsPage refreshKey={refreshKey} />} />
        <Route path="/managed-app" element={<Navigate to="/forwarder-logs" replace />} />
        <Route path="*" element={<Navigate to="/targets" replace />} />
      </Routes>
    </Suspense>
  );
}
