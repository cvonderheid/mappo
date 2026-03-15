import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import FieldHelpTooltip from "@/components/FieldHelpTooltip";
import {
  Drawer,
  DrawerClose,
  DrawerContent,
  DrawerDescription,
  DrawerFooter,
  DrawerHeader,
  DrawerTitle,
} from "@/components/ui/drawer";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { apiBaseUrl } from "@/lib/api/client";
import {
  createReleaseIngestEndpoint,
  deleteReleaseIngestEndpoint,
  listReleaseIngestEndpoints,
  patchReleaseIngestEndpoint,
} from "@/lib/api";
import type {
  ReleaseIngestEndpoint,
  ReleaseIngestEndpointCreateRequest,
  ReleaseIngestEndpointPatchRequest,
} from "@/lib/types";

type ReleaseIngestProvider = "github" | "azure_devops";

type ReleaseIngestConfigPageProps = {
  selectedProjectId: string;
};

type EndpointDraft = {
  id: string;
  name: string;
  provider: ReleaseIngestProvider;
  enabled: boolean;
  secretRef: string;
  repoFilter: string;
  branchFilter: string;
  pipelineIdFilter: string;
  manifestPath: string;
  sourceConfigText: string;
};

function emptyDraft(): EndpointDraft {
  return {
    id: "",
    name: "",
    provider: "github",
    enabled: true,
    secretRef: "",
    repoFilter: "",
    branchFilter: "",
    pipelineIdFilter: "",
    manifestPath: "",
    sourceConfigText: "{}",
  };
}

function parseSourceConfig(text: string): Record<string, unknown> {
  const normalized = text.trim();
  if (normalized === "") {
    return {};
  }
  const parsed = JSON.parse(normalized) as unknown;
  if (parsed !== null && typeof parsed === "object" && !Array.isArray(parsed)) {
    return parsed as Record<string, unknown>;
  }
  throw new Error("Source config must be a JSON object.");
}

function formatTimestamp(value: string | null | undefined): string {
  if (!value) {
    return "";
  }
  const timestamp = new Date(value);
  if (Number.isNaN(timestamp.getTime())) {
    return "";
  }
  return timestamp.toLocaleString();
}

function webhookPathFor(endpointId: string, provider: ReleaseIngestProvider): string {
  const encodedId = encodeURIComponent(endpointId);
  if (provider === "azure_devops") {
    return `/api/v1/release-ingest/endpoints/${encodedId}/webhooks/ado`;
  }
  return `/api/v1/release-ingest/endpoints/${encodedId}/webhooks/github`;
}

function toDraft(endpoint: ReleaseIngestEndpoint): EndpointDraft {
  const provider = (endpoint.provider ?? "github") as ReleaseIngestProvider;
  return {
    id: endpoint.id ?? "",
    name: endpoint.name ?? "",
    provider,
    enabled: endpoint.enabled ?? true,
    secretRef: endpoint.secretRef ?? "",
    repoFilter: endpoint.repoFilter ?? "",
    branchFilter: endpoint.branchFilter ?? "",
    pipelineIdFilter: endpoint.pipelineIdFilter ?? "",
    manifestPath: endpoint.manifestPath ?? "",
    sourceConfigText: JSON.stringify(endpoint.sourceConfig ?? {}, null, 2),
  };
}

export default function ReleaseIngestConfigPage({
  selectedProjectId,
}: ReleaseIngestConfigPageProps) {
  const [endpoints, setEndpoints] = useState<ReleaseIngestEndpoint[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string>("");
  const [drawerOpen, setDrawerOpen] = useState<boolean>(false);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [editingId, setEditingId] = useState<string>("");
  const [draft, setDraft] = useState<EndpointDraft>(emptyDraft);

  const loadEndpoints = useCallback(async (refresh = false) => {
    if (refresh) {
      setIsRefreshing(true);
    } else {
      setIsLoading(true);
    }
    try {
      const result = await listReleaseIngestEndpoints();
      setEndpoints(result);
      setErrorMessage("");
    } catch (error) {
      setErrorMessage((error as Error).message);
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void loadEndpoints();
  }, [loadEndpoints]);

  const isAzureDevOpsProvider = draft.provider === "azure_devops";
  const secretRefExample = isAzureDevOpsProvider
    ? "literal:ado-demo-webhook-20260315"
    : "literal:github-demo-webhook-20260315";

  const sortedEndpoints = useMemo(() => {
    return [...endpoints].sort((a, b) => {
      const aName = (a.name ?? "").toLowerCase();
      const bName = (b.name ?? "").toLowerCase();
      if (aName !== bName) {
        return aName.localeCompare(bName);
      }
      return (a.id ?? "").localeCompare(b.id ?? "");
    });
  }, [endpoints]);

  function openCreateDrawer(): void {
    setEditingId("");
    setDraft(emptyDraft());
    setDrawerOpen(true);
  }

  function openEditDrawer(endpoint: ReleaseIngestEndpoint): void {
    setEditingId(endpoint.id ?? "");
    setDraft(toDraft(endpoint));
    setDrawerOpen(true);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    if (draft.id.trim() === "" || draft.name.trim() === "") {
      toast.error("Endpoint id and name are required.");
      return;
    }
    setIsSubmitting(true);
    try {
      const sourceConfig = parseSourceConfig(draft.sourceConfigText);
      if (editingId) {
        const patchRequest: ReleaseIngestEndpointPatchRequest = {
          name: draft.name.trim(),
          provider: draft.provider,
          enabled: draft.enabled,
          secretRef: draft.secretRef.trim() || undefined,
          repoFilter: draft.repoFilter.trim() || undefined,
          branchFilter: draft.branchFilter.trim() || undefined,
          pipelineIdFilter: draft.pipelineIdFilter.trim() || undefined,
          manifestPath: draft.manifestPath.trim() || undefined,
          sourceConfig,
        };
        await patchReleaseIngestEndpoint(editingId, patchRequest);
        toast.success(`Updated release ingest endpoint ${editingId}.`);
      } else {
        const createRequest: ReleaseIngestEndpointCreateRequest = {
          id: draft.id.trim(),
          name: draft.name.trim(),
          provider: draft.provider,
          enabled: draft.enabled,
          secretRef: draft.secretRef.trim() || undefined,
          repoFilter: draft.repoFilter.trim() || undefined,
          branchFilter: draft.branchFilter.trim() || undefined,
          pipelineIdFilter: draft.pipelineIdFilter.trim() || undefined,
          manifestPath: draft.manifestPath.trim() || undefined,
          sourceConfig,
        };
        await createReleaseIngestEndpoint(createRequest);
        toast.success(`Created release ingest endpoint ${draft.id.trim()}.`);
      }
      setDrawerOpen(false);
      await loadEndpoints(true);
    } catch (error) {
      toast.error((error as Error).message);
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleDelete(endpoint: ReleaseIngestEndpoint): Promise<void> {
    const endpointId = endpoint.id ?? "";
    if (!endpointId) {
      return;
    }
    const confirmed = window.confirm(
      `Delete release ingest endpoint ${endpointId}? This will fail if projects are still linked.`
    );
    if (!confirmed) {
      return;
    }
    try {
      await deleteReleaseIngestEndpoint(endpointId);
      toast.success(`Deleted release ingest endpoint ${endpointId}.`);
      await loadEndpoints(true);
    } catch (error) {
      toast.error((error as Error).message);
    }
  }

  async function handleCopyWebhookUrl(endpoint: ReleaseIngestEndpoint): Promise<void> {
    const endpointId = endpoint.id ?? "";
    const provider = (endpoint.provider ?? "github") as ReleaseIngestProvider;
    if (!endpointId) {
      return;
    }
    const url = `${apiBaseUrl.replace(/\/+$/, "")}${webhookPathFor(endpointId, provider)}`;
    try {
      await navigator.clipboard.writeText(url);
      toast.success("Webhook URL copied.");
    } catch {
      toast.error("Failed to copy webhook URL.");
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex animate-fade-up items-center justify-between [animation-delay:60ms] [animation-fill-mode:forwards]">
        <p className="text-xs text-muted-foreground">
          Configure release webhook providers, auth references, and routing filters.
        </p>
        <div className="flex items-center gap-2">
          <Button type="button" variant="outline" onClick={() => void loadEndpoints(true)}>
            {isRefreshing ? "Refreshing..." : "Refresh"}
          </Button>
          <Button type="button" onClick={openCreateDrawer}>
            New Release Ingest Endpoint
          </Button>
        </div>
      </div>

      <div className="rounded-md border border-border/70 bg-background/40 p-2 text-xs text-muted-foreground">
        Selected project link check:{" "}
        <span className="font-mono text-foreground">{selectedProjectId || "none"}</span>
      </div>

      {errorMessage ? (
        <div className="rounded-md border border-destructive/60 bg-destructive/10 p-2 text-xs text-destructive-foreground">
          {errorMessage}
        </div>
      ) : null}

      <Card className="glass-card animate-fade-up [animation-delay:100ms] [animation-fill-mode:forwards]">
        <CardHeader className="flex flex-row items-center justify-between space-y-0">
          <CardTitle>Release Ingest Endpoints</CardTitle>
          <span className="rounded-full border border-border/70 px-3 py-1 font-mono text-[11px] text-muted-foreground">
            {sortedEndpoints.length} endpoints
          </span>
        </CardHeader>
        <CardContent className="space-y-3">
          {isLoading ? (
            <p className="text-sm text-muted-foreground">Loading release ingest endpoints...</p>
          ) : null}
          {!isLoading && sortedEndpoints.length === 0 ? (
            <p className="text-sm text-muted-foreground">
              No release ingest endpoints configured yet.
            </p>
          ) : null}
          {sortedEndpoints.map((endpoint) => {
            const endpointId = endpoint.id ?? "";
            const provider = (endpoint.provider ?? "github") as ReleaseIngestProvider;
            const webhookUrl = `${apiBaseUrl.replace(/\/+$/, "")}${webhookPathFor(endpointId, provider)}`;
            const linkedProjects = endpoint.linkedProjects ?? [];
            const selectedProjectLinked = linkedProjects.some(
              (project) => (project.projectId ?? "") === selectedProjectId
            );
            return (
              <div key={endpointId || endpoint.name} className="rounded-md border border-border/70 bg-card/70 p-3">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <p className="text-sm font-semibold text-primary">{endpoint.name || endpointId}</p>
                    <p className="font-mono text-[11px] text-muted-foreground">{endpointId}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="rounded-full border border-primary/40 px-2 py-0.5 text-[11px] uppercase tracking-[0.06em] text-primary">
                      {provider.replaceAll("_", " ")}
                    </span>
                    <span
                      className={`rounded-full px-2 py-0.5 text-[11px] font-semibold uppercase tracking-[0.06em] ${
                        endpoint.enabled
                          ? "bg-emerald-500/20 text-emerald-200"
                          : "bg-amber-500/20 text-amber-200"
                      }`}
                    >
                      {endpoint.enabled ? "enabled" : "disabled"}
                    </span>
                  </div>
                </div>

                <div className="mt-3 grid gap-2 text-xs text-muted-foreground md:grid-cols-2">
                  <p>Secret ref: <span className="font-mono text-foreground">{endpoint.secretRef || "default"}</span></p>
                  <p>Manifest path: <span className="font-mono text-foreground">{endpoint.manifestPath || "default"}</span></p>
                  <p>Repo filter: <span className="font-mono text-foreground">{endpoint.repoFilter || "none"}</span></p>
                  <p>Branch filter: <span className="font-mono text-foreground">{endpoint.branchFilter || "none"}</span></p>
                  <p>Pipeline filter: <span className="font-mono text-foreground">{endpoint.pipelineIdFilter || "none"}</span></p>
                  <p>Updated: <span className="text-foreground">{formatTimestamp(endpoint.updatedAt)}</span></p>
                </div>

                <div className="mt-2 rounded-md border border-border/60 bg-background/50 p-2">
                  <p className="text-[11px] uppercase tracking-[0.06em] text-muted-foreground">Webhook URL</p>
                  <p className="mt-1 break-all font-mono text-[11px] text-foreground">{webhookUrl}</p>
                </div>

                <div className="mt-2">
                  <p className="text-[11px] uppercase tracking-[0.06em] text-muted-foreground">Linked projects</p>
                  <div className="mt-1 flex flex-wrap gap-1">
                    {linkedProjects.length === 0 ? (
                      <span className="text-xs text-muted-foreground">No linked projects</span>
                    ) : (
                      linkedProjects.map((project) => {
                        const projectId = project.projectId ?? "";
                        const isSelected = projectId === selectedProjectId;
                        return (
                          <span
                            key={`${endpointId}-${projectId}`}
                            className={`rounded-full border px-2 py-0.5 text-[11px] ${
                              isSelected
                                ? "border-primary/60 bg-primary/15 text-primary"
                                : "border-border/60 bg-background/50 text-muted-foreground"
                            }`}
                          >
                            {project.projectName || projectId}
                          </span>
                        );
                      })
                    )}
                  </div>
                  {selectedProjectLinked ? (
                    <p className="mt-1 text-[11px] text-primary">Selected project is linked to this endpoint.</p>
                  ) : null}
                </div>

                <div className="mt-3 flex flex-wrap justify-end gap-2">
                  <Button type="button" variant="outline" onClick={() => void handleCopyWebhookUrl(endpoint)}>
                    Copy Webhook URL
                  </Button>
                  <Button type="button" variant="outline" onClick={() => openEditDrawer(endpoint)}>
                    Edit
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    className="border-destructive/50 text-destructive hover:bg-destructive/10 hover:text-destructive"
                    onClick={() => void handleDelete(endpoint)}
                  >
                    Delete
                  </Button>
                </div>
              </div>
            );
          })}
        </CardContent>
      </Card>

      <Drawer open={drawerOpen} onOpenChange={setDrawerOpen} direction="top">
        <DrawerContent>
          <DrawerHeader>
            <DrawerTitle>{editingId ? `Edit ${editingId}` : "New release ingest endpoint"}</DrawerTitle>
            <DrawerDescription>
              Configure provider settings and webhook routing for release ingest.
            </DrawerDescription>
          </DrawerHeader>
          <form id="release-ingest-endpoint-form" onSubmit={(event) => void handleSubmit(event)} className="space-y-3 px-4 pb-4">
            <div className="grid gap-3 md:grid-cols-2">
              <div className="space-y-1.5">
                <div className="flex items-center gap-1">
                  <Label htmlFor="endpoint-id">Endpoint ID</Label>
                  <FieldHelpTooltip content="Stable unique key used in webhook URL paths. Use lowercase letters, numbers, and hyphens." />
                </div>
                <Input
                  id="endpoint-id"
                  value={draft.id}
                  onChange={(event) => setDraft((current) => ({ ...current, id: event.target.value }))}
                  disabled={Boolean(editingId)}
                  placeholder="github-managed-app-default"
                />
              </div>
              <div className="space-y-1.5">
                <div className="flex items-center gap-1">
                  <Label htmlFor="endpoint-name">Name</Label>
                  <FieldHelpTooltip content="Human-readable display name shown in the Release Ingest list." />
                </div>
                <Input
                  id="endpoint-name"
                  value={draft.name}
                  onChange={(event) => setDraft((current) => ({ ...current, name: event.target.value }))}
                  placeholder="GitHub Managed App Default"
                />
              </div>
            </div>

            <div className="grid gap-3 md:grid-cols-3">
              <div className="space-y-1.5">
                <div className="flex items-center gap-1">
                  <Label htmlFor="endpoint-provider">Provider</Label>
                  <FieldHelpTooltip content="Webhook sender. Choose GitHub for GitHub webhooks, Azure DevOps for ADO service hooks." />
                </div>
                <Select
                  value={draft.provider}
                  onValueChange={(value) =>
                    setDraft((current) => ({ ...current, provider: value as ReleaseIngestProvider }))
                  }
                >
                  <SelectTrigger id="endpoint-provider" className="h-10 w-full bg-background/90 text-sm">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="github">GitHub</SelectItem>
                    <SelectItem value="azure_devops">Azure DevOps</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <div className="flex items-center gap-1">
                  <Label htmlFor="endpoint-enabled">Enabled</Label>
                  <FieldHelpTooltip content="Disabled endpoints ignore incoming webhook calls and do not ingest releases." />
                </div>
                <Select
                  value={draft.enabled ? "true" : "false"}
                  onValueChange={(value) => setDraft((current) => ({ ...current, enabled: value === "true" }))}
                >
                  <SelectTrigger id="endpoint-enabled" className="h-10 w-full bg-background/90 text-sm">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="true">Enabled</SelectItem>
                    <SelectItem value="false">Disabled</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1.5">
                <div className="flex items-center gap-1">
                  <Label htmlFor="endpoint-secret-ref">Secret Ref</Label>
                  <FieldHelpTooltip content="Webhook auth secret reference. Use literal:<secret> for quick setup, env:<ENV_VAR> for env-based secrets, or a managed key reference." />
                </div>
                <Input
                  id="endpoint-secret-ref"
                  value={draft.secretRef}
                  onChange={(event) => setDraft((current) => ({ ...current, secretRef: event.target.value }))}
                  placeholder={secretRefExample}
                />
              </div>
            </div>

            <div className="grid gap-3 md:grid-cols-3">
              <div className="space-y-1.5">
                <div className="flex items-center gap-1">
                  <Label htmlFor="endpoint-repo-filter">Repo Filter (GitHub only, optional)</Label>
                  <FieldHelpTooltip content="Optional repo gate for GitHub payloads (example: owner/repo). Leave empty to accept any repo." />
                </div>
                <Input
                  id="endpoint-repo-filter"
                  value={draft.repoFilter}
                  onChange={(event) => setDraft((current) => ({ ...current, repoFilter: event.target.value }))}
                  placeholder="Optional (e.g. org/repo)"
                  disabled={isAzureDevOpsProvider}
                />
              </div>
              <div className="space-y-1.5">
                <div className="flex items-center gap-1">
                  <Label htmlFor="endpoint-branch-filter">Branch Filter (optional)</Label>
                  <FieldHelpTooltip content="Optional branch gate (example: main). Leave empty to accept any branch." />
                </div>
                <Input
                  id="endpoint-branch-filter"
                  value={draft.branchFilter}
                  onChange={(event) => setDraft((current) => ({ ...current, branchFilter: event.target.value }))}
                  placeholder="Optional (e.g. main)"
                />
              </div>
              <div className="space-y-1.5">
                <div className="flex items-center gap-1">
                  <Label htmlFor="endpoint-pipeline-filter">Pipeline Filter (ADO only, optional)</Label>
                  <FieldHelpTooltip content="Optional Azure DevOps pipeline id filter. Leave empty to accept any pipeline in this endpoint." />
                </div>
                <Input
                  id="endpoint-pipeline-filter"
                  value={draft.pipelineIdFilter}
                  onChange={(event) =>
                    setDraft((current) => ({ ...current, pipelineIdFilter: event.target.value }))
                  }
                  placeholder="Optional (e.g. 1)"
                  disabled={!isAzureDevOpsProvider}
                />
              </div>
            </div>

            <div className="space-y-1.5">
              <div className="flex items-center gap-1">
                <Label htmlFor="endpoint-manifest-path">Manifest Path (GitHub only, optional)</Label>
                <FieldHelpTooltip content="Optional manifest path constraint for GitHub release ingest. Leave empty unless you need strict path filtering." />
              </div>
              <Input
                id="endpoint-manifest-path"
                value={draft.manifestPath}
                onChange={(event) => setDraft((current) => ({ ...current, manifestPath: event.target.value }))}
                placeholder="Optional (e.g. releases/releases.manifest.json)"
                disabled={isAzureDevOpsProvider}
              />
            </div>

            <div className="space-y-1.5">
              <div className="flex items-center gap-1">
                <Label htmlFor="endpoint-source-config">Source Config (JSON object)</Label>
                <FieldHelpTooltip content="Optional provider-specific advanced settings as a JSON object. Use {} when not needed." />
              </div>
              <textarea
                id="endpoint-source-config"
                value={draft.sourceConfigText}
                onChange={(event) => setDraft((current) => ({ ...current, sourceConfigText: event.target.value }))}
                className="min-h-32 w-full rounded-md border border-border/70 bg-background/90 p-2 font-mono text-xs text-foreground outline-none ring-offset-background transition focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              />
            </div>
          </form>
          <DrawerFooter>
            <div className="flex w-full items-center justify-end gap-2">
              <DrawerClose asChild>
                <Button type="button" variant="outline">
                  Cancel
                </Button>
              </DrawerClose>
              <Button type="submit" form="release-ingest-endpoint-form" disabled={isSubmitting}>
                {isSubmitting ? "Saving..." : editingId ? "Update endpoint" : "Create endpoint"}
              </Button>
            </div>
          </DrawerFooter>
        </DrawerContent>
      </Drawer>
    </div>
  );
}
