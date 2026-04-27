import { useState, type ReactNode } from "react";
import { LuActivity, LuArrowDown, LuBoxes, LuMoveRight, LuWorkflow } from "react-icons/lu";
import { SiGithub } from "react-icons/si";
import { VscAzure, VscAzureDevops } from "react-icons/vsc";

import { FlowContractDrawer, type FlowContract } from "@/components/FlowContractDetails";
import { buildProjectFlowContracts } from "@/components/ProjectFlowContracts";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { cn } from "@/lib/utils";
import type { ReleaseIngestEndpoint, Target } from "@/lib/types";

export type ProjectFlowSection =
  | "general"
  | "release-ingest"
  | "deployment-driver"
  | "targets"
  | "runtime-health";

type ProjectFlowDiagramProps = {
  projectName: string;
  releaseSourceProvider: "github" | "azure_devops";
  releaseSourceName: string;
  releaseSourceTypeLabel: string;
  releaseSourceRecord: ReleaseIngestEndpoint | null;
  releaseSourceConfigured: boolean;
  deploymentSystem: "azure" | "azure_devops";
  deploymentMethodLabel: string;
  deploymentConnectionName: string;
  azureDevOpsProjectName: string;
  repositoryName: string;
  pipelineName: string;
  branchName: string;
  deploymentConfigured: boolean;
  targetCount: number;
  projectReleaseCount: number;
  targets: Target[];
  runtimeHealthConfigured: boolean;
  runtimeHealthLabel: string;
  runtimeHealthPath: string;
  runtimeHealthExpectedStatus: string;
  runtimeHealthTimeoutMs: string;
  activeSection?: ProjectFlowSection;
  onSectionSelect?: (section: ProjectFlowSection) => void;
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
  value: string | null | undefined;
};

type FlowNodeModel = {
  section: ProjectFlowSection;
  step: string;
  icon: ReactNode;
  eyebrow: string;
  title: string;
  details: FlowDetail[];
};

type StaticFlowNodeModel = {
  step: string;
  icon: ReactNode;
  eyebrow: string;
  title: string;
  details: FlowDetail[];
};

function detailList(values: FlowDetail[]): FlowDetail[] {
  return values
    .map((value) => ({ ...value, value: String(value.value ?? "") }))
    .filter((value) => value.value.trim() !== "");
}

function FlowNode({
  icon,
  eyebrow,
  title,
  details,
  section,
  step,
  className,
  activeSection,
  onSectionSelect,
}: {
  icon: ReactNode;
  eyebrow: string;
  title: string;
  details: FlowDetail[];
  section: ProjectFlowSection;
  step: string;
  className?: string;
  activeSection?: ProjectFlowSection;
  onSectionSelect?: (section: ProjectFlowSection) => void;
}) {
  const isActive = activeSection === section;
  return (
    <button
      type="button"
      className={cn(
        "min-w-0 overflow-hidden rounded-xl border bg-gradient-to-br from-background/70 via-background/40 to-background/20 p-4 text-left shadow-sm transition",
        "flex h-full min-h-[15rem] w-full flex-col justify-between",
        "hover:border-primary/60 hover:bg-primary/5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
        isActive ? "border-primary/80 bg-primary/10" : "border-border/70",
        className
      )}
      aria-pressed={isActive}
      onClick={() => onSectionSelect?.(section)}
    >
      <div className="flex items-start gap-3">
        <div
          className={cn(
            "mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-primary/30 bg-primary/10 text-primary",
            isActive ? "bg-primary text-primary-foreground" : null
          )}
        >
          {icon}
        </div>
        <div className="min-w-0 flex-1 space-y-2">
          <div>
            <div className="mb-1 flex flex-wrap items-center gap-2">
              <span className="rounded-full border border-border/70 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
                {step}
              </span>
              <p className="text-[11px] uppercase tracking-[0.08em] text-muted-foreground">{eyebrow}</p>
            </div>
            <p className="min-w-0 break-words text-sm font-semibold text-foreground [overflow-wrap:anywhere]">{title}</p>
          </div>
          <div className="grid gap-2 text-xs text-muted-foreground 2xl:grid-cols-2">
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
    </button>
  );
}

function StaticFlowNode({
  icon,
  eyebrow,
  title,
  details,
  step,
  className,
}: StaticFlowNodeModel & {
  className?: string;
}) {
  return (
    <div
      className={cn(
        "min-w-0 overflow-hidden rounded-xl border border-dashed border-border/70 bg-background/30 p-4 text-left shadow-sm",
        "flex h-full min-h-[15rem] w-full flex-col justify-between",
        className
      )}
    >
      <div className="flex items-start gap-3">
        <div className="mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border border-muted-foreground/25 bg-muted/20 text-muted-foreground">
          {icon}
        </div>
        <div className="min-w-0 flex-1 space-y-2">
          <div>
            <div className="mb-1 flex flex-wrap items-center gap-2">
              <span className="rounded-full border border-border/70 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
                {step}
              </span>
              <p className="text-[11px] uppercase tracking-[0.08em] text-muted-foreground">{eyebrow}</p>
            </div>
            <p className="min-w-0 break-words text-sm font-semibold text-foreground [overflow-wrap:anywhere]">{title}</p>
          </div>
          <div className="grid gap-2 text-xs text-muted-foreground 2xl:grid-cols-2">
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

function FlowArrow({
  className,
  direction = "responsive",
  contract,
  onOpenContract,
}: {
  className?: string;
  direction?: "responsive" | "down" | "right";
  contract?: FlowContract;
  onOpenContract?: (contract: FlowContract) => void;
}) {
  const arrowIcon = (
    <>
      {direction === "right" ? <LuMoveRight className="h-5 w-5" /> : null}
      {direction === "down" ? <LuArrowDown className="h-5 w-5" /> : null}
      {direction === "responsive" ? (
        <>
          <LuMoveRight className="hidden h-5 w-5 xl:block" />
          <LuArrowDown className="h-5 w-5 xl:hidden" />
        </>
      ) : null}
    </>
  );

  if (contract && onOpenContract) {
    return (
      <div className={cn("flex items-center justify-center text-muted-foreground", className)}>
        <button
          type="button"
          className={cn(
            "flex h-10 w-10 items-center justify-center rounded-full border border-border/70 bg-background/60 transition",
            "hover:border-primary/70 hover:bg-primary/10 hover:text-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
          )}
          aria-label={`View contract: ${contract.title}`}
          title={`View contract: ${contract.title}`}
          onClick={() => onOpenContract(contract)}
        >
          {arrowIcon}
        </button>
      </div>
    );
  }

  return (
    <div className={cn("flex items-center justify-center text-muted-foreground", className)}>
      <div className="flex h-10 w-10 items-center justify-center rounded-full border border-border/70 bg-background/60">
        {arrowIcon}
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
  releaseSourceConfigured,
  deploymentSystem,
  deploymentMethodLabel,
  deploymentConnectionName,
  azureDevOpsProjectName,
  repositoryName,
  pipelineName,
  branchName,
  deploymentConfigured,
  targetCount,
  projectReleaseCount,
  targets,
  runtimeHealthConfigured,
  runtimeHealthLabel,
  runtimeHealthPath,
  runtimeHealthExpectedStatus,
  runtimeHealthTimeoutMs,
  activeSection,
  onSectionSelect,
}: ProjectFlowDiagramProps) {
  const [selectedContract, setSelectedContract] = useState<FlowContract | null>(null);
  const targetExamples = targets.slice(0, 2).map(targetLabel);
  const targetOverflow = targetCount - targetExamples.length;

  const releaseDetails = !releaseSourceConfigured
    ? detailList([
        { label: "Status", value: "Choose a release source" },
        { label: "Expected input", value: releaseSourceProvider === "github" ? "Release manifest" : "Pipeline event" },
      ])
    : releaseSourceProvider === "github"
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

  const deploymentDetails = !deploymentConfigured
    ? detailList([
        { label: "Status", value: "Choose deployment behavior" },
        { label: "Next step", value: "Select how MAPPO should roll releases out" },
      ])
    : deploymentSystem === "azure_devops"
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
  const runtimeDetails = runtimeHealthConfigured
    ? detailList([
        { label: "Check", value: runtimeHealthLabel },
        { label: "Path", value: runtimeHealthPath || "/" },
        { label: "Expected", value: runtimeHealthExpectedStatus || "200" },
        { label: "Timeout", value: `${runtimeHealthTimeoutMs || "5000"} ms` },
      ])
    : detailList([
        { label: "Status", value: "Configure runtime health" },
        { label: "Purpose", value: "MAPPO checks deployed targets after rollout" },
      ]);
  const externalReleaseNode: StaticFlowNodeModel = {
    step: "00",
    icon: releaseSourceConfigured ? providerIcon(releaseSourceProvider, "h-5 w-5") : <LuWorkflow className="h-5 w-5" />,
    eyebrow: "Outside MAPPO",
    title: releaseSourceConfigured ? "Release performed" : "Release system pending",
    details: detailList([
      {
        label: "Provider",
        value: releaseSourceConfigured ? (releaseSourceProvider === "github" ? "GitHub" : "Azure DevOps") : "Not selected",
      },
      {
        label: "Result",
        value: releaseSourceConfigured
          ? (releaseSourceProvider === "github" ? "Release manifest is updated" : "Pipeline event is emitted")
          : "Select a release source first",
      },
    ]),
  };
  const flowNodes: FlowNodeModel[] = [
    {
      section: "release-ingest",
      step: "01",
      icon: releaseSourceConfigured ? providerIcon(releaseSourceProvider, "h-5 w-5") : <LuWorkflow className="h-5 w-5" />,
      eyebrow: "Release Source",
      title: releaseSourceConfigured ? (releaseSourceProvider === "github" ? "GitHub" : "Azure DevOps") : "Not configured",
      details: releaseDetails,
    },
    {
      section: "general",
      step: "02",
      icon: <LuWorkflow className="h-5 w-5" />,
      eyebrow: "MAPPO",
      title: projectName,
      details: detailList([
        {
          label: "Catalog",
          value: `${projectReleaseCount} release${projectReleaseCount === 1 ? "" : "s"} ready`,
        },
        {
          label: "Targets",
          value: `${targetCount} deploy target${targetCount === 1 ? "" : "s"} linked`,
        },
      ]),
    },
    {
      section: "deployment-driver",
      step: "03",
      icon: deploymentConfigured ? deploymentIcon(deploymentSystem, "h-5 w-5") : <LuWorkflow className="h-5 w-5" />,
      eyebrow: "Deployment",
      title: deploymentConfigured ? (deploymentSystem === "azure_devops" ? "Azure DevOps" : "Azure") : "Not configured",
      details: deploymentDetails,
    },
    {
      section: "targets",
      step: "04",
      icon: <LuBoxes className="h-5 w-5" />,
      eyebrow: "Target Fleet",
      title: "Registered Targets",
      details: targetDetails,
    },
    {
      section: "runtime-health",
      step: "05",
      icon: <LuActivity className="h-5 w-5" />,
      eyebrow: "Runtime Health",
      title: runtimeHealthConfigured ? "HTTP Check" : "Not configured",
      details: runtimeDetails,
    },
  ];
  const { releaseContract, deploymentContract, healthContract } = buildProjectFlowContracts({
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
    runtimeHealthLabel,
    runtimeHealthPath,
    runtimeHealthExpectedStatus,
    runtimeHealthTimeoutMs,
  });

  function renderFlowNode(node: FlowNodeModel, className?: string) {
    return (
      <FlowNode
        key={node.section}
        icon={node.icon}
        eyebrow={node.eyebrow}
        title={node.title}
        details={node.details}
        section={node.section}
        step={node.step}
        className={className}
        activeSection={activeSection}
        onSectionSelect={onSectionSelect}
      />
    );
  }

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
        <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_2.5rem_minmax(0,1fr)_2.5rem_minmax(0,1fr)_2.5rem]">
          <StaticFlowNode {...externalReleaseNode} className="xl:col-start-1 xl:row-start-1" />
          <FlowArrow className="xl:col-start-2 xl:row-start-1" contract={releaseSourceConfigured ? releaseContract : undefined} onOpenContract={setSelectedContract} />
          {renderFlowNode(flowNodes[0], "xl:col-start-3 xl:row-start-1")}
          <FlowArrow className="xl:col-start-4 xl:row-start-1" />
          {renderFlowNode(flowNodes[1], "xl:col-start-5 xl:row-start-1")}
          <FlowArrow className="xl:col-start-6 xl:row-start-1" contract={deploymentConfigured ? deploymentContract : undefined} onOpenContract={setSelectedContract} />
          {renderFlowNode(flowNodes[2], "xl:col-start-1 xl:row-start-2")}
          <FlowArrow className="xl:col-start-2 xl:row-start-2" />
          {renderFlowNode(flowNodes[3], "xl:col-start-3 xl:row-start-2")}
          <FlowArrow className="xl:col-start-4 xl:row-start-2" contract={runtimeHealthConfigured ? healthContract : undefined} onOpenContract={setSelectedContract} />
          {renderFlowNode(flowNodes[4], "xl:col-start-5 xl:row-start-2")}
        </div>
        <p className="mt-3 text-xs text-muted-foreground">
          Select a clickable arrow to inspect the request, payload, or probe contract between steps.
        </p>
      </CardContent>
      <FlowContractDrawer
        contract={selectedContract}
        open={selectedContract !== null}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedContract(null);
          }
        }}
      />
    </Card>
  );
}
