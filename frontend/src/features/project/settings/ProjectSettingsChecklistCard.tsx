import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";

type ProjectSettingsChecklistCardProps = {
  hasProject: boolean;
  configComplete: boolean;
  targetCount: number;
  projectReleaseCount: number;
};

export default function ProjectSettingsChecklistCard({
  hasProject,
  configComplete,
  targetCount,
  projectReleaseCount,
}: ProjectSettingsChecklistCardProps) {
  const readyToDeploy = configComplete && targetCount > 0 && projectReleaseCount > 0;

  return (
    <Card className="glass-card animate-fade-up [animation-delay:30ms] [animation-fill-mode:forwards]">
      <CardHeader className="pb-2">
        <CardTitle className="text-base">Project Setup Checklist</CardTitle>
      </CardHeader>
      <CardContent className="space-y-3 pt-0 text-sm">
        <div className="grid gap-2 md:grid-cols-2 xl:grid-cols-5">
          <div className="rounded-md border border-border/70 bg-background/40 p-2">
            <p className="text-xs font-medium">Project created</p>
            <Badge className="mt-1" variant={hasProject ? "default" : "secondary"}>
              {hasProject ? "Complete" : "Pending"}
            </Badge>
          </div>
          <div className="rounded-md border border-border/70 bg-background/40 p-2">
            <p className="text-xs font-medium">Configuration complete</p>
            <Badge className="mt-1" variant={configComplete ? "default" : "secondary"}>
              {hasProject ? (configComplete ? "Complete" : "Needs attention") : "Pending"}
            </Badge>
          </div>
          <div className="rounded-md border border-border/70 bg-background/40 p-2">
            <p className="text-xs font-medium">Targets registered</p>
            <Badge className="mt-1" variant={targetCount > 0 ? "default" : "secondary"}>
              {targetCount > 0 ? "Complete" : "Pending"}
            </Badge>
          </div>
          <div className="rounded-md border border-border/70 bg-background/40 p-2">
            <p className="text-xs font-medium">Release available</p>
            <Badge className="mt-1" variant={projectReleaseCount > 0 ? "default" : "secondary"}>
              {projectReleaseCount > 0 ? "Complete" : "Pending"}
            </Badge>
          </div>
          <div className="rounded-md border border-border/70 bg-background/40 p-2">
            <p className="text-xs font-medium">Ready to deploy</p>
            <Badge className="mt-1" variant={readyToDeploy ? "default" : "secondary"}>
              {readyToDeploy ? "Complete" : "Blocked"}
            </Badge>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
