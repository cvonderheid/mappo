import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";

import FieldHelpTooltip from "@/components/FieldHelpTooltip";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
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
type WebhookSecretMode = "provider_default" | "environment_variable";

type ReleaseIngestConfigPageProps = {
  selectedProjectId: string;
};

type EndpointDraft = {
  id: string;
  name: string;
  provider: ReleaseIngestProvider;
  enabled: boolean;
  webhookSecretMode: WebhookSecretMode;
  webhookSecretEnvVar: string;
  repoFilter: string;
  branchFilter: string;
  pipelineIdFilter: string;
  manifestPath: string;
};

function emptyDraft(): EndpointDraft {
  return {
    id: "",
    name: "",
    provider: "github",
    enabled: true,
    webhookSecretMode: "provider_default",
    webhookSecretEnvVar: "",
    repoFilter: "",
    branchFilter: "",
    pipelineIdFilter: "",
    manifestPath: "",
  };
}

function providerDefaultSecretRef(provider: ReleaseIngestProvider): string {
  return provider === "azure_devops"
    ? "mappo.azure-devops.webhook-secret"
    : "mappo.managed-app-release.webhook-secret";
}

function parseWebhookSecretRef(
  value: string | undefined
): Pick<EndpointDraft, "webhookSecretMode" | "webhookSecretEnvVar"> {
  const normalized = (value ?? "").trim();
  if (normalized.startsWith("env:")) {
    return {
      webhookSecretMode: "environment_variable",
      webhookSecretEnvVar: normalized.slice("env:".length).trim(),
    };
  }
  return {
    webhookSecretMode: "provider_default",
    webhookSecretEnvVar: "",
  };
}

function buildWebhookSecretRef(draft: EndpointDraft): string {
  if (draft.webhookSecretMode === "environment_variable") {
    const envVarName = draft.webhookSecretEnvVar.trim();
    return envVarName === "" ? "" : `env:${envVarName}`;
  }
  return providerDefaultSecretRef(draft.provider);
}

function describeWebhookSecretSource(endpoint: ReleaseIngestEndpoint): string {
  const provider = (endpoint.provider ?? "github") as ReleaseIngestProvider;
  const normalized = (endpoint.secretRef ?? "").trim() || providerDefaultSecretRef(provider);
  if (normalized === providerDefaultSecretRef(provider)) {
    return "Provider default backend secret";
  }
  if (normalized.startsWith("env:")) {
    return `Environment variable (${normalized.slice("env:".length).trim()})`;
  }
  return "Unsupported legacy value";
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
  const secretReference = parseWebhookSecretRef(endpoint.secretRef);
  return {
    id: endpoint.id ?? "",
    name: endpoint.name ?? "",
    provider,
    enabled: endpoint.enabled ?? true,
    ...secretReference,
    repoFilter: endpoint.repoFilter ?? "",
    branchFilter: endpoint.branchFilter ?? "",
    pipelineIdFilter: endpoint.pipelineIdFilter ?? "",
    manifestPath: endpoint.manifestPath ?? "",
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
  const webhookSecretRef = buildWebhookSecretRef(draft);

  const sortedEndpoints = useMemo(() => {
    return [...endpoints].sort((left, right) => {
      const leftName = (left.name ?? "").toLowerCase();
      const rightName = (right.name ?? "").toLowerCase();
      if (leftName !== rightName) {
        return leftName.localeCompare(rightName);
      }
      return (left.id ?? "").localeCompare(right.id ?? "");
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
      toast.error("Endpoint ID and name are required.");
      return;
    }
    if (draft.webhookSecretMode === "environment_variable" && webhookSecretRef === "") {
      toast.error("Environment variable name is required when Webhook secret source is Environment variable.");
      return;
    }

    setIsSubmitting(true);
    try {
      if (editingId) {
        const patchRequest: ReleaseIngestEndpointPatchRequest = {
          name: draft.name.trim(),
          provider: draft.provider,
          enabled: draft.enabled,
          secretRef: webhookSecretRef || undefined,
          repoFilter: draft.repoFilter.trim() || undefined,
          branchFilter: draft.branchFilter.trim() || undefined,
          pipelineIdFilter: draft.pipelineIdFilter.trim() || undefined,
          manifestPath: draft.manifestPath.trim() || undefined,
        };
        await patchReleaseIngestEndpoint(editingId, patchRequest);
        toast.success(`Updated release ingest endpoint ${editingId}.`);
      } else {
        const createRequest: ReleaseIngestEndpointCreateRequest = {
          id: draft.id.trim(),
          name: draft.name.trim(),
          provider: draft.provider,
          enabled: draft.enabled,
          secretRef: webhookSecretRef || undefined,
          repoFilter: draft.repoFilter.trim() || undefined,
          branchFilter: draft.branchFilter.trim() || undefined,
          pipelineIdFilter: draft.pipelineIdFilter.trim() || undefined,
          manifestPath: draft.manifestPath.trim() || undefined,
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
      `Delete release ingest endpoint ${endpointId}? This fails if projects are still linked.`
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
          Configure global release ingest endpoints and webhook routing for linked projects.
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
            <p className="text-sm text-muted-foreground">No release ingest endpoints configured yet.</p>
          ) : null}
          {sortedEndpoints.map((endpoint) => {
            const endpointId = endpoint.id ?? "";
            const provider = (endpoint.provider ?? "github") as ReleaseIngestProvider;
            const webhookUrl = `${apiBaseUrl.replace(/\/+$/, "")}${webhookPathFor(endpointId, provider)}`;
            const linkedProjects = endpoint.linkedProjects ?? [];
            const selectedProjectLinked = linkedProjects.some(
              (linked) => (linked.projectId ?? "") === selectedProjectId
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
                  <p>
                    Webhook credential source:{" "}
                    <span className="font-medium text-foreground">{describeWebhookSecretSource(endpoint)}</span>
                  </p>
                  <p>
                    Branch filter: <span className="font-mono text-foreground">{endpoint.branchFilter || "none"}</span>
                  </p>
                  {provider === "github" ? (
                    <>
                      <p>
                        Repo filter: <span className="font-mono text-foreground">{endpoint.repoFilter || "none"}</span>
                      </p>
                      <p>
                        Manifest path: <span className="font-mono text-foreground">{endpoint.manifestPath || "default"}</span>
                      </p>
                    </>
                  ) : (
                    <p>
                      Pipeline filter:{" "}
                      <span className="font-mono text-foreground">{endpoint.pipelineIdFilter || "none"}</span>
                    </p>
                  )}
                  <p>
                    Updated: <span className="text-foreground">{formatTimestamp(endpoint.updatedAt)}</span>
                  </p>
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
                      linkedProjects.map((linked) => {
                        const linkedProjectId = linked.projectId ?? "";
                        const isSelected = linkedProjectId === selectedProjectId;
                        return (
                          <span
                            key={`${endpointId}-${linkedProjectId}`}
                            className={`rounded-full border px-2 py-0.5 text-[11px] ${
                              isSelected
                                ? "border-primary/60 bg-primary/15 text-primary"
                                : "border-border/60 bg-background/50 text-muted-foreground"
                            }`}
                          >
                            {linked.projectName || linkedProjectId}
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
              Configure provider routing and webhook authentication for release ingest.
            </DrawerDescription>
          </DrawerHeader>
          <form
            id="release-ingest-endpoint-form"
            onSubmit={(event) => void handleSubmit(event)}
            className="space-y-3 px-4 pb-4"
          >
            <div className="grid gap-3 md:grid-cols-2">
              <div className="space-y-1.5">
                <div className="flex items-center gap-1">
                  <Label htmlFor="endpoint-id">Endpoint ID</Label>
                  <FieldHelpTooltip content="Stable key used in webhook URL paths. Use lowercase letters, numbers, and hyphens." />
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
                  <FieldHelpTooltip content="Display name shown in the Admin > Release Ingest list." />
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
                  <FieldHelpTooltip content="Webhook sender for this endpoint." />
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
                  <FieldHelpTooltip content="Disabled endpoints ignore inbound webhook calls." />
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
                  <Label htmlFor="endpoint-webhook-secret-mode">Webhook secret source</Label>
                  <FieldHelpTooltip content="How MAPPO resolves the webhook signing secret for this endpoint. Use the provider default backend secret unless you intentionally store the secret in a specific environment variable." />
                </div>
                <Select
                  value={draft.webhookSecretMode}
                  onValueChange={(value) =>
                    setDraft((current) => ({
                      ...current,
                      webhookSecretMode: value as WebhookSecretMode,
                      webhookSecretEnvVar:
                        value === "provider_default" ? "" : current.webhookSecretEnvVar,
                    }))
                  }
                >
                  <SelectTrigger id="endpoint-webhook-secret-mode" className="h-10 w-full bg-background/90 text-sm">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="provider_default">Provider default backend secret</SelectItem>
                    <SelectItem value="environment_variable">Environment variable</SelectItem>
                  </SelectContent>
                </Select>
                {draft.webhookSecretMode === "provider_default" ? (
                  <p className="text-xs text-muted-foreground">
                    MAPPO will resolve <span className="font-mono text-foreground">{providerDefaultSecretRef(draft.provider)}</span> on the backend.
                  </p>
                ) : null}
              </div>
            </div>

            {draft.webhookSecretMode === "environment_variable" ? (
              <div className="space-y-1.5">
                <div className="flex items-center gap-1">
                  <Label htmlFor="endpoint-webhook-secret-env-var">Webhook secret environment variable</Label>
                  <FieldHelpTooltip content="Environment variable name on the MAPPO backend runtime that stores the webhook signing secret. Enter only the variable name, not the secret value." />
                </div>
                <Input
                  id="endpoint-webhook-secret-env-var"
                  value={draft.webhookSecretEnvVar}
                  onChange={(event) =>
                    setDraft((current) => ({
                      ...current,
                      webhookSecretEnvVar: event.target.value,
                    }))
                  }
                  placeholder={draft.provider === "azure_devops" ? "MAPPO_AZURE_DEVOPS_WEBHOOK_SECRET" : "MAPPO_MANAGED_APP_RELEASE_WEBHOOK_SECRET"}
                />
              </div>
            ) : null}

            {isAzureDevOpsProvider ? (
              <div className="grid gap-3 md:grid-cols-2">
                <div className="space-y-1.5">
                  <div className="flex items-center gap-1">
                    <Label htmlFor="endpoint-branch-filter">Branch filter</Label>
                    <FieldHelpTooltip content="Optional branch gate for Azure DevOps events. Leave blank to accept any branch." />
                  </div>
                  <Input
                    id="endpoint-branch-filter"
                    value={draft.branchFilter}
                    onChange={(event) => setDraft((current) => ({ ...current, branchFilter: event.target.value }))}
                    placeholder="Optional (for example main)"
                  />
                </div>
                <div className="space-y-1.5">
                  <div className="flex items-center gap-1">
                    <Label htmlFor="endpoint-pipeline-filter">Pipeline filter</Label>
                    <FieldHelpTooltip content="Optional Azure DevOps pipeline ID gate. Leave blank to accept any pipeline event." />
                  </div>
                  <Input
                    id="endpoint-pipeline-filter"
                    value={draft.pipelineIdFilter}
                    onChange={(event) =>
                      setDraft((current) => ({ ...current, pipelineIdFilter: event.target.value }))
                    }
                    placeholder="Optional (for example 1)"
                  />
                </div>
              </div>
            ) : (
              <div className="grid gap-3 md:grid-cols-3">
                <div className="space-y-1.5">
                  <div className="flex items-center gap-1">
                    <Label htmlFor="endpoint-repo-filter">Repository filter</Label>
                    <FieldHelpTooltip content="Optional GitHub repository gate. Use owner/repo. Leave blank to accept any repository that hits this endpoint." />
                  </div>
                  <Input
                    id="endpoint-repo-filter"
                    value={draft.repoFilter}
                    onChange={(event) => setDraft((current) => ({ ...current, repoFilter: event.target.value }))}
                    placeholder="Optional (for example org/repo)"
                  />
                </div>
                <div className="space-y-1.5">
                  <div className="flex items-center gap-1">
                    <Label htmlFor="endpoint-branch-filter">Branch filter</Label>
                    <FieldHelpTooltip content="Optional branch gate for GitHub events. Leave blank to accept any branch." />
                  </div>
                  <Input
                    id="endpoint-branch-filter"
                    value={draft.branchFilter}
                    onChange={(event) => setDraft((current) => ({ ...current, branchFilter: event.target.value }))}
                    placeholder="Optional (for example main)"
                  />
                </div>
                <div className="space-y-1.5">
                  <div className="flex items-center gap-1">
                    <Label htmlFor="endpoint-manifest-path">Manifest path</Label>
                    <FieldHelpTooltip content="Optional release manifest path for GitHub payloads. Leave blank to use the default manifest path." />
                  </div>
                  <Input
                    id="endpoint-manifest-path"
                    value={draft.manifestPath}
                    onChange={(event) => setDraft((current) => ({ ...current, manifestPath: event.target.value }))}
                    placeholder="Optional (for example releases/releases.manifest.json)"
                  />
                </div>
              </div>
            )}
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
