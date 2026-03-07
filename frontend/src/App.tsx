import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { BrowserRouter, Navigate, NavLink, Route, Routes, useLocation, useNavigate, useParams } from "react-router-dom";
import AdminPanel from "@/components/AdminPanel";
import DemoPanel from "@/components/DemoPanel";
import DeploymentsPage from "@/components/DeploymentsPage";
import FleetTable from "@/components/FleetTable";
import { RunDetailPanel } from "@/components/RunPanels";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardHeader, CardTitle } from "@/components/ui/card";
import { DEFAULT_FORM, type StartRunFormState } from "@/lib/deployment-form";
import {
  adminDeleteTargetRegistration,
  adminIngestGithubReleaseManifest,
  adminIngestMarketplaceEvent,
  adminUpdateTargetRegistration,
  createRun,
  getAdminOnboardingSnapshot,
  getRun,
  listReleases,
  listRuns,
  listTargets,
  resumeRun,
  retryFailed,
} from "@/lib/api";
import type {
  AdminOnboardingSnapshotResponse,
  CreateRunRequest,
  MarketplaceEventIngestRequest,
  MarketplaceEventIngestResponse,
  Release,
  ReleaseManifestIngestRequest,
  ReleaseManifestIngestResponse,
  RunDetail,
  RunSummary,
  Target,
  TargetExecutionRecord,
  UpdateTargetRegistrationRequest,
} from "@/lib/types";
import { cn } from "@/lib/utils";

const ROUTER_FUTURE_FLAGS = {
  v7_relativeSplatPath: true,
  v7_startTransition: true,
} as const;

export default function App() {
  return (
    <BrowserRouter future={ROUTER_FUTURE_FLAGS}>
      <AppShell />
    </BrowserRouter>
  );
}

function AppShell() {
  const navigate = useNavigate();
  const location = useLocation();
  const [targets, setTargets] = useState<Target[]>([]);
  const [releases, setReleases] = useState<Release[]>([]);
  const [runs, setRuns] = useState<RunSummary[]>([]);
  const [selectedReleaseId, setSelectedReleaseId] = useState<string>("");
  const [selectedRunId, setSelectedRunId] = useState<string>("");
  const [runDetail, setRunDetail] = useState<RunDetail | null>(null);
  const [targetGroupFilter, setTargetGroupFilter] = useState<string>("all");
  const [deploymentControlsOpen, setDeploymentControlsOpen] = useState<boolean>(false);
  const [selectedTargetIds, setSelectedTargetIds] = useState<string[]>([]);
  const [runActionsMenuOpen, setRunActionsMenuOpen] = useState<boolean>(false);
  const [formState, setFormState] = useState<StartRunFormState>(DEFAULT_FORM);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string>("");
  const [adminSnapshot, setAdminSnapshot] = useState<AdminOnboardingSnapshotResponse | null>(null);
  const [adminIsSubmitting, setAdminIsSubmitting] = useState<boolean>(false);
  const [adminErrorMessage, setAdminErrorMessage] = useState<string>("");
  const [adminResult, setAdminResult] = useState<MarketplaceEventIngestResponse | null>(null);
  const [releaseIngestIsSubmitting, setReleaseIngestIsSubmitting] = useState<boolean>(false);
  const [releaseIngestErrorMessage, setReleaseIngestErrorMessage] = useState<string>("");
  const [releaseIngestResult, setReleaseIngestResult] = useState<ReleaseManifestIngestResponse | null>(null);

  const refreshTargets = useCallback(async () => {
    try {
      setTargets(await listTargets());
      setErrorMessage("");
    } catch (error) {
      setErrorMessage((error as Error).message);
    }
  }, []);

  const refreshReleases = useCallback(async () => {
    try {
      const payload = await listReleases();
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

  const refreshRuns = useCallback(async () => {
    try {
      const payload = await listRuns();
      setRuns(payload);
      if (!selectedRunId && payload.length > 0) {
        setSelectedRunId(payload[0]?.id ?? "");
      }
    } catch (error) {
      setErrorMessage((error as Error).message);
    }
  }, [selectedRunId]);

  const refreshRunDetail = useCallback(async (runId: string) => {
    try {
      setRunDetail(await getRun(runId));
    } catch (error) {
      setErrorMessage((error as Error).message);
    }
  }, []);

  const refreshAdminSnapshot = useCallback(async () => {
    try {
      setAdminSnapshot(await getAdminOnboardingSnapshot());
      setAdminErrorMessage("");
    } catch (error) {
      setAdminErrorMessage((error as Error).message);
    }
  }, []);

  useEffect(() => {
    void refreshTargets();
  }, [refreshTargets]);

  useEffect(() => {
    void refreshReleases();
    void refreshRuns();
    void refreshAdminSnapshot();
  }, [refreshAdminSnapshot, refreshReleases, refreshRuns]);

  useEffect(() => {
    if (selectedRunId) {
      void refreshRunDetail(selectedRunId);
    } else {
      setRunDetail(null);
    }
  }, [refreshRunDetail, selectedRunId]);

  useEffect(() => {
    const isDeploymentsListRoute = location.pathname === "/deployments";
    if (isDeploymentsListRoute && (deploymentControlsOpen || runActionsMenuOpen)) {
      return;
    }

    const intervalId = window.setInterval(() => {
      void refreshTargets();
      void refreshRuns();
      if (selectedRunId) {
        void refreshRunDetail(selectedRunId);
      }
    }, 1200);

    return () => window.clearInterval(intervalId);
  }, [
    deploymentControlsOpen,
    location.pathname,
    refreshRunDetail,
    refreshRuns,
    refreshTargets,
    runActionsMenuOpen,
    selectedRunId,
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
      current.filter((targetId) => deploymentTargets.some((target) => target.id === targetId))
    );
  }, [deploymentTargets]);

  const runStats = useMemo(() => {
    let running = 0;
    let failedOrPartial = 0;
    for (const run of runs) {
      if (run.status === "running") {
        running += 1;
      }
      if (run.status === "failed" || run.status === "partial" || run.status === "halted") {
        failedOrPartial += 1;
      }
    }
    return { running, failedOrPartial };
  }, [runs]);

  const selectedRelease = useMemo(
    () => releases.find((release) => release.id === selectedReleaseId) ?? null,
    [releases, selectedReleaseId]
  );

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
      }
      await refreshRuns();
      if (created.id) {
        await refreshRunDetail(created.id);
      }
      await refreshTargets();
      setErrorMessage("");
      setDeploymentControlsOpen(false);
    } catch (error) {
      setErrorMessage((error as Error).message);
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleResumeRun(runId: string): Promise<void> {
    try {
      await resumeRun(runId);
      await refreshRuns();
      await refreshRunDetail(runId);
      await refreshTargets();
      setSelectedRunId(runId);
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
      await refreshAdminSnapshot();
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
    await refreshAdminSnapshot();
  }

  async function handleAdminDeleteRegistration(targetId: string): Promise<void> {
    await adminDeleteTargetRegistration(targetId);
    await refreshTargets();
    await refreshAdminSnapshot();
  }

  async function handleIngestManagedAppReleases(
    request: ReleaseManifestIngestRequest
  ): Promise<void> {
    setReleaseIngestIsSubmitting(true);
    try {
      const result = await adminIngestGithubReleaseManifest(request);
      setReleaseIngestResult(result);
      setReleaseIngestErrorMessage("");
      await refreshReleases();
    } catch (error) {
      setReleaseIngestErrorMessage((error as Error).message);
    } finally {
      setReleaseIngestIsSubmitting(false);
    }
  }

  return (
    <main className="mx-auto flex w-[min(1400px,96vw)] flex-col gap-4 py-6">
      <Card className="glass-card hero-gradient animate-fade-up [animation-fill-mode:forwards]">
        <CardHeader className="flex-col gap-4 md:flex-row md:items-start md:justify-between">
          <div className="space-y-2">
            <p className="font-mono text-[11px] uppercase tracking-[0.14em] text-primary">
              Multi-tenant Managed App Deployer
            </p>
            <CardTitle className="text-2xl md:text-3xl">MAPPO Control Plane</CardTitle>
            <p className="max-w-3xl text-sm text-muted-foreground">
              CodeDeploy-style release orchestration for Azure Managed Application fleets.
            </p>
          </div>
          <div className="w-full space-y-2 md:max-w-md">
            <div className="grid grid-cols-3 gap-2">
              <Kpi label="Total Targets" value={String(targets.length)} />
              <Kpi label="Active Runs" value={String(runStats.running)} />
              <Kpi label="Attention Needed" value={String(runStats.failedOrPartial)} />
            </div>
            <div className="grid grid-cols-4 gap-2">
              <TopNavLink label="Fleet" to="/fleet" />
              <TopNavLink label="Deployments" to="/deployments" />
              <TopNavLink label="Demo" to="/demo" />
              <TopNavLink label="Admin" to="/admin" />
            </div>
          </div>
        </CardHeader>
      </Card>

      <Routes>
        <Route path="/" element={<Navigate to="/fleet" replace />} />
        <Route path="/fleet" element={<FleetTable targets={targets} />} />
        <Route
          path="/deployments"
          element={
            <DeploymentsPage
              errorMessage={errorMessage}
              formState={formState}
              isSubmitting={isSubmitting}
              releases={releases}
              runs={runs}
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
              onResumeRun={(runId) => {
                void handleResumeRun(runId);
              }}
              onSelectedTargetIdsChange={setSelectedTargetIds}
              onStartRun={handleStartRun}
              onControlsOpenChange={setDeploymentControlsOpen}
              onTargetGroupFilterChange={setTargetGroupFilter}
              onRunActionsMenuOpenChange={setRunActionsMenuOpen}
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
              adminSnapshot={adminSnapshot}
              onIngestMarketplaceEvent={handleAdminIngestMarketplaceEvent}
              onRefreshSnapshot={refreshAdminSnapshot}
            />
          }
        />
        <Route
          path="/admin"
          element={
            <AdminPanel
              adminErrorMessage={adminErrorMessage}
              adminSnapshot={adminSnapshot}
              releaseIngestErrorMessage={releaseIngestErrorMessage}
              releaseIngestIsSubmitting={releaseIngestIsSubmitting}
              releaseIngestResult={releaseIngestResult}
              onIngestManagedAppReleases={handleIngestManagedAppReleases}
              onUpdateTargetRegistration={handleAdminUpdateRegistration}
              onDeleteTargetRegistration={handleAdminDeleteRegistration}
              onRefreshSnapshot={refreshAdminSnapshot}
            />
          }
        />
        <Route path="*" element={<Navigate to="/fleet" replace />} />
      </Routes>
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
