import type { Release } from "@/lib/types";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { releaseSourceTypeLabel } from "@/lib/releases";

type ReleaseListProps = {
  releases: Release[];
  selectedReleaseId: string;
  onSelectRelease: (releaseId: string) => void;
};

export default function ReleaseList({
  releases,
  selectedReleaseId,
  onSelectRelease,
}: ReleaseListProps) {
  return (
    <Card className="glass-card animate-fade-up [animation-delay:140ms] [animation-fill-mode:forwards]">
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <CardTitle>Releases</CardTitle>
        <Badge variant="outline" className="font-mono text-[11px]">
          {releases.length} available
        </Badge>
      </CardHeader>
      <CardContent className="space-y-2">
        {releases
          .filter((release): release is Release & { id: string } => Boolean(release.id))
          .map((release) => {
          const isSelected = release.id === selectedReleaseId;
          return (
            <button
              key={release.id}
              type="button"
              className={
                isSelected
                  ? "w-full rounded-md border border-primary/65 bg-primary/10 p-3 text-left"
                  : "w-full rounded-md border border-border/70 bg-card/70 p-3 text-left hover:bg-muted/30"
              }
              onClick={() => onSelectRelease(release.id)}
            >
              <p className="text-sm font-semibold text-primary">{release.sourceVersion}</p>
              <p className="font-mono text-[11px] text-muted-foreground">{release.id}</p>
              <p className="mt-1 text-xs uppercase tracking-[0.2em] text-muted-foreground">
                {releaseSourceTypeLabel(release.sourceType)}
              </p>
              <p className="mt-1 text-xs text-muted-foreground">{release.releaseNotes}</p>
            </button>
          );
        })}
      </CardContent>
    </Card>
  );
}
