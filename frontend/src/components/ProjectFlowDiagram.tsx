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

function detailList(values: string[]): string[] {
  return values.filter((value) => value.trim() !== "");
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
  details: string[];
}) {
  return (
    <div className="min-w-0 flex-1 rounded-xl border border-border/70 bg-background/40 p-4">
      <div className="flex items-start gap-3">
        <div className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-primary/30 bg-primary/10 text-primary">
          {icon}
        </div>
        <div className="min-w-0 space-y-2">
          <div>
            <p className="text-[11px] uppercase tracking-[0.08em] text-muted-foreground">{eyebrow}</p>
            <p className="text-sm font-semibold text-foreground">{title}</p>
          </div>
          <div className="space-y-1">
            {details.map((detail) => (
              <p key={detail} className="text-xs text-muted-foreground">
                {detail}
              </p>
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
          `Source: ${releaseSourceName}`,
          `Type: ${releaseSourceTypeLabel}`,
          releaseSourceRecord?.repoFilter ? `Repository filter: ${releaseSourceRecord.repoFilter}` : "",
          releaseSourceRecord?.branchFilter ? `Branch filter: ${releaseSourceRecord.branchFilter}` : "",
          releaseSourceRecord?.manifestPath ? `Manifest path: ${releaseSourceRecord.manifestPath}` : "",
        ])
      : detailList([
          `Source: ${releaseSourceName}`,
          `Type: ${releaseSourceTypeLabel}`,
          releaseSourceRecord?.branchFilter ? `Branch filter: ${releaseSourceRecord.branchFilter}` : "",
          releaseSourceRecord?.pipelineIdFilter ? `Pipeline filter: ${releaseSourceRecord.pipelineIdFilter}` : "",
        ]);

  const deploymentDetails =
    deploymentSystem === "azure_devops"
      ? detailList([
          `Method: ${deploymentMethodLabel}`,
          deploymentConnectionName ? `Connection: ${deploymentConnectionName}` : "Connection: not linked",
          azureDevOpsProjectName ? `Azure DevOps project: ${azureDevOpsProjectName}` : "Azure DevOps project: not selected",
          repositoryName ? `Repository: ${repositoryName}` : "Repository: not selected",
          pipelineName ? `Pipeline: ${pipelineName}` : "Pipeline: not selected",
          branchName ? `Branch: ${branchName}` : "",
        ])
      : detailList([
          `Method: ${deploymentMethodLabel}`,
          "MAPPO updates each selected target directly in Azure.",
          "Direct Azure rollout uses each target's Deployment Stack.",
        ]);

  const targetDetails = detailList([
    `${targetCount} target${targetCount === 1 ? "" : "s"} registered`,
    `${projectReleaseCount} release${projectReleaseCount === 1 ? "" : "s"} available`,
    ...targetExamples.map((label) => `Example target: ${label}`),
    targetOverflow > 0 ? `+${targetOverflow} more target${targetOverflow === 1 ? "" : "s"}` : "",
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
          <div className="flex items-center justify-center text-muted-foreground">
            <LuMoveRight className="h-5 w-5 xl:block" />
          </div>
          <FlowNode
            icon={<LuWorkflow className="h-5 w-5" />}
            eyebrow="MAPPO"
            title={projectName}
            details={detailList([
              `${projectReleaseCount} release${projectReleaseCount === 1 ? "" : "s"} in catalog`,
              `${targetCount} deploy target${targetCount === 1 ? "" : "s"} linked`,
            ])}
          />
          <div className="flex items-center justify-center text-muted-foreground">
            <LuMoveRight className="h-5 w-5 xl:block" />
          </div>
          <FlowNode
            icon={deploymentIcon(deploymentSystem, "h-5 w-5")}
            eyebrow="Deployment"
            title={deploymentSystem === "azure_devops" ? "Azure DevOps" : "Azure"}
            details={deploymentDetails}
          />
          <div className="flex items-center justify-center text-muted-foreground">
            <LuMoveRight className="h-5 w-5 xl:block" />
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
