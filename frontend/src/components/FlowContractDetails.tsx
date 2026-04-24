import { Button } from "@/components/ui/button";
import {
  Drawer,
  DrawerClose,
  DrawerContent,
  DrawerDescription,
  DrawerFooter,
  DrawerHeader,
  DrawerTitle,
} from "@/components/ui/drawer";
import { cn } from "@/lib/utils";

export type FlowContractFact = {
  label: string;
  value: string | number | null | undefined;
};

export type FlowContractField = {
  name: string;
  type?: string;
  required?: boolean;
  description: string;
};

export type FlowContractExample = {
  title: string;
  code: string;
  language?: string;
};

export type FlowContract = {
  title: string;
  description: string;
  producer?: string;
  consumer?: string;
  direction?: string;
  facts?: FlowContractFact[];
  fields?: FlowContractField[];
  examples?: FlowContractExample[];
  notes?: string[];
};

function normalizedFacts(facts: FlowContractFact[] | undefined): FlowContractFact[] {
  return (facts ?? [])
    .map((fact) => ({ ...fact, value: String(fact.value ?? "") }))
    .filter((fact) => fact.value.trim() !== "");
}

function codeLanguageLabel(language: string | undefined): string {
  if (!language) {
    return "text";
  }
  return language.toUpperCase();
}

export function FlowContractDrawer({
  contract,
  open,
  onOpenChange,
}: {
  contract: FlowContract | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const facts = normalizedFacts(contract?.facts);
  return (
    <Drawer direction="top" open={open} onOpenChange={onOpenChange}>
      <DrawerContent className="glass-card">
        <DrawerHeader>
          <DrawerTitle>{contract?.title ?? "Flow Contract"}</DrawerTitle>
          <DrawerDescription>
            {contract?.description ?? "Data exchanged between these two steps."}
          </DrawerDescription>
        </DrawerHeader>
        <div className="max-h-[66vh] space-y-4 overflow-y-auto px-4 pb-4">
          {contract?.producer || contract?.consumer || contract?.direction ? (
            <div className="grid gap-2 rounded-lg border border-border/70 bg-background/40 p-3 text-sm md:grid-cols-3">
              <ContractFact label="Producer" value={contract.producer} />
              <ContractFact label="Consumer" value={contract.consumer} />
              <ContractFact label="Direction" value={contract.direction} />
            </div>
          ) : null}

          {facts.length > 0 ? (
            <div className="grid gap-2 md:grid-cols-2">
              {facts.map((fact) => (
                <div key={`${fact.label}-${fact.value}`} className="rounded-lg border border-border/70 bg-background/30 p-3">
                  <p className="text-[11px] uppercase tracking-[0.08em] text-muted-foreground">{fact.label}</p>
                  <p className="mt-1 break-words text-sm text-foreground [overflow-wrap:anywhere]">{fact.value}</p>
                </div>
              ))}
            </div>
          ) : null}

          {contract?.fields?.length ? (
            <div className="rounded-lg border border-border/70 bg-background/30">
              <div className="border-b border-border/70 px-3 py-2">
                <p className="text-sm font-semibold text-foreground">Expected fields</p>
              </div>
              <div className="divide-y divide-border/60">
                {contract.fields.map((field) => (
                  <div key={field.name} className="grid gap-2 p-3 text-sm md:grid-cols-[minmax(0,14rem)_8rem_minmax(0,1fr)]">
                    <div>
                      <p className="break-words font-mono text-xs text-foreground [overflow-wrap:anywhere]">{field.name}</p>
                      <p
                        className={cn(
                          "mt-1 text-[11px] uppercase tracking-[0.08em]",
                          field.required ? "text-primary" : "text-muted-foreground"
                        )}
                      >
                        {field.required ? "Required" : "Optional"}
                      </p>
                    </div>
                    <p className="font-mono text-xs text-muted-foreground">{field.type ?? "string"}</p>
                    <p className="text-sm leading-relaxed text-muted-foreground">{field.description}</p>
                  </div>
                ))}
              </div>
            </div>
          ) : null}

          {contract?.examples?.length ? (
            <div className="space-y-3">
              {contract.examples.map((example) => (
                <div key={example.title} className="overflow-hidden rounded-lg border border-border/70 bg-background/30">
                  <div className="flex items-center justify-between gap-3 border-b border-border/70 px-3 py-2">
                    <p className="text-sm font-semibold text-foreground">{example.title}</p>
                    <span className="rounded-full border border-border/70 px-2 py-0.5 text-[10px] uppercase tracking-[0.08em] text-muted-foreground">
                      {codeLanguageLabel(example.language)}
                    </span>
                  </div>
                  <pre className="max-h-96 overflow-auto p-3 text-xs leading-relaxed text-foreground">
                    <code>{example.code}</code>
                  </pre>
                </div>
              ))}
            </div>
          ) : null}

          {contract?.notes?.length ? (
            <div className="rounded-lg border border-border/70 bg-background/30 p-3">
              <p className="text-sm font-semibold text-foreground">Notes</p>
              <ul className="mt-2 list-disc space-y-1 pl-5 text-sm leading-relaxed text-muted-foreground">
                {contract.notes.map((note) => (
                  <li key={note}>{note}</li>
                ))}
              </ul>
            </div>
          ) : null}
        </div>
        <DrawerFooter className="border-t border-border/70 sm:flex-row sm:justify-end">
          <DrawerClose asChild>
            <Button type="button" variant="outline">
              Close
            </Button>
          </DrawerClose>
        </DrawerFooter>
      </DrawerContent>
    </Drawer>
  );
}

function ContractFact({
  label,
  value,
}: {
  label: string;
  value: string | number | null | undefined;
}) {
  return (
    <div>
      <p className="text-[11px] uppercase tracking-[0.08em] text-muted-foreground">{label}</p>
      <p className="mt-1 break-words text-foreground [overflow-wrap:anywhere]">{value || "Not specified"}</p>
    </div>
  );
}
