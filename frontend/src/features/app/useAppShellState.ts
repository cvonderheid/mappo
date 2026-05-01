import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";

import { type StartRunFormState, DEFAULT_FORM } from "@/lib/deployment-form";
import { releaseAvailabilitySummary } from "@/lib/fleet";
import { createLiveUpdatesEventSource, parseLiveUpdateEvent } from "@/lib/live-updates";
import { projectThemeForProject } from "@/lib/project-theme";
import { canonicalizeReleases } from "@/lib/releases";
import {
  adminDeleteTargetRegistration,
  adminListTargetRegistrations,
  adminUpdateTargetRegistration,
  getRun,
  listProjects,
  listReleases,
  listRuns,
  listTargetsPage,
} from "@/lib/api";
import type {
  MarketplaceEventIngestRequest,
  MarketplaceEventIngestResponse,
  PageMetadata,
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
  RunSummaryPage,
  Target,
  TargetExecutionRecord,
  TargetRegistrationRecord,
  UpdateTargetRegistrationRequest,
} from "@/lib/types";
import {
  buildBreadcrumbEntries,
  isAdminPath,
  isDeploymentPath,
  isTargetsPath,
  type BreadcrumbEntry,
} from "@/features/app/navigation";
import { useAppLiveRefresh } from "@/features/app/useAppLiveRefresh";
import { useAppShellActions } from "@/features/app/useAppShellActions";

export function useAppShellState() {
  const navigate = useNavigate();
  const location = useLocation();
  const [projects, setProjects] = useState<ProjectDefinition[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState("");
  const [targets, setTargets] = useState<Target[]>([]);
  const [targetsRefreshVersion, setTargetsRefreshVersion] = useState(0);
  const [releases, setReleases] = useState<Release[]>([]);
  const [runsPage, setRunsPage] = useState<RunSummaryPage | null>(null);
  const [runPage, setRunPage] = useState(0);
  const [runPageSize, setRunPageSize] = useState(25);
  const [runIdFilter, setRunIdFilter] = useState("");
  const [runReleaseFilter, setRunReleaseFilter] = useState("");
  const [runStatusFilter, setRunStatusFilter] = useState<RunStatus | "">("");
  const [activeRunCount, setActiveRunCount] = useState(0);
  const [selectedReleaseId, setSelectedReleaseId] = useState("");
  const [selectedRunId, setSelectedRunId] = useState("");
  const [runDetail, setRunDetail] = useState<RunDetail | null>(null);
  const [targetGroupFilter, setTargetGroupFilter] = useState("all");
  const [deploymentControlsOpen, setDeploymentControlsOpen] = useState(false);
  const [selectedTargetIds, setSelectedTargetIds] = useState<string[]>([]);
  const [, setRunActionsMenuOpen] = useState(false);
  const [formState, setFormState] = useState<StartRunFormState>(DEFAULT_FORM);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isPreviewing, setIsPreviewing] = useState(false);
  const [previewElapsedSeconds, setPreviewElapsedSeconds] = useState(0);
  const [previewTargetCount, setPreviewTargetCount] = useState(0);
  const [errorMessage, setErrorMessage] = useState("");
  const [previewErrorMessage, setPreviewErrorMessage] = useState("");
  const [runPreview, setRunPreview] = useState<RunPreview | null>(null);
  const [registrationOptions, setRegistrationOptions] = useState<TargetRegistrationRecord[]>([]);
  const [adminRefreshVersion, setAdminRefreshVersion] = useState(0);
  const [adminIsSubmitting, setAdminIsSubmitting] = useState(false);
  const [adminErrorMessage, setAdminErrorMessage] = useState("");
  const [adminResult, setAdminResult] = useState<MarketplaceEventIngestResponse | null>(null);
  const [releaseIngestIsSubmitting, setReleaseIngestIsSubmitting] = useState(false);
  const previewAbortControllerRef = useRef<AbortController | null>(null);
  const targetsSnapshotSignatureRef = useRef("");
  const refreshTargetsRef = useRef<() => Promise<void>>(async () => {});
  const refreshRunsRef = useRef<() => Promise<void>>(async () => {});
  const refreshRunDetailRef = useRef<(runId: string) => Promise<void>>(async () => {});
  const refreshReleasesRef = useRef<() => Promise<void>>(async () => {});
  const refreshRegistrationOptionsRef = useRef<() => Promise<void>>(async () => {});
  const selectedRunIdRef = useRef("");
  const selectedProjectIdRef = useRef("");
  const isDeploymentRouteRef = useRef(false);
  const isTargetsRouteRef = useRef(false);
  const isAdminRouteRef = useRef(false);
  const previousRouteSectionRef = useRef<string | null>(null);
  const previousIsDeploymentRouteRef = useRef(false);
  const previousIsTargetsRouteRef = useRef(false);
  const previousIsAdminRouteRef = useRef(false);

  const pathname = location.pathname;
  const isDeploymentRoute = useMemo(() => isDeploymentPath(pathname), [pathname]);
  const isTargetsRoute = useMemo(() => isTargetsPath(pathname), [pathname]);
  const isAdminRoute = useMemo(() => isAdminPath(pathname), [pathname]);

  const targetsSnapshotSignature = useCallback((projectId: string, items: Target[]) => {
    return JSON.stringify({
      projectId,
      items: items.map((target) => ({
        id: target.id ?? "",
        customerName: target.customerName ?? "",
        tenantId: target.tenantId ?? "",
        subscriptionId: target.subscriptionId ?? "",
        runtimeStatus: target.runtimeStatus ?? "",
        runtimeCheckedAt: target.runtimeCheckedAt ?? "",
        lastDeploymentStatus: target.lastDeploymentStatus ?? "",
        lastDeploymentAt: target.lastDeploymentAt ?? "",
        lastDeployedRelease: target.lastDeployedRelease ?? "",
        tags: target.tags ?? {},
      })),
    });
  }, []);

  const refreshTargets = useCallback(async () => {
    if (!selectedProjectId) {
      targetsSnapshotSignatureRef.current = "";
      setTargets([]);
      setTargetsRefreshVersion((current) => current + 1);
      return;
    }
    try {
      const pageSize = 200;
      const firstPage = await listTargetsPage({ page: 0, size: pageSize, projectId: selectedProjectId });
      const items = [...(firstPage.items ?? [])];
      const totalPages = firstPage.page?.totalPages ?? 0;
      for (let page = 1; page < totalPages; page += 1) {
        const nextPage = await listTargetsPage({ page, size: pageSize, projectId: selectedProjectId });
        items.push(...(nextPage.items ?? []));
      }
      const nextSignature = targetsSnapshotSignature(selectedProjectId, items);
      if (nextSignature !== targetsSnapshotSignatureRef.current) {
        targetsSnapshotSignatureRef.current = nextSignature;
        setTargets(items);
        setTargetsRefreshVersion((current) => current + 1);
      }
      setErrorMessage("");
    } catch (error) {
      setErrorMessage((error as Error).message);
    }
  }, [selectedProjectId, targetsSnapshotSignature]);

  const refreshRegistrationOptions = useCallback(async () => {
    if (!selectedProjectId) {
      setRegistrationOptions([]);
      setAdminRefreshVersion((current) => current + 1);
      setAdminErrorMessage("");
      return;
    }
    try {
      const pageSize = 200;
      const baseQuery = { projectId: selectedProjectId };
      const firstPage = await adminListTargetRegistrations({ page: 0, size: pageSize, ...baseQuery });
      const items = [...(firstPage.items ?? [])];
      const totalPages = firstPage.page?.totalPages ?? 0;
      for (let page = 1; page < totalPages; page += 1) {
        const nextPage = await adminListTargetRegistrations({ page, size: pageSize, ...baseQuery });
        items.push(...(nextPage.items ?? []));
      }
      setRegistrationOptions(items);
      setAdminRefreshVersion((current) => current + 1);
      setAdminErrorMessage("");
    } catch (error) {
      setAdminErrorMessage((error as Error).message);
    }
  }, [selectedProjectId]);

  const refreshReleases = useCallback(async () => {
    try {
      const payload = canonicalizeReleases(await listReleases());
      setReleases(payload);
      setSelectedReleaseId((current) => {
        if (payload.length === 0) {
          return "";
        }
        if (current && payload.some((release) => release.id === current)) {
          return current;
        }
        return payload[0]?.id ?? "";
      });
    } catch (error) {
      setErrorMessage((error as Error).message);
    }
  }, []);

  const refreshProjects = useCallback(async () => {
    try {
      const payload = await listProjects();
      setProjects(payload);
      setSelectedProjectId((current) => {
        if (payload.length === 0) {
          return "";
        }
        if (current && payload.some((project) => project.id === current)) {
          return current;
        }
        return payload[0]?.id ?? "";
      });
    } catch (error) {
      setErrorMessage((error as Error).message);
    }
  }, []);

  const refreshRuns = useCallback(async () => {
    if (!selectedProjectId) {
      return;
    }
    try {
      const payload = await listRuns({
        page: runPage,
        size: runPageSize,
        projectId: selectedProjectId,
        runId: runIdFilter || undefined,
        releaseId: runReleaseFilter || undefined,
        status: runStatusFilter || undefined,
      });
      setRunsPage(payload);
      setActiveRunCount(payload.activeRunCount ?? 0);
      if (isDeploymentRouteRef.current && !selectedRunIdRef.current && (payload.items?.length ?? 0) > 0) {
        setSelectedRunId(payload.items?.[0]?.id ?? "");
      }
    } catch (error) {
      setErrorMessage((error as Error).message);
    }
  }, [runIdFilter, runPage, runPageSize, runReleaseFilter, runStatusFilter, selectedProjectId]);

  const refreshRunSummary = useCallback(async () => {
    if (!selectedProjectId || isDeploymentRouteRef.current) {
      setActiveRunCount(0);
      return;
    }
    try {
      const payload = await listRuns({ page: 0, size: 1, projectId: selectedProjectId });
      setActiveRunCount(payload.activeRunCount ?? 0);
    } catch (error) {
      setErrorMessage((error as Error).message);
    }
  }, [selectedProjectId]);

  const refreshRunDetail = useCallback(async (runId: string) => {
    if (!runId || !isDeploymentRouteRef.current || runId !== selectedRunIdRef.current) {
      return;
    }
    try {
      setRunDetail(await getRun(runId));
    } catch (error) {
      setErrorMessage((error as Error).message);
    }
  }, []);

  useEffect(() => {
    void refreshProjects();
  }, [refreshProjects]);

  useEffect(() => {
    refreshTargetsRef.current = refreshTargets;
    refreshRunsRef.current = refreshRuns;
    refreshRunDetailRef.current = refreshRunDetail;
    refreshReleasesRef.current = refreshReleases;
    refreshRegistrationOptionsRef.current = refreshRegistrationOptions;
    selectedRunIdRef.current = selectedRunId;
    selectedProjectIdRef.current = selectedProjectId;
    isDeploymentRouteRef.current = isDeploymentRoute;
    isTargetsRouteRef.current = isTargetsRoute;
    isAdminRouteRef.current = isAdminRoute;
  }, [
    isAdminRoute,
    isDeploymentRoute,
    isTargetsRoute,
    refreshRegistrationOptions,
    refreshReleases,
    refreshRunDetail,
    refreshRuns,
    refreshTargets,
    selectedProjectId,
    selectedRunId,
  ]);

  useEffect(() => {
    void refreshRegistrationOptions();
    void refreshReleases();
  }, [refreshRegistrationOptions, refreshReleases]);

  useEffect(() => {
    if (!selectedProjectId) {
      setSelectedRunId("");
      setRunDetail(null);
      setSelectedTargetIds([]);
      setTargetGroupFilter("all");
      setRunPage(0);
      return;
    }
    setSelectedRunId("");
    setRunDetail(null);
    setSelectedTargetIds([]);
    setTargetGroupFilter("all");
    setRunPage(0);
    void refreshTargetsRef.current();
    if (isDeploymentRoute) {
      void refreshRunsRef.current();
    } else {
      void refreshRunSummary();
    }
  }, [isDeploymentRoute, refreshRunSummary, selectedProjectId]);

  useEffect(() => {
    if (selectedProjectId && isDeploymentRoute) {
      void refreshRuns();
    }
  }, [
    isDeploymentRoute,
    refreshRuns,
    runIdFilter,
    runPage,
    runPageSize,
    runReleaseFilter,
    runStatusFilter,
    selectedProjectId,
  ]);

  useEffect(() => {
    const wasDeploymentRoute = previousIsDeploymentRouteRef.current;
    previousIsDeploymentRouteRef.current = isDeploymentRoute;
    if (selectedProjectId && isDeploymentRoute && !wasDeploymentRoute) {
      void refreshRunsRef.current();
      void refreshTargetsRef.current();
      void refreshReleasesRef.current();
      if (selectedRunIdRef.current) {
        void refreshRunDetailRef.current(selectedRunIdRef.current);
      }
    }
  }, [isDeploymentRoute, selectedProjectId]);

  useEffect(() => {
    const wasTargetsRoute = previousIsTargetsRouteRef.current;
    previousIsTargetsRouteRef.current = isTargetsRoute;
    if (selectedProjectId && isTargetsRoute && !wasTargetsRoute) {
      void refreshTargetsRef.current();
      void refreshReleasesRef.current();
      void refreshRunSummary();
    }
  }, [isTargetsRoute, refreshRunSummary, selectedProjectId]);

  useEffect(() => {
    const wasAdminRoute = previousIsAdminRouteRef.current;
    previousIsAdminRouteRef.current = isAdminRoute;
    if (selectedProjectId && isAdminRoute && !wasAdminRoute) {
      void refreshRegistrationOptionsRef.current();
      void refreshReleasesRef.current();
      void refreshRunSummary();
    }
  }, [isAdminRoute, refreshRunSummary, selectedProjectId]);

  useEffect(() => {
    const currentRouteSection = isDeploymentRoute ? "deployments" : isTargetsRoute ? "targets" : isAdminRoute ? "admin" : "other";
    const previousRouteSection = previousRouteSectionRef.current;
    previousRouteSectionRef.current = currentRouteSection;
    if (!selectedProjectId || previousRouteSection === currentRouteSection) {
      return;
    }
    if (currentRouteSection === "deployments") {
      void refreshRunsRef.current();
      if (selectedRunIdRef.current) {
        void refreshRunDetailRef.current(selectedRunIdRef.current);
      }
      return;
    }
    if (currentRouteSection === "targets") {
      void refreshTargetsRef.current();
      void refreshReleasesRef.current();
      void refreshRegistrationOptionsRef.current();
      void refreshRunSummary();
      return;
    }
    if (currentRouteSection === "admin") {
      void refreshRegistrationOptionsRef.current();
      void refreshReleasesRef.current();
      void refreshRunSummary();
    }
  }, [isAdminRoute, isDeploymentRoute, isTargetsRoute, refreshRunSummary, selectedProjectId]);

  useEffect(() => {
    if (isDeploymentRoute && selectedRunId) {
      void refreshRunDetail(selectedRunId);
    } else {
      setRunDetail(null);
    }
  }, [isDeploymentRoute, refreshRunDetail, selectedRunId]);

  useEffect(() => {
    if (!isDeploymentRoute) {
      setSelectedRunId("");
      selectedRunIdRef.current = "";
      setRunDetail(null);
      setRunsPage(null);
      setRunActionsMenuOpen(false);
    }
  }, [isDeploymentRoute]);

  useAppLiveRefresh({
    isAdminRoute,
    isDeploymentRoute,
    isTargetsRoute,
    selectedProjectId,
    selectedProjectIdRef,
    selectedRunIdRef,
    isAdminRouteRef,
    isDeploymentRouteRef,
    isTargetsRouteRef,
    refreshTargetsRef,
    refreshRunsRef,
    refreshRunDetailRef,
    refreshReleasesRef,
    refreshRegistrationOptionsRef,
    refreshRunSummary,
  });

  const deploymentTargets = useMemo(
    () => (targetGroupFilter === "all" ? targets : targets.filter((target) => target.tags?.ring === targetGroupFilter)),
    [targetGroupFilter, targets]
  );

  useEffect(() => {
    setSelectedTargetIds((current) => {
      const filtered = current.filter((targetId) => deploymentTargets.some((target) => target.id === targetId));
      return filtered.length === current.length && filtered.every((targetId, index) => targetId === current[index])
        ? current
        : filtered;
    });
  }, [deploymentTargets]);

  const previewResetKey = useMemo(
    () =>
      JSON.stringify({
        releaseId: selectedReleaseId,
        targetGroupFilter,
        selectedTargetIds: [...selectedTargetIds].sort(),
        strategyMode: formState.strategyMode,
        concurrency: formState.concurrency,
        maxFailureCount: formState.maxFailureCount,
        maxFailureRatePercent: formState.maxFailureRatePercent,
      }),
    [
      formState.concurrency,
      formState.maxFailureCount,
      formState.maxFailureRatePercent,
      formState.strategyMode,
      selectedReleaseId,
      selectedTargetIds,
      targetGroupFilter,
    ]
  );

  useEffect(() => {
    setRunPreview(null);
    setPreviewErrorMessage("");
  }, [previewResetKey]);

  useEffect(() => {
    if (!isPreviewing) {
      setPreviewElapsedSeconds(0);
      return;
    }
    const startedAt = Date.now();
    setPreviewElapsedSeconds(0);
    const intervalId = window.setInterval(() => {
      setPreviewElapsedSeconds(Math.max(1, Math.floor((Date.now() - startedAt) / 1000)));
    }, 250);
    return () => window.clearInterval(intervalId);
  }, [isPreviewing]);

  useEffect(() => () => previewAbortControllerRef.current?.abort(), []);

  const selectedProject = useMemo(
    () => projects.find((project) => project.id === selectedProjectId) ?? null,
    [projects, selectedProjectId]
  );
  const selectedProjectTheme = useMemo(
    () => projectThemeForProject(selectedProjectId, selectedProject?.themeKey),
    [selectedProject?.themeKey, selectedProjectId]
  );

  useEffect(() => {
    document.documentElement.setAttribute("data-project-theme", selectedProjectTheme.key);
    return () => document.documentElement.removeAttribute("data-project-theme");
  }, [selectedProjectTheme.key]);

  const runs = useMemo(() => runsPage?.items ?? [], [runsPage]);
  const runPageMetadata: PageMetadata = runsPage?.page ?? {
    page: runPage,
    size: runPageSize,
    totalItems: 0,
    totalPages: 0,
  };
  const projectReleases = useMemo(
    () => releases.filter((release) => release.projectId === selectedProjectId),
    [releases, selectedProjectId]
  );
  const selectedRelease = useMemo(
    () => projectReleases.find((release) => release.id === selectedReleaseId) ?? null,
    [projectReleases, selectedReleaseId]
  );
  const latestRelease = useMemo(() => projectReleases[0] ?? null, [projectReleases]);
  const releaseSummary = useMemo(() => releaseAvailabilitySummary(targets, latestRelease), [latestRelease, targets]);
  const breadcrumbEntries = useMemo<BreadcrumbEntry[]>(() => buildBreadcrumbEntries(pathname), [pathname]);

  useEffect(() => {
    setSelectedReleaseId((current) => {
      if (projectReleases.length === 0) {
        return "";
      }
      if (current && projectReleases.some((release) => release.id === current)) {
        return current;
      }
      return projectReleases[0]?.id ?? "";
    });
  }, [projectReleases]);

  const previewProgressPercent = useMemo(() => {
    if (!isPreviewing) {
      return 0;
    }
    const targetWeight = Math.max(1, previewTargetCount);
    const progress = 12 + (1 - Math.exp(-(previewElapsedSeconds + 1) / (targetWeight * 3.5))) * 78;
    return Math.min(90, Math.round(progress));
  }, [isPreviewing, previewElapsedSeconds, previewTargetCount]);

  useEffect(() => {
    if (pathname !== "/deployments") {
      return;
    }
    const searchParams = new URLSearchParams(location.search);
    const requestedReleaseId = searchParams.get("releaseId");
    const shouldOpenControls = searchParams.get("controls") === "open";
    if (requestedReleaseId && releases.length === 0) {
      return;
    }
    if (requestedReleaseId && releases.some((release) => release.id === requestedReleaseId)) {
      setSelectedReleaseId(requestedReleaseId);
    }
    if (shouldOpenControls) {
      setDeploymentControlsOpen(true);
    }
    if (requestedReleaseId || searchParams.has("controls")) {
      const cleaned = new URLSearchParams(location.search);
      cleaned.delete("releaseId");
      cleaned.delete("controls");
      const nextSearch = cleaned.toString();
      navigate({ pathname, search: nextSearch ? `?${nextSearch}` : "" }, { replace: true });
    }
  }, [location.search, navigate, pathname, releases]);

  const {
    handleStartRun,
    handlePreviewRun,
    handleCancelPreview,
    handleResumeRun,
    handleRetryFailed,
    handleCloneRun,
    handleOpenDeploymentForRelease,
    handleAdminIngestMarketplaceEvent,
    handleAdminRegisterOperatorTarget,
    handleAdminUpdateRegistration,
    handleAdminDeleteRegistration,
    handleIngestManagedAppReleases,
    handleCreateProject,
    handlePatchProject,
    handleDeleteProject,
    handleValidateProject,
    handleDiscoverProjectAdoPipelines,
    handleDiscoverProjectAdoRepositories,
    handleDiscoverProjectAdoBranches,
  } = useAppShellActions({
    navigate,
    selectedRelease,
    selectedReleaseId,
    formState,
    targetGroupFilter,
    selectedTargetIds,
    deploymentTargetCount: deploymentTargets.length,
    targets,
    previewAbortControllerRef,
    refreshRuns,
    refreshRunDetail,
    refreshTargets,
    refreshRegistrationOptions,
    refreshReleases,
    refreshProjects,
    setIsSubmitting,
    setIsPreviewing,
    setPreviewTargetCount,
    setErrorMessage,
    setPreviewErrorMessage,
    setRunPreview,
    setDeploymentControlsOpen,
    setSelectedRunId,
    setRunPage,
    setAdminIsSubmitting,
    setAdminResult,
    setAdminErrorMessage,
    setReleaseIngestIsSubmitting,
    setSelectedReleaseId,
    setFormState,
    setTargetGroupFilter,
    setSelectedTargetIds,
    setSelectedProjectId,
  });

  return {
    pathname,
    navigate,
    location,
    projects,
    selectedProjectId,
    setSelectedProjectId,
    targets,
    targetsRefreshVersion,
    releases,
    runs,
    runPageMetadata,
    runPage,
    runPageSize,
    runIdFilter,
    setRunIdFilter,
    runReleaseFilter,
    setRunReleaseFilter,
    runStatusFilter,
    setRunStatusFilter,
    activeRunCount,
    selectedReleaseId,
    setSelectedReleaseId,
    selectedRunId,
    setSelectedRunId,
    runDetail,
    targetGroupFilter,
    setTargetGroupFilter,
    deploymentControlsOpen,
    setDeploymentControlsOpen,
    selectedTargetIds,
    setSelectedTargetIds,
    setRunActionsMenuOpen,
    formState,
    setFormState,
    isSubmitting,
    isPreviewing,
    previewElapsedSeconds,
    previewTargetCount,
    errorMessage,
    previewErrorMessage,
    runPreview,
    registrationOptions,
    adminRefreshVersion,
    adminIsSubmitting,
    adminErrorMessage,
    adminResult,
    releaseIngestIsSubmitting,
    selectedProject,
    selectedProjectTheme,
    projectReleases,
    selectedRelease,
    latestRelease,
    releaseSummary,
    breadcrumbEntries,
    deploymentTargets,
    previewProgressPercent,
    refreshRegistrationOptions,
    handleStartRun,
    handlePreviewRun,
    handleCancelPreview,
    handleResumeRun,
    handleRetryFailed,
    handleCloneRun,
    handleOpenDeploymentForRelease,
    handleAdminIngestMarketplaceEvent,
    handleAdminRegisterOperatorTarget,
    handleAdminUpdateRegistration,
    handleAdminDeleteRegistration,
    handleIngestManagedAppReleases,
    handleCreateProject,
    handlePatchProject,
    handleDeleteProject,
    handleValidateProject,
    handleDiscoverProjectAdoPipelines,
    handleDiscoverProjectAdoRepositories,
    handleDiscoverProjectAdoBranches,
    setRunPage,
    setRunPageSize,
  };
}

export type AppShellState = ReturnType<typeof useAppShellState>;
