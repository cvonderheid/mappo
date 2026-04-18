import type { ReactNode } from "react";
import { LuBoxes, LuMoveRight, LuWorkflow } from "react-icons/lu";
import { SiGithub } from "react-icons/si";
import { VscAzure, VscAzureDevops } from "react-icons/vsc";

import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import type { ReleaseIngestEndpoint, Target } from "@/lib/types";

type ProjectFlowDiagramProps = {
  projectName: string;
  releaseSourceProvider: "github" | "azure_devops";
  releaseSourceName: string;
  releaseSourceTypeLabel: string;
  releaseSourceRecord: ReleaseIngestEndpoint | null;
  deploymentSystem: "azure" | "azure_devops";
  deploymentMethodLabel: string;
  deploymentConnectionName: string;
  azureDevOpsProjectName: string;
  repositoryName: string;
  pipelineName: string;
  branchName: string;
  targetCount: number;
  projectReleaseCount: number;
  targets: Target[];
};

function providerIcon(provider: "github" | "azure_devops", className: string) {
  return provider === "azure_devops" ? (
    <VscAzureDevops className={className} />
  ) : (
    <SiGithub className={className} />
  );
}

function deploymentIcon(system: "azure" | "azure_devops", className: string) {
  return system === "azure_devops" ? (
    <VscAzureDevops className={className} />
  ) : (
    <VscAzure className={className} />
  );
}

function targetLabel(target: Target): string {
  return (
    target.customerName
    || target.tags?.displayName
    || target.id
    || target.subscriptionId
    || "Target"
  );
}

type FlowDetail = {
  label: string;
  value: string;
};

function detailList(values: FlowDetail[]): FlowDetail[] {
  return values.filter((value) => value.value.trim() !== "");
}

function FlowNode({
  icon,
  eyebrow,
  title,
  details,
}: {
  icon: ReactNode;
  eyebrow: string;
  title: string;
  details: FlowDetail[];
}) {
  return (
    <div className="min-w-0 flex-1 overflow-hidden rounded-xl border border-border/70 bg-gradient-to-br from-background/70 via-background/40 to-background/20 p-4 shadow-sm">
      <div className="flex items-start gap-3">
        <div className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-primary/30 bg-primary/10 text-primary">
          {icon}
        </div>
        <div className="min-w-0 flex-1 space-y-2">
          <div>
            <p className="text-[11px] uppercase tracking-[0.08em] text-muted-foreground">{eyebrow}</p>
            <p className="min-w-0 break-words text-sm font-semibold text-foreground [overflow-wrap:anywhere]">{title}</p>
          </div>
          <div className="grid gap-2 text-xs text-muted-foreground">
            {details.map((detail) => (
              <div key={`${detail.label}-${detail.value}`} className="min-w-0 flex flex-col gap-0.5">
                <span className="text-[10px] uppercase tracking-[0.08em] text-muted-foreground/80">
                  {detail.label}
                </span>
                <span className="min-w-0 break-words text-xs leading-relaxed text-foreground/90 [overflow-wrap:anywhere]">
                  {detail.value}
                </span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

export default function ProjectFlowDiagram({
  projectName,
  releaseSourceProvider,
  releaseSourceName,
  releaseSourceTypeLabel,
  releaseSourceRecord,
  deploymentSystem,
  deploymentMethodLabel,
  deploymentConnectionName,
  azureDevOpsProjectName,
  repositoryName,
  pipelineName,
  branchName,
  targetCount,
  projectReleaseCount,
  targets,
}: ProjectFlowDiagramProps) {
  const targetExamples = targets.slice(0, 2).map(targetLabel);
  const targetOverflow = targetCount - targetExamples.length;

  const releaseDetails =
    releaseSourceProvider === "github"
      ? detailList([
          { label: "Source", value: releaseSourceName },
          { label: "Type", value: releaseSourceTypeLabel },
          {
            label: "Repository filter",
            value: releaseSourceRecord?.repoFilter ? releaseSourceRecord.repoFilter : "",
          },
          {
            label: "Branch filter",
            value: releaseSourceRecord?.branchFilter ? releaseSourceRecord.branchFilter : "",
          },
          {
            label: "Manifest path",
            value: releaseSourceRecord?.manifestPath ? releaseSourceRecord.manifestPath : "",
          },
        ])
      : detailList([
          { label: "Source", value: releaseSourceName },
          { label: "Type", value: releaseSourceTypeLabel },
          {
            label: "Branch filter",
            value: releaseSourceRecord?.branchFilter ? releaseSourceRecord.branchFilter : "",
          },
          {
            label: "Pipeline filter",
            value: releaseSourceRecord?.pipelineIdFilter ? releaseSourceRecord.pipelineIdFilter : "",
          },
        ]);

  const deploymentDetails =
    deploymentSystem === "azure_devops"
      ? detailList([
          { label: "Method", value: deploymentMethodLabel },
          {
            label: "Connection",
            value: deploymentConnectionName ? deploymentConnectionName : "Not linked",
          },
          {
            label: "Azure DevOps project",
            value: azureDevOpsProjectName ? azureDevOpsProjectName : "Not selected",
          },
          {
            label: "Repository",
            value: repositoryName ? repositoryName : "Not selected",
          },
          {
            label: "Pipeline",
            value: pipelineName ? pipelineName : "Not selected",
          },
          { label: "Branch", value: branchName ? branchName : "" },
        ])
      : detailList([
          { label: "Method", value: deploymentMethodLabel },
          { label: "Rollout", value: "MAPPO calls Azure directly for each selected target." },
          { label: "Mechanism", value: "MAPPO Azure API uses Azure SDK/ARM against each target's deployment resources." },
        ]);

  const targetDetails = detailList([
    {
      label: "Targets",
      value: `${targetCount} target${targetCount === 1 ? "" : "s"} registered`,
    },
    {
      label: "Releases",
      value: `${projectReleaseCount} release${projectReleaseCount === 1 ? "" : "s"} available`,
    },
    ...targetExamples.map((label, index) => ({
      label: `Example ${index + 1}`,
      value: label,
    })),
    {
      label: "More",
      value: targetOverflow > 0 ? `+${targetOverflow} more target${targetOverflow === 1 ? "" : "s"}` : "",
    },
  ]);

  return (
    <Card className="border-border/70 bg-background/50">
      <CardHeader className="pb-3">
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="space-y-1">
            <CardTitle className="text-sm uppercase tracking-[0.08em]">Project Flow</CardTitle>
            <CardDescription>
              Facts-only view of how releases reach MAPPO, how MAPPO deploys them, and where this project rolls out.
            </CardDescription>
          </div>
          <Badge variant="secondary">{projectName}</Badge>
        </div>
      </CardHeader>
      <CardContent className="pt-0">
        <div className="flex flex-col gap-3 xl:flex-row xl:items-stretch">
          <FlowNode
            icon={providerIcon(releaseSourceProvider, "h-5 w-5")}
            eyebrow="Release Source"
            title={releaseSourceProvider === "github" ? "GitHub" : "Azure DevOps"}
            details={releaseDetails}
          />
          <div className="flex items-center justify-center gap-2 text-muted-foreground">
            <span className="h-px w-8 bg-border/70" />
            <LuMoveRight className="h-5 w-5" />
          </div>
          <FlowNode
            icon={<LuWorkflow className="h-5 w-5" />}
            eyebrow="MAPPO"
            title={projectName}
            details={detailList([
              {
                label: "Catalog",
                value: `${projectReleaseCount} release${projectReleaseCount === 1 ? "" : "s"} ready`,
              },
              {
                label: "Targets",
                value: `${targetCount} deploy target${targetCount === 1 ? "" : "s"} linked`,
              },
            ])}
          />
          <div className="flex items-center justify-center gap-2 text-muted-foreground">
            <span className="h-px w-8 bg-border/70" />
            <LuMoveRight className="h-5 w-5" />
          </div>
          <FlowNode
            icon={deploymentIcon(deploymentSystem, "h-5 w-5")}
            eyebrow="Deployment"
            title={deploymentSystem === "azure_devops" ? "Azure DevOps" : "Azure"}
            details={deploymentDetails}
          />
          <div className="flex items-center justify-center gap-2 text-muted-foreground">
            <span className="h-px w-8 bg-border/70" />
            <LuMoveRight className="h-5 w-5" />
          </div>
          <FlowNode
            icon={<LuBoxes className="h-5 w-5" />}
            eyebrow="Target Fleet"
            title="Registered Targets"
            details={targetDetails}
          />
        </div>
      </CardContent>
    </Card>
  );
}
