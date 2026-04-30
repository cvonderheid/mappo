import { useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import {
  type ColumnDef,
  flexRender,
  getCoreRowModel,
  useReactTable,
} from "@tanstack/react-table";

import DataTablePagination from "@/components/DataTablePagination";
import type { Release, RunDetail, RunSummary, TargetExecutionRecord } from "@/lib/types";
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import ColumnVisibilityMenu from "@/components/ColumnVisibilityMenu";
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { usePersistentColumnVisibility } from "@/lib/table-visibility";

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
  description?: string;
  headerAction?: ReactNode;
  runs: RunSummary[];
  releases: Release[];
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
  runIdFilter: string;
  releaseFilter: string;
  statusFilter: string;
  onOpenRun: (runId: string) => void;
  onCloneRun: (runId: string) => void;
  onResumeRun: (runId: string) => void;
  onRetryFailed: (runId: string) => void;
  onRunIdFilterChange: (value: string) => void;
  onReleaseFilterChange: (value: string) => void;
  onStatusFilterChange: (value: string) => void;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  onActionsMenuOpenChange?: (open: boolean) => void;
};

type StageErrorDetails = NonNullable<
  NonNullable<
    NonNullable<TargetExecutionRecord["stages"]>[number]["error"]
  >["details"]
>;

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
  return (run.failedTargets ?? 0) > 0 || (run.queuedTargets ?? 0) > 0;
}

function canRetryFailed(run: RunSummary): boolean {
  if (run.status === "running") {
    return false;
  }
  return (run.failedTargets ?? 0) > 0;
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
  const succeeded = run.succeededTargets ?? 0;
  const failed = run.failedTargets ?? 0;
  const total = run.totalTargets ?? 0;
  const queued = run.queuedTargets ?? 0;
  const inProgress = run.inProgressTargets ?? 0;
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
  description,
  headerAction,
  runs,
  releases,
  page,
  pageSize,
  totalItems,
  totalPages,
  runIdFilter,
  releaseFilter,
  statusFilter,
  onOpenRun,
  onCloneRun,
  onResumeRun,
  onRetryFailed,
  onRunIdFilterChange,
  onReleaseFilterChange,
  onStatusFilterChange,
  onPageChange,
  onPageSizeChange,
  onActionsMenuOpenChange,
}: RunListProps) {
  const [openActionRunId, setOpenActionRunId] = useState<string | null>(null);
  const [columnVisibility, setColumnVisibility] =
    usePersistentColumnVisibility("deployments-runs");

  const releaseVersionById = useMemo(() => {
    const index = new Map<string, string>();
    for (const release of releases) {
      if (release.id && release.sourceVersion) {
        index.set(release.id, release.sourceVersion);
      }
    }
    return index;
  }, [releases]);

  useEffect(() => {
    onActionsMenuOpenChange?.(openActionRunId !== null);
    return () => onActionsMenuOpenChange?.(false);
  }, [onActionsMenuOpenChange, openActionRunId]);

  const columns = useMemo<ColumnDef<RunSummary>[]>(
    () => [
      {
        accessorKey: "id",
        header: "Run",
        cell: ({ row }) => {
          const run = row.original;
          const runId = run.id ?? "unknown";
          return (
            <button
              type="button"
              data-testid={`select-run-${runId}`}
              className="font-mono text-xs text-left hover:underline"
              onClick={() => {
                if (run.id) {
                  onOpenRun(run.id);
                }
              }}
            >
              {runId}
            </button>
          );
        },
      },
      {
        accessorKey: "releaseId",
        header: "Release",
        cell: ({ row }) => {
          const releaseId = row.original.releaseId ?? "unknown";
          const displayVersion = row.original.releaseId
            ? releaseVersionById.get(row.original.releaseId) ?? row.original.releaseId
            : "unknown";
          return (
            <span className="font-mono text-xs" title={releaseId}>
              {displayVersion}
            </span>
          );
        },
      },
      {
        accessorKey: "status",
        header: "Status",
        cell: ({ row }) => (
          <Badge variant={statusVariant(row.original.status ?? "unknown")} className="uppercase">
            {row.original.status ?? "unknown"}
          </Badge>
        ),
      },
      {
        id: "progress",
        header: "Progress",
        cell: ({ row }) => {
          const progress = progressFromSummary(row.original);
          return (
            <div className="min-w-[220px] space-y-1">
              <div className="flex items-center justify-between text-[11px] text-muted-foreground">
                <span>
                  {progress.succeeded} ok / {progress.failed} failed / {progress.total} total
                </span>
                <span>{progress.percentComplete}%</span>
              </div>
              <StackedProgressBar progress={progress} testIdPrefix={`run-progress-${row.original.id}`} />
            </div>
          );
        },
      },
      {
        accessorKey: "guardrailWarnings",
        header: "Guardrails",
        cell: ({ row }) => {
          const warningCount = row.original.guardrailWarnings?.length ?? 0;
          if (warningCount <= 0) {
            return <span className="text-xs text-muted-foreground">None</span>;
          }
          return (
            <Badge variant="outline" className="border-amber-500/60 text-amber-300">
              {warningCount} warning{warningCount === 1 ? "" : "s"}
            </Badge>
          );
        },
      },
      {
        accessorKey: "createdAt",
        header: "Created",
        cell: ({ row }) => (
          <span className="text-xs text-muted-foreground">
            {row.original.createdAt ? new Date(row.original.createdAt).toLocaleString() : "unknown"}
          </span>
        ),
      },
      {
        id: "actions",
        header: "Actions",
        enableHiding: false,
        cell: ({ row }) => {
          const run = row.original;
          const resumeEnabled = canResume(run);
          const retryEnabled = canRetryFailed(run);

          return (
            <DropdownMenu
              open={openActionRunId === run.id}
              onOpenChange={(open) => {
                setOpenActionRunId(open ? (run.id ?? null) : null);
              }}
            >
              <DropdownMenuTrigger asChild>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="h-7 w-7 p-0 font-mono"
                  data-testid={`run-actions-trigger-${run.id ?? "unknown"}`}
                  aria-label={`Actions for ${run.id ?? "unknown"}`}
                >
                  ...
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem
                  data-testid={`run-action-view-${run.id ?? "unknown"}`}
                  onSelect={() => {
                    if (run.id) {
                      onOpenRun(run.id);
                    }
                  }}
                >
                  View Run Details
                </DropdownMenuItem>
                <DropdownMenuItem
                  data-testid={`run-action-clone-${run.id ?? "unknown"}`}
                  onSelect={() => {
                    if (run.id) {
                      onCloneRun(run.id);
                    }
                  }}
                >
                  Clone Run
                </DropdownMenuItem>
                {resumeEnabled || retryEnabled ? <DropdownMenuSeparator /> : null}
                {resumeEnabled ? (
                  <DropdownMenuItem
                    data-testid={`run-action-resume-${run.id ?? "unknown"}`}
                    onSelect={() => {
                      if (run.id) {
                        onResumeRun(run.id);
                      }
                    }}
                  >
                    Resume
                  </DropdownMenuItem>
                ) : null}
                {retryEnabled ? (
                  <DropdownMenuItem
                    data-testid={`run-action-retry-failed-${run.id ?? "unknown"}`}
                    onSelect={() => {
                      if (run.id) {
                        onRetryFailed(run.id);
                      }
                    }}
                  >
                    Retry Failed
                  </DropdownMenuItem>
                ) : null}
              </DropdownMenuContent>
            </DropdownMenu>
          );
        },
      },
    ],
    [onCloneRun, onOpenRun, onResumeRun, onRetryFailed, releaseVersionById]
  );

  const table = useReactTable({
    data: runs,
    columns,
    state: {
      columnVisibility,
    },
    onColumnVisibilityChange: setColumnVisibility,
    getCoreRowModel: getCoreRowModel(),
  });

  const uniqueStatuses = [
    ...new Set(
      runs
        .map((run) => run.status)
        .filter((status): status is NonNullable<RunSummary["status"]> => Boolean(status))
    ),
  ].sort();
  const visibleColumnCount = table.getVisibleLeafColumns().length;

  function renderFilterCell(columnId: string): ReactNode {
    if (columnId === "id") {
      return (
        <Input
          value={runIdFilter}
          onChange={(event) => onRunIdFilterChange(event.target.value)}
          placeholder="Filter run"
          className="h-8"
        />
      );
    }
    if (columnId === "releaseId") {
      return (
        <Input
          value={releaseFilter}
          onChange={(event) => onReleaseFilterChange(event.target.value)}
          placeholder="Filter release"
          className="h-8"
        />
      );
    }
    if (columnId === "status") {
      return (
        <Select
          value={statusFilter || "all"}
          onValueChange={(value) => onStatusFilterChange(value === "all" ? "" : value)}
        >
          <SelectTrigger className="h-8 w-full bg-background/90 px-2 text-xs">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All statuses</SelectItem>
            {uniqueStatuses.map((status) => (
              <SelectItem key={status} value={status}>
                {status}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      );
    }
    return null;
  }

  return (
    <Card className="glass-card animate-fade-up [animation-delay:200ms] [animation-fill-mode:forwards]">
      <CardHeader className="gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div className="space-y-1">
          <CardTitle>Deployment Runs</CardTitle>
          {description ? <CardDescription>{description}</CardDescription> : null}
        </div>
        <CardAction className="flex-wrap justify-end">
          {headerAction}
          <ColumnVisibilityMenu table={table} />
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => {
              onRunIdFilterChange("");
              onReleaseFilterChange("");
              onStatusFilterChange("");
              onPageChange(0);
            }}
          >
            Clear filters
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-2">
        <Table>
          <TableHeader>
            <TableRow>
              {table.getFlatHeaders().map((header) => (
                <TableHead key={header.id}>
                  {header.column.getCanSort() ? (
                    <button
                      type="button"
                      className="inline-flex items-center gap-1 text-left"
                      onClick={header.column.getToggleSortingHandler()}
                    >
                      {flexRender(header.column.columnDef.header, header.getContext())}
                      {header.column.getIsSorted() === "asc" ? "▲" : null}
                      {header.column.getIsSorted() === "desc" ? "▼" : null}
                    </button>
                  ) : (
                    flexRender(header.column.columnDef.header, header.getContext())
                  )}
                </TableHead>
              ))}
            </TableRow>
            <TableRow>
              {table.getVisibleLeafColumns().map((column) => (
                <TableHead key={`filter-${column.id}`}>{renderFilterCell(column.id)}</TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {table.getRowModel().rows.length === 0 ? (
              <TableRow>
                <TableCell colSpan={visibleColumnCount} className="text-sm text-muted-foreground">
                  No deployment runs match current filters.
                </TableCell>
              </TableRow>
            ) : (
              table.getRowModel().rows.map((row) => (
                <TableRow
                  key={row.id}
                  data-testid={`run-row-${row.original.id ?? "unknown"}`}
                >
                  {row.getVisibleCells().map((cell) => (
                    <TableCell key={cell.id}>
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </TableCell>
                  ))}
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
        <DataTablePagination
          page={page}
          pageSize={pageSize}
          totalItems={totalItems}
          totalPages={totalPages}
          noun="runs"
          onPageChange={onPageChange}
          onPageSizeChange={onPageSizeChange}
        />
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

  const targetRecords = run.targetRecords ?? [];
  const progress = progressFromRecords(targetRecords);

  return (
    <Card className="glass-card animate-fade-up [animation-delay:240ms] [animation-fill-mode:forwards]">
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <CardTitle>Run Detail</CardTitle>
        <Badge variant={statusVariant(run.status ?? "unknown")} className="uppercase">
          {run.status ?? "unknown"}
        </Badge>
      </CardHeader>
      <CardContent className="space-y-3">
        <div className="flex flex-wrap gap-2 text-xs text-muted-foreground">
          <span>release: {run.releaseId}</span>
          <span>strategy: {formatStrategyLabel(run.strategyMode ?? "waves")}</span>
          <span>concurrency: {run.concurrency ?? 0}</span>
          <span>per-subscription: {run.subscriptionConcurrency ?? 0}</span>
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
        {run.haltReason ? (
          <div className="rounded-md border border-destructive/60 bg-destructive/10 p-2 text-xs text-destructive-foreground">
            {run.haltReason}
          </div>
        ) : null}
        {(run.guardrailWarnings ?? []).length > 0 ? (
          <GuardrailWarnings runId={`detail-${run.id ?? "unknown"}`} warnings={run.guardrailWarnings ?? []} />
        ) : null}
        <div className="space-y-2">
          {targetRecords.map((record: TargetExecutionRecord) => (
            <TargetRecordCard key={record.targetId ?? "unknown"} record={record} />
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
          <p className="font-semibold">{record.targetId}</p>
          <p className="font-mono text-[11px] text-muted-foreground">{record.subscriptionId}</p>
        </div>
        <Badge variant={statusVariant(record.status ?? "unknown")} className="uppercase">
          {record.status ?? "unknown"}
        </Badge>
      </header>
      <div className="mb-2 flex flex-wrap gap-2">
        {stages.map((stage) => (
          <div
            key={`${stage.stage}-${stage.startedAt}`}
            className="min-w-[220px] flex-1 rounded-md border border-border/60 bg-muted/30 px-2 py-1"
          >
            <div className="mb-1 flex items-start justify-between gap-2">
              <p className="text-[11px] font-semibold">{stage.stage}</p>
              <p className="font-mono text-[10px] text-muted-foreground">
                {stage.startedAt ? new Date(stage.startedAt).toLocaleTimeString() : "unknown"}
                {stage.endedAt ? ` -> ${new Date(stage.endedAt).toLocaleTimeString()}` : ""}
              </p>
            </div>
            <p className="text-[11px]">{stage.message}</p>
            {stage.error ? (
              <div className="mt-2 rounded-md border border-destructive/60 bg-destructive/10 p-2 text-[11px]">
                <p data-testid={`stage-error-code-${record.targetId}-${stage.stage}`} className="font-semibold">
                  Error code: {stage.error.code}
                </p>
                <p className="mt-1">{stage.error.message}</p>
                {isAzureDevOpsPipelineError(stage.error.code) ? (
                  <AzureDevOpsPipelineFailureGuidance record={record} />
                ) : null}
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
                key={`${log.correlationId}-${log.timestamp}-${index}`}
                className="grid gap-1 rounded-sm border-t border-border/40 pt-1 text-[11px] sm:grid-cols-[80px_60px_90px_1fr]"
              >
                <span className="font-mono text-muted-foreground">
                  {log.timestamp ? new Date(log.timestamp).toLocaleTimeString() : "unknown"}
                </span>
                <span className={log.level === "error" ? "font-semibold text-destructive" : "text-muted-foreground"}>
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

function AzureErrorSummary({
  details,
}: {
  details: StageErrorDetails;
}) {
  const summary = asNonEmptyString(details.error);
  const azureCode = asNonEmptyString(details.azureErrorCode);
  const azureMessage = asNonEmptyString(details.azureErrorMessage);
  const requestId =
    asNonEmptyString(details.azureRequestId) ?? asNonEmptyString(details.azureArmServiceRequestId);
  const correlationId = asNonEmptyString(details.azureCorrelationId);
  const deploymentName = asNonEmptyString(details.azureDeploymentName);
  const operationId = asNonEmptyString(details.azureOperationId);
  const resourceId = asNonEmptyString(details.azureResourceId);
  const azureLine =
    azureCode || azureMessage
      ? `Azure: ${azureCode ? `[${azureCode}] ` : ""}${azureMessage ?? ""}`.trim()
      : null;
  const normalizedSummary = summary?.replace(/\r\n/g, "\n");
  const normalizedAzureLine = azureLine?.replace(/\r\n/g, "\n");
  const showSummary =
    normalizedSummary && normalizedSummary !== normalizedAzureLine ? normalizedSummary : null;

  if (!showSummary && !azureLine && !requestId && !correlationId && !deploymentName && !operationId && !resourceId) {
    return null;
  }

  return (
    <div className="mt-1 space-y-1 font-mono text-[10px] text-destructive/90">
      {showSummary ? <p className="whitespace-pre-wrap">{showSummary}</p> : null}
      {azureLine ? <p>{azureLine}</p> : null}
      {requestId ? <p>request-id: {requestId}</p> : null}
      {correlationId ? <p>correlation-id: {correlationId}</p> : null}
      {deploymentName ? <p>deployment: {deploymentName}</p> : null}
      {operationId ? <p>operation-id: {operationId}</p> : null}
      {resourceId ? <p className="break-all">resource-id: {resourceId}</p> : null}
    </div>
  );
}

function isAzureDevOpsPipelineError(code: string | null | undefined): boolean {
  return (code ?? "").startsWith("ADO_PIPELINE_");
}

function AzureDevOpsPipelineFailureGuidance({ record }: { record: TargetExecutionRecord }) {
  const handle = record.externalExecutionHandle;
  const runUrl = asNonEmptyString(handle?.executionUrl);
  const logsUrl = asNonEmptyString(handle?.logsUrl);
  const executionName = asNonEmptyString(handle?.executionName) ?? asNonEmptyString(handle?.executionId);
  const executionStatus = asNonEmptyString(handle?.executionStatus);

  return (
    <div className="mt-2 space-y-2 rounded-md border border-destructive/40 bg-background/50 p-2 text-[11px] text-foreground">
      <div className="flex flex-wrap items-center gap-2">
        <span className="font-semibold">Azure DevOps run</span>
        {executionName ? <Badge variant="outline">{executionName}</Badge> : null}
        {executionStatus ? <Badge variant="destructive">{executionStatus}</Badge> : null}
      </div>
      <div className="flex flex-wrap gap-2">
        {runUrl ? (
          <a className="text-primary underline-offset-2 hover:underline" href={runUrl} target="_blank" rel="noreferrer">
            Open run
          </a>
        ) : null}
        {logsUrl ? (
          <a className="text-primary underline-offset-2 hover:underline" href={logsUrl} target="_blank" rel="noreferrer">
            Open logs
          </a>
        ) : null}
      </div>
      <ul className="list-disc space-y-1 pl-4 text-muted-foreground">
        <li>Inspect the failed Azure DevOps stage or job in the run logs.</li>
        <li>Confirm the deployment pipeline is authorized to use its Azure service connection.</li>
        <li>Confirm that service connection's service principal has the required Azure RBAC on the target subscription or resource group.</li>
      </ul>
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
