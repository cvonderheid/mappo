import { FormEvent, useMemo, useState } from "react";
import { toast } from "sonner";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  Drawer,
  DrawerClose,
  DrawerContent,
  DrawerDescription,
  DrawerFooter,
  DrawerHeader,
  DrawerTitle,
  DrawerTrigger,
} from "@/components/ui/drawer";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Textarea } from "@/components/ui/textarea";
import type {
  MarketplaceEventIngestRequest,
  ProjectDefinition,
} from "@/lib/types";

type OnboardMode = "single" | "bulk";
type BulkRowState = "ready" | "invalid" | "applied" | "failed";
type DriverType =
  | "azure_deployment_stack"
  | "azure_template_spec"
  | "pipeline_trigger";

type BulkPreviewRow = {
  rowNumber: number;
  eventId: string;
  targetId: string;
  projectId: string;
  errors: string[];
  request: MarketplaceEventIngestRequest | null;
  state: BulkRowState;
  resultMessage: string;
};

type OnboardingDraft = {
  eventId: string;
  projectId: string;
  targetId: string;
  displayName: string;
  customerName: string;
  tenantId: string;
  subscriptionId: string;
  managedApplicationId: string;
  managedResourceGroupId: string;
  containerAppResourceId: string;
  containerAppName: string;
  targetGroup: string;
  region: string;
  environment: string;
  tier: string;
  executionConfigText: string;
  ingestToken: string;
};

type TargetOnboardingDrawerProps = {
  projects: ProjectDefinition[];
  selectedProjectId: string;
  isSubmitting: boolean;
  open?: boolean;
  onOpenChange?: (open: boolean) => void;
  onIngestMarketplaceEvent: (
    request: MarketplaceEventIngestRequest,
    ingestToken?: string
  ) => Promise<void>;
  onRefreshRegistrations: () => Promise<void>;
};

const UUID_PATTERN =
  /^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;

function nextEventId(prefix = "evt-admin"): string {
  return `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1000)}`;
}

function normalize(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function createDefaultDraft(projectId: string): OnboardingDraft {
  return {
    eventId: nextEventId(),
    projectId: projectId || "",
    targetId: "",
    displayName: "",
    customerName: "",
    tenantId: "",
    subscriptionId: "",
    managedApplicationId: "",
    managedResourceGroupId: "",
    containerAppResourceId: "",
    containerAppName: "",
    targetGroup: "prod",
    region: "eastus",
    environment: "prod",
    tier: "standard",
    executionConfigText: "",
    ingestToken: "",
  };
}

function parseExecutionConfig(rawValue: string): {
  config: Record<string, string>;
  errors: string[];
} {
  const raw = normalize(rawValue);
  if (raw === "") {
    return { config: {}, errors: [] };
  }

  try {
    const parsed = JSON.parse(raw);
    if (parsed === null || typeof parsed !== "object" || Array.isArray(parsed)) {
      return {
        config: {},
        errors: ["Execution config must be a JSON object (key/value pairs)."],
      };
    }
    const config: Record<string, string> = {};
    for (const [key, value] of Object.entries(parsed as Record<string, unknown>)) {
      const normalizedKey = normalize(key);
      if (normalizedKey === "") {
        continue;
      }
      config[normalizedKey] = normalize(value);
    }
    return { config, errors: [] };
  } catch {
    return {
      config: {},
      errors: ["Execution config is not valid JSON."],
    };
  }
}

function resolveDriver(project: ProjectDefinition | null): DriverType {
  const driver = normalize(project?.deploymentDriver);
  if (
    driver === "azure_deployment_stack" ||
    driver === "azure_template_spec" ||
    driver === "pipeline_trigger"
  ) {
    return driver;
  }
  return "azure_deployment_stack";
}

function validateDraft(
  draft: OnboardingDraft,
  project: ProjectDefinition | null
): {
  errors: string[];
  executionConfig: Record<string, string>;
} {
  const errors: string[] = [];
  const executionConfigResult = parseExecutionConfig(draft.executionConfigText);
  const executionConfig = executionConfigResult.config;
  errors.push(...executionConfigResult.errors);

  if (normalize(draft.projectId) === "") {
    errors.push("Project is required.");
  }
  if (normalize(draft.tenantId) === "" || !UUID_PATTERN.test(normalize(draft.tenantId))) {
    errors.push("Tenant ID must be a valid GUID.");
  }
  if (
    normalize(draft.subscriptionId) === "" ||
    !UUID_PATTERN.test(normalize(draft.subscriptionId))
  ) {
    errors.push("Subscription ID must be a valid GUID.");
  }
  if (normalize(draft.displayName) === "" && normalize(draft.targetId) === "") {
    errors.push("Provide either Display Name or Target ID.");
  }

  const driver = resolveDriver(project);
  if (driver === "pipeline_trigger") {
    const resourceGroup =
      normalize(executionConfig.targetResourceGroup) ||
      normalize(executionConfig.resourceGroup);
    const appName =
      normalize(executionConfig.targetAppName) ||
      normalize(executionConfig.appServiceName);
    if (resourceGroup === "") {
      errors.push(
        "Pipeline project requires executionConfig.targetResourceGroup (or resourceGroup)."
      );
    }
    if (appName === "") {
      errors.push(
        "Pipeline project requires executionConfig.targetAppName (or appServiceName)."
      );
    }
  } else {
    const containerAppResourceId = normalize(draft.containerAppResourceId);
    const managedResourceGroupId = normalize(draft.managedResourceGroupId);
    if (containerAppResourceId === "" && managedResourceGroupId === "") {
      errors.push(
        "Managed App projects require Container App Resource ID or Managed Resource Group ID."
      );
    }
  }

  return {
    errors,
    executionConfig,
  };
}

function buildRequest(
  draft: OnboardingDraft,
  executionConfig: Record<string, string>
): MarketplaceEventIngestRequest {
  const request: MarketplaceEventIngestRequest = {
    eventId: normalize(draft.eventId) || nextEventId("evt-admin-auto"),
    eventType: "subscription_purchased",
    tenantId: normalize(draft.tenantId),
    subscriptionId: normalize(draft.subscriptionId),
    projectId: normalize(draft.projectId),
    displayName: normalize(draft.displayName) || undefined,
    targetId: normalize(draft.targetId) || undefined,
    managedApplicationId: normalize(draft.managedApplicationId) || undefined,
    managedResourceGroupId: normalize(draft.managedResourceGroupId) || undefined,
    containerAppResourceId: normalize(draft.containerAppResourceId) || undefined,
    containerAppName: normalize(draft.containerAppName) || undefined,
    customerName: normalize(draft.customerName) || undefined,
    targetGroup: normalize(draft.targetGroup) || "prod",
    region: normalize(draft.region) || "eastus",
    environment: normalize(draft.environment) || "prod",
    tier: normalize(draft.tier) || "standard",
    healthStatus: "registered",
    lastDeployedRelease: "unknown",
    tags: {},
    metadata: {
      source: "admin-onboarding-wizard",
      executionConfig: Object.keys(executionConfig).length > 0 ? executionConfig : undefined,
    },
  };
  return request;
}

function draftFromUnknown(
  value: unknown,
  defaultProjectId: string,
  rowNumber: number
): OnboardingDraft {
  const row = value && typeof value === "object" && !Array.isArray(value)
    ? (value as Record<string, unknown>)
    : {};
  const metadata =
    row.metadata && typeof row.metadata === "object" && !Array.isArray(row.metadata)
      ? (row.metadata as Record<string, unknown>)
      : {};
  const executionConfig =
    metadata.executionConfig &&
    typeof metadata.executionConfig === "object" &&
    !Array.isArray(metadata.executionConfig)
      ? (metadata.executionConfig as Record<string, unknown>)
      : {};

  return {
    eventId: normalize(row.eventId) || nextEventId(`evt-admin-bulk-${rowNumber}`),
    projectId: normalize(row.projectId) || defaultProjectId,
    targetId: normalize(row.targetId),
    displayName: normalize(row.displayName),
    customerName: normalize(row.customerName),
    tenantId: normalize(row.tenantId),
    subscriptionId: normalize(row.subscriptionId),
    managedApplicationId: normalize(row.managedApplicationId),
    managedResourceGroupId: normalize(row.managedResourceGroupId),
    containerAppResourceId: normalize(row.containerAppResourceId),
    containerAppName: normalize(row.containerAppName),
    targetGroup: normalize(row.targetGroup) || "prod",
    region: normalize(row.region) || "eastus",
    environment: normalize(row.environment) || "prod",
    tier: normalize(row.tier) || "standard",
    executionConfigText:
      Object.keys(executionConfig).length === 0 ? "" : JSON.stringify(executionConfig, null, 2),
    ingestToken: "",
  };
}

export default function TargetOnboardingDrawer({
  projects,
  selectedProjectId,
  isSubmitting,
  open,
  onOpenChange,
  onIngestMarketplaceEvent,
  onRefreshRegistrations,
}: TargetOnboardingDrawerProps) {
  const [internalOpen, setInternalOpen] = useState(false);
  const [mode, setMode] = useState<OnboardMode>("single");
  const [draft, setDraft] = useState<OnboardingDraft>(() =>
    createDefaultDraft(selectedProjectId)
  );
  const [bulkJson, setBulkJson] = useState<string>("[]");
  const [bulkRows, setBulkRows] = useState<BulkPreviewRow[]>([]);
  const [bulkParseError, setBulkParseError] = useState<string>("");
  const [bulkCommitting, setBulkCommitting] = useState<boolean>(false);

  const projectById = useMemo(
    () => new Map(projects.map((project) => [project.id ?? "", project])),
    [projects]
  );
  const selectedProject = projectById.get(normalize(draft.projectId)) ?? null;
  const selectedDriver = resolveDriver(selectedProject);

  const singleValidation = useMemo(
    () => validateDraft(draft, selectedProject),
    [draft, selectedProject]
  );
  const singleErrors = singleValidation.errors;
  const singleCanSubmit = singleErrors.length === 0 && !isSubmitting;

  const bulkReadyCount = bulkRows.filter((row) => row.state === "ready").length;
  const bulkInvalidCount = bulkRows.filter((row) => row.state === "invalid").length;
  const bulkAppliedCount = bulkRows.filter((row) => row.state === "applied").length;
  const bulkFailedCount = bulkRows.filter((row) => row.state === "failed").length;
  const isOpen = open ?? internalOpen;

  function setDrawerOpen(nextOpen: boolean): void {
    if (onOpenChange) {
      onOpenChange(nextOpen);
      return;
    }
    setInternalOpen(nextOpen);
  }

  function resetSingleDraft(): void {
    setDraft(createDefaultDraft(selectedProjectId));
  }

  function handleOpenChange(nextOpen: boolean): void {
    setDrawerOpen(nextOpen);
    if (nextOpen) {
      setBulkParseError("");
      setBulkRows([]);
      return;
    }
    resetSingleDraft();
    setMode("single");
    setBulkJson("[]");
    setBulkRows([]);
    setBulkParseError("");
    setBulkCommitting(false);
  }

  function updateDraft<K extends keyof OnboardingDraft>(key: K, value: OnboardingDraft[K]): void {
    setDraft((current) => ({ ...current, [key]: value }));
  }

  async function submitSingle(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    if (!singleCanSubmit) {
      return;
    }
    const request = buildRequest(draft, singleValidation.executionConfig);
    await onIngestMarketplaceEvent(request, normalize(draft.ingestToken) || undefined);
    toast.success(`Registered target ${request.targetId ?? request.displayName ?? request.eventId}.`);
    resetSingleDraft();
    await onRefreshRegistrations();
    setDrawerOpen(false);
  }

  function previewBulk(): void {
    setBulkParseError("");
    let parsed: unknown;
    try {
      parsed = JSON.parse(bulkJson);
    } catch {
      setBulkRows([]);
      setBulkParseError("Bulk payload is not valid JSON.");
      return;
    }
    if (!Array.isArray(parsed)) {
      setBulkRows([]);
      setBulkParseError("Bulk payload must be a JSON array.");
      return;
    }

    const rows: BulkPreviewRow[] = parsed.map((entry, index) => {
      const entryDraft = draftFromUnknown(entry, selectedProjectId, index + 1);
      const project = projectById.get(normalize(entryDraft.projectId)) ?? null;
      const validation = validateDraft(entryDraft, project);
      const request =
        validation.errors.length === 0 ? buildRequest(entryDraft, validation.executionConfig) : null;
      return {
        rowNumber: index + 1,
        eventId: entryDraft.eventId,
        targetId: normalize(entryDraft.targetId) || normalize(entryDraft.displayName) || "(auto)",
        projectId: normalize(entryDraft.projectId) || "(missing)",
        errors: validation.errors,
        request,
        state: validation.errors.length === 0 ? "ready" : "invalid",
        resultMessage:
          validation.errors.length === 0
            ? "Ready to import."
            : validation.errors.join(" "),
      };
    });
    setBulkRows(rows);
    if (rows.length === 0) {
      setBulkParseError("Bulk payload did not include any rows.");
    }
  }

  async function commitBulk(): Promise<void> {
    if (bulkCommitting) {
      return;
    }
    const readyRows = bulkRows.filter(
      (row): row is BulkPreviewRow & { request: MarketplaceEventIngestRequest } =>
        row.state === "ready" && row.request !== null
    );
    if (readyRows.length === 0) {
      toast.error("No valid bulk rows to import.");
      return;
    }

    setBulkCommitting(true);
    let applied = 0;
    let failed = 0;
    try {
      for (const row of readyRows) {
        try {
          await onIngestMarketplaceEvent(row.request, normalize(draft.ingestToken) || undefined);
          applied += 1;
          setBulkRows((current) =>
            current.map((candidate) =>
              candidate.rowNumber === row.rowNumber
                ? { ...candidate, state: "applied", resultMessage: "Applied." }
                : candidate
            )
          );
        } catch (error) {
          failed += 1;
          setBulkRows((current) =>
            current.map((candidate) =>
              candidate.rowNumber === row.rowNumber
                ? {
                    ...candidate,
                    state: "failed",
                    resultMessage: (error as Error).message,
                  }
                : candidate
            )
          );
        }
      }
      await onRefreshRegistrations();
      if (failed > 0) {
        toast.warning(`Bulk import complete: applied ${applied}, failed ${failed}.`);
      } else {
        toast.success(`Bulk import complete: applied ${applied} row(s).`);
      }
    } finally {
      setBulkCommitting(false);
    }
  }

  return (
    <Drawer direction="top" open={isOpen} onOpenChange={handleOpenChange}>
      <DrawerTrigger asChild>
        <Button type="button" variant="outline" data-testid="open-target-onboarding-drawer">
          Onboard Targets
        </Button>
      </DrawerTrigger>
      <DrawerContent className="glass-card">
        <DrawerHeader>
          <DrawerTitle>Target Onboarding</DrawerTitle>
          <DrawerDescription>
            Register targets directly in MAPPO using project-aware contract validation.
          </DrawerDescription>
        </DrawerHeader>
        <div className="max-h-[74vh] overflow-y-auto px-4 pb-2">
          <Tabs value={mode} onValueChange={(value) => setMode(value as OnboardMode)}>
            <TabsList>
              <TabsTrigger value="single">Single Target</TabsTrigger>
              <TabsTrigger value="bulk">Bulk Import</TabsTrigger>
            </TabsList>

            <TabsContent value="single" className="space-y-3 pt-2">
              <form
                id="target-onboarding-single-form"
                className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3"
                onSubmit={(event) => {
                  void submitSingle(event);
                }}
              >
                <div className="space-y-1">
                  <Label htmlFor="onboard-project">Project</Label>
                  <Select
                    value={draft.projectId}
                    onValueChange={(value) => updateDraft("projectId", value)}
                  >
                    <SelectTrigger id="onboard-project">
                      <SelectValue placeholder="Select project" />
                    </SelectTrigger>
                    <SelectContent>
                      {projects.map((project) => (
                        <SelectItem key={project.id ?? ""} value={project.id ?? ""}>
                          {project.name ?? project.id}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </div>
                <div className="space-y-1">
                  <Label htmlFor="onboard-event-id">Event ID</Label>
                  <Input
                    id="onboard-event-id"
                    value={draft.eventId}
                    onChange={(event) => updateDraft("eventId", event.target.value)}
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="onboard-target-id">Target ID</Label>
                  <Input
                    id="onboard-target-id"
                    value={draft.targetId}
                    onChange={(event) => updateDraft("targetId", event.target.value)}
                    placeholder="Optional (auto-generated if blank)"
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="onboard-display-name">Display Name</Label>
                  <Input
                    id="onboard-display-name"
                    value={draft.displayName}
                    onChange={(event) => updateDraft("displayName", event.target.value)}
                    placeholder="Contoso - Prod"
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="onboard-customer-name">Customer Name</Label>
                  <Input
                    id="onboard-customer-name"
                    value={draft.customerName}
                    onChange={(event) => updateDraft("customerName", event.target.value)}
                    placeholder="Contoso"
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="onboard-tenant-id">Tenant ID</Label>
                  <Input
                    id="onboard-tenant-id"
                    value={draft.tenantId}
                    onChange={(event) => updateDraft("tenantId", event.target.value)}
                    placeholder="GUID"
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="onboard-subscription-id">Subscription ID</Label>
                  <Input
                    id="onboard-subscription-id"
                    value={draft.subscriptionId}
                    onChange={(event) => updateDraft("subscriptionId", event.target.value)}
                    placeholder="GUID"
                  />
                </div>
                <div className="space-y-1 lg:col-span-2">
                  <Label htmlFor="onboard-container-app-id">Container App Resource ID</Label>
                  <Input
                    id="onboard-container-app-id"
                    value={draft.containerAppResourceId}
                    onChange={(event) =>
                      updateDraft("containerAppResourceId", event.target.value)
                    }
                    placeholder="/subscriptions/.../providers/Microsoft.App/containerApps/..."
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="onboard-container-app-name">Container App Name</Label>
                  <Input
                    id="onboard-container-app-name"
                    value={draft.containerAppName}
                    onChange={(event) => updateDraft("containerAppName", event.target.value)}
                  />
                </div>
                <div className="space-y-1 lg:col-span-2">
                  <Label htmlFor="onboard-managed-rg-id">Managed Resource Group ID</Label>
                  <Input
                    id="onboard-managed-rg-id"
                    value={draft.managedResourceGroupId}
                    onChange={(event) =>
                      updateDraft("managedResourceGroupId", event.target.value)
                    }
                    placeholder="/subscriptions/.../resourceGroups/..."
                  />
                </div>
                <div className="space-y-1 lg:col-span-2">
                  <Label htmlFor="onboard-managed-app-id">Managed Application ID</Label>
                  <Input
                    id="onboard-managed-app-id"
                    value={draft.managedApplicationId}
                    onChange={(event) =>
                      updateDraft("managedApplicationId", event.target.value)
                    }
                    placeholder="/subscriptions/.../providers/Microsoft.Solutions/applications/..."
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="onboard-target-group">Target Group</Label>
                  <Input
                    id="onboard-target-group"
                    value={draft.targetGroup}
                    onChange={(event) => updateDraft("targetGroup", event.target.value)}
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="onboard-region">Region</Label>
                  <Input
                    id="onboard-region"
                    value={draft.region}
                    onChange={(event) => updateDraft("region", event.target.value)}
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="onboard-environment">Environment</Label>
                  <Input
                    id="onboard-environment"
                    value={draft.environment}
                    onChange={(event) => updateDraft("environment", event.target.value)}
                  />
                </div>
                <div className="space-y-1">
                  <Label htmlFor="onboard-tier">Tier</Label>
                  <Input
                    id="onboard-tier"
                    value={draft.tier}
                    onChange={(event) => updateDraft("tier", event.target.value)}
                  />
                </div>
                <div className="space-y-1 lg:col-span-3">
                  <Label htmlFor="onboard-execution-config">
                    Execution Config (JSON object)
                  </Label>
                  <Textarea
                    id="onboard-execution-config"
                    value={draft.executionConfigText}
                    onChange={(event) =>
                      updateDraft("executionConfigText", event.target.value)
                    }
                    placeholder='{"targetResourceGroup":"rg-demo-appservice-target-01","targetAppName":"appsvc-demo-target-01"}'
                    rows={5}
                  />
                  <p className="text-xs text-muted-foreground">
                    Driver: <span className="font-mono">{selectedDriver}</span>
                  </p>
                </div>
                <div className="space-y-1 lg:col-span-3">
                  <Label htmlFor="onboard-ingest-token">Ingest Token (optional)</Label>
                  <Input
                    id="onboard-ingest-token"
                    value={draft.ingestToken}
                    onChange={(event) => updateDraft("ingestToken", event.target.value)}
                  />
                </div>
              </form>

              {singleErrors.length > 0 ? (
                <div className="rounded-md border border-destructive/50 bg-destructive/10 p-2 text-sm">
                  <p className="mb-1 font-semibold">Validation issues</p>
                  <ul className="list-disc space-y-1 pl-4">
                    {singleErrors.map((error) => (
                      <li key={error}>{error}</li>
                    ))}
                  </ul>
                </div>
              ) : (
                <div className="rounded-md border border-primary/40 bg-primary/10 p-2 text-sm">
                  Target payload is valid for this project contract.
                </div>
              )}
            </TabsContent>

            <TabsContent value="bulk" className="space-y-3 pt-2">
              <div className="space-y-1">
                <Label htmlFor="bulk-onboard-json">Bulk Payload (JSON array)</Label>
                <Textarea
                  id="bulk-onboard-json"
                  value={bulkJson}
                  onChange={(event) => setBulkJson(event.target.value)}
                  rows={12}
                  placeholder='[{"projectId":"azure-appservice-ado-pipeline","displayName":"Demo AppService Target","tenantId":"...","subscriptionId":"...","metadata":{"executionConfig":{"targetResourceGroup":"rg-demo","targetAppName":"appsvc-demo"}}}]'
                />
              </div>
              {bulkParseError ? (
                <div className="rounded-md border border-destructive/50 bg-destructive/10 p-2 text-sm">
                  {bulkParseError}
                </div>
              ) : null}
              <div className="flex flex-wrap items-center gap-2 text-xs">
                <Badge variant="outline">ready {bulkReadyCount}</Badge>
                <Badge variant="outline">invalid {bulkInvalidCount}</Badge>
                <Badge variant="outline">applied {bulkAppliedCount}</Badge>
                <Badge variant="outline">failed {bulkFailedCount}</Badge>
              </div>
              {bulkRows.length > 0 ? (
                <div className="max-h-[300px] overflow-y-auto rounded-md border border-border/70">
                  <table className="w-full text-left text-xs">
                    <thead className="bg-background/80">
                      <tr>
                        <th className="px-2 py-1">#</th>
                        <th className="px-2 py-1">Project</th>
                        <th className="px-2 py-1">Target</th>
                        <th className="px-2 py-1">State</th>
                        <th className="px-2 py-1">Message</th>
                      </tr>
                    </thead>
                    <tbody>
                      {bulkRows.map((row) => (
                        <tr key={`${row.rowNumber}-${row.eventId}`} className="border-t border-border/60">
                          <td className="px-2 py-1">{row.rowNumber}</td>
                          <td className="px-2 py-1">{row.projectId}</td>
                          <td className="px-2 py-1">{row.targetId}</td>
                          <td className="px-2 py-1">
                            <Badge variant={row.state === "failed" || row.state === "invalid" ? "destructive" : row.state === "applied" ? "default" : "secondary"}>
                              {row.state}
                            </Badge>
                          </td>
                          <td className="px-2 py-1">{row.resultMessage}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              ) : null}
            </TabsContent>
          </Tabs>
        </div>
        <DrawerFooter className="border-t border-border/70">
          <DrawerClose asChild>
            <Button type="button" variant="outline">
              Close
            </Button>
          </DrawerClose>

          {mode === "single" ? (
            <Button
              type="submit"
              form="target-onboarding-single-form"
              disabled={!singleCanSubmit}
            >
              {isSubmitting ? "Registering..." : "Register Target"}
            </Button>
          ) : (
            <div className="flex w-full justify-end gap-2">
              <Button type="button" variant="outline" onClick={previewBulk}>
                Preview Bulk Import
              </Button>
              <Button
                type="button"
                disabled={bulkCommitting || bulkReadyCount === 0}
                onClick={() => {
                  void commitBulk();
                }}
              >
                {bulkCommitting ? "Importing..." : "Commit Valid Rows"}
              </Button>
            </div>
          )}
        </DrawerFooter>
      </DrawerContent>
    </Drawer>
  );
}
