import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";

import ReleaseSourceCard from "@/features/admin/release-sources/ReleaseSourceCard";
import ReleaseSourceDrawer from "@/features/admin/release-sources/ReleaseSourceDrawer";
import {
  buildWebhookSecretRef,
  emptyDraft,
  formatTimestamp,
  releaseSourceDisplayName,
  toDraft,
  webhookPathFor,
  type EndpointDraft,
  type ReleaseIngestProvider,
} from "@/features/admin/release-sources/shared";
import { Button } from "@/components/ui/button";
import { Card, CardAction, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { apiBaseUrl } from "@/lib/api/client";
import {
  createReleaseIngestEndpoint,
  deleteReleaseIngestEndpoint,
  listReleaseIngestEndpoints,
  listSecretReferences,
  patchReleaseIngestEndpoint,
} from "@/lib/api";
import type {
  ReleaseIngestEndpoint,
  ReleaseIngestEndpointCreateRequest,
  ReleaseIngestEndpointPatchRequest,
  SecretReference,
} from "@/lib/types";

type ReleaseIngestConfigPageProps = {
  selectedProjectId: string;
};

export default function ReleaseIngestConfigPage({
  selectedProjectId,
}: ReleaseIngestConfigPageProps) {
  const [endpoints, setEndpoints] = useState<ReleaseIngestEndpoint[]>([]);
  const [secretReferences, setSecretReferences] = useState<SecretReference[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [editingId, setEditingId] = useState("");
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
    editingId.trim() === ""
      ? ""
      : `${apiBaseUrl.replace(/\/+$/, "")}${webhookPathFor(editingId.trim(), draft.provider)}`;

  const sortedEndpoints = useMemo(
    () =>
      [...endpoints].sort((left, right) => {
        const leftName = (left.name ?? "").toLowerCase();
        const rightName = (right.name ?? "").toLowerCase();
        if (leftName !== rightName) {
          return leftName.localeCompare(rightName);
        }
        return (left.id ?? "").localeCompare(right.id ?? "");
      }),
    [endpoints]
  );

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

  async function handleSubmit(event: React.FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    if (draft.name.trim() === "") {
      toast.error("Release source display name is required.");
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
        toast.success(`Updated release source ${draft.name.trim()}.`);
      } else {
        const createRequest: ReleaseIngestEndpointCreateRequest = {
          name: draft.name.trim(),
          provider: draft.provider,
          enabled: draft.enabled,
          secretRef: webhookSecretRef || undefined,
          repoFilter: draft.repoFilter.trim() || undefined,
          branchFilter: draft.branchFilter.trim() || undefined,
          pipelineIdFilter: draft.pipelineIdFilter.trim() || undefined,
          manifestPath: draft.manifestPath.trim() || undefined,
        };
        const created = await createReleaseIngestEndpoint(createRequest);
        toast.success(`Created release source ${releaseSourceDisplayName(created)}.`);
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
    if (!window.confirm(`Delete release source ${releaseSourceDisplayName(endpoint)}? This fails if projects are still linked.`)) {
      return;
    }
    try {
      await deleteReleaseIngestEndpoint(endpointId);
      toast.success(`Deleted release source ${releaseSourceDisplayName(endpoint)}.`);
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
          {isLoading ? <p className="text-sm text-muted-foreground">Loading release sources...</p> : null}
          {!isLoading && sortedEndpoints.length === 0 ? (
            <p className="text-sm text-muted-foreground">No release sources configured yet.</p>
          ) : null}
          {sortedEndpoints.map((endpoint) => {
            const endpointId = endpoint.id ?? "";
            const provider = (endpoint.provider ?? "github") as ReleaseIngestProvider;
            const webhookUrl = `${apiBaseUrl.replace(/\/+$/, "")}${webhookPathFor(endpointId, provider)}`;
            return (
              <ReleaseSourceCard
                key={endpointId || endpoint.name}
                endpoint={endpoint}
                provider={provider}
                webhookUrl={webhookUrl}
                selectedProjectId={selectedProjectId}
                secretReferenceLookup={secretReferenceLookup}
                onCopyWebhookUrl={(item) => {
                  void handleCopyWebhookUrl(item);
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

      <ReleaseSourceDrawer
        open={drawerOpen}
        editingId={editingId}
        editingProvider={editingProvider}
        draft={draft}
        webhookUrlPreview={webhookUrlPreview}
        webhookSecretReferences={webhookSecretReferences}
        isAzureDevOpsProvider={isAzureDevOpsProvider}
        isSubmitting={isSubmitting}
        onOpenChange={setDrawerOpen}
        onDraftChange={(updater) => setDraft((current) => updater(current))}
        onSubmit={(event) => {
          void handleSubmit(event);
        }}
      />
    </div>
  );
}
