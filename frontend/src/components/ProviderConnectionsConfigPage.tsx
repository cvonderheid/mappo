import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
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

const AZURE_DEVOPS_SCOPE_REQUIRED_MESSAGE =
  "Paste an Azure DevOps project URL from the Azure DevOps account this deployment connection should use, then verify the connection so MAPPO can load reachable Azure DevOps projects. Repository URLs also work.";

function normalizeDeploymentConnectionError(message: string): string {
  const trimmed = normalize(
    message
      .replace(/^discoverProviderConnectionAdoProjects failed \(\d+\):\s*/i, "")
      .replace(/^verifyProviderConnectionAdoProjects failed \(\d+\):\s*/i, "")
      .replace(/^patchProviderConnection failed \(\d+\):\s*/i, "")
      .replace(/^createProviderConnection failed \(\d+\):\s*/i, "")
  );
  const normalized = trimmed.toLowerCase();
  if (
    normalized.includes("project or repo url is required")
    || normalized.includes("azure devops url is required")
  ) {
    return AZURE_DEVOPS_SCOPE_REQUIRED_MESSAGE;
  }
  if (normalized.includes("pat could not be resolved")) {
    return "MAPPO could not resolve the Azure DevOps API credential for this deployment connection. Use the MAPPO backend secret or choose a backend environment variable that actually exists, then verify the connection again.";
  }
  if (normalized.includes("no accessible azure devops projects were returned")) {
    return "MAPPO authenticated to Azure DevOps, but that credential could not see any Azure DevOps projects in the selected account. Confirm the Azure DevOps project URL is correct and that the credential can read at least one project.";
  }
  return trimmed;
}

function deriveAzureDevOpsAccountUrl(value: string): string {
  const normalized = normalize(value);
  if (normalized === "") {
    return "";
  }
  try {
    const parsed = new URL(normalized);
    const host = parsed.hostname.toLowerCase();
    if (host === "dev.azure.com") {
      const [organization] = parsed.pathname.split("/").filter(Boolean);
      return organization ? `${parsed.protocol}//${parsed.host}/${organization}` : "";
    }
    if (host.endsWith(".visualstudio.com")) {
      return `${parsed.protocol}//${parsed.host}`;
    }
    return "";
  } catch {
    return "";
  }
}

function resolveConnectionAccountUrl(connection: ProviderConnection | null | undefined): string {
  const persisted = normalize(connection?.organizationUrl ?? "");
  if (persisted !== "") {
    return deriveAzureDevOpsAccountUrl(persisted) || persisted;
  }
  for (const project of connection?.discoveredProjects ?? []) {
    const derived = deriveAzureDevOpsAccountUrl(project.webUrl ?? "");
    if (derived !== "") {
      return derived;
    }
  }
  return "";
}

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
    organizationUrl: resolveConnectionAccountUrl(connection) || (connection.organizationUrl ?? ""),
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

function isUsableAzureDevOpsConnection(connection: ProviderConnection | null | undefined): boolean {
  return (
    (connection?.provider ?? "").toLowerCase() === "azure_devops"
    && (connection?.enabled ?? true)
    && resolveConnectionAccountUrl(connection) !== ""
    && (connection?.discoveredProjects?.length ?? 0) > 0
  );
}

function buildPersonalAccessTokenRef(draft: ProviderConnectionDraft): string {
  if (draft.personalAccessTokenMode === "environment_variable") {
    const envVarName = normalize(draft.personalAccessTokenEnvVar);
    return envVarName === "" ? "" : `env:${envVarName}`;
  }
  return DEFAULT_AZURE_DEVOPS_PAT_REF;
}

function isAzureDevOpsScopeMissing(draft: ProviderConnectionDraft): boolean {
  return draft.provider === "azure_devops" && draft.enabled && normalize(draft.organizationUrl) === "";
}

function buildDraftSignature(draft: ProviderConnectionDraft): string {
  return [
    normalize(draft.provider),
    draft.enabled ? "enabled" : "disabled",
    normalize(draft.organizationUrl),
    normalize(buildPersonalAccessTokenRef(draft)),
  ].join("|");
}

function deriveDraftAccountUrl(draft: ProviderConnectionDraft): string {
  return deriveAzureDevOpsAccountUrl(normalize(draft.organizationUrl));
}

function describePersonalAccessTokenSource(connection: ProviderConnection): string {
  const normalized = normalize(connection.personalAccessTokenRef ?? DEFAULT_AZURE_DEVOPS_PAT_REF);
  if (normalized === DEFAULT_AZURE_DEVOPS_PAT_REF) {
    return "MAPPO runtime secret";
  }
  if (normalized.startsWith("env:")) {
    return `Environment variable (${normalize(normalized.slice("env:".length))})`;
  }
  return maskedSecretRef(normalized);
}

type DraftVerificationResult = {
  effectiveDraft: ProviderConnectionDraft;
  projects: ProviderConnectionAdoProject[];
  normalizedOrganizationUrl: string;
  verifiedSignature: string;
};

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

  const loadConnections = useCallback(async (refresh = false) => {
    if (refresh) {
      setIsRefreshing(true);
    } else {
      setIsLoading(true);
    }
    try {
      const payload = await listProviderConnections();
      setConnections(payload);
      setDiscoveredProjectsByConnectionId(() => {
        const next: Record<string, ProviderConnectionAdoProject[]> = {};
        for (const connection of payload) {
          const connectionId = normalize(connection.id ?? "");
          if (connectionId === "") {
            continue;
          }
          next[connectionId] = [...(connection.discoveredProjects ?? [])];
        }
        return next;
      });
      setVerifiedConnectionIds(() => {
        const next: Record<string, boolean> = {};
        for (const connection of payload) {
          const connectionId = normalize(connection.id ?? "");
          if (connectionId === "") {
            continue;
          }
          next[connectionId] = isUsableAzureDevOpsConnection(connection);
        }
        return next;
      });
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
    setDraftDiscoveredProjects(discoveredProjectsByConnectionId[connectionId] ?? connection.discoveredProjects ?? []);
    setDraftNormalizedOrganizationUrl(resolveConnectionAccountUrl(connection));
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

  async function verifyDraft(): Promise<DraftVerificationResult> {
    if (isAzureDevOpsScopeMissing(draft)) {
      throw new Error(AZURE_DEVOPS_SCOPE_REQUIRED_MESSAGE);
    }
    const request: ProviderConnectionVerifyRequest = {
      id: normalize(draft.id) || undefined,
      provider: draft.provider,
      organizationUrl: normalize(draft.organizationUrl) || undefined,
      personalAccessTokenRef: normalize(buildPersonalAccessTokenRef(draft)) || undefined,
    };
    const response = await verifyProviderConnectionAdoProjects(request);
    const projects = [...(response.projects ?? [])].sort((left, right) =>
      `${left.name ?? ""}`.localeCompare(`${right.name ?? ""}`, undefined, { sensitivity: "base" })
    );
    if (projects.length === 0) {
      throw new Error(
        "MAPPO authenticated to Azure DevOps, but no accessible Azure DevOps projects were returned. Confirm the URL points to the correct Azure DevOps account and that the PAT can read at least one Azure DevOps project."
      );
    }
    const normalizedOrganizationUrl = normalize(response.organizationUrl ?? "");
    const effectiveDraft = {
      ...draft,
      organizationUrl: normalizedOrganizationUrl || draft.organizationUrl,
    };
    return {
      effectiveDraft,
      projects,
      normalizedOrganizationUrl,
      verifiedSignature: buildDraftSignature(effectiveDraft),
    };
  }

  function applyVerificationResult(result: DraftVerificationResult): void {
    setDraft(result.effectiveDraft);
    setDraftNormalizedOrganizationUrl(result.normalizedOrganizationUrl);
    setDraftDiscoveredProjects(result.projects);
    setDraftVerifiedSignature(result.verifiedSignature);
    setDraftVerificationError("");
  }

  async function handleVerifyDraft(): Promise<void> {
    setIsVerifyingDraft(true);
    setDraftVerificationError("");
    try {
      const result = await verifyDraft();
      applyVerificationResult(result);
      toast.success(
        `Verified Azure DevOps access. MAPPO found ${result.projects.length} project${result.projects.length === 1 ? "" : "s"}.`
      );
    } catch (error) {
      const message = normalizeDeploymentConnectionError((error as Error).message);
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
      toast.error("Environment variable name is required when API credential source is Environment variable.");
      return;
    }
    if (isAzureDevOpsScopeMissing(draft)) {
      setDraftVerificationError(AZURE_DEVOPS_SCOPE_REQUIRED_MESSAGE);
      toast.error(AZURE_DEVOPS_SCOPE_REQUIRED_MESSAGE);
      return;
    }

    setIsSubmitting(true);
    try {
      let effectiveDraft = draft;
      let verifiedProjects: ProviderConnectionAdoProject[] = [];
      if (draft.provider === "azure_devops" && draft.enabled) {
        const verification = await verifyDraft();
        applyVerificationResult(verification);
        effectiveDraft = verification.effectiveDraft;
        verifiedProjects = verification.projects;
      }

      let savedConnection: ProviderConnection;
      if (editingId) {
        const patchRequest: ProviderConnectionPatchRequest = {
          name,
          provider: effectiveDraft.provider,
          enabled: effectiveDraft.enabled,
          organizationUrl: normalize(effectiveDraft.organizationUrl) || undefined,
          personalAccessTokenRef: normalize(buildPersonalAccessTokenRef(effectiveDraft)) || undefined,
        };
        savedConnection = await patchProviderConnection(editingId, patchRequest);
        toast.success(`Updated deployment connection ${editingId}.`);
      } else {
        const createRequest: ProviderConnectionCreateRequest = {
          id: connectionId,
          name,
          provider: effectiveDraft.provider,
          enabled: effectiveDraft.enabled,
          organizationUrl: normalize(effectiveDraft.organizationUrl) || undefined,
          personalAccessTokenRef: normalize(buildPersonalAccessTokenRef(effectiveDraft)) || undefined,
        };
        savedConnection = await createProviderConnection(createRequest);
        toast.success(`Created deployment connection ${connectionId}.`);
      }
      setDiscoveredProjectsByConnectionId((current) => ({
        ...current,
        [savedConnection.id ?? connectionId]: verifiedProjects,
      }));
      setDiscoveryErrorsByConnectionId((current) => ({
        ...current,
        [savedConnection.id ?? connectionId]: "",
      }));
      setVerifiedConnectionIds((current) => ({
        ...current,
        [savedConnection.id ?? connectionId]: verifiedProjects.length > 0,
      }));
      setDrawerOpen(false);
      await loadConnections(true);
    } catch (error) {
      const message = normalizeDeploymentConnectionError((error as Error).message);
      setDraftVerificationError(message);
      toast.error(message);
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
      if (projects.length === 0) {
        throw new Error(
          "MAPPO authenticated to Azure DevOps, but no accessible Azure DevOps projects were returned. Confirm the URL points to the correct Azure DevOps account and that the PAT can read at least one Azure DevOps project."
        );
      }
      setDiscoveredProjectsByConnectionId((current) => ({ ...current, [connectionId]: projects }));
      setVerifiedConnectionIds((current) => ({ ...current, [connectionId]: true }));
      if (!options?.silent) {
        toast.success(
          `Verified ${connection.name || connectionId}. Found ${projects.length} Azure DevOps project${projects.length === 1 ? "" : "s"}.`
        );
      }
    } catch (error) {
      const message = normalizeDeploymentConnectionError((error as Error).message);
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
      `Delete deployment connection ${connectionId}? This fails if projects are still linked.`
    );
    if (!confirmed) {
      return;
    }
    try {
      await deleteProviderConnection(connectionId);
      toast.success(`Deleted deployment connection ${connectionId}.`);
      await loadConnections(true);
    } catch (error) {
      toast.error((error as Error).message);
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex animate-fade-up items-center justify-between [animation-delay:60ms] [animation-fill-mode:forwards]">
        <p className="text-xs text-muted-foreground">
          Configure authenticated deployment systems MAPPO can call. For Azure DevOps, MAPPO verifies the API access, confirms the Azure DevOps account, and loads reachable Azure DevOps projects before operators use the connection in Project Config.
        </p>
        <div className="flex items-center gap-2">
          <Button type="button" variant="outline" onClick={() => void loadConnections(true)}>
            {isRefreshing ? "Reloading..." : "Reload"}
          </Button>
          <Button type="button" onClick={openCreateDrawer}>
            New Deployment Connection
          </Button>
        </div>
      </div>

      {errorMessage ? (
        <div className="rounded-md border border-destructive/60 bg-destructive/10 p-2 text-xs text-destructive-foreground">
          {errorMessage}
        </div>
      ) : null}

      <Card className="glass-card animate-fade-up [animation-delay:100ms] [animation-fill-mode:forwards]">
        <CardHeader>
          <CardTitle>Deployment Connections</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          {isLoading ? (
            <p className="text-sm text-muted-foreground">Loading deployment connections...</p>
          ) : null}
          {!isLoading && sortedConnections.length === 0 ? (
            <p className="text-sm text-muted-foreground">No deployment connections configured yet.</p>
          ) : null}
          {sortedConnections.map((connection) => {
            const connectionId = normalize(connection.id ?? "");
            const linkedProjects = connection.linkedProjects ?? [];
            const selectedProjectLinked = linkedProjects.some(
              (linked) => normalize(linked.projectId ?? "") === selectedProjectId
            );
            const isDiscovering = Boolean(discoveringConnectionIds[connectionId]);
            const isVerified = Boolean(verifiedConnectionIds[connectionId]);
            const discoveredProjects = discoveredProjectsByConnectionId[connectionId] ?? connection.discoveredProjects ?? [];
            const hasDiscoveryState =
              Object.prototype.hasOwnProperty.call(discoveredProjectsByConnectionId, connectionId)
              || discoveredProjects.length > 0
              || isVerified
              || Boolean(discoveryErrorsByConnectionId[connectionId]);
            const resolvedAccountUrl = resolveConnectionAccountUrl(connection);
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
                    Azure DevOps account:{" "}
                    <span className="font-mono text-foreground">
                      {resolvedAccountUrl || "not configured"}
                    </span>
                  </p>
                  <p>
                    Azure DevOps API access: <span className="font-medium text-foreground">{describePersonalAccessTokenSource(connection)}</span>
                  </p>
                </div>
                {resolvedAccountUrl === "" ? (
                  <p className="mt-2 rounded-md border border-amber-400/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-100">
                    This deployment connection still needs verification. Edit it, paste any Azure DevOps project or repository URL from the correct Azure DevOps account, then verify the connection.
                  </p>
                ) : null}
                <div className="mt-2 flex flex-wrap items-center gap-2">
                  <span className="text-xs text-muted-foreground">MAPPO projects using this connection:</span>
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
                {hasDiscoveryState ? (
                  <div className="mt-2 flex flex-wrap items-center gap-2">
                    <span className="text-xs text-muted-foreground">Accessible Azure DevOps projects:</span>
                    {discoveredProjects.length ? (
                      discoveredProjects.map((project) => (
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
                    {isDiscovering ? "Verifying..." : hasDiscoveryState ? "Re-verify connection" : "Verify connection"}
                  </Button>
                  {discoveredProjects.length > 0 ? (
                    <span className="text-xs text-muted-foreground">
                      {discoveredProjects.length} accessible Azure DevOps project{discoveredProjects.length === 1 ? "" : "s"} loaded.
                    </span>
                  ) : null}
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
            <DrawerTitle>{editingId ? `Edit ${editingId}` : "New Deployment Connection"}</DrawerTitle>
            <DrawerDescription>
              Configure how MAPPO authenticates to an external deployment system, then verify that MAPPO can browse the projects operators will select later.
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
                  <Label htmlFor="provider-connection-provider">Deployment system</Label>
                  <FieldHelpTooltip content="External deployment system MAPPO will call through this connection." />
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
                    <Label htmlFor="provider-connection-organization-url">Azure DevOps Project URL</Label>
                    <FieldHelpTooltip content="Paste an Azure DevOps project URL from the Azure DevOps account this deployment connection should use. Repository URLs also work. MAPPO derives the Azure DevOps account automatically, verifies the API credential, and lists reachable Azure DevOps projects that operators can choose later." />
                  </div>
                  <Input
                    id="provider-connection-organization-url"
                    value={draft.organizationUrl}
                  onChange={(event) =>
                    setDraft((current) => ({ ...current, organizationUrl: event.target.value }))
                  }
                  placeholder="https://dev.azure.com/pg123/demo-app-service"
                />
                <p className="text-xs text-muted-foreground">
                  MAPPO derives the Azure DevOps account from this URL. Operators will choose from the discovered Azure DevOps projects later in Project → Config.
                </p>
                {deriveDraftAccountUrl(draft) !== "" ? (
                  <p className="text-xs text-muted-foreground">
                    Detected Azure DevOps account:{" "}
                    <span className="font-mono text-foreground">{deriveDraftAccountUrl(draft)}</span>
                  </p>
                ) : null}
              </div>
              <div className="space-y-1 sm:col-span-2">
                <div className="flex items-center gap-1">
                  <Label htmlFor="provider-connection-pat-mode">Azure DevOps API access</Label>
                  <FieldHelpTooltip content="How MAPPO resolves the Azure DevOps API credential for this deployment connection. Verify connection performs a real Azure DevOps API call so MAPPO can prove the credential works before operators use this connection in a project." />
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
                    <SelectItem value="backend_default">Use MAPPO backend secret</SelectItem>
                    <SelectItem value="environment_variable">Use backend environment variable</SelectItem>
                  </SelectContent>
                </Select>
                {draft.personalAccessTokenMode === "backend_default" ? (
                  <p className="text-xs text-muted-foreground">
                    MAPPO will use the Azure DevOps API credential already configured on the backend runtime.
                  </p>
                ) : null}
              </div>
              {draft.personalAccessTokenMode === "environment_variable" ? (
                <div className="space-y-1 sm:col-span-2">
                  <div className="flex items-center gap-1">
                    <Label htmlFor="provider-connection-pat-env-var">Backend environment variable</Label>
                    <FieldHelpTooltip content="Name of the environment variable on the MAPPO backend runtime that contains the Azure DevOps API credential." />
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
              <p className="font-medium text-foreground">What MAPPO checks</p>
                <p className="mt-1">
                  MAPPO resolves the selected API credential source, derives the Azure DevOps account from the project URL above, and loads reachable Azure DevOps projects before saving this connection.
                </p>
                {draftNormalizedOrganizationUrl ? (
                  <p className="mt-2">
                    Verified Azure DevOps account:{" "}
                    <span className="font-mono text-foreground">{draftNormalizedOrganizationUrl}</span>
                  </p>
                ) : null}
                {draftDiscoveredProjects.length > 0 ? (
                  <div className="mt-3 flex flex-wrap items-center gap-2">
                    <span className="text-xs text-muted-foreground">Accessible Azure DevOps projects:</span>
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
                    Verification passed. Save will re-run the check and persist the discovered Azure DevOps projects.
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
              {isVerifyingDraft ? "Verifying..." : "Verify and load projects"}
            </Button>
            <Button
              form="provider-connection-form"
              type="submit"
              disabled={isSubmitting || isVerifyingDraft}
            >
              {isSubmitting ? "Saving..." : editingId ? "Save connection" : "Create connection"}
            </Button>
          </DrawerFooter>
        </DrawerContent>
      </Drawer>
    </div>
  );
}
