import type { ReactNode } from "react";
import { LuArrowDown, LuMoveRight } from "react-icons/lu";

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

function FlowArrow({ className }: { className?: string }) {
  return (
    <div className={cn("flex items-center justify-center text-muted-foreground", className)}>
      <div className="flex h-10 w-10 items-center justify-center rounded-full border border-border/70 bg-background/60">
        <LuMoveRight className="hidden h-5 w-5 xl:block" />
        <LuArrowDown className="h-5 w-5 xl:hidden" />
      </div>
    </div>
  );
}

export default function AdminIntegrationFlowDiagram({
  nodes,
}: {
  nodes: AdminIntegrationFlowNode[];
}) {
  return (
    <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_2.5rem_minmax(0,1fr)_2.5rem_minmax(0,1fr)_2.5rem]">
      {nodes[0] ? <FlowNode node={nodes[0]} className="xl:col-start-1 xl:row-start-1" /> : null}
      {nodes[1] ? <FlowArrow className="xl:col-start-2 xl:row-start-1" /> : null}
      {nodes[1] ? <FlowNode node={nodes[1]} className="xl:col-start-3 xl:row-start-1" /> : null}
      {nodes[2] ? <FlowArrow className="xl:col-start-4 xl:row-start-1" /> : null}
      {nodes[2] ? <FlowNode node={nodes[2]} className="xl:col-start-5 xl:row-start-1" /> : null}
      {nodes[3] ? <FlowArrow className="xl:col-start-6 xl:row-start-1" /> : null}
      {nodes[3] ? <FlowNode node={nodes[3]} className="xl:col-start-1 xl:row-start-2" /> : null}
      {nodes[4] ? <FlowArrow className="xl:col-start-2 xl:row-start-2" /> : null}
      {nodes[4] ? <FlowNode node={nodes[4]} className="xl:col-start-3 xl:row-start-2" /> : null}
      {nodes[5] ? <FlowArrow className="xl:col-start-4 xl:row-start-2" /> : null}
      {nodes[5] ? <FlowNode node={nodes[5]} className="xl:col-start-5 xl:row-start-2" /> : null}
    </div>
  );
}
