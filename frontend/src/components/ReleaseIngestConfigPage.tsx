import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { LuActivity, LuBoxes, LuWorkflow } from "react-icons/lu";
import { SiGithub } from "react-icons/si";
import { VscAzureDevops } from "react-icons/vsc";
import { toast } from "sonner";

import AdminIntegrationFlowDiagram, {
  type AdminIntegrationFlowNode,
} from "@/components/AdminIntegrationFlowDiagram";
import FieldHelpTooltip from "@/components/FieldHelpTooltip";
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
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
  listSecretReferences,
  listReleaseIngestEndpoints,
  patchReleaseIngestEndpoint,
} from "@/lib/api";
import type {
  ReleaseIngestEndpoint,
  ReleaseIngestEndpointCreateRequest,
  ReleaseIngestEndpointPatchRequest,
  SecretReference,
} from "@/lib/types";

type ReleaseIngestProvider = "github" | "azure_devops";
type WebhookSecretMode = "provider_default" | "environment_variable" | "key_vault_secret" | "secret_reference";

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
  webhookSecretKeyVaultSecret: string;
  webhookSecretReferenceId: string;
  repoFilter: string;
  branchFilter: string;
  pipelineIdFilter: string;
  manifestPath: string;
};

type LinkedProject = NonNullable<ReleaseIngestEndpoint["linkedProjects"]>[number];

function emptyDraft(): EndpointDraft {
  return {
    id: "",
    name: "",
    provider: "github",
    enabled: true,
    webhookSecretMode: "provider_default",
    webhookSecretEnvVar: "",
    webhookSecretKeyVaultSecret: "",
    webhookSecretReferenceId: "",
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
): Pick<EndpointDraft, "webhookSecretMode" | "webhookSecretEnvVar" | "webhookSecretKeyVaultSecret" | "webhookSecretReferenceId"> {
  const normalized = (value ?? "").trim();
  if (normalized.startsWith("secret:")) {
    return {
      webhookSecretMode: "secret_reference",
      webhookSecretEnvVar: "",
      webhookSecretKeyVaultSecret: "",
      webhookSecretReferenceId: normalized.slice("secret:".length).trim(),
    };
  }
  if (normalized.startsWith("env:")) {
    return {
      webhookSecretMode: "environment_variable",
      webhookSecretEnvVar: normalized.slice("env:".length).trim(),
      webhookSecretKeyVaultSecret: "",
      webhookSecretReferenceId: "",
    };
  }
  if (normalized.startsWith("kv:")) {
    return {
      webhookSecretMode: "key_vault_secret",
      webhookSecretEnvVar: "",
      webhookSecretKeyVaultSecret: normalized.slice("kv:".length).trim(),
      webhookSecretReferenceId: "",
    };
  }
  return {
    webhookSecretMode: "provider_default",
    webhookSecretEnvVar: "",
    webhookSecretKeyVaultSecret: "",
    webhookSecretReferenceId: "",
  };
}

function buildWebhookSecretRef(draft: EndpointDraft): string {
  if (draft.webhookSecretMode === "secret_reference") {
    const secretReferenceId = draft.webhookSecretReferenceId.trim();
    return secretReferenceId === "" ? "" : `secret:${secretReferenceId}`;
  }
  if (draft.webhookSecretMode === "environment_variable") {
    const envVarName = draft.webhookSecretEnvVar.trim();
    return envVarName === "" ? "" : `env:${envVarName}`;
  }
  if (draft.webhookSecretMode === "key_vault_secret") {
    const secretName = draft.webhookSecretKeyVaultSecret.trim();
    return secretName === "" ? "" : `kv:${secretName}`;
  }
  return providerDefaultSecretRef(draft.provider);
}

function describeWebhookSecretSource(
  endpoint: ReleaseIngestEndpoint,
  secretReferenceLookup: Record<string, SecretReference>
): string {
  const provider = (endpoint.provider ?? "github") as ReleaseIngestProvider;
  const normalized = (endpoint.secretRef ?? "").trim() || providerDefaultSecretRef(provider);
  if (normalized === providerDefaultSecretRef(provider)) {
    return "MAPPO runtime secret";
  }
  if (normalized.startsWith("secret:")) {
    const secretReferenceId = normalized.slice("secret:".length).trim();
    return `Secret reference (${secretReferenceLookup[secretReferenceId]?.name || secretReferenceId})`;
  }
  if (normalized.startsWith("env:")) {
    return `Environment variable (${normalized.slice("env:".length).trim()})`;
  }
  if (normalized.startsWith("kv:")) {
    return `Azure Key Vault secret (${normalized.slice("kv:".length).trim()})`;
  }
  return "Named runtime secret";
}

function releaseProviderLabel(provider: ReleaseIngestProvider): string {
  return provider === "azure_devops" ? "Azure DevOps" : "GitHub";
}

function releaseProviderIcon(provider: ReleaseIngestProvider, className: string) {
  return provider === "azure_devops" ? (
    <VscAzureDevops className={className} />
  ) : (
    <SiGithub className={className} />
  );
}

function summarizeLinkedProjects(linkedProjects: LinkedProject[]): string {
  if (linkedProjects.length === 0) {
    return "No linked projects";
  }
  const labels = linkedProjects.map((linked) => linked.projectName || linked.projectId || "Project");
  const visible = labels.slice(0, 3).join(", ");
  const overflow = labels.length - 3;
  return overflow > 0 ? `${visible}, +${overflow} more` : visible;
}

function releaseResultLabel(provider: ReleaseIngestProvider): string {
  return provider === "azure_devops"
    ? "Pipeline event is emitted"
    : "Release manifest is updated";
}

function buildReleaseSourceFlowNodes({
  endpoint,
  provider,
  webhookUrl,
  secretSource,
  linkedProjects,
  selectedProjectLinked,
}: {
  endpoint: ReleaseIngestEndpoint;
  provider: ReleaseIngestProvider;
  webhookUrl: string;
  secretSource: string;
  linkedProjects: LinkedProject[];
  selectedProjectLinked: boolean;
}): AdminIntegrationFlowNode[] {
  const enabled = endpoint.enabled ?? true;
  const providerLabel = releaseProviderLabel(provider);
  const hasRoutingFilters = Boolean(
    endpoint.repoFilter || endpoint.branchFilter || endpoint.pipelineIdFilter || endpoint.manifestPath
  );
  return [
    {
      step: "00",
      icon: releaseProviderIcon(provider, "h-5 w-5"),
      eyebrow: "Outside MAPPO",
      title: "Release performed",
      tone: "muted",
      details: [
        { label: "Provider", value: providerLabel },
        { label: "Result", value: releaseResultLabel(provider) },
      ],
    },
    {
      step: "01",
      icon: releaseProviderIcon(provider, "h-5 w-5"),
      eyebrow: "Provider",
      title: providerLabel,
      details: [
        { label: "Source", value: endpoint.name || endpoint.id },
        { label: "Status", value: enabled ? "Enabled" : "Disabled" },
      ],
    },
    {
      step: "02",
      icon: <LuWorkflow className="h-5 w-5" />,
      eyebrow: "MAPPO endpoint",
      title: endpoint.id || "Release source",
      tone: "primary",
      details: [
        { label: "Webhook URL", value: webhookUrl },
        { label: "Direction", value: "Inbound release notification" },
      ],
    },
    {
      step: "03",
      icon: <LuActivity className="h-5 w-5" />,
      eyebrow: "Verification",
      title: "Webhook secret",
      details: [
        { label: "Secret source", value: secretSource },
        { label: "Purpose", value: "Verify inbound payloads" },
      ],
    },
    {
      step: "04",
      icon: <LuWorkflow className="h-5 w-5" />,
      eyebrow: "Routing filters",
      title: hasRoutingFilters ? "Filters configured" : "No routing filters",
      tone: hasRoutingFilters ? "default" : "muted",
      details: provider === "azure_devops"
        ? [
            { label: "Branch", value: endpoint.branchFilter || "Any branch" },
            { label: "Pipeline", value: endpoint.pipelineIdFilter || "Any pipeline" },
          ]
        : [
            { label: "Repository", value: endpoint.repoFilter || "Any repository" },
            { label: "Branch", value: endpoint.branchFilter || "Any branch" },
            { label: "Manifest", value: endpoint.manifestPath || "Default manifest path" },
          ],
    },
    {
      step: "05",
      icon: <LuBoxes className="h-5 w-5" />,
      eyebrow: "Consumers",
      title: "Linked projects",
      tone: selectedProjectLinked ? "success" : "default",
      details: [
        { label: "Count", value: linkedProjects.length },
        { label: "Projects", value: summarizeLinkedProjects(linkedProjects) },
      ],
    },
  ];
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
  const [secretReferences, setSecretReferences] = useState<SecretReference[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string>("");
  const [drawerOpen, setDrawerOpen] = useState<boolean>(false);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [editingId, setEditingId] = useState<string>("");
  const [draft, setDraft] = useState<EndpointDraft>(emptyDraft);
  const [editingProvider, setEditingProvider] = useState<ReleaseIngestProvider | null>(null);

  const loadEndpoints = useCallback(async (refresh = false) => {
    if (refresh) {
      setIsRefreshing(true);
    } else {
      setIsLoading(true);
    }
    try {
      const [result, loadedSecretReferences] = await Promise.all([
        listReleaseIngestEndpoints(),
        listSecretReferences(),
      ]);
      setEndpoints(result);
      setSecretReferences(loadedSecretReferences);
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
  const webhookUrlPreview =
    draft.id.trim() === ""
      ? ""
      : `${apiBaseUrl.replace(/\/+$/, "")}${webhookPathFor(draft.id.trim(), draft.provider)}`;

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

  const secretReferenceLookup = useMemo(() => {
    const lookup: Record<string, SecretReference> = {};
    for (const secretReference of secretReferences) {
      const secretReferenceId = (secretReference.id ?? "").trim();
      if (secretReferenceId !== "") {
        lookup[secretReferenceId] = secretReference;
      }
    }
    return lookup;
  }, [secretReferences]);

  const webhookSecretReferences = useMemo(
    () =>
      secretReferences
        .filter(
          (secretReference) =>
            (secretReference.provider ?? "").trim() === draft.provider
            && (secretReference.usage ?? "").trim() === "webhook_verification"
        )
        .sort((left, right) =>
          `${left.name ?? left.id ?? ""}`.localeCompare(`${right.name ?? right.id ?? ""}`, undefined, {
            sensitivity: "base",
          })
        ),
    [draft.provider, secretReferences]
  );

  function openCreateDrawer(): void {
    setEditingId("");
    setDraft(emptyDraft());
    setEditingProvider(null);
    setDrawerOpen(true);
  }

  function openEditDrawer(endpoint: ReleaseIngestEndpoint): void {
    setEditingId(endpoint.id ?? "");
    const nextDraft = toDraft(endpoint);
    setDraft(nextDraft);
    setEditingProvider(nextDraft.provider);
    setDrawerOpen(true);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    if (draft.id.trim() === "" || draft.name.trim() === "") {
      toast.error("Endpoint ID and name are required.");
      return;
    }
    if (draft.webhookSecretMode === "environment_variable" && webhookSecretRef === "") {
      toast.error("Environment variable name is required when Webhook verification secret is Environment variable.");
      return;
    }
    if (draft.webhookSecretMode === "key_vault_secret" && webhookSecretRef === "") {
      toast.error("Azure Key Vault secret name is required when Webhook verification secret is Azure Key Vault secret.");
      return;
    }
    if (draft.webhookSecretMode === "secret_reference" && webhookSecretRef === "") {
      toast.error("Secret reference is required when Webhook verification is Secret reference.");
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
        toast.success(`Updated release source ${editingId}.`);
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
        toast.success(`Created release source ${draft.id.trim()}.`);
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
      `Delete release source ${endpointId}? This fails if projects are still linked.`
    );
    if (!confirmed) {
      return;
    }
    try {
      await deleteReleaseIngestEndpoint(endpointId);
      toast.success(`Deleted release source ${endpointId}.`);
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
      {errorMessage ? (
        <div className="rounded-md border border-destructive/60 bg-destructive/10 p-2 text-xs text-destructive-foreground">
          {errorMessage}
        </div>
      ) : null}

      <Card className="glass-card animate-fade-up [animation-delay:100ms] [animation-fill-mode:forwards]">
        <CardHeader className="gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="space-y-1">
            <CardTitle>Release Sources</CardTitle>
            <CardDescription>
              Configure the webhook URLs external release systems call when new versions are available. Release Sources are inbound notifications; Deployment Connections are configured separately and let MAPPO call external deployment systems.
            </CardDescription>
          </div>
          <CardAction className="flex-wrap justify-end">
            <Button type="button" variant="outline" onClick={() => void loadEndpoints(true)}>
              {isRefreshing ? "Refreshing..." : "Refresh"}
            </Button>
            <Button type="button" onClick={openCreateDrawer}>
              New Release Source
            </Button>
          </CardAction>
        </CardHeader>
        <CardContent className="space-y-3">
          {isLoading ? (
            <p className="text-sm text-muted-foreground">Loading release sources...</p>
          ) : null}
          {!isLoading && sortedEndpoints.length === 0 ? (
            <p className="text-sm text-muted-foreground">No release sources configured yet.</p>
          ) : null}
          {sortedEndpoints.map((endpoint) => {
            const endpointId = endpoint.id ?? "";
            const provider = (endpoint.provider ?? "github") as ReleaseIngestProvider;
            const webhookUrl = `${apiBaseUrl.replace(/\/+$/, "")}${webhookPathFor(endpointId, provider)}`;
            const linkedProjects = endpoint.linkedProjects ?? [];
            const selectedProjectLinked = linkedProjects.some(
              (linked) => (linked.projectId ?? "") === selectedProjectId
            );
            const secretSource = describeWebhookSecretSource(endpoint, secretReferenceLookup);
            const flowNodes = buildReleaseSourceFlowNodes({
              endpoint,
              provider,
              webhookUrl,
              secretSource,
              linkedProjects,
              selectedProjectLinked,
            });
            return (
              <Card key={endpointId || endpoint.name} className="border border-border/70 bg-card/70">
                <CardHeader className="gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div className="space-y-1">
                    <CardTitle>{endpoint.name || endpointId}</CardTitle>
                    <p className="font-mono text-[11px] text-muted-foreground">{endpointId}</p>
                  </div>
                  <CardAction className="flex-wrap justify-end">
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
                  </CardAction>
                </CardHeader>
                <CardContent className="space-y-3">
                  <AdminIntegrationFlowDiagram nodes={flowNodes} />

                  <div className="flex flex-wrap items-center gap-2">
                    <Badge variant="outline">Provider: {releaseProviderLabel(provider)}</Badge>
                    <Badge variant={endpoint.enabled ? "default" : "secondary"}>
                      {endpoint.enabled ? "Enabled" : "Disabled"}
                    </Badge>
                    <Badge variant="secondary">
                      Verification: {secretSource}
                    </Badge>
                    {formatTimestamp(endpoint.updatedAt) ? (
                      <Badge variant="secondary">Updated {formatTimestamp(endpoint.updatedAt)}</Badge>
                    ) : null}
                  </div>

                  <div className="rounded-md border border-border/60 bg-background/50 p-2">
                    <p className="text-[11px] uppercase tracking-[0.06em] text-muted-foreground">Webhook URL</p>
                    <p className="mt-1 break-all font-mono text-[11px] text-foreground">{webhookUrl}</p>
                  </div>

                  {endpoint.repoFilter || endpoint.branchFilter || endpoint.pipelineIdFilter || endpoint.manifestPath ? (
                    <Accordion type="single" collapsible className="rounded-md border border-border/60 bg-background/40 px-3">
                      <AccordionItem value="advanced-routing" className="border-none">
                        <AccordionTrigger className="py-2 text-xs font-medium text-muted-foreground hover:no-underline">
                          Optional routing filters
                        </AccordionTrigger>
                        <AccordionContent>
                          <div className="grid gap-2 text-xs text-muted-foreground md:grid-cols-2">
                            {endpoint.branchFilter ? (
                              <p>
                                Branch filter: <span className="font-mono text-foreground">{endpoint.branchFilter}</span>
                              </p>
                            ) : null}
                            {provider === "github" && endpoint.repoFilter ? (
                              <p>
                                Repository filter: <span className="font-mono text-foreground">{endpoint.repoFilter}</span>
                              </p>
                            ) : null}
                            {provider === "github" && endpoint.manifestPath ? (
                              <p>
                                Manifest path: <span className="font-mono text-foreground">{endpoint.manifestPath}</span>
                              </p>
                            ) : null}
                            {provider === "azure_devops" && endpoint.pipelineIdFilter ? (
                              <p>
                                Pipeline filter: <span className="font-mono text-foreground">{endpoint.pipelineIdFilter}</span>
                              </p>
                            ) : null}
                          </div>
                        </AccordionContent>
                      </AccordionItem>
                    </Accordion>
                  ) : null}

                  <div>
                    <p className="text-[11px] uppercase tracking-[0.06em] text-muted-foreground">Linked projects</p>
                    <div className="mt-1 flex flex-wrap gap-1">
                      {linkedProjects.length === 0 ? (
                        <span className="text-xs text-muted-foreground">No linked projects</span>
                      ) : (
                        linkedProjects.map((linked) => {
                          const linkedProjectId = linked.projectId ?? "";
                          const isSelected = linkedProjectId === selectedProjectId;
                          return (
                            <Badge
                              key={`${endpointId}-${linkedProjectId}`}
                              variant={isSelected ? "default" : "secondary"}
                            >
                              {linked.projectName || linkedProjectId}
                            </Badge>
                          );
                        })
                      )}
                    </div>
                    {selectedProjectLinked ? (
                      <p className="mt-1 text-[11px] text-primary">Selected project is linked to this endpoint.</p>
                    ) : null}
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </CardContent>
      </Card>

      <Drawer open={drawerOpen} onOpenChange={setDrawerOpen} direction="top">
        <DrawerContent>
          <DrawerHeader>
            <DrawerTitle>{editingId ? `Edit ${editingId}` : "New Release Source"}</DrawerTitle>
            <DrawerDescription>
              Configure how an external system notifies MAPPO about new releases. This is separate from Deployment Connections, which handle outbound API access.
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
                  <Label htmlFor="endpoint-id">Source ID</Label>
                  <FieldHelpTooltip content="Stable key used in webhook URL paths. Use lowercase letters, numbers, and hyphens." />
                </div>
                <Input
                  id="endpoint-id"
                  value={draft.id}
                  onChange={(event) => setDraft((current) => ({ ...current, id: event.target.value }))}
                  disabled={Boolean(editingId)}
                  placeholder="github-managed-app-default"
                />
                <p className="text-xs text-muted-foreground">
                  This ID becomes part of the webhook URL MAPPO exposes for this release source.
                </p>
              </div>
              <div className="space-y-1.5">
                <div className="flex items-center gap-1">
                  <Label htmlFor="endpoint-name">Display name</Label>
                  <FieldHelpTooltip content="Display name shown in the Admin > Release Sources list." />
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
                  <Label htmlFor="endpoint-provider">Release provider</Label>
                  <FieldHelpTooltip content="External system that tells MAPPO a new release is available." />
                </div>
                <Select
                  value={draft.provider}
                  onValueChange={(value) =>
                    setDraft((current) => {
                      const nextProvider = value as ReleaseIngestProvider;
                      return {
                        ...current,
                        provider: nextProvider,
                        webhookSecretReferenceId:
                          current.webhookSecretMode === "secret_reference" ? "" : current.webhookSecretReferenceId,
                        repoFilter: nextProvider === "github" ? current.repoFilter : "",
                        pipelineIdFilter: nextProvider === "azure_devops" ? current.pipelineIdFilter : "",
                        manifestPath: nextProvider === "github" ? current.manifestPath : "",
                      };
                    })
                  }
                  disabled={Boolean(editingId)}
                >
                  <SelectTrigger id="endpoint-provider" className="h-10 w-full bg-background/90 text-sm">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="github">GitHub</SelectItem>
                    <SelectItem value="azure_devops">Azure DevOps</SelectItem>
                  </SelectContent>
                </Select>
                {editingId ? (
                  <p className="text-xs text-muted-foreground">
                    Provider is fixed after creation so the source ID, webhook URL, and linked project behavior stay stable. Create a new release source if you need a different provider.
                  </p>
                ) : null}
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
                  <Label htmlFor="endpoint-webhook-secret-mode">Webhook verification</Label>
                  <FieldHelpTooltip content="How MAPPO resolves the shared secret used to verify inbound webhook deliveries for this release source. Use the MAPPO backend secret unless you intentionally keep the secret in a named environment variable." />
                </div>
                <Select
                  value={draft.webhookSecretMode}
                  onValueChange={(value) =>
                    setDraft((current) => ({
                      ...current,
                      webhookSecretMode: value as WebhookSecretMode,
                      webhookSecretReferenceId:
                        value === "secret_reference" ? current.webhookSecretReferenceId : "",
                      webhookSecretEnvVar:
                        value === "environment_variable" ? current.webhookSecretEnvVar : "",
                      webhookSecretKeyVaultSecret:
                        value === "key_vault_secret" ? current.webhookSecretKeyVaultSecret : "",
                    }))
                  }
                >
                  <SelectTrigger id="endpoint-webhook-secret-mode" className="h-10 w-full bg-background/90 text-sm">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="provider_default">Use MAPPO backend secret</SelectItem>
                    <SelectItem value="secret_reference">Use secret reference</SelectItem>
                    <SelectItem value="environment_variable">Use backend environment variable</SelectItem>
                    <SelectItem value="key_vault_secret">Use Azure Key Vault secret</SelectItem>
                  </SelectContent>
                </Select>
                {draft.webhookSecretMode === "provider_default" ? (
                  <p className="text-xs text-muted-foreground">
                    MAPPO will resolve <span className="font-mono text-foreground">{providerDefaultSecretRef(draft.provider)}</span> from the backend runtime.
                  </p>
                ) : null}
                {draft.webhookSecretMode === "secret_reference" ? (
                  <p className="text-xs text-muted-foreground">
                    Use a named secret from <span className="font-medium text-foreground">Admin → Secret References</span> so webhook verification is reusable and easier to explain.
                  </p>
                ) : null}
              </div>
            </div>

            <div className="rounded-md border border-border/60 bg-background/40 p-3 text-xs text-muted-foreground">
              <p className="font-medium text-foreground">Webhook URL preview</p>
              {webhookUrlPreview ? (
                <>
                  <p className="mt-1 break-all font-mono text-foreground">{webhookUrlPreview}</p>
                  <p className="mt-2">
                    Use this URL when creating the webhook or service hook in{" "}
                    <span className="font-medium text-foreground">
                      {draft.provider === "azure_devops" ? "Azure DevOps" : "GitHub"}
                    </span>.
                  </p>
                </>
              ) : (
                <p className="mt-1">
                  Enter a <span className="font-medium text-foreground">Source ID</span> above and MAPPO will generate the webhook URL here.
                </p>
              )}
              {editingId && editingProvider && editingProvider !== draft.provider ? (
                <p className="mt-2">
                  This source was originally created for{" "}
                  <span className="font-medium text-foreground">
                    {editingProvider === "azure_devops" ? "Azure DevOps" : "GitHub"}
                  </span>
                  . Save will fail because release source provider cannot change after creation.
                </p>
              ) : null}
            </div>

            <div className="rounded-md border border-border/60 bg-background/40 p-3 text-xs text-muted-foreground">
              <p className="font-medium text-foreground">How operators wire this up</p>
              {draft.provider === "azure_devops" ? (
                <ul className="mt-2 list-disc space-y-1 pl-4">
                  <li>Create an Azure DevOps service hook or webhook subscription that posts to the webhook URL preview above.</li>
                  <li>Configure the same shared secret MAPPO verifies here.</li>
                  <li>Leave routing filters empty unless one endpoint accepts events from multiple pipelines.</li>
                </ul>
              ) : (
                <ul className="mt-2 list-disc space-y-1 pl-4">
                  <li>Create a GitHub webhook that posts JSON to the webhook URL preview above.</li>
                  <li>Configure the same shared secret MAPPO verifies here.</li>
                  <li>Leave routing filters empty unless one endpoint accepts events from multiple repositories.</li>
                </ul>
              )}
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
            {draft.webhookSecretMode === "secret_reference" ? (
              <div className="space-y-1.5">
                <div className="flex items-center gap-1">
                  <Label htmlFor="endpoint-webhook-secret-reference">Secret reference</Label>
                  <FieldHelpTooltip content="Named webhook-verification secret from Admin → Secret References. MAPPO still resolves the real secret value server-side." />
                </div>
                <Select
                  value={draft.webhookSecretReferenceId.trim() === "" ? "__none" : draft.webhookSecretReferenceId}
                  onValueChange={(value) =>
                    setDraft((current) => ({
                      ...current,
                      webhookSecretReferenceId: value === "__none" ? "" : value,
                    }))
                  }
                >
                  <SelectTrigger id="endpoint-webhook-secret-reference">
                    <SelectValue placeholder="Select secret reference" />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="__none">Select secret reference</SelectItem>
                    {webhookSecretReferences.map((secretReference) => (
                      <SelectItem key={secretReference.id ?? secretReference.name} value={secretReference.id ?? ""}>
                        {secretReference.name || secretReference.id}
                        {" ("}
                        {secretReference.id}
                        {")"}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {webhookSecretReferences.length === 0 ? (
                  <p className="text-xs text-muted-foreground">
                    No {draft.provider === "azure_devops" ? "Azure DevOps" : "GitHub"} webhook secret references exist yet. Create one in <span className="font-medium text-foreground">Admin → Secret References</span>.
                  </p>
                ) : null}
              </div>
            ) : null}
            {draft.webhookSecretMode === "key_vault_secret" ? (
              <div className="space-y-1.5">
                <div className="flex items-center gap-1">
                  <Label htmlFor="endpoint-webhook-secret-key-vault-secret">Azure Key Vault secret name</Label>
                  <FieldHelpTooltip content="Name of the secret in MAPPO's Azure Key Vault that stores the webhook signing secret. Enter only the secret name, not the secret value." />
                </div>
                <Input
                  id="endpoint-webhook-secret-key-vault-secret"
                  value={draft.webhookSecretKeyVaultSecret}
                  onChange={(event) =>
                    setDraft((current) => ({
                      ...current,
                      webhookSecretKeyVaultSecret: event.target.value,
                    }))
                  }
                  placeholder={draft.provider === "azure_devops" ? "mappo-ado-webhook-secret" : "mappo-github-webhook-secret"}
                />
                <p className="text-xs text-muted-foreground">
                  MAPPO will resolve this as <span className="font-mono text-foreground">kv:{draft.webhookSecretKeyVaultSecret.trim() || "secret-name"}</span> using the Azure Key Vault configured on the backend runtime. To keep this linked to Admin → Secret References, choose <span className="font-medium text-foreground">Use secret reference</span> above instead.
                </p>
              </div>
            ) : null}

            <Accordion type="single" collapsible className="rounded-md border border-border/60 bg-background/30 px-3">
              <AccordionItem value="advanced-routing" className="border-none">
                <AccordionTrigger className="py-2 text-sm font-medium text-foreground hover:no-underline">
                          Optional routing filters
                </AccordionTrigger>
                <AccordionContent>
                  <p className="mb-3 text-xs text-muted-foreground">
                    Most operators can leave this empty. Use routing filters only when one release source accepts events from multiple repositories or pipelines and MAPPO must narrow which deliveries it trusts.
                  </p>
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
                </AccordionContent>
              </AccordionItem>
            </Accordion>
          </form>
          <DrawerFooter>
            <div className="flex w-full items-center justify-end gap-2">
              <DrawerClose asChild>
                <Button type="button" variant="outline">
                  Cancel
                </Button>
              </DrawerClose>
              <Button type="submit" form="release-ingest-endpoint-form" disabled={isSubmitting}>
                {isSubmitting ? "Saving..." : editingId ? "Update release source" : "Create release source"}
              </Button>
            </div>
          </DrawerFooter>
        </DrawerContent>
      </Drawer>
    </div>
  );
}
