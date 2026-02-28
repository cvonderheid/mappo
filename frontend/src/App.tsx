import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { BrowserRouter, Navigate, NavLink, Route, Routes, useLocation } from "react-router-dom";

import AdminPanel from "@/components/AdminPanel";
import FleetTable from "@/components/FleetTable";
import { RunDetailPanel, RunList } from "@/components/RunPanels";
import { Button, buttonVariants } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
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

type DeploymentScope = "filtered" | "specific";

export default function App() {
  return (
    <BrowserRouter>
      <AppShell />
    </BrowserRouter>
  );
}

function AppShell() {
  const location = useLocation();
  const [targets, setTargets] = useState<Target[]>([]);
  const [releases, setReleases] = useState<Release[]>([]);
  const [runs, setRuns] = useState<RunSummary[]>([]);
  const [selectedReleaseId, setSelectedReleaseId] = useState<string>("");
  const [selectedRunId, setSelectedRunId] = useState<string>("");
  const [runDetail, setRunDetail] = useState<RunDetail | null>(null);
  const [targetGroupFilter, setTargetGroupFilter] = useState<string>("all");
  const [deploymentScope, setDeploymentScope] = useState<DeploymentScope>("filtered");
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
      setTargets(await listTargets(targetGroupFilter));
      setErrorMessage("");
    } catch (error) {
      setErrorMessage((error as Error).message);
    }
  }, [targetGroupFilter]);

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

  useEffect(() => {
    setSelectedTargetIds((current) =>
      current.filter((targetId) => targets.some((target) => target.id === targetId))
    );
  }, [targets]);

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

    if (deploymentScope === "specific") {
      if (selectedTargetIds.length === 0) {
        setErrorMessage("Select at least one target for a specific-target deployment.");
        return;
      }
      request.target_ids = [...selectedTargetIds].sort();
      request.target_tags = {};
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

  const showTargetFilters = !location.pathname.startsWith("/admin");

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

      {showTargetFilters ? (
        <Card className="glass-card animate-fade-up [animation-delay:40ms] [animation-fill-mode:forwards]">
          <CardHeader className="flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <CardTitle>Target Filters</CardTitle>
            <div className="flex items-center gap-2">
              <Label htmlFor="target-group-filter">Target group</Label>
              <select
                id="target-group-filter"
                className="h-10 rounded-md border border-input bg-background/90 px-3 text-sm"
                value={targetGroupFilter}
                onChange={(event) => setTargetGroupFilter(event.target.value)}
              >
                <option value="all">All groups</option>
                <option value="canary">Canary group</option>
                <option value="prod">Production group</option>
              </select>
            </div>
          </CardHeader>
          <CardContent className="text-xs text-muted-foreground">
            Group is the deployment cohort tag currently stored as `ring` (`canary`, `prod`) in target metadata.
          </CardContent>
        </Card>
      ) : null}

      <Routes>
        <Route path="/" element={<Navigate to="/fleet" replace />} />
        <Route path="/fleet" element={<FleetTable targets={targets} />} />
        <Route
          path="/deployments"
          element={
            <DeploymentsPage
              deploymentScope={deploymentScope}
              errorMessage={errorMessage}
              formState={formState}
              isSubmitting={isSubmitting}
              releases={releases}
              runDetail={runDetail}
              runs={runs}
              selectedRelease={selectedRelease}
              selectedReleaseId={selectedReleaseId}
              selectedRunId={selectedRunId}
              selectedTargetIds={selectedTargetIds}
              targets={targets}
              onDeploymentScopeChange={setDeploymentScope}
              onFormStateChange={setFormState}
              onReleaseChange={setSelectedReleaseId}
              onRetryFailed={(runId) => {
                void handleRetryFailed(runId);
              }}
              onResumeRun={(runId) => {
                void handleResumeRun(runId);
              }}
              onRunSelect={setSelectedRunId}
              onSelectedTargetIdsChange={setSelectedTargetIds}
              onStartRun={handleStartRun}
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
  deploymentScope: DeploymentScope;
  errorMessage: string;
  formState: StartRunFormState;
  isSubmitting: boolean;
  releases: Release[];
  runDetail: RunDetail | null;
  runs: RunSummary[];
  selectedRelease: Release | null;
  selectedReleaseId: string;
  selectedRunId: string;
  selectedTargetIds: string[];
  targets: Target[];
  onDeploymentScopeChange: (scope: DeploymentScope) => void;
  onFormStateChange: (state: StartRunFormState) => void;
  onReleaseChange: (releaseId: string) => void;
  onRetryFailed: (runId: string) => void;
  onResumeRun: (runId: string) => void;
  onRunSelect: (runId: string) => void;
  onSelectedTargetIdsChange: (targetIds: string[]) => void;
  onStartRun: (event: FormEvent<HTMLFormElement>) => Promise<void>;
};

function DeploymentsPage({
  deploymentScope,
  errorMessage,
  formState,
  isSubmitting,
  releases,
  runDetail,
  runs,
  selectedRelease,
  selectedReleaseId,
  selectedRunId,
  selectedTargetIds,
  targets,
  onDeploymentScopeChange,
  onFormStateChange,
  onReleaseChange,
  onRetryFailed,
  onResumeRun,
  onRunSelect,
  onSelectedTargetIdsChange,
  onStartRun,
}: DeploymentsPageProps) {
  return (
    <>
      <Card className="glass-card animate-fade-up [animation-delay:60ms] [animation-fill-mode:forwards]">
        <CardHeader>
          <CardTitle>Start Deployment Run</CardTitle>
        </CardHeader>
        <CardContent>
          <form className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-7" onSubmit={onStartRun}>
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
              <Label htmlFor="target-scope">Target scope</Label>
              <select
                id="target-scope"
                className="h-10 w-full rounded-md border border-input bg-background/90 px-3 text-sm"
                value={deploymentScope}
                onChange={(event) => onDeploymentScopeChange(event.target.value as DeploymentScope)}
              >
                <option value="filtered">Current target-group filter</option>
                <option value="specific">Specific targets</option>
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
          {deploymentScope === "filtered" ? (
            <div className="mt-3 rounded-md border border-border/70 bg-muted/20 p-3">
              <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                <p className="text-xs text-muted-foreground">
                  Targets in selected target group: {targets.length}
                </p>
              </div>
              <div className="grid max-h-44 grid-cols-1 gap-2 overflow-auto pr-1 sm:grid-cols-2 lg:grid-cols-4">
                {targets.map((target) => (
                  <label
                    key={target.id}
                    data-testid="filtered-member-row"
                    className="flex items-center gap-2 rounded-md border border-border/70 bg-card/70 px-2 py-1.5 text-xs"
                  >
                    <input
                      data-testid={`filtered-member-checkbox-${target.id}`}
                      type="checkbox"
                      checked
                      readOnly
                      disabled
                      className="h-3.5 w-3.5 accent-primary"
                    />
                    <span className="font-mono">{target.id}</span>
                    <span className="text-muted-foreground">
                      {target.tags.ring}/{target.tags.region}
                    </span>
                  </label>
                ))}
              </div>
            </div>
          ) : null}
          {deploymentScope === "specific" ? (
            <div className="mt-3 rounded-md border border-border/70 bg-muted/20 p-3">
              <div className="mb-2 flex flex-wrap items-center justify-between gap-2">
                <p className="text-xs text-muted-foreground">Selected targets: {selectedTargetIds.length}</p>
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
          ) : null}
          {errorMessage ? (
            <div className="mt-3 rounded-md border border-destructive/60 bg-destructive/10 p-2 text-xs text-destructive-foreground">
              {errorMessage}
            </div>
          ) : null}
        </CardContent>
      </Card>

      <div className="grid gap-4 lg:grid-cols-1">
        <RunList
          runs={runs}
          selectedRunId={selectedRunId}
          onSelectRun={onRunSelect}
          onResumeRun={onResumeRun}
          onRetryFailed={onRetryFailed}
        />
      </div>

      <RunDetailPanel run={runDetail} />
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
