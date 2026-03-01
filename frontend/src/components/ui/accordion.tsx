import * as React from "react";

import { cn } from "@/lib/utils";

function Accordion({
  className,
  children,
  ...props
}: React.HTMLAttributes<HTMLDivElement> & {
  type?: "single" | "multiple";
  collapsible?: boolean;
}) {
  return (
    <div className={cn("w-full", className)} {...props}>
      {children}
    </div>
  );
}

function AccordionItem({
  className,
  children,
  value,
  ...props
}: React.DetailsHTMLAttributes<HTMLDetailsElement> & { value?: string }) {
  return (
    <details
      className={cn("border-b border-border/60", className)}
      data-value={value}
      {...props}
    >
      {children}
    </details>
  );
}

function AccordionTrigger({
  className,
  children,
}: React.HTMLAttributes<HTMLElement>) {
  return (
    <summary
      className={cn(
        "flex cursor-pointer list-none items-center justify-between py-2 text-left text-xs font-medium transition-all hover:underline",
        className
      )}
    >
      <span>{children}</span>
      <span className="text-[10px] text-muted-foreground">toggle</span>
    </summary>
  );
}

function AccordionContent({
  className,
  children,
  ...props
}: React.HTMLAttributes<HTMLDivElement>) {
  return (
    <div
      className={cn("overflow-hidden text-xs", className)}
      {...props}
    >
      <div className={cn("pb-2 pt-1", className)}>
        {children}
      </div>
    </div>
  );
}

export { Accordion, AccordionContent, AccordionItem, AccordionTrigger };
