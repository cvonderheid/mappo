import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import type {
  Release,
  RunPreview,
  RunPreviewChange,
  RunPreviewPropertyChange,
  RunTargetPreview,
  Target,
} from "@/lib/types";

type RunPreviewPanelProps = {
  preview: RunPreview;
  selectedRelease: Release | null;
  targets: Target[];
};

type PreviewProgressCardProps = {
  elapsedSeconds: number;
  isPreviewing: boolean;
  progressPercent: number;
  targetCount: number;
  onCancelPreview: () => void;
};

type PreviewFinding = {
  severity: "critical" | "warning" | "info";
  message: string;
};

const NOISE_PATH_PATTERNS = [
  /^properties\.configuration\.ingress\.exposedPort$/i,
  /^properties\.configuration\.ingress\.traffic$/i,
  /^properties\.runningStatus$/i,
  /^properties\.workloadProfileName$/i,
];

const EXPECTED_RESOURCE_TYPES = new Set([
  "Microsoft.App/containerApps",
  "Microsoft.Resources/deploymentStacks",
]);

const CRITICAL_RESOURCE_TYPE_PATTERNS = [
  /^Microsoft\.DBforPostgreSQL\//i,
  /^Microsoft\.Sql\//i,
  /^Microsoft\.Storage\//i,
  /^Microsoft\.Network\//i,
  /^Microsoft\.KeyVault\//i,
  /^Microsoft\.ManagedIdentity\//i,
  /^Microsoft\.Authorization\//i,
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

export function RunPreviewPanel({ preview, selectedRelease, targets }: RunPreviewPanelProps) {
  const modeLabel = preview.mode === "ARM_WHAT_IF" ? "ARM what-if" : "Unsupported";
  const targetMap = new Map(targets.map((target) => [target.id ?? "", target]));
  const targetAnalyses = (preview.targets ?? []).map((targetPreview) =>
    analyzeTargetPreview(targetPreview, targetMap, selectedRelease)
  );
  const overallRisk = summarizeOverallRisk(targetAnalyses);

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
      <CardContent className="space-y-4">
        <section className="rounded-md border border-border/70 bg-muted/20 p-4">
          <h3 className="text-sm font-semibold">Release Impact</h3>
          <div className="mt-3 grid gap-3 md:grid-cols-2">
            {targetAnalyses.map((analysis) => (
              <div key={analysis.targetId} className="rounded-md border border-border/70 bg-card/70 p-3">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <p className="font-mono text-sm">{analysis.targetId}</p>
                  <Badge variant="outline">
                    {analysis.currentVersion} {"->"} {analysis.nextVersion}
                  </Badge>
                </div>
                <ul className="mt-2 space-y-1 text-xs text-muted-foreground">
                  {analysis.releaseImpact.map((item) => (
                    <li key={`${analysis.targetId}-${item}`}>{item}</li>
                  ))}
                </ul>
              </div>
            ))}
          </div>
        </section>

        <section
          className={
            overallRisk.critical.length > 0
              ? "rounded-md border border-destructive/60 bg-destructive/10 p-4"
              : overallRisk.warning.length > 0
                ? "rounded-md border border-amber-500/40 bg-amber-500/10 p-4"
                : "rounded-md border border-emerald-500/40 bg-emerald-500/10 p-4"
          }
        >
          <div className="flex flex-wrap items-center justify-between gap-2">
            <h3 className="text-sm font-semibold">Infrastructure Risk</h3>
            <div className="flex flex-wrap gap-2">
              {overallRisk.critical.length > 0 ? (
                <Badge variant="destructive">{overallRisk.critical.length} critical</Badge>
              ) : null}
              {overallRisk.warning.length > 0 ? (
                <Badge variant="secondary">{overallRisk.warning.length} warning</Badge>
              ) : null}
              {overallRisk.critical.length === 0 && overallRisk.warning.length === 0 ? (
                <Badge variant="default">No destructive or unexpected changes</Badge>
              ) : null}
            </div>
          </div>
          {overallRisk.critical.length > 0 || overallRisk.warning.length > 0 ? (
            <ul className="mt-3 space-y-1 text-xs">
              {overallRisk.critical.map((finding) => (
                <li key={`critical-${finding}`}>{finding}</li>
              ))}
              {overallRisk.warning.map((finding) => (
                <li key={`warning-${finding}`}>{finding}</li>
              ))}
            </ul>
          ) : (
            <p className="mt-2 text-xs text-muted-foreground">
              Azure what-if did not report deletes or unexpected infrastructure mutations. The
              remaining technical details are mostly Container App configuration drift and secure
              field churn.
            </p>
          )}
        </section>

        <Accordion type="single" collapsible className="w-full">
          {targetAnalyses.map((analysis) => (
            <AccordionItem key={analysis.targetId} value={analysis.targetId}>
              <AccordionTrigger className="py-3 no-underline hover:no-underline">
                <div className="flex w-full flex-wrap items-center justify-between gap-2 pr-3">
                  <div>
                    <p className="font-mono text-sm">{analysis.targetId}</p>
                    <p className="text-xs text-muted-foreground">{analysis.summary}</p>
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    {analysis.risk.critical.length > 0 ? (
                      <Badge variant="destructive">Critical risk</Badge>
                    ) : analysis.risk.warning.length > 0 ? (
                      <Badge variant="secondary">Review warnings</Badge>
                    ) : (
                      <Badge variant="default">Low risk</Badge>
                    )}
                    <Badge variant={targetStatusVariant(analysis.target.status)}>
                      {analysis.target.status ?? "UNKNOWN"}
                    </Badge>
                  </div>
                </div>
              </AccordionTrigger>
              <AccordionContent>
                <div className="space-y-3 text-xs">
                  {analysis.target.managedResourceGroupId ? (
                    <p className="font-mono text-muted-foreground">
                      {analysis.target.managedResourceGroupId}
                    </p>
                  ) : null}

                  <div className="rounded-md border border-border/70 bg-background/50 p-3">
                    <p className="font-medium">Release impact</p>
                    <ul className="mt-2 space-y-1 text-muted-foreground">
                      {analysis.releaseImpact.map((item) => (
                        <li key={`${analysis.targetId}-impact-${item}`}>{item}</li>
                      ))}
                    </ul>
                  </div>

                  <div
                    className={
                      analysis.risk.critical.length > 0
                        ? "rounded-md border border-destructive/60 bg-destructive/10 p-3"
                        : analysis.risk.warning.length > 0
                          ? "rounded-md border border-amber-500/40 bg-amber-500/10 p-3"
                          : "rounded-md border border-border/70 bg-background/50 p-3"
                    }
                  >
                    <p className="font-medium">Infrastructure risk</p>
                    {analysis.risk.critical.length > 0 || analysis.risk.warning.length > 0 ? (
                      <ul className="mt-2 space-y-1">
                        {analysis.risk.critical.map((finding) => (
                          <li key={`${analysis.targetId}-critical-${finding}`}>{finding}</li>
                        ))}
                        {analysis.risk.warning.map((finding) => (
                          <li key={`${analysis.targetId}-warning-${finding}`}>{finding}</li>
                        ))}
                      </ul>
                    ) : (
                      <p className="mt-2 text-muted-foreground">
                        No destructive or unexpected infrastructure changes were detected for this target.
                      </p>
                    )}
                    {analysis.risk.info.length > 0 ? (
                      <div className="mt-3 rounded-md border border-border/70 bg-background/50 p-2 text-muted-foreground">
                        {analysis.risk.info.map((finding) => (
                          <p key={`${analysis.targetId}-info-${finding}`}>{finding}</p>
                        ))}
                      </div>
                    ) : null}
                  </div>

                  <details className="rounded-md border border-border/70 bg-background/50 p-3">
                    <summary className="cursor-pointer text-[11px] text-muted-foreground">
                      Technical details
                    </summary>
                    <div className="mt-3 space-y-3">
                      {analysis.target.error ? (
                        <div className="rounded-md border border-destructive/60 bg-destructive/10 p-2 text-destructive-foreground">
                          <p className="font-medium">
                            {analysis.target.error.message ?? "Preview failed."}
                          </p>
                          {analysis.target.error.details?.error ? (
                            <p className="mt-1">{analysis.target.error.details.error}</p>
                          ) : null}
                        </div>
                      ) : null}

                      {analysis.target.warnings && analysis.target.warnings.length > 0 ? (
                        <div className="rounded-md border border-amber-500/40 bg-amber-500/10 p-2 text-amber-100">
                          {analysis.target.warnings.map((warning) => (
                            <p key={warning}>{warning}</p>
                          ))}
                        </div>
                      ) : null}

                      {analysis.target.changes && analysis.target.changes.length > 0 ? (
                        <div className="space-y-2">
                          {analysis.target.changes.map((change) => (
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
                              <p className="mt-2 text-muted-foreground">{technicalChangeSummary(change)}</p>
                              {change.propertyChanges && change.propertyChanges.length > 0 ? (
                                <div className="mt-3 flex flex-wrap gap-1.5">
                                  {change.propertyChanges.map((propertyChange) => (
                                    <span
                                      key={`${change.resourceId ?? "unknown"}-${propertyChange.changeType}-${propertyChange.path}`}
                                      className="rounded-full border border-border/70 px-2 py-0.5 font-mono text-[10px] text-muted-foreground"
                                    >
                                      {propertyChange.changeType}: {propertyChange.path}
                                    </span>
                                  ))}
                                </div>
                              ) : null}
                            </div>
                          ))}
                        </div>
                      ) : (
                        <p className="text-muted-foreground">
                          No Azure resource changes were returned for this target.
                        </p>
                      )}
                    </div>
                  </details>
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

function analyzeTargetPreview(
  targetPreview: RunTargetPreview,
  targetMap: Map<string, Target>,
  selectedRelease: Release | null
) {
  const target = targetMap.get(targetPreview.targetId ?? "") ?? null;
  const currentVersion = target?.lastDeployedRelease ?? "unknown";
  const nextVersion = selectedRelease?.sourceVersion ?? "unknown";
  const releaseImpact = releaseImpactItems(currentVersion, nextVersion, selectedRelease, targetPreview);
  const risk = summarizeTargetRisk(targetPreview.changes ?? []);
  const summary =
    risk.critical.length > 0
      ? `${currentVersion} -> ${nextVersion}. Critical infrastructure changes detected.`
      : risk.warning.length > 0
        ? `${currentVersion} -> ${nextVersion}. Review infrastructure warnings.`
        : `${currentVersion} -> ${nextVersion}. No destructive or unexpected infrastructure changes detected.`;

  return {
    target: targetPreview,
    targetId: targetPreview.targetId ?? "unknown-target",
    currentVersion,
    nextVersion,
    releaseImpact,
    risk,
    summary,
  };
}

function summarizeOverallRisk(
  analyses: Array<ReturnType<typeof analyzeTargetPreview>>
): { critical: string[]; warning: string[] } {
  const critical = new Set<string>();
  const warning = new Set<string>();

  for (const analysis of analyses) {
    for (const finding of analysis.risk.critical) {
      critical.add(`${analysis.targetId}: ${finding}`);
    }
    for (const finding of analysis.risk.warning) {
      warning.add(`${analysis.targetId}: ${finding}`);
    }
  }

  return {
    critical: Array.from(critical),
    warning: Array.from(warning),
  };
}

function releaseImpactItems(
  currentVersion: string,
  nextVersion: string,
  selectedRelease: Release | null,
  targetPreview: RunTargetPreview
): string[] {
  const items = [`Release version ${currentVersion} -> ${nextVersion}.`];
  const softwareVersion = releaseParameterDefault(selectedRelease, "softwareVersion");
  if (softwareVersion) {
    items.push(`Software version output will move to ${softwareVersion}.`);
  }
  const dataModelVersion = releaseParameterDefault(selectedRelease, "dataModelVersion");
  if (dataModelVersion) {
    items.push(`Data model version output will move to ${dataModelVersion}.`);
  }
  const changeCount = targetPreview.changes?.length ?? 0;
  items.push(
    changeCount === 0
      ? "Azure what-if returned no infrastructure changes."
      : `Azure what-if reported ${changeCount} resource change${changeCount === 1 ? "" : "s"}.`
  );
  return items;
}

function releaseParameterDefault(
  release: Release | null,
  key: string
): string | null {
  const defaults = release?.parameterDefaults as Record<string, unknown> | null | undefined;
  const value = defaults?.[key];
  if (typeof value === "string" && value.trim() !== "") {
    return value;
  }
  if (typeof value === "number" || typeof value === "boolean") {
    return String(value);
  }
  return null;
}

function summarizeTargetRisk(changes: RunPreviewChange[]) {
  const critical = new Set<string>();
  const warning = new Set<string>();
  const info = new Set<string>();

  for (const change of changes) {
    for (const finding of analyzeChangeRisk(change)) {
      if (finding.severity === "critical") {
        critical.add(finding.message);
      } else if (finding.severity === "warning") {
        warning.add(finding.message);
      } else {
        info.add(finding.message);
      }
    }
  }

  return {
    critical: Array.from(critical),
    warning: Array.from(warning),
    info: Array.from(info),
  };
}

function analyzeChangeRisk(change: RunPreviewChange): PreviewFinding[] {
  const findings: PreviewFinding[] = [];
  const resourceType = resourceTypeFromId(change.resourceId);
  const label = resourceLabel(change.resourceId);
  const changeType = String(change.changeType ?? "").trim().toLowerCase();
  const propertyChanges = change.propertyChanges ?? [];

  if (changeType === "delete") {
    findings.push({
      severity: "critical",
      message: `${label} would be deleted.`,
    });
  } else if (resourceType && !EXPECTED_RESOURCE_TYPES.has(resourceType)) {
    findings.push({
      severity: isCriticalResourceType(resourceType) ? "critical" : "warning",
      message: `${label} (${resourceType}) would be ${changeType || "updated"}.`,
    });
  }

  if (isCriticalResourceType(resourceType)) {
    findings.push({
      severity: changeType === "delete" ? "critical" : "warning",
      message: `${label} touches a high-risk Azure resource type.`,
    });
  }

  if (resourceType === "Microsoft.App/containerApps") {
    if (hasMeaningfulPath(propertyChanges, /^properties\.configuration\.ingress/i)) {
      findings.push({
        severity: "warning",
        message: "Container App ingress settings would change.",
      });
    }
    if (hasMeaningfulPath(propertyChanges, /^properties\.template\.scale/i)) {
      findings.push({
        severity: "warning",
        message: "Container App scale settings would change.",
      });
    }
    if (hasMeaningfulPath(propertyChanges, /^properties\.configuration\.registries/i)) {
      findings.push({
        severity: "info",
        message:
          "Azure reports registry credential drift. Secure and runtime-managed fields often appear modified in what-if.",
      });
    }
    if (hasMeaningfulPath(propertyChanges, /^properties\.configuration\.secrets/i)) {
      findings.push({
        severity: "info",
        message:
          "Azure reports secret drift. Secure values are commonly surfaced as changes even when the release only updates image or release metadata.",
      });
    }
  }

  return dedupeFindings(findings);
}

function hasMeaningfulPath(
  propertyChanges: RunPreviewPropertyChange[],
  pattern: RegExp
) {
  return propertyChanges.some((propertyChange) => {
    const path = normalizePreviewPath(propertyChange.path);
    return path !== "" && !NOISE_PATH_PATTERNS.some((noise) => noise.test(path)) && pattern.test(path);
  });
}

function dedupeFindings(findings: PreviewFinding[]) {
  const seen = new Set<string>();
  return findings.filter((finding) => {
    const key = `${finding.severity}:${finding.message}`;
    if (seen.has(key)) {
      return false;
    }
    seen.add(key);
    return true;
  });
}

function resourceTypeFromId(resourceId: string | null | undefined) {
  if (!resourceId) {
    return "";
  }
  const providersIndex = resourceId.indexOf("/providers/");
  if (providersIndex < 0) {
    return "";
  }
  const parts = resourceId.slice(providersIndex + "/providers/".length).split("/");
  const namespace = parts[0] ?? "";
  const type = parts[1] ?? "";
  return namespace && type ? `${namespace}/${type}` : "";
}

function isCriticalResourceType(resourceType: string) {
  return CRITICAL_RESOURCE_TYPE_PATTERNS.some((pattern) => pattern.test(resourceType));
}

function resourceLabel(resourceId: string | null | undefined) {
  const kind = resourceTypeFromId(resourceId);
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

function technicalChangeSummary(change: RunPreviewChange) {
  const label = resourceLabel(change.resourceId);
  const changeType = String(change.changeType ?? "").trim().toLowerCase();
  if (changeType === "create") {
    return `${label} would be created.`;
  }
  if (changeType === "delete") {
    return `${label} would be deleted.`;
  }
  const propertyChanges = change.propertyChanges ?? [];
  if (propertyChanges.length === 0) {
    return `${label} would be updated.`;
  }
  return `${label} has ${propertyChanges.length} Azure-reported property change${
    propertyChanges.length === 1 ? "" : "s"
  }.`;
}

function normalizePreviewPath(path: string | null | undefined) {
  return String(path ?? "")
    .replace(/\[(\d+)\]/g, ".$1")
    .replace(/^\.+/, "")
    .trim();
}
