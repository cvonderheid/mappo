import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { BrowserRouter, Navigate, NavLink, Route, Routes, useNavigate, useParams } from "react-router-dom";

import AdminPanel from "@/components/AdminPanel";
import FleetTable from "@/components/FleetTable";
import { RunDetailPanel, RunList } from "@/components/RunPanels";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Drawer,
  DrawerClose,
  DrawerContent,
  DrawerDescription,
  DrawerFooter,
  DrawerHeader,
  DrawerTitle,
  DrawerTrigger,
} from "@/components/ui/drawer";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  adminIngestMarketplaceEvent,
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
  RunDetail,
  RunSummary,
  StrategyMode,
  Target,
} from "@/lib/types";
import { cn } from "@/lib/utils";

type StartRunFormState = {
  strategyMode: StrategyMode;
  concurrency: number;
  maxFailureCount: string;
  maxFailureRatePercent: string;
};

const DEFAULT_FORM: StartRunFormState = {
  strategyMode: "waves",
  concurrency: 3,
  maxFailureCount: "2",
  maxFailureRatePercent: "35",
};

export default function App() {
  return (
    <BrowserRouter>
      <AppShell />
    </BrowserRouter>
  );
}

function AppShell() {
  const navigate = useNavigate();
  const [targets, setTargets] = useState<Target[]>([]);
  const [releases, setReleases] = useState<Release[]>([]);
  const [runs, setRuns] = useState<RunSummary[]>([]);
  const [selectedReleaseId, setSelectedReleaseId] = useState<string>("");
  const [selectedRunId, setSelectedRunId] = useState<string>("");
  const [runDetail, setRunDetail] = useState<RunDetail | null>(null);
  const [targetGroupFilter, setTargetGroupFilter] = useState<string>("all");
  const [deploymentControlsOpen, setDeploymentControlsOpen] = useState<boolean>(false);
  const [selectedTargetIds, setSelectedTargetIds] = useState<string[]>([]);
  const [formState, setFormState] = useState<StartRunFormState>(DEFAULT_FORM);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string>("");
  const [adminSnapshot, setAdminSnapshot] = useState<AdminOnboardingSnapshotResponse | null>(null);
  const [adminIsSubmitting, setAdminIsSubmitting] = useState<boolean>(false);
  const [adminErrorMessage, setAdminErrorMessage] = useState<string>("");
  const [adminResult, setAdminResult] = useState<MarketplaceEventIngestResponse | null>(null);

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
        return payload[0].id;
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
        setSelectedRunId(payload[0].id);
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
    const intervalId = window.setInterval(() => {
      void refreshTargets();
      void refreshRuns();
      if (selectedRunId) {
        void refreshRunDetail(selectedRunId);
      }
    }, 1200);

    return () => window.clearInterval(intervalId);
  }, [refreshRunDetail, refreshRuns, refreshTargets, selectedRunId]);

  const deploymentTargets = useMemo(
    () =>
      targetGroupFilter === "all"
        ? targets
        : targets.filter((target) => target.tags.ring === targetGroupFilter),
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
      release_id: selectedRelease.id,
      strategy_mode: formState.strategyMode,
      wave_tag: "ring",
      wave_order: ["canary", "prod"],
      concurrency: formState.concurrency,
      target_tags: targetGroupFilter === "all" ? {} : { ring: targetGroupFilter },
      stop_policy: {
        max_failure_count:
          formState.maxFailureCount.trim() === "" ? undefined : Number(formState.maxFailureCount),
        max_failure_rate:
          formState.maxFailureRatePercent.trim() === ""
            ? undefined
            : Number(formState.maxFailureRatePercent) / 100,
      },
    };

    if (selectedTargetIds.length > 0) {
      request.target_ids = [...selectedTargetIds].sort();
    }

    setIsSubmitting(true);
    try {
      const created = await createRun(request);
      setSelectedRunId(created.id);
      await refreshRuns();
      await refreshRunDetail(created.id);
      await refreshTargets();
      setErrorMessage("");
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
            <div className="grid grid-cols-3 gap-2">
              <TopNavLink label="Fleet" to="/fleet" />
              <TopNavLink label="Deployments" to="/deployments" />
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
          path="/admin"
          element={
            <AdminPanel
              adminErrorMessage={adminErrorMessage}
              adminIsSubmitting={adminIsSubmitting}
              adminResult={adminResult}
              adminSnapshot={adminSnapshot}
              onIngestMarketplaceEvent={handleAdminIngestMarketplaceEvent}
              onRefreshSnapshot={refreshAdminSnapshot}
            />
          }
        />
        <Route path="*" element={<Navigate to="/fleet" replace />} />
      </Routes>
    </main>
  );
}

type DeploymentsPageProps = {
  errorMessage: string;
  formState: StartRunFormState;
  isSubmitting: boolean;
  releases: Release[];
  runs: RunSummary[];
  selectedRelease: Release | null;
  selectedReleaseId: string;
  selectedTargetIds: string[];
  targetGroupFilter: string;
  targets: Target[];
  controlsOpen: boolean;
  onControlsOpenChange: (open: boolean) => void;
  onFormStateChange: (state: StartRunFormState) => void;
  onOpenRun: (runId: string) => void;
  onReleaseChange: (releaseId: string) => void;
  onRetryFailed: (runId: string) => void;
  onResumeRun: (runId: string) => void;
  onSelectedTargetIdsChange: (targetIds: string[]) => void;
  onStartRun: (event: FormEvent<HTMLFormElement>) => Promise<void>;
  onTargetGroupFilterChange: (targetGroup: string) => void;
};

function DeploymentsPage({
  errorMessage,
  formState,
  isSubmitting,
  releases,
  runs,
  selectedRelease,
  selectedReleaseId,
  selectedTargetIds,
  targetGroupFilter,
  targets,
  controlsOpen,
  onControlsOpenChange,
  onFormStateChange,
  onOpenRun,
  onReleaseChange,
  onRetryFailed,
  onResumeRun,
  onSelectedTargetIdsChange,
  onStartRun,
  onTargetGroupFilterChange,
}: DeploymentsPageProps) {
  return (
    <>
      <div className="flex animate-fade-up items-center justify-between [animation-delay:60ms] [animation-fill-mode:forwards]">
        <p className="text-xs text-muted-foreground">
          Historical deployment runs and actions.
        </p>
        <Drawer direction="top" open={controlsOpen} onOpenChange={onControlsOpenChange}>
          <DrawerTrigger asChild>
            <Button data-testid="open-deployment-controls" variant="outline">
              Target Filters + Start Deployment Run
            </Button>
          </DrawerTrigger>
          <DrawerContent className="glass-card">
            <DrawerHeader>
              <DrawerTitle>Deployment Controls</DrawerTitle>
              <DrawerDescription>
                Choose target group, optional specific targets, release version, and stop policies before starting a run.
              </DrawerDescription>
            </DrawerHeader>
            <div className="max-h-[74vh] overflow-y-auto px-4 pb-2">
              <div className="mb-3 rounded-md border border-border/70 bg-muted/20 p-3">
                <div className="flex items-center gap-2">
                  <Label htmlFor="target-group-filter">Target group</Label>
                  <select
                    id="target-group-filter"
                    className="h-10 rounded-md border border-input bg-background/90 px-3 text-sm"
                    value={targetGroupFilter}
                    onChange={(event) => onTargetGroupFilterChange(event.target.value)}
                  >
                    <option value="all">All groups</option>
                    <option value="canary">Canary group</option>
                    <option value="prod">Production group</option>
                  </select>
                </div>
                <p className="mt-2 text-xs text-muted-foreground">
                  Group is the deployment cohort tag stored as <code>ring</code> in target metadata.
                </p>
              </div>
              <form className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-6" onSubmit={onStartRun}>
                <div className="space-y-1">
                  <Label htmlFor="release-version">Release version</Label>
                  <select
                    id="release-version"
                    className="h-10 w-full rounded-md border border-input bg-background/90 px-3 text-sm"
                    value={selectedReleaseId}
                    onChange={(event) => onReleaseChange(event.target.value)}
                    required
                  >
                    {releases.length === 0 ? <option value="">No releases available</option> : null}
                    {releases.map((release) => (
                      <option key={release.id} value={release.id}>
                        {release.template_spec_version}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="space-y-1">
                  <Label htmlFor="strategy-mode">Strategy</Label>
                  <select
                    id="strategy-mode"
                    className="h-10 w-full rounded-md border border-input bg-background/90 px-3 text-sm"
                    value={formState.strategyMode}
                    onChange={(event) =>
                      onFormStateChange({
                        ...formState,
                        strategyMode: event.target.value as StrategyMode,
                      })
                    }
                  >
                    <option value="waves">Grouped rollout (target group order)</option>
                    <option value="all_at_once">All-at-once</option>
                  </select>
                </div>
                <div className="space-y-1">
                  <Label>Concurrency</Label>
                  <Input
                    type="number"
                    min={1}
                    max={25}
                    value={formState.concurrency}
                    onChange={(event) =>
                      onFormStateChange({
                        ...formState,
                        concurrency: Number(event.target.value),
                      })
                    }
                  />
                </div>
                <div className="space-y-1">
                  <Label>Max failures</Label>
                  <Input
                    type="number"
                    min={1}
                    value={formState.maxFailureCount}
                    onChange={(event) =>
                      onFormStateChange({
                        ...formState,
                        maxFailureCount: event.target.value,
                      })
                    }
                  />
                </div>
                <div className="space-y-1">
                  <Label>Max failure rate (%)</Label>
                  <Input
                    type="number"
                    min={0}
                    max={100}
                    value={formState.maxFailureRatePercent}
                    onChange={(event) =>
                      onFormStateChange({
                        ...formState,
                        maxFailureRatePercent: event.target.value,
                      })
                    }
                  />
                </div>
                <div className="flex items-end">
                  <Button type="submit" className="w-full" disabled={isSubmitting || selectedRelease === null}>
                    {isSubmitting ? "Starting..." : "Start Run"}
                  </Button>
                </div>
              </form>
              <p className="mt-2 text-xs text-muted-foreground">
                Max failures and max failure rate are both active when provided. The run halts if either threshold is exceeded.
              </p>
              <div className="mt-3 rounded-md border border-border/70 bg-muted/20 p-3">
                <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                  <div>
                    <p className="text-xs font-medium">Specific Targets (optional)</p>
                    <p className="text-xs text-muted-foreground">
                      Targets in selected target group: {targets.length}. If none are selected, deployment targets the full group.
                    </p>
                    <p className="text-xs text-muted-foreground">
                      Specific targets selected: {selectedTargetIds.length}
                    </p>
                  </div>
                  <div className="flex gap-2">
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => onSelectedTargetIdsChange(targets.map((target) => target.id))}
                    >
                      Select all visible
                    </Button>
                    <Button type="button" variant="outline" size="sm" onClick={() => onSelectedTargetIdsChange([])}>
                      Clear
                    </Button>
                  </div>
                </div>
                <div className="grid max-h-44 grid-cols-1 gap-2 overflow-auto pr-1 sm:grid-cols-2 lg:grid-cols-4">
                  {targets.map((target) => {
                    const checked = selectedTargetIds.includes(target.id);
                    return (
                      <label
                        key={target.id}
                        data-testid={`specific-target-row-${target.id}`}
                        className="flex cursor-pointer items-center gap-2 rounded-md border border-border/70 bg-card/70 px-2 py-1.5 text-xs"
                      >
                        <input
                          data-testid={`specific-target-checkbox-${target.id}`}
                          type="checkbox"
                          checked={checked}
                          onChange={() =>
                            onSelectedTargetIdsChange(
                              checked
                                ? selectedTargetIds.filter((id) => id !== target.id)
                                : [...selectedTargetIds, target.id]
                            )
                          }
                          className="h-3.5 w-3.5 accent-primary"
                        />
                        <span className="font-mono">{target.id}</span>
                        <span className="text-muted-foreground">
                          {target.tags.ring}/{target.tags.region}
                        </span>
                      </label>
                    );
                  })}
                </div>
              </div>
              {errorMessage ? (
                <div className="mt-3 rounded-md border border-destructive/60 bg-destructive/10 p-2 text-xs text-destructive-foreground">
                  {errorMessage}
                </div>
              ) : null}
            </div>
            <DrawerFooter className="border-t border-border/70">
              <DrawerClose asChild>
                <Button type="button" variant="outline">Close</Button>
              </DrawerClose>
            </DrawerFooter>
          </DrawerContent>
        </Drawer>
      </div>

      <div className="grid gap-4 lg:grid-cols-1">
        <RunList
          runs={runs}
          selectedRunId=""
          onOpenRun={onOpenRun}
          onResumeRun={onResumeRun}
          onRetryFailed={onRetryFailed}
        />
      </div>
    </>
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
