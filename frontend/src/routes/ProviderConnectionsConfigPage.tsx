import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";

import ProviderConnectionCard from "@/features/admin/provider-connections/ProviderConnectionCard";
import ProviderConnectionDrawer from "@/features/admin/provider-connections/ProviderConnectionDrawer";
import {
  AZURE_DEVOPS_SCOPE_REQUIRED_MESSAGE,
  buildDraftSignature,
  buildPersonalAccessTokenRef,
  deploymentConnectionDisplayName,
  emptyDraft,
  isAzureDevOpsScopeMissing,
  isUsableAzureDevOpsConnection,
  normalize,
  normalizeDeploymentConnectionError,
  resolveConnectionAccountUrl,
  toDraft,
  type DraftVerificationResult,
  type ProviderConnectionDraft,
} from "@/features/admin/provider-connections/shared";
import { Button } from "@/components/ui/button";
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import {
  createProviderConnection,
  deleteProviderConnection,
  discoverProviderConnectionAdoProjects,
  listProviderConnections,
  listSecretReferences,
  patchProviderConnection,
  verifyProviderConnectionAdoProjects,
} from "@/lib/api";
import type {
  ProviderConnection,
  ProviderConnectionAdoProject,
  ProviderConnectionCreateRequest,
  ProviderConnectionPatchRequest,
  ProviderConnectionVerifyRequest,
  SecretReference,
} from "@/lib/types";

type ProviderConnectionsConfigPageProps = {
  selectedProjectId: string;
};

export default function ProviderConnectionsConfigPage({
  selectedProjectId,
}: ProviderConnectionsConfigPageProps) {
  const [connections, setConnections] = useState<ProviderConnection[]>([]);
  const [secretReferences, setSecretReferences] = useState<SecretReference[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editingId, setEditingId] = useState("");
  const [draft, setDraft] = useState<ProviderConnectionDraft>(emptyDraft);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [isVerifyingDraft, setIsVerifyingDraft] = useState(false);
  const [draftVerifiedSignature, setDraftVerifiedSignature] = useState("");
  const [draftVerificationError, setDraftVerificationError] = useState("");
  const [draftDiscoveredProjects, setDraftDiscoveredProjects] = useState<ProviderConnectionAdoProject[]>([]);
  const [draftNormalizedOrganizationUrl, setDraftNormalizedOrganizationUrl] = useState("");
  const [discoveringConnectionIds, setDiscoveringConnectionIds] = useState<Record<string, boolean>>({});
  const [discoveredProjectsByConnectionId, setDiscoveredProjectsByConnectionId] = useState<Record<string, ProviderConnectionAdoProject[]>>({});
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
          if (connectionId !== "") {
            next[connectionId] = [...(connection.discoveredProjects ?? [])];
          }
        }
        return next;
      });
      setVerifiedConnectionIds(() => {
        const next: Record<string, boolean> = {};
        for (const connection of payload) {
          const connectionId = normalize(connection.id ?? "");
          if (connectionId !== "") {
            next[connectionId] = isUsableAzureDevOpsConnection(connection);
          }
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

  const sortedConnections = useMemo(
    () =>
      [...connections].sort((left, right) => {
        const leftName = `${left.name ?? left.id ?? ""}`.toLowerCase();
        const rightName = `${right.name ?? right.id ?? ""}`.toLowerCase();
        if (leftName !== rightName) {
          return leftName.localeCompare(rightName);
        }
        return `${left.id ?? ""}`.localeCompare(`${right.id ?? ""}`);
      }),
    [connections]
  );

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
    normalize(draft.name) !== ""
    && !isAzureDevOpsScopeMissing(draft)
    && (
      (draft.personalAccessTokenMode !== "environment_variable" || normalize(draft.personalAccessTokenEnvVar) !== "")
      && (draft.personalAccessTokenMode !== "key_vault_secret" || normalize(draft.personalAccessTokenKeyVaultSecret) !== "")
      && (draft.personalAccessTokenMode !== "secret_reference" || normalize(draft.personalAccessTokenSecretReferenceId) !== "")
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
    setDraftVerifiedSignature(Boolean(verifiedConnectionIds[connectionId]) ? buildDraftSignature(nextDraft) : "");
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
  }, [drawerOpen, draft, draftDiscoveredProjects.length, draftNormalizedOrganizationUrl, draftVerificationError, draftVerifiedSignature]);

  async function verifyDraft(): Promise<DraftVerificationResult> {
    if (isAzureDevOpsScopeMissing(draft)) {
      throw new Error(AZURE_DEVOPS_SCOPE_REQUIRED_MESSAGE);
    }
    const request: ProviderConnectionVerifyRequest = {
      id: editingId || undefined,
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
    const effectiveDraft = { ...draft, organizationUrl: normalizedOrganizationUrl || draft.organizationUrl };
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

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    const name = normalize(draft.name);
    if (name === "") {
      toast.error("Deployment connection display name is required.");
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
          `Saved and verified deployment connection ${name}. MAPPO can access ${verifiedProjects.length} Azure DevOps project${verifiedProjects.length === 1 ? "" : "s"}.`
        );
      } else {
        const createRequest: ProviderConnectionCreateRequest = {
          name,
          provider: effectiveDraft.provider,
          enabled: effectiveDraft.enabled,
          organizationUrl: normalize(effectiveDraft.organizationUrl) || undefined,
          personalAccessTokenRef: normalize(buildPersonalAccessTokenRef(effectiveDraft)) || undefined,
        };
        savedConnection = await createProviderConnection(createRequest);
        toast.success(
          `Created and verified deployment connection ${deploymentConnectionDisplayName(savedConnection)}. MAPPO can access ${verifiedProjects.length} Azure DevOps project${verifiedProjects.length === 1 ? "" : "s"}.`
        );
      }

      setDiscoveredProjectsByConnectionId((current) => ({
        ...current,
        [savedConnection.id ?? editingId]: verifiedProjects,
      }));
      setDiscoveryErrorsByConnectionId((current) => ({
        ...current,
        [savedConnection.id ?? editingId]: "",
      }));
      setVerifiedConnectionIds((current) => ({
        ...current,
        [savedConnection.id ?? editingId]: verifiedProjects.length > 0,
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

  async function handleDiscoverProjects(connection: ProviderConnection): Promise<void> {
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
      toast.success(
        `Verified ${deploymentConnectionDisplayName(connection)}. Found ${projects.length} Azure DevOps project${projects.length === 1 ? "" : "s"}.`
      );
    } catch (error) {
      const message = normalizeDeploymentConnectionError((error as Error).message);
      setDiscoveryErrorsByConnectionId((current) => ({ ...current, [connectionId]: message }));
      setVerifiedConnectionIds((current) => ({ ...current, [connectionId]: false }));
      toast.error(message);
    } finally {
      setDiscoveringConnectionIds((current) => ({ ...current, [connectionId]: false }));
    }
  }

  async function handleDelete(connection: ProviderConnection): Promise<void> {
    const connectionId = normalize(connection.id ?? "");
    if (connectionId === "") {
      return;
    }
    if (!window.confirm(`Delete deployment connection ${deploymentConnectionDisplayName(connection)}? This fails if projects are still linked.`)) {
      return;
    }
    try {
      await deleteProviderConnection(connectionId);
      toast.success(`Deleted deployment connection ${deploymentConnectionDisplayName(connection)}.`);
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
          {isLoading ? <p className="text-sm text-muted-foreground">Loading deployment connections...</p> : null}
          {!isLoading && sortedConnections.length === 0 ? (
            <p className="text-sm text-muted-foreground">No deployment connections configured yet.</p>
          ) : null}
          {sortedConnections.map((connection) => {
            const connectionId = normalize(connection.id ?? "");
            const discoveredProjects = discoveredProjectsByConnectionId[connectionId] ?? connection.discoveredProjects ?? [];
            const discoveryError = discoveryErrorsByConnectionId[connectionId] ?? "";
            const isDiscovering = Boolean(discoveringConnectionIds[connectionId]);
            const isVerified = Boolean(verifiedConnectionIds[connectionId]);
            const hasDiscoveryState =
              Object.prototype.hasOwnProperty.call(discoveredProjectsByConnectionId, connectionId)
              || discoveredProjects.length > 0
              || isVerified
              || Boolean(discoveryError);
            return (
              <ProviderConnectionCard
                key={connectionId || connection.name}
                connection={connection}
                selectedProjectId={selectedProjectId}
                isDiscovering={isDiscovering}
                isVerified={isVerified}
                discoveredProjects={discoveredProjects}
                discoveryError={discoveryError}
                hasDiscoveryState={hasDiscoveryState}
                secretReferenceLookup={secretReferenceLookup}
                onDiscover={(item) => {
                  void handleDiscoverProjects(item);
                }}
                onEdit={openEditDrawer}
                onDelete={(item) => {
                  void handleDelete(item);
                }}
              />
            );
          })}
        </CardContent>
      </Card>

      <ProviderConnectionDrawer
        open={drawerOpen}
        editingId={editingId}
        draft={draft}
        deploymentApiSecretReferences={deploymentApiSecretReferences}
        draftDiscoveredProjects={draftDiscoveredProjects}
        draftNormalizedOrganizationUrl={draftNormalizedOrganizationUrl}
        draftVerificationError={draftVerificationError}
        draftVerifiedSignature={draftVerifiedSignature}
        isSubmitting={isSubmitting}
        isVerifyingDraft={isVerifyingDraft}
        canVerifyDraft={canVerifyDraft}
        canSaveDraft={canSaveDraft}
        onOpenChange={setDrawerOpen}
        onDraftChange={(updater) => setDraft((current) => updater(current))}
        onSubmit={(event) => {
          void handleSubmit(event);
        }}
        onVerify={() => {
          void handleVerifyDraft();
        }}
      />
    </div>
  );
}
