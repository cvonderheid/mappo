import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";

import FleetTable from "@/components/FleetTable";
import ReleaseList from "@/components/ReleaseList";
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
import type { Release, RunDetail, RunSummary, StrategyMode, Target } from "@/lib/types";

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
  const [targets, setTargets] = useState<Target[]>([]);
  const [releases, setReleases] = useState<Release[]>([]);
  const [runs, setRuns] = useState<RunSummary[]>([]);
  const [selectedReleaseId, setSelectedReleaseId] = useState<string>("");
  const [selectedRunId, setSelectedRunId] = useState<string>("");
  const [runDetail, setRunDetail] = useState<RunDetail | null>(null);
  const [ringFilter, setRingFilter] = useState<string>("all");
  const [formState, setFormState] = useState<StartRunFormState>(DEFAULT_FORM);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string>("");

  const refreshTargets = useCallback(async () => {
    try {
      setTargets(await listTargets(ringFilter));
      setErrorMessage("");
    } catch (error) {
      setErrorMessage((error as Error).message);
    }
  }, [ringFilter]);

  const refreshReleases = useCallback(async () => {
    try {
      const payload = await listReleases();
      setReleases(payload);
      if (!selectedReleaseId && payload.length > 0) {
        setSelectedReleaseId(payload[0].id);
      }
    } catch (error) {
      setErrorMessage((error as Error).message);
    }
  }, [selectedReleaseId]);

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
      void refreshRuns();
      if (selectedRunId) {
        void refreshRunDetail(selectedRunId);
      }
    }, 1200);

    return () => window.clearInterval(intervalId);
  }, [refreshRunDetail, refreshRuns, selectedRunId]);

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

  async function handleStartRun(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    if (!selectedReleaseId) {
      setErrorMessage("Select a release first.");
      return;
    }

    setIsSubmitting(true);
    try {
      const created = await createRun({
        release_id: selectedReleaseId,
        strategy_mode: formState.strategyMode,
        wave_tag: "ring",
        wave_order: ["canary", "prod"],
        concurrency: formState.concurrency,
        target_tags: ringFilter === "all" ? {} : { ring: ringFilter },
        stop_policy: {
          max_failure_count:
            formState.maxFailureCount.trim() === ""
              ? undefined
              : Number(formState.maxFailureCount),
          max_failure_rate:
            formState.maxFailureRatePercent.trim() === ""
              ? undefined
              : Number(formState.maxFailureRatePercent) / 100,
        },
      });
      setSelectedRunId(created.id);
      await refreshRuns();
      await refreshRunDetail(created.id);
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
          <div className="grid w-full grid-cols-3 gap-2 md:max-w-md">
            <Kpi label="Total Targets" value={String(targets.length)} />
            <Kpi label="Active Runs" value={String(runStats.running)} />
            <Kpi label="Attention Needed" value={String(runStats.failedOrPartial)} />
          </div>
        </CardHeader>
      </Card>

      <Card className="glass-card animate-fade-up [animation-delay:40ms] [animation-fill-mode:forwards]">
        <CardHeader className="flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <CardTitle>Start Deployment Run</CardTitle>
          <div className="flex items-center gap-2">
            <Label>Fleet filter</Label>
            <select
              className="h-10 rounded-md border border-input bg-background/90 px-3 text-sm"
              value={ringFilter}
              onChange={(event) => setRingFilter(event.target.value)}
            >
              <option value="all">All rings</option>
              <option value="canary">Canary ring only</option>
              <option value="prod">Prod ring only</option>
            </select>
          </div>
        </CardHeader>
        <CardContent>
          <form className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-6" onSubmit={handleStartRun}>
            <div className="space-y-1">
              <Label>Release</Label>
              <select
                className="h-10 w-full rounded-md border border-input bg-background/90 px-3 text-sm"
                value={selectedReleaseId}
                onChange={(event) => setSelectedReleaseId(event.target.value)}
                required
              >
                <option value="" disabled>
                  Select release
                </option>
                {releases.map((release) => (
                  <option key={release.id} value={release.id}>
                    {release.template_spec_version}
                  </option>
                ))}
              </select>
            </div>
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
                <option value="waves">Waves by ring</option>
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
              <Button type="submit" className="w-full" disabled={isSubmitting || releases.length === 0}>
                {isSubmitting ? "Starting..." : "Start Run"}
              </Button>
            </div>
          </form>
          {errorMessage ? (
            <div className="mt-3 rounded-md border border-destructive/60 bg-destructive/10 p-2 text-xs text-destructive-foreground">
              {errorMessage}
            </div>
          ) : null}
        </CardContent>
      </Card>

      <div className="grid gap-4 lg:grid-cols-[2fr_1fr]">
        <FleetTable targets={targets} />
        <ReleaseList
          releases={releases}
          selectedReleaseId={selectedReleaseId}
          onSelectRelease={setSelectedReleaseId}
        />
      </div>

      <div className="grid gap-4 lg:grid-cols-[1fr_2fr]">
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
        <RunDetailPanel run={runDetail} />
      </div>
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
