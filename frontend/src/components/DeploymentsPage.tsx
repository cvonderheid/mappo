import { FormEvent } from "react";

import { RunList } from "@/components/RunPanels";
import { PreviewProgressCard, RunPreviewPanel } from "@/components/RunPreviewPanel";
import { Button } from "@/components/ui/button";
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
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { StartRunFormState } from "@/lib/deployment-form";
import type { Release, RunPreview, RunSummary, StrategyMode, Target } from "@/lib/types";

type DeploymentsPageProps = {
  errorMessage: string;
  formState: StartRunFormState;
  isSubmitting: boolean;
  isPreviewing: boolean;
  previewElapsedSeconds: number;
  previewErrorMessage: string;
  previewProgressPercent: number;
  previewTargetCount: number;
  releases: Release[];
  runPreview: RunPreview | null;
  runs: RunSummary[];
  runPage: number;
  runPageSize: number;
  runTotalItems: number;
  runTotalPages: number;
  runIdFilter: string;
  runReleaseFilter: string;
  runStatusFilter: string;
  selectedRelease: Release | null;
  selectedReleaseId: string;
  selectedTargetIds: string[];
  targetGroupFilter: string;
  targets: Target[];
  controlsOpen: boolean;
  onControlsOpenChange: (open: boolean) => void;
  onFormStateChange: (state: StartRunFormState) => void;
  onOpenRun: (runId: string) => void;
  onCloneRun: (runId: string) => void;
  onReleaseChange: (releaseId: string) => void;
  onRunIdFilterChange: (value: string) => void;
  onRunReleaseFilterChange: (value: string) => void;
  onRunStatusFilterChange: (value: string) => void;
  onRunsPageChange: (page: number) => void;
  onRunsPageSizeChange: (size: number) => void;
  onRetryFailed: (runId: string) => void;
  onResumeRun: (runId: string) => void;
  onSelectedTargetIdsChange: (targetIds: string[]) => void;
  onStartRun: (event: FormEvent<HTMLFormElement>) => Promise<void>;
  onTargetGroupFilterChange: (targetGroup: string) => void;
  onRunActionsMenuOpenChange: (open: boolean) => void;
  onPreviewRun: () => Promise<void>;
  onCancelPreview: () => void;
};

export default function DeploymentsPage({
  errorMessage,
  formState,
  isSubmitting,
  isPreviewing,
  previewElapsedSeconds,
  previewErrorMessage,
  previewProgressPercent,
  previewTargetCount,
  releases,
  runPreview,
  runs,
  runPage,
  runPageSize,
  runTotalItems,
  runTotalPages,
  runIdFilter,
  runReleaseFilter,
  runStatusFilter,
  selectedRelease,
  selectedReleaseId,
  selectedTargetIds,
  targetGroupFilter,
  targets,
  controlsOpen,
  onControlsOpenChange,
  onFormStateChange,
  onOpenRun,
  onCloneRun,
  onReleaseChange,
  onRunIdFilterChange,
  onRunReleaseFilterChange,
  onRunStatusFilterChange,
  onRunsPageChange,
  onRunsPageSizeChange,
  onRetryFailed,
  onResumeRun,
  onSelectedTargetIdsChange,
  onStartRun,
  onTargetGroupFilterChange,
  onRunActionsMenuOpenChange,
  onPreviewRun,
  onCancelPreview,
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
              New Deployment
            </Button>
          </DrawerTrigger>
          <DrawerContent className="glass-card">
            <DrawerHeader>
              <DrawerTitle>Deployment Controls</DrawerTitle>
              <DrawerDescription>
                Choose target group, optional specific targets, release version, and stop
                policies before starting a run.
              </DrawerDescription>
            </DrawerHeader>
            <div className="max-h-[74vh] overflow-y-auto px-4 pb-2">
              <div className="mb-3 rounded-md border border-border/70 bg-muted/20 p-3">
                <div className="flex items-center gap-2">
                  <Label htmlFor="target-group-filter">Target group</Label>
                  <Select value={targetGroupFilter} onValueChange={onTargetGroupFilterChange}>
                    <SelectTrigger id="target-group-filter" className="h-10 w-[220px] bg-background/90 text-sm">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="all">All groups</SelectItem>
                      <SelectItem value="canary">Canary group</SelectItem>
                      <SelectItem value="prod">Production group</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
                <p className="mt-2 text-xs text-muted-foreground">
                  Group is the deployment cohort tag stored as <code>ring</code> in target
                  metadata.
                </p>
              </div>
              <form id="start-run-form" className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-5" onSubmit={onStartRun}>
                <div className="space-y-1">
                  <Label htmlFor="release-version">Release version</Label>
                  <Select value={selectedReleaseId} onValueChange={onReleaseChange} disabled={releases.length === 0} required>
                    <SelectTrigger id="release-version" className="h-10 w-full bg-background/90 text-sm">
                      <SelectValue placeholder="No releases available" />
                    </SelectTrigger>
                      <SelectContent>
                      {releases
                        .filter((release): release is Release & { id: string } => Boolean(release.id))
                        .map((release) => (
                          <SelectItem key={release.id} value={release.id}>
                            {release.sourceVersion}
                          </SelectItem>
                        ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1">
                  <Label htmlFor="strategy-mode">Strategy</Label>
                  <Select value={formState.strategyMode} onValueChange={(value) => onFormStateChange({ ...formState, strategyMode: value as StrategyMode })}>
                    <SelectTrigger id="strategy-mode" className="h-10 w-full bg-background/90 text-sm">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="waves">Grouped rollout (target group order)</SelectItem>
                      <SelectItem value="all_at_once">All-at-once</SelectItem>
                    </SelectContent>
                  </Select>
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
              </form>
              <p className="mt-2 text-xs text-muted-foreground">
                Max failures and max failure rate are both active when provided. The run halts if either threshold is exceeded.
              </p>
              <div className="mt-3 flex flex-wrap justify-end gap-2">
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    void onPreviewRun();
                  }}
                  disabled={isPreviewing || selectedRelease === null}
                >
                  {isPreviewing ? "Previewing..." : "Preview Changes"}
                </Button>
                <Button
                  type="submit"
                  form="start-run-form"
                  className="min-w-32"
                  disabled={isSubmitting || selectedRelease === null}
                >
                  {isSubmitting ? "Starting..." : "Start Run"}
                </Button>
              </div>
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
                      onClick={() =>
                        onSelectedTargetIdsChange(
                          targets.flatMap((target) => (target.id ? [target.id] : []))
                        )
                      }
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
                    const targetId = target.id ?? "";
                    const checked = selectedTargetIds.includes(targetId);
                    return (
                      <label
                        key={targetId}
                        data-testid={`specific-target-row-${targetId}`}
                        className="flex cursor-pointer items-center gap-2 rounded-md border border-border/70 bg-card/70 px-2 py-1.5 text-xs"
                      >
                        <input
                          data-testid={`specific-target-checkbox-${targetId}`}
                          type="checkbox"
                          checked={checked}
                          onChange={() =>
                            onSelectedTargetIdsChange(
                              checked
                                ? selectedTargetIds.filter((id) => id !== targetId)
                                : [...selectedTargetIds, targetId]
                            )
                          }
                          className="h-3.5 w-3.5 accent-primary"
                        />
                        <span className="font-mono">{targetId}</span>
                        <span className="text-muted-foreground">
                          {target.tags?.ring ?? "unassigned"}/{target.tags?.region ?? "unknown"}
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
              {previewErrorMessage ? (
                <div className="mt-3 rounded-md border border-destructive/60 bg-destructive/10 p-2 text-xs text-destructive-foreground">
                  {previewErrorMessage}
                </div>
              ) : null}
              <PreviewProgressCard
                elapsedSeconds={previewElapsedSeconds}
                isPreviewing={isPreviewing}
                progressPercent={previewProgressPercent}
                targetCount={previewTargetCount}
                onCancelPreview={onCancelPreview}
              />
              {runPreview ? (
                <RunPreviewPanel
                  preview={runPreview}
                  selectedRelease={selectedRelease}
                  targets={targets}
                />
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
          releases={releases}
          page={runPage}
          pageSize={runPageSize}
          totalItems={runTotalItems}
          totalPages={runTotalPages}
          runIdFilter={runIdFilter}
          releaseFilter={runReleaseFilter}
          statusFilter={runStatusFilter}
          onOpenRun={onOpenRun}
          onCloneRun={onCloneRun}
          onResumeRun={onResumeRun}
          onRetryFailed={onRetryFailed}
          onRunIdFilterChange={onRunIdFilterChange}
          onReleaseFilterChange={onRunReleaseFilterChange}
          onStatusFilterChange={onRunStatusFilterChange}
          onPageChange={onRunsPageChange}
          onPageSizeChange={onRunsPageSizeChange}
          onActionsMenuOpenChange={onRunActionsMenuOpenChange}
        />
      </div>
    </>
  );
}
