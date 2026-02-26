import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";

import FleetTable from "@/components/FleetTable";
import { RunDetailPanel, RunList } from "@/components/RunPanels";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  createRun,
  getRun,
  listReleases,
  listRuns,
  listTargets,
  resumeRun,
  retryFailed,
} from "@/lib/api";
import type {
  CreateRunRequest,
  Release,
  RunDetail,
  RunSummary,
  StrategyMode,
  Target,
} from "@/lib/types";

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
type ScreenView = "fleet" | "deployments";

export default function App() {
  const [targets, setTargets] = useState<Target[]>([]);
  const [releases, setReleases] = useState<Release[]>([]);
  const [runs, setRuns] = useState<RunSummary[]>([]);
  const [selectedReleaseId, setSelectedReleaseId] = useState<string>("");
  const [selectedRunId, setSelectedRunId] = useState<string>("");
  const [runDetail, setRunDetail] = useState<RunDetail | null>(null);
  const [activeScreen, setActiveScreen] = useState<ScreenView>("fleet");
  const [targetGroupFilter, setTargetGroupFilter] = useState<string>("all");
  const [deploymentScope, setDeploymentScope] = useState<DeploymentScope>("filtered");
  const [selectedTargetIds, setSelectedTargetIds] = useState<string[]>([]);
  const [formState, setFormState] = useState<StartRunFormState>(DEFAULT_FORM);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string>("");

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

  useEffect(() => {
    void refreshTargets();
  }, [refreshTargets]);

  useEffect(() => {
    void refreshReleases();
    void refreshRuns();
  }, [refreshReleases, refreshRuns]);

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
              CodeDeploy-style release orchestration across Azure Lighthouse delegated subscriptions.
            </p>
          </div>
          <div className="w-full space-y-2 md:max-w-md">
            <div className="grid grid-cols-3 gap-2">
              <Kpi label="Total Targets" value={String(targets.length)} />
              <Kpi label="Active Runs" value={String(runStats.running)} />
              <Kpi label="Attention Needed" value={String(runStats.failedOrPartial)} />
            </div>
            <div className="grid grid-cols-2 gap-2">
              <Button
                variant={activeScreen === "fleet" ? "default" : "outline"}
                size="sm"
                onClick={() => setActiveScreen("fleet")}
              >
                Fleet
              </Button>
              <Button
                variant={activeScreen === "deployments" ? "default" : "outline"}
                size="sm"
                onClick={() => setActiveScreen("deployments")}
              >
                Deployments
              </Button>
            </div>
          </div>
        </CardHeader>
      </Card>

      <Card className="glass-card animate-fade-up [animation-delay:40ms] [animation-fill-mode:forwards]">
        <CardHeader className="flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <CardTitle>Target Filters</CardTitle>
          <div className="flex items-center gap-2">
            <Label>Target group</Label>
            <select
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

      {activeScreen === "fleet" ? (
        <FleetTable targets={targets} />
      ) : (
        <>
          <Card className="glass-card animate-fade-up [animation-delay:60ms] [animation-fill-mode:forwards]">
            <CardHeader>
              <CardTitle>Start Deployment Run</CardTitle>
            </CardHeader>
            <CardContent>
              <div className="mb-3 space-y-2">
                <Label>Release version</Label>
                <select
                  className="h-10 w-full rounded-md border border-input bg-background/90 px-3 text-sm"
                  value={selectedReleaseId}
                  onChange={(event) => setSelectedReleaseId(event.target.value)}
                  required
                >
                  {releases.length === 0 ? (
                    <option value="">No releases available</option>
                  ) : null}
                  {releases.map((release) => (
                    <option key={release.id} value={release.id}>
                      {release.template_spec_version}
                    </option>
                  ))}
                </select>
              </div>
              <form className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-6" onSubmit={handleStartRun}>
                <div className="space-y-1">
                  <Label>Strategy</Label>
                  <select
                    className="h-10 w-full rounded-md border border-input bg-background/90 px-3 text-sm"
                    value={formState.strategyMode}
                    onChange={(event) =>
                      setFormState((current) => ({
                        ...current,
                        strategyMode: event.target.value as StrategyMode,
                      }))
                    }
                  >
                    <option value="waves">Grouped rollout (target group order)</option>
                    <option value="all_at_once">All-at-once</option>
                  </select>
                </div>
                <div className="space-y-1">
                  <Label>Target scope</Label>
                  <select
                    className="h-10 w-full rounded-md border border-input bg-background/90 px-3 text-sm"
                    value={deploymentScope}
                    onChange={(event) => setDeploymentScope(event.target.value as DeploymentScope)}
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
                      setFormState((current) => ({
                        ...current,
                        concurrency: Number(event.target.value),
                      }))
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
                      setFormState((current) => ({ ...current, maxFailureCount: event.target.value }))
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
                      setFormState((current) => ({
                        ...current,
                        maxFailureRatePercent: event.target.value,
                      }))
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
                        className="flex items-center gap-2 rounded-md border border-border/70 bg-card/70 px-2 py-1.5 text-xs"
                      >
                        <input type="checkbox" checked readOnly disabled className="h-3.5 w-3.5 accent-primary" />
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
                    <p className="text-xs text-muted-foreground">
                      Selected targets: {selectedTargetIds.length}
                    </p>
                    <div className="flex gap-2">
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => setSelectedTargetIds(targets.map((target) => target.id))}
                      >
                        Select all visible
                      </Button>
                      <Button
                        type="button"
                        variant="outline"
                        size="sm"
                        onClick={() => setSelectedTargetIds([])}
                      >
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
                          className="flex cursor-pointer items-center gap-2 rounded-md border border-border/70 bg-card/70 px-2 py-1.5 text-xs"
                        >
                          <input
                            type="checkbox"
                            checked={checked}
                            onChange={() =>
                              setSelectedTargetIds((current) =>
                                current.includes(target.id)
                                  ? current.filter((id) => id !== target.id)
                                  : [...current, target.id]
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
              onSelectRun={setSelectedRunId}
              onResumeRun={(runId) => {
                void handleResumeRun(runId);
              }}
              onRetryFailed={(runId) => {
                void handleRetryFailed(runId);
              }}
            />
          </div>

          <RunDetailPanel run={runDetail} />
        </>
      )}
    </main>
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
