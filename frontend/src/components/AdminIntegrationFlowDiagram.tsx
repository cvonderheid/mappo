import { useState, type ReactNode } from "react";
import { LuArrowDown, LuMoveRight } from "react-icons/lu";

import { FlowContractDrawer, type FlowContract } from "@/components/FlowContractDetails";
import { cn } from "@/lib/utils";

export type AdminIntegrationFlowDetail = {
  label: string;
  value: string | number | null | undefined;
};

export type AdminIntegrationFlowNode = {
  step: string;
  icon: ReactNode;
  eyebrow: string;
  title: string;
  details: AdminIntegrationFlowDetail[];
  tone?: "default" | "muted" | "success" | "warning" | "danger" | "primary";
};

function normalizedDetails(values: AdminIntegrationFlowDetail[]): AdminIntegrationFlowDetail[] {
  return values
    .map((value) => ({ ...value, value: String(value.value ?? "") }))
    .filter((value) => value.value.trim() !== "");
}

function nodeToneClass(tone: AdminIntegrationFlowNode["tone"]): string {
  switch (tone) {
    case "danger":
      return "border-destructive/60 bg-destructive/10";
    case "muted":
      return "border-dashed border-border/70 bg-background/30";
    case "primary":
      return "border-primary/70 bg-primary/10";
    case "success":
      return "border-emerald-400/50 bg-emerald-500/10";
    case "warning":
      return "border-amber-400/50 bg-amber-500/10";
    default:
      return "border-border/70 bg-gradient-to-br from-background/70 via-background/40 to-background/20";
  }
}

function iconToneClass(tone: AdminIntegrationFlowNode["tone"]): string {
  switch (tone) {
    case "danger":
      return "border-destructive/40 bg-destructive/15 text-destructive";
    case "muted":
      return "border-muted-foreground/25 bg-muted/20 text-muted-foreground";
    case "success":
      return "border-emerald-400/40 bg-emerald-500/15 text-emerald-200";
    case "warning":
      return "border-amber-400/40 bg-amber-500/15 text-amber-200";
    default:
      return "border-primary/30 bg-primary/10 text-primary";
  }
}

function FlowNode({ node, className }: { node: AdminIntegrationFlowNode; className?: string }) {
  const details = normalizedDetails(node.details);
  return (
    <div
      className={cn(
        "min-w-0 overflow-hidden rounded-xl border p-4 text-left shadow-sm",
        "flex h-full min-h-[13rem] w-full flex-col justify-between",
        nodeToneClass(node.tone),
        className
      )}
    >
      <div className="flex items-start gap-3">
        <div
          className={cn(
            "mt-0.5 flex h-10 w-10 shrink-0 items-center justify-center rounded-lg border",
            iconToneClass(node.tone)
          )}
        >
          {node.icon}
        </div>
        <div className="min-w-0 flex-1 space-y-2">
          <div>
            <div className="mb-1 flex flex-wrap items-center gap-2">
              <span className="rounded-full border border-border/70 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-[0.08em] text-muted-foreground">
                {node.step}
              </span>
              <p className="text-[11px] uppercase tracking-[0.08em] text-muted-foreground">{node.eyebrow}</p>
            </div>
            <p className="min-w-0 break-words text-sm font-semibold text-foreground [overflow-wrap:anywhere]">
              {node.title}
            </p>
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

function buildFlowContract(
  from: AdminIntegrationFlowNode,
  to: AdminIntegrationFlowNode
): FlowContract {
  const sourceFacts = normalizedDetails(from.details).map((detail) => ({
    label: `${from.eyebrow}: ${detail.label}`,
    value: detail.value,
  }));
  const destinationFacts = normalizedDetails(to.details).map((detail) => ({
    label: `${to.eyebrow}: ${detail.label}`,
    value: detail.value,
  }));

  return {
    title: `${from.eyebrow} to ${to.eyebrow}`,
    description:
      "Configured handoff between these two setup steps. Values shown here come from the configured records on this page.",
    producer: from.title,
    consumer: to.title,
    direction: `${from.step} -> ${to.step}`,
    facts: [
      { label: "From", value: `${from.step} ${from.eyebrow}` },
      { label: "To", value: `${to.step} ${to.eyebrow}` },
      ...sourceFacts,
      ...destinationFacts,
    ],
    notes: [
      "This is a configuration relationship view, not an inferred runtime trace.",
      "Project-specific release and deployment payload details are shown in Project -> Config -> Project Flow.",
    ],
  };
}

function FlowArrow({
  className,
  from,
  to,
  onOpenContract,
}: {
  className?: string;
  from?: AdminIntegrationFlowNode;
  to?: AdminIntegrationFlowNode;
  onOpenContract?: (contract: FlowContract) => void;
}) {
  const contract = from && to ? buildFlowContract(from, to) : null;
  const arrowIcon = (
    <>
      <LuMoveRight className="hidden h-5 w-5 xl:block" />
      <LuArrowDown className="h-5 w-5 xl:hidden" />
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

export default function AdminIntegrationFlowDiagram({
  nodes,
}: {
  nodes: AdminIntegrationFlowNode[];
}) {
  const [selectedContract, setSelectedContract] = useState<FlowContract | null>(null);

  return (
    <>
      <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_2.5rem_minmax(0,1fr)_2.5rem_minmax(0,1fr)_2.5rem]">
        {nodes[0] ? <FlowNode node={nodes[0]} className="xl:col-start-1 xl:row-start-1" /> : null}
        {nodes[1] ? (
          <FlowArrow
            className="xl:col-start-2 xl:row-start-1"
            from={nodes[0]}
            to={nodes[1]}
            onOpenContract={setSelectedContract}
          />
        ) : null}
        {nodes[1] ? <FlowNode node={nodes[1]} className="xl:col-start-3 xl:row-start-1" /> : null}
        {nodes[2] ? (
          <FlowArrow
            className="xl:col-start-4 xl:row-start-1"
            from={nodes[1]}
            to={nodes[2]}
            onOpenContract={setSelectedContract}
          />
        ) : null}
        {nodes[2] ? <FlowNode node={nodes[2]} className="xl:col-start-5 xl:row-start-1" /> : null}
        {nodes[3] ? (
          <FlowArrow
            className="xl:col-start-6 xl:row-start-1"
            from={nodes[2]}
            to={nodes[3]}
            onOpenContract={setSelectedContract}
          />
        ) : null}
        {nodes[3] ? <FlowNode node={nodes[3]} className="xl:col-start-1 xl:row-start-2" /> : null}
        {nodes[4] ? (
          <FlowArrow
            className="xl:col-start-2 xl:row-start-2"
            from={nodes[3]}
            to={nodes[4]}
            onOpenContract={setSelectedContract}
          />
        ) : null}
        {nodes[4] ? <FlowNode node={nodes[4]} className="xl:col-start-3 xl:row-start-2" /> : null}
        {nodes[5] ? (
          <FlowArrow
            className="xl:col-start-4 xl:row-start-2"
            from={nodes[4]}
            to={nodes[5]}
            onOpenContract={setSelectedContract}
          />
        ) : null}
        {nodes[5] ? <FlowNode node={nodes[5]} className="xl:col-start-5 xl:row-start-2" /> : null}
      </div>
      <p className="mt-3 text-xs text-muted-foreground">
        Select an arrow to inspect the configured handoff between steps.
      </p>
      <FlowContractDrawer
        contract={selectedContract}
        open={selectedContract !== null}
        onOpenChange={(open) => {
          if (!open) {
            setSelectedContract(null);
          }
        }}
      />
    </>
  );
}
