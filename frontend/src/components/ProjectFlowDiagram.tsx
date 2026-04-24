import { useState, type ReactNode } from "react";
import { LuActivity, LuArrowDown, LuBoxes, LuMoveRight, LuWorkflow } from "react-icons/lu";
import { SiGithub } from "react-icons/si";
import { VscAzure, VscAzureDevops } from "react-icons/vsc";

import { FlowContractDrawer, type FlowContract } from "@/components/FlowContractDetails";
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

function releaseManifestExample(): string {
  return JSON.stringify(
    {
      releases: [
        {
          source_ref: "github://owner/repo/managed-app/mainTemplate.json",
          source_version: "2026.04.19.1",
          source_type: "deployment_stack",
          source_version_ref: "https://storage.example/releases/2026.04.19.1/mainTemplate.json",
          parameter_defaults: {
            containerImage: "registry.example/app:2026.04.19.1",
            softwareVersion: "2026.04.19.1",
            dataModelVersion: "12",
          },
        },
      ],
    },
    null,
    2
  );
}

function azureDevOpsReleaseEventExample(): string {
  return JSON.stringify(
    {
      eventType: "ms.vss-pipelines.run-state-changed-event",
      resource: {
        run: {
          id: "1234",
          name: "2026.04.19.1",
          result: "succeeded",
          url: "https://dev.azure.com/org/project/_build/results?buildId=1234",
        },
        pipeline: {
          id: "2",
          name: "release-readiness",
        },
        repository: {
          name: "demo-app-service",
          refName: "refs/heads/main",
        },
      },
    },
    null,
    2
  );
}

function pipelineRunExample(): string {
  return JSON.stringify(
    {
      resources: {
        repositories: {
          self: {
            refName: "refs/heads/main",
          },
        },
      },
      templateParameters: {
        targetTenantId: "<target tenant>",
        targetSubscriptionId: "<target subscription>",
        targetId: "<mappo target id>",
        mappoReleaseVersion: "2026.04.19.1",
        appVersion: "2026.04.19.1",
        dataModelVersion: "12",
      },
    },
    null,
    2
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
  const runtimeDetails = detailList([
    { label: "Check", value: runtimeHealthLabel },
    { label: "Path", value: runtimeHealthPath || "/" },
    { label: "Expected", value: runtimeHealthExpectedStatus || "200" },
    { label: "Timeout", value: `${runtimeHealthTimeoutMs || "5000"} ms` },
  ]);
  const externalReleaseNode: StaticFlowNodeModel = {
    step: "00",
    icon: providerIcon(releaseSourceProvider, "h-5 w-5"),
    eyebrow: "Outside MAPPO",
    title: "Release performed",
    details: detailList([
      {
        label: "Provider",
        value: releaseSourceProvider === "github" ? "GitHub" : "Azure DevOps",
      },
      {
        label: "Result",
        value: releaseSourceProvider === "github" ? "Release manifest is updated" : "Pipeline event is emitted",
      },
    ]),
  };
  const flowNodes: FlowNodeModel[] = [
    {
      section: "release-ingest",
      step: "01",
      icon: providerIcon(releaseSourceProvider, "h-5 w-5"),
      eyebrow: "Release Source",
      title: releaseSourceProvider === "github" ? "GitHub" : "Azure DevOps",
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
      icon: deploymentIcon(deploymentSystem, "h-5 w-5"),
      eyebrow: "Deployment",
      title: deploymentSystem === "azure_devops" ? "Azure DevOps" : "Azure",
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
      title: "HTTP Check",
      details: runtimeDetails,
    },
  ];
  const releaseContract: FlowContract =
    releaseSourceProvider === "github"
      ? {
          title: "Release notification and manifest",
          description:
            "The external provider performs the release, updates the release manifest, and sends MAPPO a webhook. MAPPO uses the webhook as a signal to fetch the manifest.",
          producer: "GitHub repository",
          consumer: "MAPPO Release Source",
          direction: "Inbound webhook, then manifest fetch",
          facts: [
            { label: "Release source", value: releaseSourceName },
            { label: "Manifest path", value: releaseSourceRecord?.manifestPath || "releases/releases.manifest.json" },
            { label: "Repository filter", value: releaseSourceRecord?.repoFilter || "Any repository" },
            { label: "Branch filter", value: releaseSourceRecord?.branchFilter || "Any branch" },
          ],
          fields: [
            { name: "source_ref", required: true, description: "Stable logical identity for the release artifact family." },
            { name: "source_version", required: true, description: "Operator-visible version MAPPO stores in the release catalog." },
            { name: "source_type", required: true, description: "Artifact type. The managed-app Azure SDK demo uses deployment_stack." },
            { name: "source_version_ref", required: true, description: "Immutable URI MAPPO can fetch when deploying this release." },
            { name: "parameter_defaults", required: true, type: "object", description: "Release-level deployment values, such as image and app version." },
          ],
          examples: [{ title: "Minimal releases.manifest.json", language: "json", code: releaseManifestExample() }],
          notes: [
            "Do not include MAPPO project IDs in the publisher manifest.",
            "Do not include publish workflow state, registry secrets, or local artifact bookkeeping.",
            "MAPPO links the release to projects through the selected Release Source.",
          ],
        }
      : {
          title: "Azure DevOps release-ready event",
          description:
            "The release-readiness pipeline succeeds and Azure DevOps sends MAPPO a service-hook event. MAPPO turns that event into a release record.",
          producer: "Azure DevOps pipeline",
          consumer: "MAPPO Release Source",
          direction: "Inbound service hook",
          facts: [
            { label: "Release source", value: releaseSourceName },
            { label: "Pipeline filter", value: releaseSourceRecord?.pipelineIdFilter || "Any pipeline" },
            { label: "Branch filter", value: releaseSourceRecord?.branchFilter || "Any branch" },
          ],
          fields: [
            { name: "run id", required: true, description: "Azure DevOps run identifier. MAPPO requires this to create a release." },
            { name: "run name", required: true, description: "Used as the release version when present; otherwise MAPPO falls back to run id." },
            { name: "run result", required: true, description: "MAPPO only creates a release when the run result is succeeded." },
            { name: "pipeline id", required: false, description: "Matched against the configured pipeline filter when one is set." },
            { name: "repository ref", required: false, description: "Matched against the configured branch filter when one is set." },
          ],
          examples: [{ title: "Service-hook shape MAPPO reads", language: "json", code: azureDevOpsReleaseEventExample() }],
          notes: [
            "The Azure DevOps release-ready path does not require releases.manifest.json.",
            "MAPPO stores external deployment inputs such as artifactVersion and deployedBy from the event.",
          ],
        };
  const ingestContract: FlowContract = {
    title: "Release Source to MAPPO catalog",
    description:
      "MAPPO normalizes the release signal into release records scoped to the projects linked to this Release Source.",
    producer: "MAPPO Release Source",
    consumer: "MAPPO release catalog",
    direction: "Internal ingest",
    facts: [
      { label: "Project", value: projectName },
      { label: "Releases ready", value: projectReleaseCount },
      { label: "Release source type", value: releaseSourceTypeLabel },
    ],
    fields: [
      { name: "project", required: true, description: "Resolved from MAPPO configuration, not from the publisher payload." },
      { name: "sourceRef", required: true, description: "Release family identity used for duplicate detection." },
      { name: "sourceVersion", required: true, description: "Release version shown to operators." },
      { name: "sourceType", required: true, description: "Controls which deployment materializer can consume the release." },
      { name: "parameterDefaults", required: false, type: "object", description: "Release-level values merged into deployment inputs." },
    ],
    notes: [
      "Duplicate detection is project + sourceRef + sourceVersion.",
      "The selected project consumes only release source types compatible with its deployment configuration.",
    ],
  };
  const deploymentContract: FlowContract =
    deploymentSystem === "azure_devops"
      ? {
          title: "MAPPO to Azure DevOps deployment pipeline",
          description:
            "When an operator starts a deployment, MAPPO triggers the configured Azure DevOps pipeline for each selected target.",
          producer: "MAPPO deployment driver",
          consumer: "Azure DevOps pipeline",
          direction: "Outbound API call",
          facts: [
            { label: "Deployment connection", value: deploymentConnectionName || "Not linked" },
            { label: "Azure DevOps project", value: azureDevOpsProjectName || "Not selected" },
            { label: "Repository", value: repositoryName || "Not selected" },
            { label: "Pipeline", value: pipelineName || "Not selected" },
            { label: "Branch", value: branchName || "main" },
          ],
          fields: [
            { name: "targetTenantId", required: true, description: "Tenant for the selected MAPPO target." },
            { name: "targetSubscriptionId", required: true, description: "Subscription for the selected MAPPO target." },
            { name: "targetId", required: true, description: "MAPPO target identity for the pipeline run." },
            { name: "mappoReleaseVersion", required: true, description: "Release version selected by the operator." },
            { name: "appVersion", required: false, description: "Deployment version passed to the pipeline." },
            { name: "dataModelVersion", required: false, description: "Optional release-level data model version." },
          ],
          examples: [{ title: "Pipeline run payload shape", language: "json", code: pipelineRunExample() }],
          notes: [
            "MAPPO does not inspect or manage the Azure service connection inside the pipeline.",
            "Pipeline maintainers own the Azure permissions required by the pipeline steps.",
          ],
        }
      : {
          title: "MAPPO Azure SDK deployment",
          description:
            "When an operator starts a deployment, MAPPO fetches the release template and calls Azure directly for each selected target.",
          producer: "MAPPO deployment driver",
          consumer: "Azure Resource Manager",
          direction: "Outbound Azure SDK/ARM call",
          facts: [
            { label: "Deployment method", value: deploymentMethodLabel },
            { label: "Targets available", value: targetCount },
            { label: "Release source type", value: releaseSourceTypeLabel },
          ],
          fields: [
            { name: "source_version_ref", required: true, description: "Template URI from the release manifest." },
            { name: "target tenant/subscription", required: true, description: "Resolved from the selected registered target." },
            { name: "managed resource group", required: true, description: "Target deployment scope discovered from the registered target." },
            { name: "parameter_defaults", required: true, type: "object", description: "Release-level template parameter values." },
            { name: "target parameters", required: true, type: "object", description: "Target-specific values MAPPO derives from target registration and Azure discovery." },
          ],
          examples: [{ title: "Release artifact contract", language: "json", code: releaseManifestExample() }],
          notes: [
            "The MAPPO Azure SDK path runs once per selected target.",
            "Registry credentials and target-specific Azure metadata come from MAPPO runtime configuration and target registration, not the release manifest.",
          ],
        };
  const targetContract: FlowContract = {
    title: "Deployment target selection",
    description:
      "MAPPO expands the operator's deployment request into target-specific work items using registered targets for this project.",
    producer: "MAPPO deployment request",
    consumer: "Registered target fleet",
    direction: "Internal target resolution",
    facts: [
      { label: "Registered targets", value: targetCount },
      { label: "Example target", value: targetExamples[0] || "No targets registered" },
    ],
    fields: [
      { name: "targetId", required: true, description: "Stable MAPPO target identity." },
      { name: "tenantId", required: true, description: "Azure tenant that owns the target." },
      { name: "subscriptionId", required: true, description: "Azure subscription that owns the target." },
      { name: "executionConfig", required: false, type: "object", description: "Target-specific deployment values, such as resource group or app name." },
      { name: "tags", required: false, type: "object", description: "Operator-facing targeting attributes such as ring, tier, or environment." },
    ],
    notes: [
      "Targets are registered before deployment. The deployment page only selects from targets MAPPO already knows.",
      "A single deployment request can fan out to multiple targets.",
    ],
  };
  const healthContract: FlowContract = {
    title: "Runtime health check",
    description:
      "After rollout, MAPPO can probe each target's configured HTTP endpoint and compare the response against the expected status.",
    producer: "Registered target runtime",
    consumer: "MAPPO runtime health check",
    direction: "HTTP request",
    facts: [
      { label: "Check", value: runtimeHealthLabel },
      { label: "Path", value: runtimeHealthPath || "/" },
      { label: "Expected status", value: runtimeHealthExpectedStatus || "200" },
      { label: "Timeout", value: `${runtimeHealthTimeoutMs || "5000"} ms` },
    ],
    fields: [
      { name: "base URL", required: true, description: "Runtime endpoint resolved from the registered target." },
      { name: "path", required: true, description: "Configured health path appended to the target endpoint." },
      { name: "expected status", required: true, description: "HTTP status MAPPO treats as healthy." },
      { name: "timeout", required: true, description: "Maximum request duration before MAPPO marks the probe failed." },
    ],
  };
  const flowContracts = [releaseContract, ingestContract, deploymentContract, targetContract, healthContract];

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
          <FlowArrow className="xl:col-start-2 xl:row-start-1" contract={flowContracts[0]} onOpenContract={setSelectedContract} />
          {renderFlowNode(flowNodes[0], "xl:col-start-3 xl:row-start-1")}
          <FlowArrow className="xl:col-start-4 xl:row-start-1" contract={flowContracts[1]} onOpenContract={setSelectedContract} />
          {renderFlowNode(flowNodes[1], "xl:col-start-5 xl:row-start-1")}
          <FlowArrow className="xl:col-start-6 xl:row-start-1" contract={flowContracts[2]} onOpenContract={setSelectedContract} />
          {renderFlowNode(flowNodes[2], "xl:col-start-1 xl:row-start-2")}
          <FlowArrow className="xl:col-start-2 xl:row-start-2" contract={flowContracts[3]} onOpenContract={setSelectedContract} />
          {renderFlowNode(flowNodes[3], "xl:col-start-3 xl:row-start-2")}
          <FlowArrow className="xl:col-start-4 xl:row-start-2" contract={flowContracts[4]} onOpenContract={setSelectedContract} />
          {renderFlowNode(flowNodes[4], "xl:col-start-5 xl:row-start-2")}
        </div>
        <p className="mt-3 text-xs text-muted-foreground">
          Select an arrow to inspect the contract between steps.
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
