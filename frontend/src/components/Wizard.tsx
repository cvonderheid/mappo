import type { ReactNode } from "react";

import { Badge } from "@/components/ui/badge";
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Progress } from "@/components/ui/progress";
import { cn } from "@/lib/utils";

export type WizardStepDefinition = {
  key: string;
  label: string;
  description?: string;
};

type WizardShellProps = {
  title: string;
  description: string;
  steps: WizardStepDefinition[];
  activeStep: string;
  children: ReactNode;
  actions?: ReactNode;
  className?: string;
};

export function WizardShell({
  title,
  description,
  steps,
  activeStep,
  children,
  actions,
  className,
}: WizardShellProps) {
  const activeStepIndex = Math.max(0, steps.findIndex((step) => step.key === activeStep));
  const activeStepDefinition = steps[activeStepIndex] ?? steps[0];
  const progressValue = steps.length <= 1 ? 100 : ((activeStepIndex + 1) / steps.length) * 100;

  return (
    <Card className={cn("border-border/80 bg-background/35", className)}>
      <CardHeader className="gap-4">
        <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
          <div className="space-y-2">
            <CardTitle>{title}</CardTitle>
            <CardDescription>{description}</CardDescription>
          </div>
          <CardAction className="sm:ml-0 lg:ml-auto">
            <Badge variant="outline">
              Step {activeStepIndex + 1} of {steps.length}
            </Badge>
          </CardAction>
        </div>
        <div className="space-y-3">
          <Progress value={progressValue} />
          <div className="grid gap-2 md:grid-cols-5">
            {steps.map((step, index) => {
              const isActive = step.key === activeStep;
              const isComplete = index < activeStepIndex;
              return (
                <div
                  key={step.key}
                  className={cn(
                    "rounded-md border px-3 py-2 text-left",
                    isActive
                      ? "border-primary/70 bg-primary/10 text-foreground"
                      : "border-border/60 bg-background/40 text-muted-foreground",
                    isComplete ? "text-foreground" : null
                  )}
                  aria-current={isActive ? "step" : undefined}
                >
                  <div className="flex items-center gap-2">
                    <span
                      className={cn(
                        "flex h-5 w-5 shrink-0 items-center justify-center rounded-full border text-[10px] font-semibold",
                        isActive || isComplete
                          ? "border-primary/80 bg-primary text-primary-foreground"
                          : "border-border/80 text-muted-foreground"
                      )}
                    >
                      {index + 1}
                    </span>
                    <span className="text-xs font-semibold">{step.label}</span>
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      </CardHeader>
      <CardContent className="space-y-4">
        {activeStepDefinition?.description ? (
          <div className="rounded-md border border-border/60 bg-background/40 px-3 py-2 text-sm text-muted-foreground">
            {activeStepDefinition.description}
          </div>
        ) : null}
        {children}
        {actions ? (
          <div className="flex flex-wrap justify-end gap-2 border-t border-border/60 pt-4">
            {actions}
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}

type WizardDecisionCardProps = {
  title: string;
  description: string;
  selected: boolean;
  onSelect: () => void;
  badge?: ReactNode;
  children?: ReactNode;
  disabled?: boolean;
  className?: string;
};

export function WizardDecisionCard({
  title,
  description,
  selected,
  onSelect,
  badge,
  children,
  disabled,
  className,
}: WizardDecisionCardProps) {
  return (
    <button
      type="button"
      className={cn(
        "flex min-h-full w-full flex-col rounded-lg border bg-background/35 p-4 text-left transition",
        "hover:border-primary/60 hover:bg-primary/5 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring",
        selected ? "border-primary/80 bg-primary/10 shadow-sm" : "border-border/70",
        disabled ? "cursor-not-allowed opacity-60" : "cursor-pointer",
        className
      )}
      aria-pressed={selected}
      onClick={onSelect}
      disabled={disabled}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="space-y-1">
          <p className="text-sm font-semibold text-foreground">{title}</p>
          <p className="text-xs leading-relaxed text-muted-foreground">{description}</p>
        </div>
        {badge ? <div className="shrink-0">{badge}</div> : null}
      </div>
      {children ? <div className="mt-3 text-xs leading-relaxed text-muted-foreground">{children}</div> : null}
    </button>
  );
}

type WizardExplainerProps = {
  title: string;
  children: ReactNode;
  className?: string;
};

export function WizardExplainer({ title, children, className }: WizardExplainerProps) {
  return (
    <div className={cn("rounded-lg border border-border/70 bg-background/40 p-4", className)}>
      <p className="text-sm font-semibold text-foreground">{title}</p>
      <div className="mt-2 text-sm leading-relaxed text-muted-foreground">{children}</div>
    </div>
  );
}

type WizardReviewRowProps = {
  label: string;
  value: ReactNode;
};

export function WizardReviewRow({ label, value }: WizardReviewRowProps) {
  return (
    <div className="flex flex-col gap-1 rounded-md border border-border/60 bg-background/35 p-3 sm:flex-row sm:items-center sm:justify-between">
      <dt className="text-xs uppercase tracking-[0.08em] text-muted-foreground">{label}</dt>
      <dd className="text-sm font-medium text-foreground">{value}</dd>
    </div>
  );
}
