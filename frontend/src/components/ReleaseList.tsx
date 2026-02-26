import type { Release } from "../lib/types";
import { Badge } from "./ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "./ui/card";

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
        {releases.map((release) => {
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
              <p className="text-sm font-semibold text-primary">{release.template_spec_version}</p>
              <p className="font-mono text-[11px] text-muted-foreground">{release.id}</p>
              <p className="mt-1 text-xs text-muted-foreground">{release.release_notes}</p>
            </button>
          );
        })}
      </CardContent>
    </Card>
  );
}
