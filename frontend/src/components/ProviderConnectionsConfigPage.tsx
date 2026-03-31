import { FormEvent, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { toast } from "sonner";

import FieldHelpTooltip from "@/components/FieldHelpTooltip";
import { Badge } from "@/components/ui/badge";
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
import {
  createProviderConnection,
  deleteProviderConnection,
  discoverProviderConnectionAdoProjects,
  listProviderConnections,
  patchProviderConnection,
  verifyProviderConnectionAdoProjects,
} from "@/lib/api";
import type {
  ProviderConnectionAdoProject,
  ProviderConnection,
  ProviderConnectionCreateRequest,
  ProviderConnectionPatchRequest,
  ProviderConnectionProvider,
  ProviderConnectionVerifyRequest,
} from "@/lib/types";

type ProviderConnectionsConfigPageProps = {
  selectedProjectId: string;
};

type ProviderConnectionDraft = {
  id: string;
  name: string;
  provider: ProviderConnectionProvider;
  enabled: boolean;
  organizationUrl: string;
  personalAccessTokenMode: "backend_default" | "environment_variable";
  personalAccessTokenEnvVar: string;
};

const DEFAULT_AZURE_DEVOPS_PAT_REF = "mappo.azure-devops.personal-access-token";

function emptyDraft(): ProviderConnectionDraft {
  return {
    id: "",
    name: "",
    provider: "azure_devops",
    enabled: true,
    organizationUrl: "",
    personalAccessTokenMode: "backend_default",
    personalAccessTokenEnvVar: "",
  };
}

function parsePersonalAccessTokenRef(
  value: string | undefined
): Pick<ProviderConnectionDraft, "personalAccessTokenMode" | "personalAccessTokenEnvVar"> {
  const normalized = normalize(value ?? "");
  if (normalized.startsWith("env:")) {
    return {
      personalAccessTokenMode: "environment_variable",
      personalAccessTokenEnvVar: normalize(normalized.slice("env:".length)),
    };
  }
  return {
    personalAccessTokenMode: "backend_default",
    personalAccessTokenEnvVar: "",
  };
}

function toDraft(connection: ProviderConnection): ProviderConnectionDraft {
  const tokenReference = parsePersonalAccessTokenRef(connection.personalAccessTokenRef);
  return {
    id: connection.id ?? "",
    name: connection.name ?? "",
    provider: (connection.provider ?? "azure_devops") as ProviderConnectionProvider,
    enabled: connection.enabled ?? true,
    organizationUrl: connection.organizationUrl ?? "",
    ...tokenReference,
  };
}

function normalize(value: string): string {
  return value.trim();
}

function maskedSecretRef(value: string): string {
  const normalized = normalize(value);
  if (normalized.startsWith("literal:")) {
    return "literal:(hidden)";
  }
  return normalized;
}

function buildPersonalAccessTokenRef(draft: ProviderConnectionDraft): string {
  if (draft.personalAccessTokenMode === "environment_variable") {
    const envVarName = normalize(draft.personalAccessTokenEnvVar);
    return envVarName === "" ? "" : `env:${envVarName}`;
  }
  return DEFAULT_AZURE_DEVOPS_PAT_REF;
}

function buildDraftSignature(draft: ProviderConnectionDraft): string {
  return [
    normalize(draft.provider),
    draft.enabled ? "enabled" : "disabled",
    normalize(draft.organizationUrl),
    normalize(buildPersonalAccessTokenRef(draft)),
  ].join("|");
}

function describePersonalAccessTokenSource(connection: ProviderConnection): string {
  const normalized = normalize(connection.personalAccessTokenRef ?? DEFAULT_AZURE_DEVOPS_PAT_REF);
  if (normalized === DEFAULT_AZURE_DEVOPS_PAT_REF) {
    return "Backend default PAT";
  }
  if (normalized.startsWith("env:")) {
    return `Environment variable (${normalize(normalized.slice("env:".length))})`;
  }
  return maskedSecretRef(normalized);
}

export default function ProviderConnectionsConfigPage({
  selectedProjectId,
}: ProviderConnectionsConfigPageProps) {
  const [connections, setConnections] = useState<ProviderConnection[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string>("");
  const [drawerOpen, setDrawerOpen] = useState<boolean>(false);
  const [editingId, setEditingId] = useState<string>("");
  const [draft, setDraft] = useState<ProviderConnectionDraft>(emptyDraft);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [isVerifyingDraft, setIsVerifyingDraft] = useState<boolean>(false);
  const [draftVerifiedSignature, setDraftVerifiedSignature] = useState<string>("");
  const [draftVerificationError, setDraftVerificationError] = useState<string>("");
  const [draftDiscoveredProjects, setDraftDiscoveredProjects] = useState<ProviderConnectionAdoProject[]>([]);
  const [draftNormalizedOrganizationUrl, setDraftNormalizedOrganizationUrl] = useState<string>("");
  const [discoveringConnectionIds, setDiscoveringConnectionIds] = useState<Record<string, boolean>>({});
  const [discoveredProjectsByConnectionId, setDiscoveredProjectsByConnectionId] = useState<
    Record<string, ProviderConnectionAdoProject[]>
  >({});
  const [discoveryErrorsByConnectionId, setDiscoveryErrorsByConnectionId] = useState<Record<string, string>>({});
  const [verifiedConnectionIds, setVerifiedConnectionIds] = useState<Record<string, boolean>>({});
  const autoDiscoveredConnectionSignaturesRef = useRef<Record<string, string>>({});

  const loadConnections = useCallback(async (refresh = false) => {
    if (refresh) {
      setIsRefreshing(true);
    } else {
      setIsLoading(true);
    }
    try {
      const payload = await listProviderConnections();
      setConnections(payload);
      setErrorMessage("");
    } catch (error) {
      setErrorMessage((error as Error).message);
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void loadConnections();
  }, [loadConnections]);

  const sortedConnections = useMemo(() => {
    return [...connections].sort((left, right) => {
      const leftName = `${left.name ?? left.id ?? ""}`.toLowerCase();
      const rightName = `${right.name ?? right.id ?? ""}`.toLowerCase();
      if (leftName !== rightName) {
        return leftName.localeCompare(rightName);
      }
      return `${left.id ?? ""}`.localeCompare(`${right.id ?? ""}`);
    });
  }, [connections]);

  useEffect(() => {
    const eligibleConnections = sortedConnections.filter((connection) => {
      const connectionId = normalize(connection.id ?? "");
      if (connectionId === "") {
        return false;
      }
      if ((connection.provider ?? "").toLowerCase() !== "azure_devops") {
        return false;
      }
      if (!(connection.enabled ?? true)) {
        return false;
      }
      if (normalize(connection.organizationUrl ?? "") === "") {
        return false;
      }
      const signature = [
        normalize(connection.organizationUrl ?? ""),
        normalize(connection.personalAccessTokenRef ?? DEFAULT_AZURE_DEVOPS_PAT_REF),
        String(connection.enabled ?? true),
      ].join("|");
      if (autoDiscoveredConnectionSignaturesRef.current[connectionId] === signature) {
        return false;
      }
      autoDiscoveredConnectionSignaturesRef.current[connectionId] = signature;
      return true;
    });

    if (eligibleConnections.length === 0) {
      return;
    }

    void (async () => {
      for (const connection of eligibleConnections) {
        await handleDiscoverProjects(connection, { silent: true });
      }
    })();
  }, [sortedConnections]);

  function openCreateDrawer(): void {
    setEditingId("");
    setDraft(emptyDraft());
    setDraftVerifiedSignature("");
    setDraftVerificationError("");
    setDraftDiscoveredProjects([]);
    setDraftNormalizedOrganizationUrl("");
    setDrawerOpen(true);
  }

  function openEditDrawer(connection: ProviderConnection): void {
    setEditingId(connection.id ?? "");
    const nextDraft = toDraft(connection);
    const connectionId = normalize(connection.id ?? "");
    setDraft(nextDraft);
    const alreadyVerified = Boolean(verifiedConnectionIds[connectionId]);
    setDraftVerifiedSignature(alreadyVerified ? buildDraftSignature(nextDraft) : "");
    setDraftVerificationError(discoveryErrorsByConnectionId[connectionId] ?? "");
    setDraftDiscoveredProjects(discoveredProjectsByConnectionId[connectionId] ?? []);
    setDraftNormalizedOrganizationUrl(normalize(connection.organizationUrl ?? ""));
    setDrawerOpen(true);
  }

  useEffect(() => {
    if (!drawerOpen) {
      return;
    }
    const currentSignature = buildDraftSignature(draft);
    if (draftVerifiedSignature !== "" && currentSignature === draftVerifiedSignature) {
      return;
    }
    if (
      draftVerifiedSignature === "" &&
      draftVerificationError === "" &&
      draftDiscoveredProjects.length === 0 &&
      draftNormalizedOrganizationUrl === ""
    ) {
      return;
    }
    setDraftVerificationError("");
    setDraftDiscoveredProjects([]);
    setDraftNormalizedOrganizationUrl("");
    if (draftVerifiedSignature !== "") {
      setDraftVerifiedSignature("");
    }
  }, [
    drawerOpen,
    draft,
    draftDiscoveredProjects.length,
    draftNormalizedOrganizationUrl,
    draftVerificationError,
    draftVerifiedSignature,
  ]);

  async function handleVerifyDraft(): Promise<void> {
    const request: ProviderConnectionVerifyRequest = {
      id: normalize(draft.id) || undefined,
      provider: draft.provider,
      organizationUrl: normalize(draft.organizationUrl) || undefined,
      personalAccessTokenRef: normalize(buildPersonalAccessTokenRef(draft)) || undefined,
    };
    setIsVerifyingDraft(true);
    setDraftVerificationError("");
    try {
      const response = await verifyProviderConnectionAdoProjects(request);
      const projects = [...(response.projects ?? [])].sort((left, right) =>
        `${left.name ?? ""}`.localeCompare(`${right.name ?? ""}`, undefined, { sensitivity: "base" })
      );
      const normalizedOrganizationUrl = normalize(response.organizationUrl ?? "");
      setDraft((current) =>
        normalizedOrganizationUrl !== "" && current.organizationUrl.trim() !== normalizedOrganizationUrl
          ? { ...current, organizationUrl: normalizedOrganizationUrl }
          : current
      );
      setDraftNormalizedOrganizationUrl(normalizedOrganizationUrl);
      setDraftDiscoveredProjects(projects);
      const verifiedSignature = buildDraftSignature({
        ...draft,
        organizationUrl: normalizedOrganizationUrl || draft.organizationUrl,
      });
      setDraftVerifiedSignature(verifiedSignature);
      toast.success(
        `Verified Azure DevOps access. MAPPO found ${projects.length} project${projects.length === 1 ? "" : "s"}.`
      );
    } catch (error) {
      const message = (error as Error).message;
      setDraftVerificationError(message);
      toast.error(message);
    } finally {
      setIsVerifyingDraft(false);
    }
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    const connectionId = normalize(draft.id);
    const name = normalize(draft.name);
    const organizationUrl = normalize(draft.organizationUrl);
    if (connectionId === "" || name === "") {
      toast.error("Connection ID and name are required.");
      return;
    }

    const personalAccessTokenRef = normalize(buildPersonalAccessTokenRef(draft));
    if (draft.personalAccessTokenMode === "environment_variable" && personalAccessTokenRef === "") {
      toast.error("Environment variable name is required when Credential source is Environment variable.");
      return;
    }

    setIsSubmitting(true);
    try {
      let savedConnection: ProviderConnection;
      if (editingId) {
        const patchRequest: ProviderConnectionPatchRequest = {
          name,
          provider: draft.provider,
          enabled: draft.enabled,
          organizationUrl: organizationUrl || undefined,
          personalAccessTokenRef: personalAccessTokenRef || undefined,
        };
        savedConnection = await patchProviderConnection(editingId, patchRequest);
        toast.success(`Updated provider connection ${editingId}.`);
      } else {
        const createRequest: ProviderConnectionCreateRequest = {
          id: connectionId,
          name,
          provider: draft.provider,
          enabled: draft.enabled,
          organizationUrl: organizationUrl || undefined,
          personalAccessTokenRef: personalAccessTokenRef || undefined,
        };
        savedConnection = await createProviderConnection(createRequest);
        toast.success(`Created provider connection ${connectionId}.`);
      }
      setDiscoveredProjectsByConnectionId((current) => ({
        ...current,
        [savedConnection.id ?? connectionId]: [],
      }));
      setDiscoveryErrorsByConnectionId((current) => ({
        ...current,
        [savedConnection.id ?? connectionId]: "",
      }));
      setVerifiedConnectionIds((current) => ({
        ...current,
        [savedConnection.id ?? connectionId]: false,
      }));
      autoDiscoveredConnectionSignaturesRef.current[savedConnection.id ?? connectionId] = "";
      setDrawerOpen(false);
      await loadConnections(true);
      if ((savedConnection.provider ?? "").toLowerCase() === "azure_devops" && (savedConnection.enabled ?? true)) {
        await handleDiscoverProjects(savedConnection);
      }
    } catch (error) {
      toast.error((error as Error).message);
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleDiscoverProjects(
    connection: ProviderConnection,
    options?: { silent?: boolean }
  ): Promise<void> {
    const connectionId = normalize(connection.id ?? "");
    if (connectionId === "") {
      return;
    }
    setDiscoveringConnectionIds((current) => ({ ...current, [connectionId]: true }));
    setDiscoveryErrorsByConnectionId((current) => ({ ...current, [connectionId]: "" }));
    try {
      const response = await discoverProviderConnectionAdoProjects(connectionId);
      const projects = [...(response.projects ?? [])].sort((left, right) =>
        `${left.name ?? ""}`.localeCompare(`${right.name ?? ""}`, undefined, { sensitivity: "base" })
      );
      setDiscoveredProjectsByConnectionId((current) => ({ ...current, [connectionId]: projects }));
      setVerifiedConnectionIds((current) => ({ ...current, [connectionId]: true }));
      if (!options?.silent) {
        toast.success(
          `Verified ${connection.name || connectionId}. Found ${projects.length} Azure DevOps project${projects.length === 1 ? "" : "s"}.`
        );
      }
    } catch (error) {
      const message = (error as Error).message;
      setDiscoveryErrorsByConnectionId((current) => ({ ...current, [connectionId]: message }));
      setVerifiedConnectionIds((current) => ({ ...current, [connectionId]: false }));
      if (!options?.silent) {
        toast.error(message);
      }
    } finally {
      setDiscoveringConnectionIds((current) => ({ ...current, [connectionId]: false }));
    }
  }

  async function handleDelete(connection: ProviderConnection): Promise<void> {
    const connectionId = normalize(connection.id ?? "");
    if (connectionId === "") {
      return;
    }
    const confirmed = window.confirm(
      `Delete provider connection ${connectionId}? This fails if projects are still linked.`
    );
    if (!confirmed) {
      return;
    }
    try {
      await deleteProviderConnection(connectionId);
      toast.success(`Deleted provider connection ${connectionId}.`);
      await loadConnections(true);
    } catch (error) {
      toast.error((error as Error).message);
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex animate-fade-up items-center justify-between [animation-delay:60ms] [animation-fill-mode:forwards]">
        <p className="text-xs text-muted-foreground">
          Configure provider API credentials used by deployment drivers. Saving verifies Azure DevOps access by enumerating reachable projects.
        </p>
        <div className="flex items-center gap-2">
          <Button type="button" variant="outline" onClick={() => void loadConnections(true)}>
            {isRefreshing ? "Refreshing..." : "Refresh"}
          </Button>
          <Button type="button" onClick={openCreateDrawer}>
            New Provider Connection
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
          <CardTitle>Provider Connections</CardTitle>
          <span className="rounded-full border border-border/70 px-3 py-1 font-mono text-[11px] text-muted-foreground">
            {sortedConnections.length} total
          </span>
        </CardHeader>
        <CardContent className="space-y-3">
          {isLoading ? (
            <p className="text-sm text-muted-foreground">Loading provider connections...</p>
          ) : null}
          {!isLoading && sortedConnections.length === 0 ? (
            <p className="text-sm text-muted-foreground">No provider connections configured yet.</p>
          ) : null}
          {sortedConnections.map((connection) => {
            const connectionId = normalize(connection.id ?? "");
            const linkedProjects = connection.linkedProjects ?? [];
            const selectedProjectLinked = linkedProjects.some(
              (linked) => normalize(linked.projectId ?? "") === selectedProjectId
            );
            const isDiscovering = Boolean(discoveringConnectionIds[connectionId]);
            const hasDiscoveryAttempt = Object.prototype.hasOwnProperty.call(
              discoveredProjectsByConnectionId,
              connectionId
            );
            const isVerified = Boolean(verifiedConnectionIds[connectionId]);
            return (
              <div key={connectionId || connection.name} className="rounded-md border border-border/70 bg-card/70 p-3">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <p className="text-sm font-semibold text-primary">
                      {connection.name || connectionId}
                    </p>
                    <p className="font-mono text-[11px] text-muted-foreground">{connectionId}</p>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="rounded-full border border-primary/40 px-2 py-0.5 text-[11px] uppercase tracking-[0.06em] text-primary">
                      {(connection.provider ?? "azure_devops").replaceAll("_", " ")}
                    </span>
                    <Badge variant={connection.enabled ? "default" : "secondary"}>
                      {connection.enabled ? "Enabled" : "Disabled"}
                    </Badge>
                    {isDiscovering ? (
                      <Badge variant="secondary">Verifying...</Badge>
                    ) : isVerified ? (
                      <Badge variant="default">Verified</Badge>
                    ) : discoveryErrorsByConnectionId[connectionId] ? (
                      <Badge variant="destructive">Verification failed</Badge>
                    ) : null}
                  </div>
                </div>
                <div className="mt-2 grid gap-2 text-xs text-muted-foreground md:grid-cols-2">
                  <p>
                    Normalized Azure DevOps organization:{" "}
                    <span className="font-mono text-foreground">
                      {connection.organizationUrl?.trim() || "not configured"}
                    </span>
                  </p>
                  <p>
                    Credential source: <span className="font-medium text-foreground">{describePersonalAccessTokenSource(connection)}</span>
                  </p>
                </div>
                <div className="mt-2 flex flex-wrap items-center gap-2">
                  <span className="text-xs text-muted-foreground">Linked projects:</span>
                  {linkedProjects.length === 0 ? (
                    <Badge variant="secondary">none</Badge>
                  ) : (
                    linkedProjects.map((linked) => {
                      const projectId = linked.projectId ?? "unknown";
                      const label = linked.projectDisplayName || linked.projectName || projectId;
                      return (
                        <Badge
                          key={`${connectionId}-${projectId}`}
                          variant={selectedProjectLinked && projectId === selectedProjectId ? "default" : "secondary"}
                        >
                          {label}
                        </Badge>
                      );
                    })
                  )}
                </div>
                {hasDiscoveryAttempt ? (
                  <div className="mt-2 flex flex-wrap items-center gap-2">
                    <span className="text-xs text-muted-foreground">Reachable Azure DevOps projects:</span>
                    {discoveredProjectsByConnectionId[connectionId]?.length ? (
                      discoveredProjectsByConnectionId[connectionId].map((project) => (
                        <Badge key={`${connectionId}-${project.id}`} variant="secondary">
                          {project.name}
                        </Badge>
                      ))
                    ) : (
                      <Badge variant="secondary">none returned</Badge>
                    )}
                  </div>
                ) : null}
                {discoveryErrorsByConnectionId[connectionId] ? (
                  <p className="mt-2 rounded-md border border-amber-400/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-100">
                    {discoveryErrorsByConnectionId[connectionId]}
                  </p>
                ) : null}
                <div className="mt-3 flex flex-wrap items-center gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => {
                      void handleDiscoverProjects(connection);
                    }}
                    disabled={connectionId === "" || isDiscovering}
                  >
                    {isDiscovering ? "Verifying..." : hasDiscoveryAttempt ? "Refresh Projects" : "Verify & Discover Projects"}
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => openEditDrawer(connection)}
                    disabled={connectionId === ""}
                  >
                    Edit
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    className="border-destructive/60 text-destructive hover:bg-destructive/10 hover:text-destructive"
                    onClick={() => {
                      void handleDelete(connection);
                    }}
                    disabled={connectionId === ""}
                  >
                    Delete
                  </Button>
                </div>
              </div>
            );
          })}
        </CardContent>
      </Card>

      <Drawer direction="top" open={drawerOpen} onOpenChange={setDrawerOpen}>
        <DrawerContent className="glass-card">
          <DrawerHeader>
            <DrawerTitle>{editingId ? `Edit ${editingId}` : "New Provider Connection"}</DrawerTitle>
            <DrawerDescription>
              Configure provider API auth used by linked project deployment drivers.
            </DrawerDescription>
          </DrawerHeader>
          <div className="max-h-[72vh] overflow-y-auto px-4 pb-2">
            <form id="provider-connection-form" className="grid grid-cols-1 gap-3 sm:grid-cols-2" onSubmit={handleSubmit}>
              <div className="space-y-1">
                <Label htmlFor="provider-connection-id">Connection ID</Label>
                <Input
                  id="provider-connection-id"
                  value={draft.id}
                  onChange={(event) => setDraft((current) => ({ ...current, id: event.target.value }))}
                  disabled={Boolean(editingId)}
                  placeholder="ado-default"
                  required
                />
              </div>
              <div className="space-y-1">
                <Label htmlFor="provider-connection-name">Name</Label>
                <Input
                  id="provider-connection-name"
                  value={draft.name}
                  onChange={(event) => setDraft((current) => ({ ...current, name: event.target.value }))}
                  placeholder="Azure DevOps Default"
                  required
                />
              </div>
              <div className="space-y-1">
                <div className="flex items-center gap-1">
                  <Label htmlFor="provider-connection-provider">Provider</Label>
                  <FieldHelpTooltip content="External system this API credential belongs to." />
                </div>
                <Select
                  value={draft.provider}
                  onValueChange={(value) =>
                    setDraft((current) => ({
                      ...current,
                      provider: value as ProviderConnectionProvider,
                      personalAccessTokenMode: "backend_default",
                      personalAccessTokenEnvVar: "",
                    }))
                  }
                >
                  <SelectTrigger id="provider-connection-provider">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="azure_devops">Azure DevOps</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1">
                <Label htmlFor="provider-connection-enabled">Enabled</Label>
                <Select
                  value={draft.enabled ? "enabled" : "disabled"}
                  onValueChange={(value) =>
                    setDraft((current) => ({ ...current, enabled: value === "enabled" }))
                  }
                >
                  <SelectTrigger id="provider-connection-enabled">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="enabled">Enabled</SelectItem>
                    <SelectItem value="disabled">Disabled</SelectItem>
                  </SelectContent>
                </Select>
              </div>
              <div className="space-y-1 sm:col-span-2">
                <div className="flex items-center gap-1">
                  <Label htmlFor="provider-connection-organization-url">Azure DevOps URL</Label>
                  <FieldHelpTooltip content="Paste any Azure DevOps organization, project, or repo URL. MAPPO normalizes it to the organization root and uses that to enumerate Azure DevOps projects after save." />
                </div>
                <Input
                  id="provider-connection-organization-url"
                  value={draft.organizationUrl}
                  onChange={(event) =>
                    setDraft((current) => ({ ...current, organizationUrl: event.target.value }))
                  }
                  placeholder="https://dev.azure.com/pg123/demo-app-service"
                />
              </div>
              <div className="space-y-1 sm:col-span-2">
                <div className="flex items-center gap-1">
                  <Label htmlFor="provider-connection-pat-mode">Credential source</Label>
                  <FieldHelpTooltip content="How MAPPO resolves the Azure DevOps PAT for this Provider Connection. Saving verifies the selected source by calling Azure DevOps project discovery." />
                </div>
                <Select
                  value={draft.personalAccessTokenMode}
                  onValueChange={(value) =>
                    setDraft((current) => ({
                      ...current,
                      personalAccessTokenMode: value as ProviderConnectionDraft["personalAccessTokenMode"],
                      personalAccessTokenEnvVar:
                        value === "backend_default" ? "" : current.personalAccessTokenEnvVar,
                    }))
                  }
                >
                  <SelectTrigger id="provider-connection-pat-mode">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    <SelectItem value="backend_default">Backend default PAT</SelectItem>
                    <SelectItem value="environment_variable">Environment variable</SelectItem>
                  </SelectContent>
                </Select>
                {draft.personalAccessTokenMode === "backend_default" ? (
                  <p className="text-xs text-muted-foreground">
                    MAPPO will resolve the backend secret key{" "}
                    <span className="font-mono text-foreground">{DEFAULT_AZURE_DEVOPS_PAT_REF}</span>.
                  </p>
                ) : null}
              </div>
              {draft.personalAccessTokenMode === "environment_variable" ? (
                <div className="space-y-1 sm:col-span-2">
                  <div className="flex items-center gap-1">
                    <Label htmlFor="provider-connection-pat-env-var">Environment variable name</Label>
                    <FieldHelpTooltip content="Name of the environment variable on the MAPPO backend runtime that contains the Azure DevOps PAT." />
                  </div>
                  <Input
                    id="provider-connection-pat-env-var"
                    value={draft.personalAccessTokenEnvVar}
                    onChange={(event) =>
                      setDraft((current) => ({ ...current, personalAccessTokenEnvVar: event.target.value }))
                    }
                    placeholder="MAPPO_AZURE_DEVOPS_PERSONAL_ACCESS_TOKEN"
                  />
                </div>
              ) : null}
              <div className="rounded-md border border-border/60 bg-background/40 p-3 text-xs text-muted-foreground sm:col-span-2">
                <p className="font-medium text-foreground">Verify before save</p>
                <p className="mt-1">
                  Use <span className="font-medium text-foreground">Verify connection</span> to confirm that MAPPO can resolve the configured PAT source, normalize the Azure DevOps URL, and enumerate reachable Azure DevOps projects before persisting this connection.
                </p>
                {draftNormalizedOrganizationUrl ? (
                  <p className="mt-2">
                    Normalized Azure DevOps organization:{" "}
                    <span className="font-mono text-foreground">{draftNormalizedOrganizationUrl}</span>
                  </p>
                ) : null}
                {draftDiscoveredProjects.length > 0 ? (
                  <div className="mt-3 flex flex-wrap items-center gap-2">
                    <span className="text-xs text-muted-foreground">Reachable Azure DevOps projects:</span>
                    {draftDiscoveredProjects.map((project) => (
                      <Badge key={`draft-${project.id}`} variant="secondary">
                        {project.name}
                      </Badge>
                    ))}
                  </div>
                ) : null}
                {draftVerificationError ? (
                  <p className="mt-2 rounded-md border border-amber-400/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-100">
                    {draftVerificationError}
                  </p>
                ) : null}
                {draftVerifiedSignature !== "" ? (
                  <p className="mt-2 text-emerald-300">
                    Verification passed for the current draft. Save will re-run verification before persisting.
                  </p>
                ) : null}
              </div>
            </form>
          </div>
          <DrawerFooter className="flex-row justify-end gap-2">
            <DrawerClose asChild>
              <Button type="button" variant="outline">
                Cancel
              </Button>
            </DrawerClose>
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                void handleVerifyDraft();
              }}
              disabled={isSubmitting || isVerifyingDraft}
            >
              {isVerifyingDraft ? "Verifying..." : "Verify connection"}
            </Button>
            <Button
              form="provider-connection-form"
              type="submit"
              disabled={isSubmitting || isVerifyingDraft}
            >
              {isSubmitting ? "Saving..." : editingId ? "Update connection" : "Create connection"}
            </Button>
          </DrawerFooter>
        </DrawerContent>
      </Drawer>
    </div>
  );
}
