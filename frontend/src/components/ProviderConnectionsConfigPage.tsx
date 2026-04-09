import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";

import FieldHelpTooltip from "@/components/FieldHelpTooltip";
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
import {
  createProviderConnection,
  deleteProviderConnection,
  discoverProviderConnectionAdoProjects,
  listSecretReferences,
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
  SecretReference,
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
  personalAccessTokenMode: "backend_default" | "environment_variable" | "key_vault_secret" | "secret_reference";
  personalAccessTokenEnvVar: string;
  personalAccessTokenKeyVaultSecret: string;
  personalAccessTokenSecretReferenceId: string;
};

const DEFAULT_AZURE_DEVOPS_PAT_REF = "mappo.azure-devops.personal-access-token";

const AZURE_DEVOPS_SCOPE_REQUIRED_MESSAGE =
  "Paste any Azure DevOps project or repository URL from the Azure DevOps account MAPPO should browse. The access token proves MAPPO can authenticate; the URL tells MAPPO which Azure DevOps account to browse so it can load the projects operators choose later.";

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
    || normalized.includes("account url is required")
  ) {
    return AZURE_DEVOPS_SCOPE_REQUIRED_MESSAGE;
  }
  if (normalized.includes("pat could not be resolved")) {
    return "MAPPO could not resolve the Azure DevOps API credential for this deployment connection. Use the MAPPO backend secret, a named secret reference, a backend environment variable, or an Azure Key Vault secret that actually exists, then verify the connection again.";
  }
  if (normalized.includes("no accessible azure devops projects were returned")) {
    return "MAPPO authenticated to Azure DevOps, but that access token could not see any Azure DevOps projects in the selected account. Confirm the URL is correct and that the token can read at least one project.";
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
    personalAccessTokenKeyVaultSecret: "",
    personalAccessTokenSecretReferenceId: "",
  };
}

function parsePersonalAccessTokenRef(
  value: string | undefined
): Pick<
  ProviderConnectionDraft,
  "personalAccessTokenMode" | "personalAccessTokenEnvVar" | "personalAccessTokenKeyVaultSecret" | "personalAccessTokenSecretReferenceId"
> {
  const normalized = normalize(value ?? "");
  if (normalized.startsWith("secret:")) {
    return {
      personalAccessTokenMode: "secret_reference",
      personalAccessTokenEnvVar: "",
      personalAccessTokenKeyVaultSecret: "",
      personalAccessTokenSecretReferenceId: normalize(normalized.slice("secret:".length)),
    };
  }
  if (normalized.startsWith("env:")) {
    return {
      personalAccessTokenMode: "environment_variable",
      personalAccessTokenEnvVar: normalize(normalized.slice("env:".length)),
      personalAccessTokenKeyVaultSecret: "",
      personalAccessTokenSecretReferenceId: "",
    };
  }
  if (normalized.startsWith("kv:")) {
    return {
      personalAccessTokenMode: "key_vault_secret",
      personalAccessTokenEnvVar: "",
      personalAccessTokenKeyVaultSecret: normalize(normalized.slice("kv:".length)),
      personalAccessTokenSecretReferenceId: "",
    };
  }
  return {
    personalAccessTokenMode: "backend_default",
    personalAccessTokenEnvVar: "",
    personalAccessTokenKeyVaultSecret: "",
    personalAccessTokenSecretReferenceId: "",
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
  if (draft.personalAccessTokenMode === "secret_reference") {
    const secretReferenceId = normalize(draft.personalAccessTokenSecretReferenceId);
    return secretReferenceId === "" ? "" : `secret:${secretReferenceId}`;
  }
  if (draft.personalAccessTokenMode === "environment_variable") {
    const envVarName = normalize(draft.personalAccessTokenEnvVar);
    return envVarName === "" ? "" : `env:${envVarName}`;
  }
  if (draft.personalAccessTokenMode === "key_vault_secret") {
    const secretName = normalize(draft.personalAccessTokenKeyVaultSecret);
    return secretName === "" ? "" : `kv:${secretName}`;
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

function describePersonalAccessTokenSource(
  connection: ProviderConnection,
  secretReferenceLookup: Record<string, SecretReference>
): string {
  const normalized = normalize(connection.personalAccessTokenRef ?? DEFAULT_AZURE_DEVOPS_PAT_REF);
  if (normalized === DEFAULT_AZURE_DEVOPS_PAT_REF) {
    return "MAPPO runtime secret";
  }
  if (normalized.startsWith("secret:")) {
    const secretReferenceId = normalize(normalized.slice("secret:".length));
    return `Secret reference (${secretReferenceLookup[secretReferenceId]?.name || secretReferenceId})`;
  }
  if (normalized.startsWith("env:")) {
    return `Environment variable (${normalize(normalized.slice("env:".length))})`;
  }
  if (normalized.startsWith("kv:")) {
    return `Azure Key Vault secret (${normalize(normalized.slice("kv:".length))})`;
  }
  return maskedSecretRef(normalized);
}

function describeDiscoveredProjectCount(count: number): string {
  if (count <= 0) {
    return "No Azure DevOps projects verified yet";
  }
  return `${count} Azure DevOps project${count === 1 ? "" : "s"} available`;
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
  const [secretReferences, setSecretReferences] = useState<SecretReference[]>([]);
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
      const [payload, loadedSecretReferences] = await Promise.all([
        listProviderConnections(),
        listSecretReferences(),
      ]);
      setConnections(payload);
      setSecretReferences(loadedSecretReferences);
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

  const secretReferenceLookup = useMemo(() => {
    const lookup: Record<string, SecretReference> = {};
    for (const secretReference of secretReferences) {
      const secretReferenceId = normalize(secretReference.id ?? "");
      if (secretReferenceId !== "") {
        lookup[secretReferenceId] = secretReference;
      }
    }
    return lookup;
  }, [secretReferences]);

  const deploymentApiSecretReferences = useMemo(
    () =>
      secretReferences
        .filter(
          (secretReference) =>
            normalize(secretReference.provider ?? "") === "azure_devops"
            && normalize(secretReference.usage ?? "") === "deployment_api_credential"
        )
        .sort((left, right) =>
          `${left.name ?? left.id ?? ""}`.localeCompare(`${right.name ?? right.id ?? ""}`, undefined, {
            sensitivity: "base",
          })
        ),
    [secretReferences]
  );

  const canVerifyDraft =
    normalize(draft.id) !== ""
    && normalize(draft.name) !== ""
    && (!isAzureDevOpsScopeMissing(draft))
    && (
      (
        draft.personalAccessTokenMode !== "environment_variable"
        || normalize(draft.personalAccessTokenEnvVar) !== ""
      )
      && (
        draft.personalAccessTokenMode !== "key_vault_secret"
        || normalize(draft.personalAccessTokenKeyVaultSecret) !== ""
      )
      && (
        draft.personalAccessTokenMode !== "secret_reference"
        || normalize(draft.personalAccessTokenSecretReferenceId) !== ""
      )
    );
  const canSaveDraft = canVerifyDraft;

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
        `Previewed Azure DevOps access. MAPPO found ${result.projects.length} project${result.projects.length === 1 ? "" : "s"}.`
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
    if (draft.personalAccessTokenMode === "key_vault_secret" && personalAccessTokenRef === "") {
      toast.error("Azure Key Vault secret name is required when API credential source is Azure Key Vault secret.");
      return;
    }
    if (draft.personalAccessTokenMode === "secret_reference" && personalAccessTokenRef === "") {
      toast.error("Secret reference is required when API credential source is Secret reference.");
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
        toast.success(
          `Saved and verified deployment connection ${editingId}. MAPPO can access ${verifiedProjects.length} Azure DevOps project${verifiedProjects.length === 1 ? "" : "s"}.`
        );
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
        toast.success(
          `Created and verified deployment connection ${connectionId}. MAPPO can access ${verifiedProjects.length} Azure DevOps project${verifiedProjects.length === 1 ? "" : "s"}.`
        );
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
      {errorMessage ? (
        <div className="rounded-md border border-destructive/60 bg-destructive/10 p-2 text-xs text-destructive-foreground">
          {errorMessage}
        </div>
      ) : null}

      <Card className="glass-card animate-fade-up [animation-delay:100ms] [animation-fill-mode:forwards]">
        <CardHeader className="gap-4 sm:flex-row sm:items-start sm:justify-between">
          <div className="space-y-1">
            <CardTitle>Deployment Connections</CardTitle>
            <CardDescription>
              Configure how MAPPO authenticates to external deployment systems. For Azure DevOps, MAPPO verifies the access token, confirms which Azure DevOps account it can browse, and loads the Azure DevOps projects operators can choose later in Project → Config.
            </CardDescription>
          </div>
          <CardAction className="flex-wrap justify-end">
            <Button type="button" variant="outline" onClick={() => void loadConnections(true)}>
              {isRefreshing ? "Refreshing..." : "Refresh connections"}
            </Button>
            <Button type="button" onClick={openCreateDrawer}>
              New Deployment Connection
            </Button>
          </CardAction>
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
                    Azure DevOps account scope:{" "}
                    <span className="font-mono text-foreground">
                      {resolvedAccountUrl || "not configured"}
                    </span>
                  </p>
                  <p>
                    API credential source: <span className="font-medium text-foreground">{describePersonalAccessTokenSource(connection, secretReferenceLookup)}</span>
                  </p>
                </div>
                {resolvedAccountUrl === "" ? (
                  <p className="mt-2 rounded-md border border-amber-400/40 bg-amber-500/10 px-3 py-2 text-xs text-amber-100">
                    This deployment connection still needs verification. Edit it, paste any Azure DevOps project or repository URL from the Azure DevOps account MAPPO should browse, then verify the connection.
                  </p>
                ) : null}
                {isVerified ? (
                  <p className="mt-2 rounded-md border border-emerald-400/30 bg-emerald-500/10 px-3 py-2 text-xs text-emerald-100">
                    Verified Azure DevOps access. MAPPO can browse {discoveredProjects.length} Azure DevOps project{discoveredProjects.length === 1 ? "" : "s"} through this deployment connection.
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
                    <span className="text-xs text-muted-foreground">Azure DevOps projects MAPPO can browse:</span>
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
                    {isDiscovering
                      ? "Verifying..."
                      : hasDiscoveryState
                        ? "Re-verify access"
                        : "Verify / refresh access"}
                  </Button>
                  <span className="text-xs text-muted-foreground">
                    {describeDiscoveredProjectCount(discoveredProjects.length)}
                  </span>
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
              Configure how MAPPO authenticates to an external deployment system, then verify that MAPPO can browse the Azure DevOps projects operators will select later.
            </DrawerDescription>
          </DrawerHeader>
          <div className="max-h-[72vh] overflow-y-auto px-4 pb-2">
            <form id="provider-connection-form" className="grid grid-cols-1 gap-3 sm:grid-cols-2" onSubmit={handleSubmit}>
              <div className="space-y-1">
                <div className="flex items-center gap-1">
                  <Label htmlFor="provider-connection-id">Deployment connection ID</Label>
                  <FieldHelpTooltip content="Stable key MAPPO stores for this deployment connection. Use lowercase letters, numbers, and hyphens." />
                </div>
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
                <div className="flex items-center gap-1">
                  <Label htmlFor="provider-connection-name">Display name</Label>
                  <FieldHelpTooltip content="Friendly name operators will pick later in Project → Config when they choose how MAPPO talks to this deployment system." />
                </div>
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
                      personalAccessTokenKeyVaultSecret: "",
                      personalAccessTokenSecretReferenceId: "",
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
              <div className="space-y-3 rounded-md border border-border/60 bg-background/40 p-3 sm:col-span-2">
                <div>
                  <p className="text-sm font-medium text-foreground">Azure DevOps account scope</p>
                  <p className="text-xs text-muted-foreground">
                    Tell MAPPO which Azure DevOps account it should browse. Azure DevOps PATs do not tell MAPPO which account to inspect, so paste any project or repository URL from that account and MAPPO will derive the account automatically.
                  </p>
                </div>
                <div className="space-y-1">
                  <div className="flex items-center gap-1">
                    <Label htmlFor="provider-connection-organization-url">Example Azure DevOps project or repository URL</Label>
                    <FieldHelpTooltip content="Paste any Azure DevOps project or repository URL from the account this deployment connection should browse. MAPPO derives the Azure DevOps account automatically from that URL and verifies that the access token can read projects there." />
                  </div>
                  <Input
                    id="provider-connection-organization-url"
                    value={draft.organizationUrl}
                    onChange={(event) =>
                      setDraft((current) => ({ ...current, organizationUrl: event.target.value }))
                    }
                    placeholder="https://dev.azure.com/pg123/demo-app-service or https://pg123.visualstudio.com/demo-app-service/_git/demo-app-service"
                  />
                  <p className="text-xs text-muted-foreground">
                    MAPPO derives the Azure DevOps account from this URL, verifies the access token against that account, and loads the Azure DevOps projects operators can choose later in Project → Config.
                  </p>
                  {deriveDraftAccountUrl(draft) !== "" ? (
                    <p className="text-xs text-muted-foreground">
                      Derived Azure DevOps account scope:{" "}
                      <span className="font-mono text-foreground">{deriveDraftAccountUrl(draft)}</span>
                    </p>
                  ) : null}
                </div>
              </div>
              <div className="space-y-3 rounded-md border border-border/60 bg-background/40 p-3 sm:col-span-2">
                <div>
                  <p className="text-sm font-medium text-foreground">Azure DevOps API credential</p>
                  <p className="text-xs text-muted-foreground">
                    Choose how MAPPO resolves the Azure DevOps access token for this deployment connection. Save and verify performs a real Azure DevOps API call before operators can use this connection in a project.
                  </p>
                </div>
                <div className="space-y-1">
                  <div className="flex items-center gap-1">
                    <Label htmlFor="provider-connection-pat-mode">Azure DevOps access token source</Label>
                    <FieldHelpTooltip content="How MAPPO resolves the Azure DevOps access token for this deployment connection. Save and verify performs a real Azure DevOps API call so MAPPO can prove the credential works before operators use this connection in a project." />
                  </div>
                  <Select
                    value={draft.personalAccessTokenMode}
                  onValueChange={(value) =>
                    setDraft((current) => ({
                      ...current,
                      personalAccessTokenMode: value as ProviderConnectionDraft["personalAccessTokenMode"],
                      personalAccessTokenSecretReferenceId:
                        value === "secret_reference" ? current.personalAccessTokenSecretReferenceId : "",
                      personalAccessTokenEnvVar:
                        value === "environment_variable" ? current.personalAccessTokenEnvVar : "",
                      personalAccessTokenKeyVaultSecret:
                        value === "key_vault_secret" ? current.personalAccessTokenKeyVaultSecret : "",
                    }))
                    }
                  >
                    <SelectTrigger id="provider-connection-pat-mode">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="backend_default">Use MAPPO backend secret</SelectItem>
                      <SelectItem value="secret_reference">Use secret reference</SelectItem>
                      <SelectItem value="environment_variable">Use backend environment variable</SelectItem>
                      <SelectItem value="key_vault_secret">Use Azure Key Vault secret</SelectItem>
                    </SelectContent>
                  </Select>
                  {draft.personalAccessTokenMode === "backend_default" ? (
                    <p className="text-xs text-muted-foreground">
                      MAPPO will use the Azure DevOps access token already configured on the backend runtime at{" "}
                      <span className="font-mono text-foreground">{DEFAULT_AZURE_DEVOPS_PAT_REF}</span>.
                    </p>
                  ) : null}
                  {draft.personalAccessTokenMode === "secret_reference" ? (
                    <p className="text-xs text-muted-foreground">
                      Use a named secret from <span className="font-medium text-foreground">Admin → Secret References</span> so operators do not have to type raw Key Vault secret names here.
                    </p>
                  ) : null}
                </div>
                {draft.personalAccessTokenMode === "secret_reference" ? (
                  <div className="space-y-1">
                    <div className="flex items-center gap-1">
                      <Label htmlFor="provider-connection-pat-secret-reference">Secret reference</Label>
                      <FieldHelpTooltip content="Named Azure DevOps deployment API credential from Admin → Secret References. MAPPO still resolves the real secret value server-side." />
                    </div>
                    <Select
                      value={normalize(draft.personalAccessTokenSecretReferenceId) === "" ? "__none" : draft.personalAccessTokenSecretReferenceId}
                      onValueChange={(value) =>
                        setDraft((current) => ({
                          ...current,
                          personalAccessTokenSecretReferenceId: value === "__none" ? "" : value,
                        }))
                      }
                    >
                      <SelectTrigger id="provider-connection-pat-secret-reference">
                        <SelectValue placeholder="Select secret reference" />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="__none">Select secret reference</SelectItem>
                        {deploymentApiSecretReferences.map((secretReference) => (
                          <SelectItem key={secretReference.id ?? secretReference.name} value={secretReference.id ?? ""}>
                            {secretReference.name || secretReference.id}
                            {" ("}
                            {secretReference.id}
                            {")"}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    {deploymentApiSecretReferences.length === 0 ? (
                      <p className="text-xs text-muted-foreground">
                        No Azure DevOps API secret references exist yet. Create one in <span className="font-medium text-foreground">Admin → Secret References</span>.
                      </p>
                    ) : null}
                  </div>
                ) : null}
                {draft.personalAccessTokenMode === "environment_variable" ? (
                  <div className="space-y-1">
                    <div className="flex items-center gap-1">
                      <Label htmlFor="provider-connection-pat-env-var">Backend environment variable</Label>
                      <FieldHelpTooltip content="Name of the environment variable on the MAPPO backend runtime that contains the Azure DevOps access token." />
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
                {draft.personalAccessTokenMode === "key_vault_secret" ? (
                  <div className="space-y-1">
                    <div className="flex items-center gap-1">
                      <Label htmlFor="provider-connection-pat-key-vault-secret">Azure Key Vault secret name</Label>
                      <FieldHelpTooltip content="Name of the secret in MAPPO's Azure Key Vault that stores the Azure DevOps access token. Enter only the secret name, not the secret value." />
                    </div>
                    <Input
                      id="provider-connection-pat-key-vault-secret"
                      value={draft.personalAccessTokenKeyVaultSecret}
                      onChange={(event) =>
                        setDraft((current) => ({ ...current, personalAccessTokenKeyVaultSecret: event.target.value }))
                      }
                      placeholder="mappo-ado-pg123-pat"
                    />
                    <p className="text-xs text-muted-foreground">
                      MAPPO will resolve this as <span className="font-mono text-foreground">kv:{normalize(draft.personalAccessTokenKeyVaultSecret) || "secret-name"}</span> using the Azure Key Vault configured on the backend runtime.
                    </p>
                  </div>
                ) : null}
              </div>
              <div className="rounded-md border border-border/60 bg-background/40 p-3 text-xs text-muted-foreground sm:col-span-2">
              <p className="font-medium text-foreground">Preview Azure DevOps access</p>
                <p className="mt-1">
                  Save and verify performs this check automatically. Use Preview only if you want to confirm the Azure DevOps account scope and the projects MAPPO can browse before you save the deployment connection.
                </p>
                {draftNormalizedOrganizationUrl ? (
                  <p className="mt-2">
                    Verified Azure DevOps account scope:{" "}
                    <span className="font-mono text-foreground">{draftNormalizedOrganizationUrl}</span>
                  </p>
                ) : null}
                {draftDiscoveredProjects.length > 0 ? (
                  <div className="mt-3 flex flex-wrap items-center gap-2">
                    <span className="text-xs text-muted-foreground">Azure DevOps projects MAPPO can browse:</span>
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
                    Preview passed. Save and verify will persist this Azure DevOps account and the Azure DevOps projects MAPPO discovered.
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
              disabled={isSubmitting || isVerifyingDraft || !canVerifyDraft}
            >
              {isVerifyingDraft ? "Previewing..." : "Preview access"}
            </Button>
            <Button
              form="provider-connection-form"
              type="submit"
              disabled={isSubmitting || isVerifyingDraft || !canSaveDraft}
            >
              {isSubmitting
                ? "Saving..."
                : editingId
                  ? "Save and verify connection"
                  : "Create and verify connection"}
            </Button>
          </DrawerFooter>
        </DrawerContent>
      </Drawer>
    </div>
  );
}
