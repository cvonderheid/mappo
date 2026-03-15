import { useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { toast } from "sonner";

import { ReleaseWebhookDeliveriesDataTable } from "@/components/AdminTables";
import ReleaseIngestDrawer from "@/components/ReleaseIngestDrawer";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import type {
  Release,
  ReleaseManifestIngestRequest,
  ReleaseManifestIngestResponse,
} from "@/lib/types";

type ReleasesPageProps = {
  releases: Release[];
  releaseIngestIsSubmitting: boolean;
  refreshKey: number;
  onIngestManagedAppReleases: (
    request: ReleaseManifestIngestRequest
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
  releases,
  releaseIngestIsSubmitting,
  refreshKey,
  onIngestManagedAppReleases,
}: ReleasesPageProps) {
  const location = useLocation();
  const navigate = useNavigate();
  const [releaseIngestDrawerOpen, setReleaseIngestDrawerOpen] = useState(false);

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
    request: ReleaseManifestIngestRequest
  ): Promise<void> {
    try {
      const result = await onIngestManagedAppReleases(request);
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

  return (
    <div className="space-y-4">
      <div className="flex animate-fade-up items-center justify-between [animation-delay:60ms] [animation-fill-mode:forwards]">
        <p className="text-xs text-muted-foreground">
          Release catalog, webhook audit, and manual ingest controls.
        </p>
        <div className="flex items-center gap-2">
          <Button
            type="button"
            variant="outline"
            onClick={() => setReleaseIngestDrawerOpen(true)}
          >
            Check All Release Sources
          </Button>
          <ReleaseIngestDrawer
            isSubmitting={releaseIngestIsSubmitting}
            open={releaseIngestDrawerOpen}
            onOpenChange={setReleaseIngestDrawerOpen}
            onIngest={handleIngestManagedAppReleases}
          />
        </div>
      </div>

      <Card className="glass-card animate-fade-up [animation-delay:100ms] [animation-fill-mode:forwards]">
        <CardHeader className="flex flex-row items-center justify-between space-y-0">
          <CardTitle>Release Catalog</CardTitle>
          <Badge variant="outline" className="font-mono text-[11px]">
            {releases.length} releases
          </Badge>
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

      <Card className="glass-card animate-fade-up [animation-delay:120ms] [animation-fill-mode:forwards]">
        <CardHeader>
          <CardTitle>Release Webook Events</CardTitle>
        </CardHeader>
        <CardContent>
          <ReleaseWebhookDeliveriesDataTable refreshKey={refreshKey} />
        </CardContent>
      </Card>
    </div>
  );
}
