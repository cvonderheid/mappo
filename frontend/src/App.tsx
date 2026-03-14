import { FormEvent, Suspense, lazy, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { BrowserRouter, Navigate, NavLink, Route, Routes, useLocation, useNavigate, useParams } from "react-router-dom";
import { Toaster } from "sonner";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { DEFAULT_FORM, type StartRunFormState } from "@/lib/deployment-form";
import { releaseAvailabilitySummary } from "@/lib/fleet";
import { createLiveUpdatesEventSource, parseLiveUpdateEvent } from "@/lib/live-updates";
import { canonicalizeReleases } from "@/lib/releases";
import {
  adminListTargetRegistrations,
  adminDeleteTargetRegistration,
  adminIngestGithubReleaseManifest,
  adminIngestMarketplaceEvent,
  createProject,
  adminUpdateTargetRegistration,
  createRun,
  getRun,
  listProjectAudit,
  listProjects,
  listReleases,
  listRuns,
  listTargetsPage,
  patchProjectConfiguration,
  previewRun,
  resumeRun,
  retryFailed,
  validateProjectConfiguration,
} from "@/lib/api";
import type {
  CreateRunRequest,
  ListProjectAuditQuery,
  MarketplaceEventIngestRequest,
  MarketplaceEventIngestResponse,
  PageMetadata,
  ProjectConfigurationAuditPage,
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
  RunSummaryPage,
  Target,
  TargetExecutionRecord,
  TargetRegistrationRecord,
  UpdateTargetRegistrationRequest,
} from "@/lib/types";
import { cn } from "@/lib/utils";

const ROUTER_FUTURE_FLAGS = {
  v7_relativeSplatPath: true,
  v7_startTransition: true,
} as const;

const AdminPanel = lazy(() => import("@/components/AdminPanel"));
const DemoPanel = lazy(() => import("@/components/DemoPanel"));
const DeploymentsPage = lazy(() => import("@/components/DeploymentsPage"));
const FleetTable = lazy(() => import("@/components/FleetTable"));
const ProjectSettingsPage = lazy(() => import("@/components/ProjectSettingsPage"));
const RunDetailPanel = lazy(() =>
  import("@/components/RunPanels").then((module) => ({ default: module.RunDetailPanel }))
);

export default function App() {
  return (
    <BrowserRouter future={ROUTER_FUTURE_FLAGS}>
      <AppShell />
    </BrowserRouter>
  );
}

function AppShell() {
  const [projects, setProjects] = useState<ProjectDefinition[]>([]);
  const [selectedProjectId, setSelectedProjectId] = useState<string>("");
  const navigate = useNavigate();
  const location = useLocation();
  const [targets, setTargets] = useState<Target[]>([]);
  const [targetsRefreshVersion, setTargetsRefreshVersion] = useState(0);
  const [releases, setReleases] = useState<Release[]>([]);
  const [runsPage, setRunsPage] = useState<RunSummaryPage | null>(null);
  const [runPage, setRunPage] = useState<number>(0);
  const [runPageSize, setRunPageSize] = useState<number>(25);
  const [runIdFilter, setRunIdFilter] = useState<string>("");
  const [runReleaseFilter, setRunReleaseFilter] = useState<string>("");
  const [runStatusFilter, setRunStatusFilter] = useState<RunStatus | "">("");
  const [activeRunCount, setActiveRunCount] = useState<number>(0);
  const [selectedReleaseId, setSelectedReleaseId] = useState<string>("");
  const [selectedRunId, setSelectedRunId] = useState<string>("");
  const [runDetail, setRunDetail] = useState<RunDetail | null>(null);
  const [targetGroupFilter, setTargetGroupFilter] = useState<string>("all");
  const [deploymentControlsOpen, setDeploymentControlsOpen] = useState<boolean>(false);
  const [selectedTargetIds, setSelectedTargetIds] = useState<string[]>([]);
  const [runActionsMenuOpen, setRunActionsMenuOpen] = useState<boolean>(false);
  const [formState, setFormState] = useState<StartRunFormState>(DEFAULT_FORM);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [isPreviewing, setIsPreviewing] = useState<boolean>(false);
  const [previewElapsedSeconds, setPreviewElapsedSeconds] = useState<number>(0);
  const [previewTargetCount, setPreviewTargetCount] = useState<number>(0);
  const [errorMessage, setErrorMessage] = useState<string>("");
  const [previewErrorMessage, setPreviewErrorMessage] = useState<string>("");
  const [runPreview, setRunPreview] = useState<RunPreview | null>(null);
  const [registrationOptions, setRegistrationOptions] = useState<TargetRegistrationRecord[]>([]);
  const [adminRefreshVersion, setAdminRefreshVersion] = useState(0);
  const [adminIsSubmitting, setAdminIsSubmitting] = useState<boolean>(false);
  const [adminErrorMessage, setAdminErrorMessage] = useState<string>("");
  const [adminResult, setAdminResult] = useState<MarketplaceEventIngestResponse | null>(null);
  const [releaseIngestIsSubmitting, setReleaseIngestIsSubmitting] = useState<boolean>(false);
  const previewAbortControllerRef = useRef<AbortController | null>(null);
  const targetsSnapshotSignatureRef = useRef<string>("");
  const refreshTargetsRef = useRef<() => Promise<void>>(async () => {});
  const refreshRunsRef = useRef<() => Promise<void>>(async () => {});
  const refreshRunDetailRef = useRef<(runId: string) => Promise<void>>(async () => {});
  const refreshReleasesRef = useRef<() => Promise<void>>(async () => {});
  const refreshRegistrationOptionsRef = useRef<() => Promise<void>>(async () => {});
  const selectedRunIdRef = useRef<string>("");
  const selectedProjectIdRef = useRef<string>("");
  const isDeploymentRouteRef = useRef<boolean>(false);
  const isFleetRouteRef = useRef<boolean>(false);
  const isAdminRouteRef = useRef<boolean>(false);
  const previousRouteSectionRef = useRef<string | null>(null);
  const previousIsDeploymentRouteRef = useRef<boolean>(false);
  const previousIsFleetRouteRef = useRef<boolean>(false);
  const previousIsAdminRouteRef = useRef<boolean>(false);
  const isDeploymentRoute = useMemo(
    () => location.pathname.startsWith("/deployments"),
    [location.pathname]
  );
  const isFleetRoute = useMemo(
    () => location.pathname === "/fleet",
    [location.pathname]
  );
  const isAdminRoute = useMemo(
    () => location.pathname === "/admin" || location.pathname === "/demo",
    [location.pathname]
  );

  const targetsSnapshotSignature = useCallback((projectId: string, items: Target[]): string => {
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
    try {
      const pageSize = 200;
      const firstPage = await adminListTargetRegistrations({ page: 0, size: pageSize });
      const items = [...(firstPage.items ?? [])];
      const totalPages = firstPage.page?.totalPages ?? 0;
      for (let page = 1; page < totalPages; page += 1) {
        const nextPage = await adminListTargetRegistrations({ page, size: pageSize });
        items.push(...(nextPage.items ?? []));
      }
      setRegistrationOptions(items);
      setAdminRefreshVersion((current) => current + 1);
      setAdminErrorMessage("");
    } catch (error) {
      setAdminErrorMessage((error as Error).message);
    }
  }, []);

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
  }, [
    runIdFilter,
    runPage,
    runPageSize,
    runReleaseFilter,
    runStatusFilter,
    selectedProjectId,
    selectedRunId,
  ]);

  const refreshRunSummary = useCallback(async () => {
    if (!selectedProjectId || isDeploymentRouteRef.current) {
      setActiveRunCount(0);
      return;
    }
    try {
      const payload = await listRuns({
        page: 0,
        size: 1,
        projectId: selectedProjectId,
      });
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
    isFleetRouteRef.current = isFleetRoute;
    isAdminRouteRef.current = isAdminRoute;
  }, [
    refreshRegistrationOptions,
    refreshReleases,
    refreshRunDetail,
    refreshRuns,
    refreshTargets,
    selectedProjectId,
    selectedRunId,
    isAdminRoute,
    isDeploymentRoute,
    isFleetRoute,
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
  }, [selectedProjectId]);

  useEffect(() => {
    if (!selectedProjectId || !isDeploymentRoute) {
      return;
    }
    void refreshRuns();
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

    if (!selectedProjectId || !isDeploymentRoute || wasDeploymentRoute) {
      return;
    }

    void refreshRunsRef.current();
    void refreshTargetsRef.current();
    void refreshReleasesRef.current();
    if (selectedRunIdRef.current) {
      void refreshRunDetailRef.current(selectedRunIdRef.current);
    }
  }, [isDeploymentRoute, selectedProjectId]);

  useEffect(() => {
    const wasFleetRoute = previousIsFleetRouteRef.current;
    previousIsFleetRouteRef.current = isFleetRoute;

    if (!selectedProjectId || !isFleetRoute || wasFleetRoute) {
      return;
    }

    void refreshTargetsRef.current();
    void refreshReleasesRef.current();
    void refreshRunSummary();
  }, [isFleetRoute, refreshRunSummary, selectedProjectId]);

  useEffect(() => {
    const wasAdminRoute = previousIsAdminRouteRef.current;
    previousIsAdminRouteRef.current = isAdminRoute;

    if (!selectedProjectId || !isAdminRoute || wasAdminRoute) {
      return;
    }

    void refreshRegistrationOptionsRef.current();
    void refreshReleasesRef.current();
    void refreshRunSummary();
  }, [isAdminRoute, refreshRunSummary, selectedProjectId]);

  useEffect(() => {
    const currentRouteSection = isDeploymentRoute
      ? "deployments"
      : isFleetRoute
        ? "fleet"
        : isAdminRoute
          ? "admin"
          : "other";
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

    if (currentRouteSection === "fleet") {
      void refreshTargetsRef.current();
      void refreshReleasesRef.current();
      void refreshRunSummary();
      return;
    }

    if (currentRouteSection === "admin") {
      void refreshRegistrationOptionsRef.current();
      void refreshReleasesRef.current();
      void refreshRunSummary();
    }
  }, [
    isAdminRoute,
    isDeploymentRoute,
    isFleetRoute,
    refreshRunSummary,
    selectedProjectId,
  ]);

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

  const liveTopics = useMemo(() => {
    if (isDeploymentRoute) {
      return ["targets", "runs", "releases"];
    }
    if (isAdminRoute) {
      return ["targets", "releases", "admin"];
    }
    return [];
  }, [isAdminRoute, isDeploymentRoute]);

  useEffect(() => {
    const intervalMs = isFleetRoute ? 30000 : 15000;
    const intervalId = window.setInterval(() => {
      if (isDeploymentRouteRef.current) {
        void refreshTargetsRef.current();
        void refreshRunsRef.current();
        if (selectedRunIdRef.current) {
          void refreshRunDetailRef.current(selectedRunIdRef.current);
        }
        void refreshReleasesRef.current();
        return;
      }
      if (isFleetRouteRef.current) {
        void refreshTargetsRef.current();
        void refreshReleasesRef.current();
        void refreshRunSummary();
        return;
      }
      if (isAdminRouteRef.current) {
        void refreshReleasesRef.current();
        void refreshRunSummary();
        void refreshRegistrationOptionsRef.current();
      }
    }, intervalMs);

    return () => window.clearInterval(intervalId);
  }, [isFleetRoute, refreshRunSummary]);

  useEffect(() => {
    if (liveTopics.length === 0) {
      return;
    }
    if (!selectedProjectId) {
      return;
    }
    if (typeof window === "undefined" || typeof EventSource === "undefined") {
      return;
    }

    const eventSource = createLiveUpdatesEventSource(liveTopics, selectedProjectId);
    const scheduledRefreshes = new Map<string, number>();

    const scheduleRefresh = (key: string, action: () => void): void => {
      if (scheduledRefreshes.has(key)) {
        return;
      }
      const timeoutId = window.setTimeout(() => {
        scheduledRefreshes.delete(key);
        action();
      }, 200);
      scheduledRefreshes.set(key, timeoutId);
    };

    const refreshSelectedRun = (runId: string | null | undefined): void => {
      if (runId && runId === selectedRunIdRef.current) {
        scheduleRefresh(`run:${runId}`, () => {
          void refreshRunDetailRef.current(runId);
        });
      }
    };

    const matchesSelectedProject = (projectId: string | null | undefined): boolean => {
      if (!projectId) {
        return true;
      }
      return projectId === selectedProjectIdRef.current;
    };

    eventSource.addEventListener("targets-updated", (event: MessageEvent<string>) => {
      const payload = parseLiveUpdateEvent(event.data);
      if (!matchesSelectedProject(payload?.projectId)) {
        return;
      }
      scheduleRefresh("targets", () => {
        void refreshTargetsRef.current();
      });
    });

    eventSource.addEventListener("releases-updated", (event: MessageEvent<string>) => {
      const payload = parseLiveUpdateEvent(event.data);
      if (!matchesSelectedProject(payload?.projectId)) {
        return;
      }
      scheduleRefresh("releases", () => {
        void refreshReleasesRef.current();
      });
    });

    eventSource.addEventListener("admin-updated", () => {
      if (isAdminRouteRef.current) {
        scheduleRefresh("admin", () => {
          void refreshRegistrationOptionsRef.current();
        });
      }
    });

    eventSource.addEventListener("connected", (event: MessageEvent<string>) => {
      const payload = parseLiveUpdateEvent(event.data);
      if (!matchesSelectedProject(payload?.projectId)) {
        return;
      }
      if (isDeploymentRouteRef.current) {
        scheduleRefresh("targets", () => {
          void refreshTargetsRef.current();
        });
        scheduleRefresh("releases", () => {
          void refreshReleasesRef.current();
        });
        scheduleRefresh("runs", () => {
          void refreshRunsRef.current();
        });
        refreshSelectedRun(selectedRunIdRef.current);
        return;
      }
      if (isAdminRouteRef.current) {
        scheduleRefresh("admin", () => {
          void refreshRegistrationOptionsRef.current();
        });
        scheduleRefresh("releases", () => {
          void refreshReleasesRef.current();
        });
        return;
      }
      if (isFleetRouteRef.current) {
        scheduleRefresh("targets", () => {
          void refreshTargetsRef.current();
        });
        scheduleRefresh("releases", () => {
          void refreshReleasesRef.current();
        });
      }
    });

    eventSource.addEventListener("runs-updated", (event: MessageEvent<string>) => {
      if (!isDeploymentRouteRef.current) {
        return;
      }
      const payload = parseLiveUpdateEvent(event.data);
      if (!matchesSelectedProject(payload?.projectId)) {
        return;
      }
      scheduleRefresh("runs", () => {
        void refreshRunsRef.current();
      });
      refreshSelectedRun(selectedRunIdRef.current);
    });

    eventSource.addEventListener("run-updated", (event: MessageEvent<string>) => {
      if (!isDeploymentRouteRef.current) {
        return;
      }
      const payload = parseLiveUpdateEvent(event.data);
      if (!matchesSelectedProject(payload?.projectId)) {
        return;
      }
      scheduleRefresh("runs", () => {
        void refreshRunsRef.current();
      });
      refreshSelectedRun(payload?.subjectId);
    });

    eventSource.onerror = () => {
      // Browser reconnect behavior is sufficient; slow polling remains as fallback.
    };

    return () => {
      scheduledRefreshes.forEach((timeoutId) => window.clearTimeout(timeoutId));
      scheduledRefreshes.clear();
      eventSource.close();
    };
  }, [
    isAdminRoute,
    isDeploymentRoute,
    liveTopics,
    selectedProjectId,
  ]);

  const deploymentTargets = useMemo(
    () =>
      targetGroupFilter === "all"
        ? targets
        : targets.filter((target) => target.tags?.ring === targetGroupFilter),
    [targetGroupFilter, targets]
  );

  useEffect(() => {
    setSelectedTargetIds((current) =>
      {
        const filtered = current.filter((targetId) =>
          deploymentTargets.some((target) => target.id === targetId)
        );
        if (
          filtered.length === current.length &&
          filtered.every((targetId, index) => targetId === current[index])
        ) {
          return current;
        }
        return filtered;
      }
    );
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

  useEffect(
    () => () => {
      previewAbortControllerRef.current?.abort();
    },
    []
  );

  const runStats = useMemo(() => {
    return { running: activeRunCount };
  }, [activeRunCount]);

  const selectedProject = useMemo(
    () => projects.find((project) => project.id === selectedProjectId) ?? null,
    [projects, selectedProjectId]
  );

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
  const releaseSummary = useMemo(
    () => releaseAvailabilitySummary(targets, latestRelease),
    [latestRelease, targets]
  );

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
    if (location.pathname !== "/deployments") {
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
      navigate(
        {
          pathname: location.pathname,
          search: nextSearch ? `?${nextSearch}` : "",
        },
        { replace: true }
      );
    }
  }, [location.pathname, location.search, navigate, releases]);

  async function handleStartRun(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    if (!selectedRelease) {
      setErrorMessage("No releases are available yet.");
      return;
    }

    const request: CreateRunRequest = {
      releaseId: selectedRelease.id ?? selectedReleaseId,
      strategyMode: formState.strategyMode,
      waveTag: "ring",
      waveOrder: ["canary", "prod"],
      concurrency: formState.concurrency,
      targetTags: targetGroupFilter === "all" ? {} : { ring: targetGroupFilter },
      stopPolicy: {
        maxFailureCount:
          formState.maxFailureCount.trim() === "" ? undefined : Number(formState.maxFailureCount),
        maxFailureRate:
          formState.maxFailureRatePercent.trim() === ""
            ? undefined
            : Number(formState.maxFailureRatePercent) / 100,
      },
    };

    if (selectedTargetIds.length > 0) {
      request.targetIds = [...selectedTargetIds].sort();
    }

    setIsSubmitting(true);
    try {
      const created = await createRun(request);
      if (created.id) {
      setSelectedRunId(created.id);
      setRunPage(0);
      }
      await refreshRuns();
      if (created.id) {
        await refreshRunDetail(created.id);
      }
      await refreshTargets();
      setErrorMessage("");
      setRunPreview(null);
      setPreviewErrorMessage("");
      setDeploymentControlsOpen(false);
    } catch (error) {
      setErrorMessage((error as Error).message);
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handlePreviewRun(): Promise<void> {
    if (!selectedRelease) {
      setPreviewErrorMessage("No releases are available yet.");
      return;
    }

    const request: CreateRunRequest = {
      releaseId: selectedRelease.id ?? selectedReleaseId,
      strategyMode: formState.strategyMode,
      waveTag: "ring",
      waveOrder: ["canary", "prod"],
      concurrency: formState.concurrency,
      targetTags: targetGroupFilter === "all" ? {} : { ring: targetGroupFilter },
      stopPolicy: {
        maxFailureCount:
          formState.maxFailureCount.trim() === "" ? undefined : Number(formState.maxFailureCount),
        maxFailureRate:
          formState.maxFailureRatePercent.trim() === ""
            ? undefined
            : Number(formState.maxFailureRatePercent) / 100,
      },
    };

    if (selectedTargetIds.length > 0) {
      request.targetIds = [...selectedTargetIds].sort();
    }

    previewAbortControllerRef.current?.abort();
    const abortController = new AbortController();
    previewAbortControllerRef.current = abortController;
    setPreviewTargetCount(request.targetIds?.length ?? deploymentTargets.length);
    setIsPreviewing(true);
    try {
      setRunPreview(null);
      setPreviewErrorMessage("");
      setRunPreview(await previewRun(request, abortController.signal));
    } catch (error) {
      if (error instanceof DOMException && error.name === "AbortError") {
        setPreviewErrorMessage("Preview canceled before Azure returned a result.");
      } else {
        setPreviewErrorMessage((error as Error).message);
      }
    } finally {
      if (previewAbortControllerRef.current === abortController) {
        previewAbortControllerRef.current = null;
      }
      setIsPreviewing(false);
    }
  }

  function handleCancelPreview(): void {
    previewAbortControllerRef.current?.abort();
  }

  async function handleResumeRun(runId: string): Promise<void> {
    try {
      await resumeRun(runId);
      await refreshRuns();
      await refreshRunDetail(runId);
      await refreshTargets();
      setSelectedRunId(runId);
      setRunPage(0);
      setErrorMessage("");
    } catch (error) {
      setErrorMessage((error as Error).message);
    }
  }

  async function handleRetryFailed(runId: string): Promise<void> {
    try {
      await retryFailed(runId);
      await refreshRuns();
      await refreshRunDetail(runId);
      await refreshTargets();
      setSelectedRunId(runId);
      setRunPage(0);
      setErrorMessage("");
    } catch (error) {
      setErrorMessage((error as Error).message);
    }
  }

  async function handleCloneRun(runId: string): Promise<void> {
    try {
      const sourceRun = await getRun(runId);
      const clonedTargetIds = Array.from(
        new Set(
          (sourceRun.targetRecords ?? []).flatMap((record: TargetExecutionRecord) =>
            record.targetId ? [record.targetId] : []
          )
        )
      ).sort();
      const clonedTargetGroups = [
        ...new Set(
          clonedTargetIds
            .map((targetId) => targets.find((target) => target.id === targetId)?.tags?.ring)
            .filter((group): group is string => Boolean(group))
        ),
      ];
      const clonedTargetGroup =
        clonedTargetGroups.length === 1 ? (clonedTargetGroups[0] ?? "all") : "all";
      const stopPolicy = sourceRun.stopPolicy ?? {};

      setSelectedReleaseId(sourceRun.releaseId ?? "");
      setFormState({
        strategyMode: sourceRun.strategyMode ?? DEFAULT_FORM.strategyMode,
        concurrency: sourceRun.concurrency ?? DEFAULT_FORM.concurrency,
        maxFailureCount:
          stopPolicy.maxFailureCount === null ||
          stopPolicy.maxFailureCount === undefined
            ? ""
            : String(stopPolicy.maxFailureCount),
        maxFailureRatePercent:
          stopPolicy.maxFailureRate === null ||
          stopPolicy.maxFailureRate === undefined
            ? ""
            : String(Math.round(stopPolicy.maxFailureRate * 100)),
      });
      setTargetGroupFilter(clonedTargetGroup);
      setSelectedTargetIds(clonedTargetIds);
      setDeploymentControlsOpen(true);
      navigate("/deployments");
      setErrorMessage("");
    } catch (error) {
      setErrorMessage((error as Error).message);
    }
  }

  function handleOpenDeploymentForRelease(releaseId: string): void {
    navigate(`/deployments?releaseId=${encodeURIComponent(releaseId)}&controls=open`);
  }

  async function handleAdminIngestMarketplaceEvent(
    request: MarketplaceEventIngestRequest,
    ingestToken?: string
  ): Promise<void> {
    setAdminIsSubmitting(true);
    try {
      const result = await adminIngestMarketplaceEvent(request, ingestToken);
      setAdminResult(result);
      setAdminErrorMessage("");
      await refreshTargets();
      await refreshRegistrationOptions();
    } catch (error) {
      setAdminErrorMessage((error as Error).message);
    } finally {
      setAdminIsSubmitting(false);
    }
  }

  async function handleAdminUpdateRegistration(
    targetId: string,
    request: UpdateTargetRegistrationRequest
  ): Promise<void> {
    await adminUpdateTargetRegistration(targetId, request);
    await refreshTargets();
    await refreshRegistrationOptions();
  }

  async function handleAdminDeleteRegistration(targetId: string): Promise<void> {
    await adminDeleteTargetRegistration(targetId);
    await refreshTargets();
    await refreshRegistrationOptions();
  }

  async function handleIngestManagedAppReleases(
    request: ReleaseManifestIngestRequest
  ): Promise<ReleaseManifestIngestResponse> {
    setReleaseIngestIsSubmitting(true);
    try {
      const result = await adminIngestGithubReleaseManifest(request);
      await refreshReleases();
      await refreshRegistrationOptions();
      return result;
    } catch (error) {
      throw error;
    } finally {
      setReleaseIngestIsSubmitting(false);
    }
  }

  async function handleCreateProject(request: ProjectCreateRequest): Promise<ProjectDefinition> {
    const created = await createProject(request);
    await refreshProjects();
    if (created.id) {
      setSelectedProjectId(created.id);
      navigate("/projects");
    }
    return created;
  }

  async function handlePatchProject(
    projectId: string,
    request: ProjectConfigurationPatchRequest
  ): Promise<ProjectDefinition> {
    const updated = await patchProjectConfiguration(projectId, request);
    await refreshProjects();
    if (updated.id) {
      setSelectedProjectId(updated.id);
    }
    return updated;
  }

  async function handleValidateProject(
    projectId: string,
    request: ProjectValidationRequest
  ): Promise<ProjectValidationResult> {
    return validateProjectConfiguration(projectId, request);
  }

  async function handleListProjectAudit(
    projectId: string,
    query: ListProjectAuditQuery
  ): Promise<ProjectConfigurationAuditPage> {
    return listProjectAudit(projectId, query);
  }

  return (
    <main className="mx-auto flex w-[min(1400px,96vw)] flex-col gap-4 py-6">
      <Toaster richColors position="top-right" />
      <Card className="glass-card hero-gradient animate-fade-up [animation-fill-mode:forwards]">
        <CardHeader className="flex-col gap-4 md:flex-row md:items-start md:justify-between">
          <div className="space-y-2">
            <p className="font-mono text-[11px] uppercase tracking-[0.14em] text-primary">
              MAPPO Control Plane
            </p>
            <CardTitle className="text-2xl uppercase md:text-3xl">
              Multi-tenant Managed App Orchestrator
            </CardTitle>
          </div>
          <div className="w-full space-y-2 md:max-w-md">
            <div className="grid grid-cols-2 gap-2">
              <Kpi label="Total Targets" value={String(targets.length)} />
              <Kpi label="Active Runs" value={String(runStats.running)} />
            </div>
            <div className="rounded-lg border border-border/60 bg-background/40 p-2">
              <Label htmlFor="project-switcher" className="mb-1 block text-[11px] uppercase tracking-[0.12em] text-muted-foreground">
                Project
              </Label>
              <Select value={selectedProjectId} onValueChange={setSelectedProjectId}>
                <SelectTrigger id="project-switcher" className="h-10 w-full bg-background/90 text-sm">
                  <SelectValue placeholder="Select project" />
                </SelectTrigger>
                <SelectContent>
                  {projects.map((project) => (
                    <SelectItem key={project.id} value={project.id ?? ""}>
                      {project.name ?? project.id}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid grid-cols-5 gap-2">
              <TopNavLink label="Fleet" to="/fleet" />
              <TopNavLink label="Deployments" to="/deployments" />
              <TopNavLink label="Projects" to="/projects" />
              <TopNavLink label="Demo" to="/demo" />
              <TopNavLink label="Admin" to="/admin" />
            </div>
          </div>
        </CardHeader>
      </Card>

      {releaseSummary.hasBanner && latestRelease ? (
        <div className="rounded-lg border border-primary/40 bg-primary/10 p-4">
          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div className="space-y-1">
              <p className="text-sm font-semibold">
                New release {releaseSummary.latestVersion} is available.
              </p>
              <p className="text-sm text-muted-foreground">
                {releaseSummary.outdatedCount} target
                {releaseSummary.outdatedCount === 1 ? "" : "s"} are behind the latest release.
                {releaseSummary.unknownCount > 0
                  ? ` ${releaseSummary.unknownCount} target${releaseSummary.unknownCount === 1 ? "" : "s"} have no known version yet.`
                  : ""}
              </p>
            </div>
            <Button
              type="button"
              onClick={() => latestRelease.id && handleOpenDeploymentForRelease(latestRelease.id)}
              disabled={!latestRelease.id}
            >
              Deploy {releaseSummary.latestVersion}
            </Button>
          </div>
        </div>
      ) : null}

      <Suspense fallback={<RouteLoadingFallback />}>
        <Routes>
          <Route path="/" element={<Navigate to="/fleet" replace />} />
          <Route
            path="/fleet"
            element={
              <FleetTable
                latestRelease={latestRelease}
                refreshKey={targetsRefreshVersion}
                selectedProjectId={selectedProjectId}
              />
            }
          />
          <Route
            path="/projects"
            element={
              <ProjectSettingsPage
                project={selectedProject}
                projects={projects}
                selectedProjectId={selectedProjectId}
                targets={targets}
                onCreateProject={handleCreateProject}
                onPatchProject={handlePatchProject}
                onValidateProject={handleValidateProject}
                onListProjectAudit={handleListProjectAudit}
              />
            }
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
                runPage={runPageMetadata.page ?? 0}
                runPageSize={runPageMetadata.size ?? runPageSize}
                runTotalItems={runPageMetadata.totalItems ?? 0}
                runTotalPages={runPageMetadata.totalPages ?? 0}
                runIdFilter={runIdFilter}
                runReleaseFilter={runReleaseFilter}
                runStatusFilter={runStatusFilter}
                selectedRelease={selectedRelease}
                selectedReleaseId={selectedReleaseId}
                selectedTargetIds={selectedTargetIds}
                targetGroupFilter={targetGroupFilter}
                targets={deploymentTargets}
                controlsOpen={deploymentControlsOpen}
                onFormStateChange={setFormState}
                onOpenRun={(runId) => {
                  setSelectedRunId(runId);
                  navigate(`/deployments/${encodeURIComponent(runId)}`);
                }}
                onReleaseChange={setSelectedReleaseId}
                onCloneRun={(runId) => {
                  void handleCloneRun(runId);
                }}
                onRetryFailed={(runId) => {
                  void handleRetryFailed(runId);
                }}
                onRunIdFilterChange={(value) => {
                  setRunIdFilter(value);
                  setRunPage(0);
                }}
                onRunReleaseFilterChange={(value) => {
                  setRunReleaseFilter(value);
                  setRunPage(0);
                }}
                onRunStatusFilterChange={(value) => {
                  setRunStatusFilter(value as RunStatus | "");
                  setRunPage(0);
                }}
                onRunsPageChange={setRunPage}
                onRunsPageSizeChange={(size) => {
                  setRunPageSize(size);
                  setRunPage(0);
                }}
                onResumeRun={(runId) => {
                  void handleResumeRun(runId);
                }}
                onSelectedTargetIdsChange={setSelectedTargetIds}
                onStartRun={handleStartRun}
                onControlsOpenChange={setDeploymentControlsOpen}
                onTargetGroupFilterChange={setTargetGroupFilter}
                onRunActionsMenuOpenChange={setRunActionsMenuOpen}
                onPreviewRun={handlePreviewRun}
                onCancelPreview={handleCancelPreview}
              />
            }
          />
          <Route
            path="/deployments/:runId"
            element={
              <DeploymentRunDetailRoute
                errorMessage={errorMessage}
                runDetail={runDetail}
                onBack={() => navigate("/deployments")}
                onRunChange={setSelectedRunId}
              />
            }
          />
          <Route
            path="/demo"
            element={
              <DemoPanel
                adminErrorMessage={adminErrorMessage}
                adminIsSubmitting={adminIsSubmitting}
                adminResult={adminResult}
                registrations={registrationOptions}
                onIngestMarketplaceEvent={handleAdminIngestMarketplaceEvent}
                onRefreshRegistrations={refreshRegistrationOptions}
              />
            }
          />
          <Route
            path="/admin"
            element={
              <AdminPanel
                adminErrorMessage={adminErrorMessage}
                registrations={registrationOptions}
                refreshKey={adminRefreshVersion}
                releaseIngestIsSubmitting={releaseIngestIsSubmitting}
                onIngestManagedAppReleases={handleIngestManagedAppReleases}
                onUpdateTargetRegistration={handleAdminUpdateRegistration}
                onDeleteTargetRegistration={handleAdminDeleteRegistration}
                onRefreshRegistrations={refreshRegistrationOptions}
              />
            }
          />
          <Route path="*" element={<Navigate to="/fleet" replace />} />
        </Routes>
      </Suspense>
    </main>
  );
}

type DeploymentRunDetailRouteProps = {
  errorMessage: string;
  runDetail: RunDetail | null;
  onBack: () => void;
  onRunChange: (runId: string) => void;
};

function DeploymentRunDetailRoute({
  errorMessage,
  runDetail,
  onBack,
  onRunChange,
}: DeploymentRunDetailRouteProps) {
  const params = useParams<{ runId: string }>();
  const runId = params.runId ? decodeURIComponent(params.runId) : "";

  useEffect(() => {
    if (runId) {
      onRunChange(runId);
    }
  }, [onRunChange, runId]);

  return (
    <>
      <div className="flex animate-fade-up items-center justify-between [animation-delay:60ms] [animation-fill-mode:forwards]">
        <Button variant="outline" onClick={onBack}>
          Back To Deployments
        </Button>
        <p className="font-mono text-xs text-muted-foreground">{runId}</p>
      </div>
      {errorMessage ? (
        <div className="rounded-md border border-destructive/60 bg-destructive/10 p-2 text-xs text-destructive-foreground">
          {errorMessage}
        </div>
      ) : null}
      <RunDetailPanel run={runDetail && runDetail.id === runId ? runDetail : null} />
    </>
  );
}

function TopNavLink({ label, to }: { label: string; to: string }) {
  return (
    <NavLink
      className={({ isActive }) =>
        cn(
          buttonVariants({ variant: isActive ? "default" : "outline", size: "sm" }),
          "w-full text-center"
        )
      }
      to={to}
    >
      {label}
    </NavLink>
  );
}

function Kpi({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md border border-border/70 bg-card/80 px-3 py-2">
      <p className="text-[11px] uppercase tracking-[0.08em] text-muted-foreground">{label}</p>
      <p className="text-xl font-semibold">{value}</p>
    </div>
  );
}

function RouteLoadingFallback() {
  return (
    <div className="rounded-lg border border-border/70 bg-card/80 p-4 text-sm text-muted-foreground">
      Loading view...
    </div>
  );
}
