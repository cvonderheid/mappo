import type { RunDetail, RunSummary, TargetExecutionRecord } from "../lib/types";
import { Badge } from "./ui/badge";
import { Button } from "./ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "./ui/card";

type RunListProps = {
  runs: RunSummary[];
  selectedRunId: string;
  onSelectRun: (runId: string) => void;
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

export function RunList({
  runs,
  selectedRunId,
  onSelectRun,
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
        {runs.map((run) => (
          <div
            key={run.id}
            className={
              run.id === selectedRunId
                ? "rounded-md border border-secondary/60 bg-secondary/5 p-3"
                : "rounded-md border border-border/70 bg-card/70 p-3"
            }
          >
            <button
              type="button"
              className="mb-2 flex w-full items-center justify-between text-left"
              onClick={() => onSelectRun(run.id)}
            >
              <div>
                <p className="font-mono text-xs">{run.id}</p>
                <p className="text-xs text-muted-foreground">release: {run.release_id}</p>
              </div>
              <Badge variant={statusVariant(run.status)} className="uppercase">
                {run.status}
              </Badge>
            </button>
            <div className="grid grid-cols-2 gap-2">
              <Button variant="outline" size="sm" onClick={() => onResumeRun(run.id)}>
                Resume
              </Button>
              <Button variant="outline" size="sm" onClick={() => onRetryFailed(run.id)}>
                Retry Failed
              </Button>
            </div>
          </div>
        ))}
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
          <span>strategy: {run.strategy_mode}</span>
          <span>concurrency: {run.concurrency}</span>
        </div>
        {run.halt_reason ? (
          <div className="rounded-md border border-destructive/60 bg-destructive/10 p-2 text-xs text-destructive-foreground">
            {run.halt_reason}
          </div>
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
        {record.stages.map((stage) => (
          <div key={`${stage.stage}-${stage.started_at}`} className="rounded-md border border-border/60 bg-muted/30 px-2 py-1">
            <p className="text-[11px] font-semibold">{stage.stage}</p>
            <p className="font-mono text-[10px] text-muted-foreground">
              {new Date(stage.started_at).toLocaleTimeString()}
            </p>
          </div>
        ))}
      </div>
      {record.logs.length > 0 ? (
        <details>
          <summary className="cursor-pointer text-xs text-muted-foreground">
            Logs ({record.logs.length})
          </summary>
          <div className="mt-2 space-y-1">
            {record.logs.slice(-6).map((log) => (
              <div
                key={`${log.correlation_id}-${log.timestamp}`}
                className="grid gap-1 rounded-sm border-t border-border/40 pt-1 text-[11px] sm:grid-cols-[80px_90px_1fr]"
              >
                <span className="font-mono text-muted-foreground">
                  {new Date(log.timestamp).toLocaleTimeString()}
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
