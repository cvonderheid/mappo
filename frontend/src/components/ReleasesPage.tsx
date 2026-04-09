import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { toast } from "sonner";

import { ReleaseWebhookDeliveriesDataTable } from "@/components/AdminTables";
import ReleaseIngestDrawer from "@/components/ReleaseIngestDrawer";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import type {
  Release,
  ReleaseManifestIngestRequest,
  ReleaseManifestIngestResponse,
} from "@/lib/types";

type ReleasesPageProps = {
  selectedProjectId?: string;
  releases: Release[];
  releaseIngestIsSubmitting: boolean;
  refreshKey: number;
  onIngestManagedAppReleases: (
    request?: ReleaseManifestIngestRequest
  ) => Promise<ReleaseManifestIngestResponse>;
};

function formatTimestamp(value: string | null | undefined): string {
  if (!value) {
    return "";
  }
  const timestamp = new Date(value);
  if (Number.isNaN(timestamp.getTime())) {
    return "";
  }
  return timestamp.toLocaleString();
}

export default function ReleasesPage({
  selectedProjectId,
  releases,
  releaseIngestIsSubmitting,
  refreshKey,
  onIngestManagedAppReleases,
}: ReleasesPageProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const [releaseIngestDrawerOpen, setReleaseIngestDrawerOpen] = useState(false);
  const [isCheckingReleases, setIsCheckingReleases] = useState(false);

  const latestRelease = useMemo(() => releases[0] ?? null, [releases]);

  useEffect(() => {
    if (location.pathname !== "/releases") {
      return;
    }
    const params = new URLSearchParams(location.search);
    const openIngest = params.get("ingest") === "1";
    if (!openIngest) {
      return;
    }
    setReleaseIngestDrawerOpen(true);
    params.delete("ingest");
    const nextSearch = params.toString();
    void navigate(
      {
        pathname: location.pathname,
        search: nextSearch ? `?${nextSearch}` : "",
      },
      { replace: true }
    );
  }, [location.pathname, location.search, navigate]);

  async function handleIngestManagedAppReleases(
    request?: ReleaseManifestIngestRequest
  ): Promise<void> {
    const resolvedRequest = selectedProjectId
      ? {
          ...request,
          projectId: request?.projectId ?? selectedProjectId,
        }
      : request;
    try {
      const result = await onIngestManagedAppReleases(resolvedRequest);
      const baseMessage = `Ingested ${result.createdCount} new release(s), skipped ${result.skippedCount}, ignored drafts ${result.ignoredCount ?? 0}, manifest entries ${result.manifestReleaseCount}.`;
      if ((result.createdCount ?? 0) > 0) {
        toast.success(baseMessage);
      } else {
        toast(baseMessage);
      }
    } catch (error) {
      toast.error((error as Error).message);
      throw error;
    }
  }

  async function handleCheckForNewReleases(): Promise<void> {
    if (!selectedProjectId) {
      toast.error("Select a project before checking for new releases.");
      return;
    }
    setIsCheckingReleases(true);
    try {
      await handleIngestManagedAppReleases({ projectId: selectedProjectId });
    } finally {
      setIsCheckingReleases(false);
    }
  }

  return (
    <div className="space-y-4">
      <Card className="glass-card animate-fade-up [animation-delay:100ms] [animation-fill-mode:forwards]">
        <CardHeader className="gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="space-y-1">
            <CardTitle>Release Catalog</CardTitle>
            <CardDescription>
              Review available releases, inspect inbound release events, and check linked release
              sources for new versions.
            </CardDescription>
          </div>
          <CardAction className="flex-wrap justify-end">
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                void handleCheckForNewReleases();
              }}
              disabled={releaseIngestIsSubmitting || isCheckingReleases}
            >
              {releaseIngestIsSubmitting || isCheckingReleases ? "Checking..." : "Check for new releases"}
            </Button>
            <Button
              type="button"
              variant="ghost"
              onClick={() => setReleaseIngestDrawerOpen(true)}
            >
              Advanced
            </Button>
          </CardAction>
        </CardHeader>
        <CardContent className="space-y-2">
          {latestRelease ? (
            <div className="rounded-md border border-border/70 bg-background/40 p-2 text-xs text-muted-foreground">
              Latest: <span className="font-medium text-foreground">{latestRelease.sourceVersion}</span>{" "}
              ({latestRelease.id}) at{" "}
              <span className="font-medium text-foreground">
                {formatTimestamp(latestRelease.createdAt)}
              </span>
            </div>
          ) : null}
          {releases.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              No releases available for this project yet.
            </p>
          ) : (
            <div className="space-y-2">
              {releases
                .filter((release): release is Release & { id: string } => Boolean(release.id))
                .slice(0, 8)
                .map((release) => (
                  <div key={release.id} className="rounded-md border border-border/70 bg-card/70 p-3">
                    <div className="flex items-start justify-between gap-2">
                      <div>
                        <p className="text-sm font-semibold text-primary">{release.sourceVersion}</p>
                        <p className="font-mono text-[11px] text-muted-foreground">{release.id}</p>
                      </div>
                      <Badge variant="outline" className="uppercase tracking-[0.06em]">
                        {release.sourceType?.replaceAll("_", " ") ?? "unknown"}
                      </Badge>
                    </div>
                    <p className="mt-2 text-xs text-muted-foreground">{release.releaseNotes || "No notes."}</p>
                  </div>
                ))}
            </div>
          )}
        </CardContent>
      </Card>

      <ReleaseIngestDrawer
        defaultProjectId={selectedProjectId}
        isSubmitting={releaseIngestIsSubmitting}
        open={releaseIngestDrawerOpen}
        onOpenChange={setReleaseIngestDrawerOpen}
        onIngest={handleIngestManagedAppReleases}
        showTrigger={false}
      />

      <ReleaseWebhookDeliveriesDataTable
        refreshKey={refreshKey}
        title="Release Webhook Events"
        cardClassName="glass-card animate-fade-up [animation-delay:120ms] [animation-fill-mode:forwards]"
      />
    </div>
  );
}
