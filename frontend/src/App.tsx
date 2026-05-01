import { BrowserRouter } from "react-router-dom";
import { Toaster } from "sonner";

import AppRoutes from "@/features/app/AppRoutes";
import AppShellChrome from "@/features/app/AppShellChrome";
import { ROUTER_FUTURE_FLAGS } from "@/features/app/lazyRoutes";
import { useAppShellState } from "@/features/app/useAppShellState";
import type { RunStatus } from "@/lib/types";

export default function App() {
  return (
    <BrowserRouter future={ROUTER_FUTURE_FLAGS}>
      <AppShell />
    </BrowserRouter>
  );
}

function AppShell() {
  const app = useAppShellState();

  return (
    <>
      <Toaster richColors position="top-right" />
      <AppShellChrome
        projectThemeKey={app.selectedProjectTheme.key}
        pathname={app.pathname}
        projects={app.projects}
        selectedProjectId={app.selectedProjectId}
        targetsCount={app.targets.length}
        activeRunCount={app.activeRunCount}
        projectReleaseCount={app.projectReleases.length}
        breadcrumbEntries={app.breadcrumbEntries}
        hasReleaseBanner={app.releaseSummary.hasBanner && app.latestRelease !== null}
        latestReleaseId={app.latestRelease?.id ?? ""}
        latestReleaseVersion={app.releaseSummary.latestVersion}
        outdatedTargetCount={app.releaseSummary.outdatedCount}
        unknownTargetCount={app.releaseSummary.unknownCount}
        onSelectProject={app.setSelectedProjectId}
        onOpenProjectSettings={() => app.navigate("/projects")}
        onOpenCreateProject={() => app.navigate("/projects?new=1")}
        onOpenLatestRelease={app.handleOpenDeploymentForRelease}
      >
        <AppRoutes
          errorMessage={app.errorMessage}
          formState={app.formState}
          isSubmitting={app.isSubmitting}
          isPreviewing={app.isPreviewing}
          previewElapsedSeconds={app.previewElapsedSeconds}
          previewErrorMessage={app.previewErrorMessage}
          previewProgressPercent={app.previewProgressPercent}
          previewTargetCount={app.previewTargetCount}
          releases={app.releases}
          runPreview={app.runPreview}
          runs={app.runs}
          runPage={app.runPageMetadata.page ?? 0}
          runPageSize={app.runPageMetadata.size ?? app.runPageSize}
          runTotalItems={app.runPageMetadata.totalItems ?? 0}
          runTotalPages={app.runPageMetadata.totalPages ?? 0}
          runIdFilter={app.runIdFilter}
          runReleaseFilter={app.runReleaseFilter}
          runStatusFilter={app.runStatusFilter}
          selectedRelease={app.selectedRelease}
          selectedReleaseId={app.selectedReleaseId}
          selectedTargetIds={app.selectedTargetIds}
          targetGroupFilter={app.targetGroupFilter}
          targets={app.deploymentTargets}
          controlsOpen={app.deploymentControlsOpen}
          runDetail={app.runDetail}
          projects={app.projects}
          selectedProject={app.selectedProject}
          selectedProjectId={app.selectedProjectId}
          latestRelease={app.latestRelease}
          projectTargets={app.targets}
          projectReleases={app.projectReleases}
          registrations={app.registrationOptions}
          refreshKey={app.adminRefreshVersion}
          targetRefreshKey={app.targetsRefreshVersion}
          adminErrorMessage={app.adminErrorMessage}
          adminIsSubmitting={app.adminIsSubmitting}
          adminResult={app.adminResult}
          releaseIngestIsSubmitting={app.releaseIngestIsSubmitting}
          navigate={app.navigate}
          onFormStateChange={app.setFormState}
          onOpenRun={(runId) => {
            app.setSelectedRunId(runId);
            app.navigate(`/deployments/${encodeURIComponent(runId)}`);
          }}
          onReleaseChange={app.setSelectedReleaseId}
          onCloneRun={(runId) => {
            void app.handleCloneRun(runId);
          }}
          onRetryFailed={(runId) => {
            void app.handleRetryFailed(runId);
          }}
          onRunIdFilterChange={(value) => {
            app.setRunIdFilter(value);
            app.setRunPage(0);
          }}
          onRunReleaseFilterChange={(value) => {
            app.setRunReleaseFilter(value);
            app.setRunPage(0);
          }}
          onRunStatusFilterChange={(value) => {
            app.setRunStatusFilter(value as RunStatus | "");
            app.setRunPage(0);
          }}
          onRunsPageChange={app.setRunPage}
          onRunsPageSizeChange={(size) => {
            app.setRunPageSize(size);
            app.setRunPage(0);
          }}
          onResumeRun={(runId) => {
            void app.handleResumeRun(runId);
          }}
          onSelectedTargetIdsChange={app.setSelectedTargetIds}
          onStartRun={app.handleStartRun}
          onControlsOpenChange={app.setDeploymentControlsOpen}
          onTargetGroupFilterChange={app.setTargetGroupFilter}
          onRunActionsMenuOpenChange={app.setRunActionsMenuOpen}
          onPreviewRun={app.handlePreviewRun}
          onCancelPreview={app.handleCancelPreview}
          onRunChange={app.setSelectedRunId}
          onIngestMarketplaceEvent={app.handleAdminIngestMarketplaceEvent}
          onRegisterOperatorTarget={app.handleAdminRegisterOperatorTarget}
          onUpdateTargetRegistration={app.handleAdminUpdateRegistration}
          onDeleteTargetRegistration={app.handleAdminDeleteRegistration}
          onRefreshRegistrations={app.refreshRegistrationOptions}
          onIngestManagedAppReleases={app.handleIngestManagedAppReleases}
          onCreateProject={app.handleCreateProject}
          onPatchProject={app.handlePatchProject}
          onDeleteProject={app.handleDeleteProject}
          onValidateProject={app.handleValidateProject}
          onDiscoverAdoBranches={app.handleDiscoverProjectAdoBranches}
          onDiscoverAdoRepositories={app.handleDiscoverProjectAdoRepositories}
          onDiscoverAdoPipelines={app.handleDiscoverProjectAdoPipelines}
        />
      </AppShellChrome>
    </>
  );
}
