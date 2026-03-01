import { Link } from "react-router-dom";

import type { RunDetail, RunSummary, TargetExecutionRecord } from "@/lib/types";
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

type ProgressCounts = {
  total: number;
  queued: number;
  inProgress: number;
  succeeded: number;
  failed: number;
  completed: number;
  percentComplete: number;
};

type RunListProps = {
  runs: RunSummary[];
  selectedRunId: string;
  onOpenRun: (runId: string) => void;
  onResumeRun: (runId: string) => void;
  onRetryFailed: (runId: string) => void;
};

function statusVariant(status: string): "default" | "secondary" | "destructive" | "outline" {
  if (status === "succeeded" || status === "SUCCEEDED") {
    return "default";
  }
  if (status === "running" || status === "VALIDATING" || status === "DEPLOYING" || status === "VERIFYING") {
    return "secondary";
  }
  if (status === "queued" || status === "QUEUED") {
    return "outline";
  }
  return "destructive";
}

function canResume(run: RunSummary): boolean {
  if (run.status === "running" || run.status === "succeeded") {
    return false;
  }
  return run.failed_targets > 0 || run.queued_targets > 0;
}

function canRetryFailed(run: RunSummary): boolean {
  if (run.status === "running") {
    return false;
  }
  return run.failed_targets > 0;
}

function formatStrategyLabel(strategyMode: string): string {
  if (strategyMode === "waves") {
    return "grouped rollout";
  }
  if (strategyMode === "all_at_once") {
    return "all-at-once";
  }
  return strategyMode;
}

function percentComplete(completed: number, total: number): number {
  if (total <= 0) {
    return 0;
  }
  return Math.round((completed / total) * 100);
}

function asNonEmptyString(value: unknown): string | null {
  if (typeof value !== "string") {
    return null;
  }
  const normalized = value.trim();
  if (normalized.length === 0) {
    return null;
  }
  return normalized;
}

type ProgressSegment = {
  key: string;
  count: number;
  widthPercent: number;
  className: string;
  label: string;
};

function progressSegments(progress: ProgressCounts): ProgressSegment[] {
  if (progress.total <= 0) {
    return [];
  }

  const raw: Array<{
    key: string;
    count: number;
    className: string;
    label: string;
  }> = [
    { key: "succeeded", count: progress.succeeded, className: "bg-emerald-500", label: "succeeded" },
    { key: "failed", count: progress.failed, className: "bg-red-500", label: "failed" },
    { key: "in-progress", count: progress.inProgress, className: "bg-sky-500", label: "in-progress" },
    { key: "queued", count: progress.queued, className: "bg-slate-500", label: "queued" },
  ];

  return raw
    .filter((segment) => segment.count > 0)
    .map((segment) => ({
      ...segment,
      widthPercent: (segment.count / progress.total) * 100,
    }));
}

function progressFromSummary(run: RunSummary): ProgressCounts {
  const completed = run.succeeded_targets + run.failed_targets;
  return {
    total: run.total_targets,
    queued: run.queued_targets,
    inProgress: run.in_progress_targets,
    succeeded: run.succeeded_targets,
    failed: run.failed_targets,
    completed,
    percentComplete: percentComplete(completed, run.total_targets),
  };
}

function progressFromRecords(records: TargetExecutionRecord[]): ProgressCounts {
  let queued = 0;
  let inProgress = 0;
  let succeeded = 0;
  let failed = 0;

  for (const record of records) {
    if (record.status === "QUEUED") {
      queued += 1;
      continue;
    }
    if (record.status === "SUCCEEDED") {
      succeeded += 1;
      continue;
    }
    if (record.status === "FAILED") {
      failed += 1;
      continue;
    }
    inProgress += 1;
  }

  const total = records.length;
  const completed = succeeded + failed;
  return {
    total,
    queued,
    inProgress,
    succeeded,
    failed,
    completed,
    percentComplete: percentComplete(completed, total),
  };
}

function StackedProgressBar({
  progress,
  testIdPrefix,
}: {
  progress: ProgressCounts;
  testIdPrefix?: string;
}) {
  const segments = progressSegments(progress);

  return (
    <div className="flex h-2 overflow-hidden rounded-full bg-muted/70">
      {segments.map((segment) => (
        <div
          key={segment.key}
          data-testid={testIdPrefix ? `${testIdPrefix}-segment-${segment.key}` : undefined}
          className={`h-full ${segment.className}`}
          style={{ width: `${segment.widthPercent}%` }}
          title={`${segment.label}: ${segment.count}`}
        />
      ))}
    </div>
  );
}

export function RunList({
  runs,
  selectedRunId,
  onOpenRun,
  onResumeRun,
  onRetryFailed,
}: RunListProps) {
  return (
    <Card className="glass-card animate-fade-up [animation-delay:200ms] [animation-fill-mode:forwards]">
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <CardTitle>Deployment Runs</CardTitle>
        <Badge variant="outline" className="font-mono text-[11px]">
          {runs.length} total
        </Badge>
      </CardHeader>
      <CardContent className="space-y-2">
        {runs.map((run) => {
          const progress = progressFromSummary(run);
          const resumeEnabled = canResume(run);
          const retryEnabled = canRetryFailed(run);
          return (
            <div
              key={run.id}
              data-testid={`run-card-${run.id}`}
              className={
                run.id === selectedRunId
                  ? "rounded-md border border-secondary/60 bg-secondary/5 p-3"
                  : "rounded-md border border-border/70 bg-card/70 p-3"
              }
            >
              <button
                type="button"
                data-testid={`select-run-${run.id}`}
                className="mb-2 flex w-full items-center justify-between text-left"
                onClick={() => onOpenRun(run.id)}
              >
                <div>
                  <p className="font-mono text-xs">{run.id}</p>
                  <p className="text-xs text-muted-foreground">release: {run.release_id}</p>
                </div>
                <Badge variant={statusVariant(run.status)} className="uppercase">
                  {run.status}
                </Badge>
              </button>
              <div className="mb-2 space-y-1">
                <div className="flex items-center justify-between text-[11px] text-muted-foreground">
                  <span>
                    succeeded: {progress.succeeded} failed: {progress.failed} total: {progress.total}
                  </span>
                  <span>{progress.percentComplete}% processed</span>
                </div>
                <StackedProgressBar progress={progress} testIdPrefix={`run-progress-${run.id}`} />
              </div>
              {(run.guardrail_warnings ?? []).length > 0 ? <GuardrailWarnings runId={run.id} warnings={run.guardrail_warnings ?? []} /> : null}
              <div className="grid grid-cols-2 gap-2">
                <Button
                  data-testid={`resume-${run.id}`}
                  variant="outline"
                  size="sm"
                  onClick={() => onResumeRun(run.id)}
                  disabled={!resumeEnabled}
                >
                  Resume
                </Button>
                <Button
                  data-testid={`retry-failed-${run.id}`}
                  variant="outline"
                  size="sm"
                  onClick={() => onRetryFailed(run.id)}
                  disabled={!retryEnabled}
                >
                  Retry Failed
                </Button>
              </div>
              <div className="mt-2">
                <Button asChild variant="ghost" size="sm" className="w-full">
                  <Link to={`/deployments/${encodeURIComponent(run.id)}`}>View Run Details</Link>
                </Button>
              </div>
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
}

type RunDetailProps = {
  run: RunDetail | null;
};

export function RunDetailPanel({ run }: RunDetailProps) {
  if (!run) {
    return (
      <Card className="glass-card animate-fade-up [animation-delay:240ms] [animation-fill-mode:forwards]">
        <CardHeader>
          <CardTitle>Run Detail</CardTitle>
        </CardHeader>
        <CardContent>
          <p className="text-sm text-muted-foreground">Select a run to inspect per-target stages and logs.</p>
        </CardContent>
      </Card>
    );
  }

  const progress = progressFromRecords(run.target_records);

  return (
    <Card className="glass-card animate-fade-up [animation-delay:240ms] [animation-fill-mode:forwards]">
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <CardTitle>Run Detail</CardTitle>
        <Badge variant={statusVariant(run.status)} className="uppercase">
          {run.status}
        </Badge>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="flex flex-wrap gap-2 text-xs text-muted-foreground">
          <span>release: {run.release_id}</span>
          <span>strategy: {formatStrategyLabel(run.strategy_mode)}</span>
          <span>concurrency: {run.concurrency}</span>
          <span>per-subscription: {run.subscription_concurrency}</span>
        </div>
        <div className="rounded-md border border-border/70 bg-card/70 p-3">
          <div className="mb-2 flex items-center justify-between text-xs">
            <span className="font-semibold">Overall Progress</span>
            <span className="font-mono">
              {progress.completed}/{progress.total} processed ({progress.percentComplete}%)
            </span>
          </div>
          <StackedProgressBar progress={progress} testIdPrefix="run-detail-progress" />
          <div className="mt-2 flex flex-wrap gap-2 text-[11px] text-muted-foreground">
            <span>succeeded: {progress.succeeded}</span>
            <span>failed: {progress.failed}</span>
            <span>in-progress: {progress.inProgress}</span>
            <span>queued: {progress.queued}</span>
          </div>
        </div>
        {run.halt_reason ? (
          <div className="rounded-md border border-destructive/60 bg-destructive/10 p-2 text-xs text-destructive-foreground">
            {run.halt_reason}
          </div>
        ) : null}
        {(run.guardrail_warnings ?? []).length > 0 ? (
          <GuardrailWarnings runId={`detail-${run.id}`} warnings={run.guardrail_warnings ?? []} />
        ) : null}
        <div className="space-y-2">
          {run.target_records.map((record) => (
            <TargetRecordCard key={record.target_id} record={record} />
          ))}
        </div>
      </CardContent>
    </Card>
  );
}

function TargetRecordCard({ record }: { record: TargetExecutionRecord }) {
  const stages = record.stages ?? [];
  const logs = record.logs ?? [];
  const visibleLogs = logs.slice(-12);

  return (
    <article className="rounded-md border border-border/70 bg-card/70 p-3">
      <header className="mb-2 flex items-center justify-between gap-2">
        <div>
          <p className="font-semibold">{record.target_id}</p>
          <p className="font-mono text-[11px] text-muted-foreground">{record.subscription_id}</p>
        </div>
        <Badge variant={statusVariant(record.status)} className="uppercase">
          {record.status}
        </Badge>
      </header>
      <div className="mb-2 flex flex-wrap gap-2">
        {stages.map((stage) => (
          <div
            key={`${stage.stage}-${stage.started_at}`}
            className="min-w-[220px] flex-1 rounded-md border border-border/60 bg-muted/30 px-2 py-1"
          >
            <div className="mb-1 flex items-start justify-between gap-2">
              <p className="text-[11px] font-semibold">{stage.stage}</p>
              <p className="font-mono text-[10px] text-muted-foreground">
                {new Date(stage.started_at).toLocaleTimeString()}
                {stage.ended_at ? ` -> ${new Date(stage.ended_at).toLocaleTimeString()}` : ""}
              </p>
            </div>
            <p className="text-[11px]">{stage.message}</p>
            <div className="mt-1 flex flex-wrap gap-2 text-[10px] text-muted-foreground">
              <span className="font-mono">correlation-id: {stage.correlation_id}</span>
            </div>
            {stage.error ? (
              <div className="mt-2 rounded-md border border-destructive/60 bg-destructive/10 p-2 text-[11px]">
                <p data-testid={`stage-error-code-${record.target_id}-${stage.stage}`} className="font-semibold">
                  Error code: {stage.error.code}
                </p>
                <p className="mt-1">{stage.error.message}</p>
                {stage.error.details ? (
                  <AzureErrorSummary details={stage.error.details} />
                ) : null}
                {stage.error.details ? (
                  <details className="mt-2">
                    <summary className="cursor-pointer text-[10px] text-muted-foreground">
                      Azure error details
                    </summary>
                    <pre className="mt-1 overflow-x-auto rounded-md border border-border/60 bg-background/60 p-2 font-mono text-[10px]">
                      {JSON.stringify(stage.error.details, null, 2)}
                    </pre>
                  </details>
                ) : null}
              </div>
            ) : null}
          </div>
        ))}
      </div>
      {logs.length > 0 ? (
        <details>
          <summary className="cursor-pointer text-xs text-muted-foreground">
            Logs ({logs.length})
          </summary>
          <div className="mt-2 space-y-1">
            {visibleLogs.map((log, index) => (
              <div
                key={`${log.correlation_id}-${log.timestamp}-${index}`}
                className="grid gap-1 rounded-sm border-t border-border/40 pt-1 text-[11px] sm:grid-cols-[80px_60px_90px_1fr]"
              >
                <span className="font-mono text-muted-foreground">
                  {new Date(log.timestamp).toLocaleTimeString()}
                </span>
                <span className={log.level === "ERROR" ? "font-semibold text-destructive" : "text-muted-foreground"}>
                  {log.level}
                </span>
                <span>{log.stage}</span>
                <span>{log.message}</span>
              </div>
            ))}
          </div>
        </details>
      ) : null}
    </article>
  );
}

function AzureErrorSummary({ details }: { details: Record<string, unknown> }) {
  const azureCode = asNonEmptyString(details.azure_error_code);
  const azureMessage = asNonEmptyString(details.azure_error_message);
  const requestId =
    asNonEmptyString(details.azure_request_id) ?? asNonEmptyString(details.azure_arm_service_request_id);
  const correlationId = asNonEmptyString(details.azure_correlation_id);

  if (!azureCode && !azureMessage && !requestId && !correlationId) {
    return null;
  }

  return (
    <div className="mt-1 space-y-1 font-mono text-[10px] text-destructive/90">
      {azureCode || azureMessage ? (
        <p>
          Azure: {azureCode ? `[${azureCode}] ` : ""}
          {azureMessage ?? ""}
        </p>
      ) : null}
      {requestId ? <p>request-id: {requestId}</p> : null}
      {correlationId ? <p>correlation-id: {correlationId}</p> : null}
    </div>
  );
}

function GuardrailWarnings({ runId, warnings }: { runId: string; warnings: string[] }) {
  return (
    <Accordion type="single" collapsible className="mb-2 rounded-md border border-amber-500/40 bg-amber-500/10 px-2 text-amber-200">
      <AccordionItem value={`warnings-${runId}`} className="border-b-0">
        <AccordionTrigger className="py-2 text-[11px] hover:no-underline">
          Guardrail warnings ({warnings.length})
        </AccordionTrigger>
        <AccordionContent className="space-y-1 pb-2 text-[11px]">
          {warnings.map((warning) => (
            <p key={`${runId}-${warning}`}>{warning}</p>
          ))}
        </AccordionContent>
      </AccordionItem>
    </Accordion>
  );
}
