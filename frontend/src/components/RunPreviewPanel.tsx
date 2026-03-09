import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import type {
  RunPreview,
  RunPreviewChange,
  RunPreviewPropertyChange,
  RunTargetPreview,
} from "@/lib/types";

type RunPreviewPanelProps = {
  preview: RunPreview;
};

type PreviewProgressCardProps = {
  elapsedSeconds: number;
  isPreviewing: boolean;
  progressPercent: number;
  targetCount: number;
  onCancelPreview: () => void;
};

const NOISE_PATH_PATTERNS = [
  /^properties\.configuration\.ingress\.exposedPort$/i,
  /^properties\.configuration\.ingress\.traffic$/i,
  /^properties\.runningStatus$/i,
  /^properties\.workloadProfileName$/i,
];

const PRIMARY_IMPACT_CATEGORIES: Array<{ label: string; pattern: RegExp }> = [
  { label: "Container image", pattern: /^properties\.template\.containers(\[\d+\])?\.image$/i },
  { label: "Environment variables", pattern: /^properties\.template\.containers(\[\d+\])?\.env/i },
  { label: "Ingress", pattern: /^properties\.configuration\.ingress/i },
  { label: "Scale settings", pattern: /^properties\.template\.scale/i },
  { label: "Container command", pattern: /^properties\.template\.containers(\[\d+\])?\.command/i },
  { label: "Container arguments", pattern: /^properties\.template\.containers(\[\d+\])?\.args/i },
  { label: "Container resources", pattern: /^properties\.template\.containers(\[\d+\])?\.resources/i },
];

const SECONDARY_IMPACT_CATEGORIES: Array<{ label: string; pattern: RegExp }> = [
  { label: "Registry credentials", pattern: /^properties\.configuration\.registries/i },
  { label: "Secrets", pattern: /^properties\.configuration\.secrets/i },
];

export function PreviewProgressCard({
  elapsedSeconds,
  isPreviewing,
  progressPercent,
  targetCount,
  onCancelPreview,
}: PreviewProgressCardProps) {
  if (!isPreviewing) {
    return null;
  }

  return (
    <Card className="mt-3 border-border/70 bg-card/70">
      <CardHeader className="pb-3">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <CardTitle>Previewing Changes</CardTitle>
            <CardDescription>
              Running ARM what-if for {targetCount} target{targetCount === 1 ? "" : "s"}.
            </CardDescription>
          </div>
          <Button type="button" variant="outline" size="sm" onClick={onCancelPreview}>
            Cancel Preview
          </Button>
        </div>
      </CardHeader>
      <CardContent className="space-y-3">
        <Progress value={progressPercent} aria-label="Preview progress" />
        <div className="flex flex-wrap items-center justify-between gap-2 text-xs text-muted-foreground">
          <span>{progressPercent}% complete estimate</span>
          <span>{elapsedSeconds}s elapsed</span>
        </div>
        <p className="text-xs text-muted-foreground">
          This progress bar is approximate. Azure what-if runs target-by-target and does not expose
          a native live percentage for deployment stack previews.
        </p>
      </CardContent>
    </Card>
  );
}

export function RunPreviewPanel({ preview }: RunPreviewPanelProps) {
  const modeLabel = preview.mode === "ARM_WHAT_IF" ? "ARM what-if" : "Unsupported";

  return (
    <Card className="mt-3 border-border/70 bg-card/70">
      <CardHeader className="pb-3">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <div>
            <CardTitle>Preview Results</CardTitle>
            <CardDescription>
              Release {preview.releaseVersion ?? "unknown"} for {preview.targets?.length ?? 0} target
              {preview.targets?.length === 1 ? "" : "s"}.
            </CardDescription>
          </div>
          <Badge variant={preview.mode === "ARM_WHAT_IF" ? "default" : "outline"}>{modeLabel}</Badge>
        </div>
        {preview.caveat ? <p className="text-xs text-muted-foreground">{preview.caveat}</p> : null}
        {preview.warnings && preview.warnings.length > 0 ? (
          <div className="rounded-md border border-amber-500/40 bg-amber-500/10 p-2 text-xs text-amber-100">
            {preview.warnings.map((warning) => (
              <p key={warning}>{warning}</p>
            ))}
          </div>
        ) : null}
      </CardHeader>
      <CardContent>
        <Accordion type="single" collapsible className="w-full">
          {preview.targets?.map((target) => (
            <AccordionItem key={target.targetId ?? "unknown"} value={target.targetId ?? "unknown"}>
              <AccordionTrigger className="py-3 no-underline hover:no-underline">
                <div className="flex w-full flex-wrap items-center justify-between gap-2 pr-3">
                  <div>
                    <p className="font-mono text-sm">{target.targetId ?? "unknown-target"}</p>
                    <p className="text-xs text-muted-foreground">{targetSummary(target)}</p>
                  </div>
                  <Badge variant={targetStatusVariant(target.status)}>{target.status ?? "UNKNOWN"}</Badge>
                </div>
              </AccordionTrigger>
              <AccordionContent>
                <div className="space-y-3 text-xs">
                  {target.managedResourceGroupId ? (
                    <p className="font-mono text-muted-foreground">{target.managedResourceGroupId}</p>
                  ) : null}
                  {target.warnings && target.warnings.length > 0 ? (
                    <div className="rounded-md border border-amber-500/40 bg-amber-500/10 p-2 text-amber-100">
                      {target.warnings.map((warning) => (
                        <p key={warning}>{warning}</p>
                      ))}
                    </div>
                  ) : null}
                  {target.error ? (
                    <div className="rounded-md border border-destructive/60 bg-destructive/10 p-2 text-destructive-foreground">
                      <p className="font-medium">{target.error.message ?? "Preview failed."}</p>
                      {target.error.details?.error ? <p className="mt-1">{target.error.details.error}</p> : null}
                    </div>
                  ) : null}
                  {target.changes && target.changes.length > 0 ? (
                    <div className="space-y-2">
                      {target.changes.map((change) => {
                        const propertyChanges = change.propertyChanges ?? [];
                        const primaryChanges = primaryPropertyChanges(propertyChanges);
                        const secondaryChanges = secondaryPropertyChanges(propertyChanges);
                        const hiddenChangeCount =
                          propertyChanges.length - primaryChanges.length - secondaryChanges.length;
                        return (
                          <div
                            key={`${change.resourceId ?? "unknown"}-${change.changeType ?? "change"}`}
                            className="rounded-md border border-border/70 bg-muted/20 p-3"
                          >
                            <div className="flex flex-wrap items-center justify-between gap-2">
                              <div>
                                <p className="font-medium">{resourceLabel(change.resourceId)}</p>
                                <p className="font-mono text-[11px] text-muted-foreground">
                                  {change.resourceId ?? "unknown-resource"}
                                </p>
                              </div>
                              <Badge variant="outline">{change.changeType ?? "Unknown"}</Badge>
                            </div>
                            <p className="mt-2 text-muted-foreground">{changeSummary(change)}</p>
                            {change.unsupportedReason ? (
                              <p className="mt-2 text-muted-foreground">{change.unsupportedReason}</p>
                            ) : null}
                            {primaryChanges.length > 0 ? (
                              <div className="mt-3 flex flex-wrap gap-1.5">
                                {primaryChanges.map((propertyChange) => (
                                  <span
                                    key={`${propertyChange.changeType}-${propertyChange.path}`}
                                    className="rounded-full border border-border/70 px-2 py-0.5 text-[10px] font-medium text-foreground"
                                  >
                                    {impactLabel(propertyChange.path)}
                                  </span>
                                ))}
                              </div>
                            ) : null}
                            {secondaryChanges.length > 0 ? (
                              <div className="mt-3 rounded-md border border-border/70 bg-background/50 p-2 text-[11px] text-muted-foreground">
                                <p>
                                  Azure also reports auth/runtime drift for{" "}
                                  {joinLabels(uniqueImpactLabels(secondaryChanges))}. Secure values and
                                  runtime-populated fields often appear modified even when the release only
                                  changes the image or release metadata.
                                </p>
                              </div>
                            ) : null}
                            {hiddenChangeCount > 0 ? (
                              <details className="mt-3 rounded-md border border-border/70 bg-background/50 p-2">
                                <summary className="cursor-pointer text-[11px] text-muted-foreground">
                                  Show raw ARM property paths ({hiddenChangeCount} hidden)
                                </summary>
                                <div className="mt-2 flex flex-wrap gap-1.5">
                                  {propertyChanges
                                    .filter((propertyChange) => !isPrimaryImpact(propertyChange) && !isSecondaryImpact(propertyChange))
                                    .map((propertyChange) => (
                                    <span
                                      key={`${propertyChange.changeType}-${propertyChange.path}`}
                                      className="rounded-full border border-border/70 px-2 py-0.5 font-mono text-[10px] text-muted-foreground"
                                    >
                                      {propertyChange.changeType}: {propertyChange.path}
                                    </span>
                                    ))}
                                </div>
                              </details>
                            ) : null}
                          </div>
                        );
                      })}
                    </div>
                  ) : (
                    <p className="text-muted-foreground">No resource changes returned for this target.</p>
                  )}
                </div>
              </AccordionContent>
            </AccordionItem>
          ))}
        </Accordion>
      </CardContent>
    </Card>
  );
}

function targetStatusVariant(status: string | null | undefined) {
  if (status === "PREVIEWED") {
    return "default";
  }
  if (status === "FAILED") {
    return "destructive";
  }
  return "outline";
}

function resourceLabel(resourceId: string | null | undefined) {
  if (!resourceId) {
    return "Azure resource";
  }
  const providersIndex = resourceId.indexOf("/providers/");
  if (providersIndex < 0) {
    return "Azure resource";
  }
  const providerPath = resourceId.slice(providersIndex + "/providers/".length).split("/");
  const namespace = providerPath[0] ?? "";
  const type = providerPath[1] ?? "";
  const kind = `${namespace}/${type}`;
  if (kind === "Microsoft.App/containerApps") {
    return "Container App";
  }
  if (kind === "Microsoft.App/managedEnvironments") {
    return "Container Apps Environment";
  }
  if (kind === "Microsoft.Resources/deploymentStacks") {
    return "Deployment Stack";
  }
  return kind || "Azure resource";
}

function changeSummary(change: RunPreviewChange) {
  const resource = resourceLabel(change.resourceId);
  const changeType = (change.changeType ?? "").toLowerCase();
  if (changeType === "create") {
    return `${resource} will be created.`;
  }
  if (changeType === "delete") {
    return `${resource} will be deleted.`;
  }
  const propertyChanges = change.propertyChanges ?? [];
  const primaryHighlights = uniqueImpactLabels(primaryPropertyChanges(propertyChanges));
  const secondaryHighlights = uniqueImpactLabels(secondaryPropertyChanges(propertyChanges));
  if (primaryHighlights.length > 0 && secondaryHighlights.length > 0) {
    return `${resource} will update ${joinLabels(primaryHighlights)}. Azure also reports ${joinLabels(secondaryHighlights)} drift.`;
  }
  if (primaryHighlights.length > 0) {
    return `${resource} will update ${joinLabels(primaryHighlights)}.`;
  }
  if (secondaryHighlights.length > 0) {
    return `${resource} mainly shows Azure-reported ${joinLabels(secondaryHighlights)} drift.`;
  }
  if (propertyChanges.length > 0) {
    return `${resource} only shows platform or runtime metadata differences in Azure what-if.`;
  }
  return `${resource} will be updated.`;
}

function targetSummary(target: RunTargetPreview) {
  if (target.status === "FAILED") {
    return target.summary ?? "Preview failed.";
  }
  const summaries = (target.changes ?? []).map(changeSummary).filter(Boolean);
  if (summaries.length === 0) {
    return target.summary ?? "No preview summary.";
  }
  if (summaries.length === 1) {
    return summaries[0] ?? "No preview summary.";
  }
  return `${summaries[0]} ${summaries.length - 1} more resource change${summaries.length - 1 === 1 ? "" : "s"} pending.`;
}

function primaryPropertyChanges(propertyChanges: RunPreviewPropertyChange[]) {
  return propertyChanges.filter(isPrimaryImpact);
}

function secondaryPropertyChanges(propertyChanges: RunPreviewPropertyChange[]) {
  return propertyChanges.filter(isSecondaryImpact);
}

function isPrimaryImpact(propertyChange: RunPreviewPropertyChange) {
  const path = normalizePreviewPath(propertyChange.path);
  return path !== "" && !NOISE_PATH_PATTERNS.some((pattern) => pattern.test(path))
    && PRIMARY_IMPACT_CATEGORIES.some((category) => category.pattern.test(path));
}

function isSecondaryImpact(propertyChange: RunPreviewPropertyChange) {
  const path = normalizePreviewPath(propertyChange.path);
  return path !== "" && !NOISE_PATH_PATTERNS.some((pattern) => pattern.test(path))
    && SECONDARY_IMPACT_CATEGORIES.some((category) => category.pattern.test(path));
}

function uniqueImpactLabels(propertyChanges: RunPreviewPropertyChange[]) {
  return Array.from(new Set(propertyChanges.map((propertyChange) => impactLabel(propertyChange.path))));
}

function impactLabel(path: string | null | undefined) {
  const value = normalizePreviewPath(path);
  for (const category of PRIMARY_IMPACT_CATEGORIES) {
    if (category.pattern.test(value)) {
      return category.label;
    }
  }
  for (const category of SECONDARY_IMPACT_CATEGORIES) {
    if (category.pattern.test(value)) {
      return category.label;
    }
  }
  return value.startsWith("properties.template.") ? "Container template" : value;
}

function normalizePreviewPath(path: string | null | undefined) {
  const value = path?.trim() ?? "";
  if (value.startsWith("$.")) {
    return value.slice(2);
  }
  if (value === "$") {
    return "";
  }
  if (value.startsWith("$")) {
    return value.slice(1);
  }
  return value;
}

function joinLabels(labels: string[]) {
  if (labels.length === 1) {
    return labels[0] ?? "";
  }
  if (labels.length === 2) {
    return `${labels[0]} and ${labels[1]}`;
  }
  return `${labels.slice(0, -1).join(", ")}, and ${labels[labels.length - 1]}`;
}
