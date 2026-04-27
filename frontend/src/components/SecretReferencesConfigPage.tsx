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
  createSecretReference,
  deleteSecretReference,
  listSecretReferences,
  patchSecretReference,
} from "@/lib/api";
import type {
  SecretReference,
  SecretReferenceCreateRequest,
  SecretReferenceMode,
  SecretReferencePatchRequest,
  SecretReferenceProvider,
  SecretReferenceUsage,
} from "@/lib/types";

type SecretReferencesConfigPageProps = {
  selectedProjectId: string;
};

type SecretReferenceDraft = {
  id: string;
  name: string;
  provider: SecretReferenceProvider;
  usage: SecretReferenceUsage;
  mode: SecretReferenceMode;
  environmentVariable: string;
  keyVaultSecretName: string;
};

const PROVIDER_LABELS: Record<SecretReferenceProvider, string> = {
  azure_devops: "Azure DevOps",
  github: "GitHub",
};

const USAGE_LABELS: Record<SecretReferenceUsage, string> = {
  deployment_api_credential: "Deployment API credential",
  webhook_verification: "Webhook verification",
};

const MODE_LABELS: Record<SecretReferenceMode, string> = {
  mappo_default: "MAPPO backend secret",
  environment_variable: "Backend environment variable",
  key_vault_secret: "Azure Key Vault secret",
};

const STATUS_LABELS = {
  configured: "Configured",
  missing: "Missing reference",
  runtime: "Runtime default",
  unused: "Unused",
} as const;

type SecretInventoryStatus = keyof typeof STATUS_LABELS;

function normalize(value: string | undefined | null): string {
  return (value ?? "").trim();
}

function emptyDraft(): SecretReferenceDraft {
  return {
    id: "",
    name: "",
    provider: "azure_devops",
    usage: "deployment_api_credential",
    mode: "mappo_default",
    environmentVariable: "",
    keyVaultSecretName: "",
  };
}

function parseBackendRef(secretReference: SecretReference): Pick<SecretReferenceDraft, "mode" | "environmentVariable" | "keyVaultSecretName"> {
  const backendRef = normalize(secretReference.backendRef);
  if (backendRef.startsWith("env:")) {
    return {
      mode: "environment_variable",
      environmentVariable: normalize(backendRef.slice("env:".length)),
      keyVaultSecretName: "",
    };
  }
  if (backendRef.startsWith("kv:")) {
    return {
      mode: "key_vault_secret",
      environmentVariable: "",
      keyVaultSecretName: normalize(backendRef.slice("kv:".length)),
    };
  }
  return {
    mode: "mappo_default",
    environmentVariable: "",
    keyVaultSecretName: "",
  };
}

function toDraft(secretReference: SecretReference): SecretReferenceDraft {
  return {
    id: normalize(secretReference.id),
    name: normalize(secretReference.name),
    provider: (secretReference.provider ?? "azure_devops") as SecretReferenceProvider,
    usage: (secretReference.usage ?? "deployment_api_credential") as SecretReferenceUsage,
    ...parseBackendRef(secretReference),
  };
}

function buildBackendRef(draft: SecretReferenceDraft): string | undefined {
  if (draft.mode === "environment_variable") {
    const envName = normalize(draft.environmentVariable);
    return envName === "" ? undefined : `env:${envName}`;
  }
  if (draft.mode === "key_vault_secret") {
    const secretName = normalize(draft.keyVaultSecretName);
    return secretName === "" ? undefined : `kv:${secretName}`;
  }
  return undefined;
}

function describeBackendRef(secretReference: SecretReference): string {
  const backendRef = normalize(secretReference.backendRef);
  if (backendRef.startsWith("env:")) {
    return `Environment variable (${normalize(backendRef.slice("env:".length))})`;
  }
  if (backendRef.startsWith("kv:")) {
    return `Azure Key Vault secret (${normalize(backendRef.slice("kv:".length))})`;
  }
  return "MAPPO backend secret";
}

function referenceToken(secretReference: SecretReference): string {
  const backendRef = normalize(secretReference.backendRef);
  if (backendRef.startsWith("env:") || backendRef.startsWith("kv:")) {
    return backendRef;
  }
  return "";
}

function linkedUsageCount(secretReference: SecretReference): number {
  return (
    (secretReference.linkedDeploymentConnections?.length ?? 0)
    + (secretReference.linkedReleaseSources?.length ?? 0)
  );
}

function inventoryStatus(secretReference: SecretReference): SecretInventoryStatus {
  const mode = (secretReference.mode ?? "mappo_default") as SecretReferenceMode;
  if (mode === "mappo_default") {
    return linkedUsageCount(secretReference) > 0 ? "runtime" : "unused";
  }
  if (referenceToken(secretReference) === "") {
    return "missing";
  }
  return linkedUsageCount(secretReference) > 0 ? "configured" : "unused";
}

function inventoryStatusClassName(status: SecretInventoryStatus): string {
  if (status === "missing") {
    return "border-destructive/60 bg-destructive/10 text-destructive";
  }
  if (status === "unused") {
    return "border-muted-foreground/40 text-muted-foreground";
  }
  if (status === "runtime") {
    return "border-amber-400/60 bg-amber-400/10 text-amber-200";
  }
  return "border-emerald-400/60 bg-emerald-400/10 text-emerald-200";
}

function formatDateTime(value: string | undefined): string {
  if (!value) {
    return "Not recorded";
  }
  const parsed = new Date(value);
  if (Number.isNaN(parsed.getTime())) {
    return value;
  }
  return parsed.toLocaleString();
}

async function copyText(value: string, successMessage: string): Promise<void> {
  await navigator.clipboard.writeText(value);
  toast.success(successMessage);
}

export default function SecretReferencesConfigPage({
  selectedProjectId,
}: SecretReferencesConfigPageProps) {
  void selectedProjectId;
  const [secretReferences, setSecretReferences] = useState<SecretReference[]>([]);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [isRefreshing, setIsRefreshing] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string>("");
  const [drawerOpen, setDrawerOpen] = useState<boolean>(false);
  const [editingId, setEditingId] = useState<string>("");
  const [draft, setDraft] = useState<SecretReferenceDraft>(emptyDraft);
  const [isSubmitting, setIsSubmitting] = useState<boolean>(false);
  const [deletingId, setDeletingId] = useState<string>("");

  const loadSecretReferences = useCallback(async (refresh = false) => {
    if (refresh) {
      setIsRefreshing(true);
    } else {
      setIsLoading(true);
    }
    try {
      const result = await listSecretReferences();
      setSecretReferences(result);
      setErrorMessage("");
    } catch (error) {
      setErrorMessage((error as Error).message);
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void loadSecretReferences();
  }, [loadSecretReferences]);

  const sortedSecretReferences = useMemo(
    () =>
      [...secretReferences].sort((left, right) =>
        `${left.name ?? ""}\u0000${left.id ?? ""}`.localeCompare(`${right.name ?? ""}\u0000${right.id ?? ""}`)
      ),
    [secretReferences]
  );
  const inventorySummary = useMemo(() => {
    const counts = {
      total: sortedSecretReferences.length,
      configured: 0,
      missing: 0,
      unused: 0,
      linked: 0,
    };
    for (const secretReference of sortedSecretReferences) {
      const status = inventoryStatus(secretReference);
      if (status === "configured" || status === "runtime") {
        counts.configured += 1;
      }
      if (status === "missing") {
        counts.missing += 1;
      }
      if (status === "unused") {
        counts.unused += 1;
      }
      if (linkedUsageCount(secretReference) > 0) {
        counts.linked += 1;
      }
    }
    return counts;
  }, [sortedSecretReferences]);

  function openCreateDrawer(): void {
    setEditingId("");
    setDraft(emptyDraft());
    setDrawerOpen(true);
  }

  function openEditDrawer(secretReference: SecretReference): void {
    setEditingId(normalize(secretReference.id));
    setDraft(toDraft(secretReference));
    setDrawerOpen(true);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    if (normalize(draft.name) === "") {
      toast.error("Secret reference display name is required.");
      return;
    }
    if (draft.mode === "environment_variable" && normalize(draft.environmentVariable) === "") {
      toast.error("Environment variable name is required.");
      return;
    }
    if (draft.mode === "key_vault_secret" && normalize(draft.keyVaultSecretName) === "") {
      toast.error("Azure Key Vault secret name is required.");
      return;
    }

    setIsSubmitting(true);
    try {
      if (editingId) {
        const request: SecretReferencePatchRequest = {
          name: normalize(draft.name),
          provider: draft.provider,
          usage: draft.usage,
          mode: draft.mode,
          backendRef: buildBackendRef(draft),
        };
        await patchSecretReference(editingId, request);
        toast.success(`Updated secret reference ${editingId}.`);
      } else {
        const request: SecretReferenceCreateRequest = {
          name: normalize(draft.name),
          provider: draft.provider,
          usage: draft.usage,
          mode: draft.mode,
          backendRef: buildBackendRef(draft),
        };
        const created = await createSecretReference(request);
        toast.success(`Created secret reference ${created.name || created.id}.`);
      }
      setDrawerOpen(false);
      setDraft(emptyDraft());
      setEditingId("");
      await loadSecretReferences(true);
    } catch (error) {
      toast.error((error as Error).message);
    } finally {
      setIsSubmitting(false);
    }
  }

  async function handleDelete(secretReference: SecretReference): Promise<void> {
    const secretReferenceId = normalize(secretReference.id);
    if (secretReferenceId === "") {
      return;
    }
    const confirmed = window.confirm(
      `Delete secret reference ${secretReference.name || secretReferenceId}?`
    );
    if (!confirmed) {
      return;
    }
    setDeletingId(secretReferenceId);
    try {
      await deleteSecretReference(secretReferenceId);
      toast.success(`Deleted secret reference ${secretReferenceId}.`);
      await loadSecretReferences(true);
    } catch (error) {
      toast.error((error as Error).message);
    } finally {
      setDeletingId("");
    }
  }

  return (
    <section className="space-y-4">
      <Card className="glass-card animate-fade-up [animation-delay:120ms] [animation-fill-mode:forwards]">
        <CardHeader>
          <div className="space-y-1">
            <CardTitle>Secret Inventory</CardTitle>
            <CardDescription>
              Read-only inventory of the external-system credentials MAPPO expects, where each value is resolved, and which Release Sources or Deployment Connections use it. Secret values never appear in the browser.
            </CardDescription>
          </div>
          <CardAction className="flex-wrap justify-end">
            <Button
              type="button"
              variant="outline"
              onClick={() => {
                void loadSecretReferences(true);
              }}
              disabled={isRefreshing}
            >
              {isRefreshing ? "Refreshing..." : "Refresh"}
            </Button>
            <Button type="button" onClick={openCreateDrawer}>
              New Secret Reference
            </Button>
          </CardAction>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="grid gap-3 md:grid-cols-4">
            <div className="rounded-lg border border-border/70 bg-background/40 p-3">
              <p className="text-xs font-medium uppercase tracking-[0.08em] text-muted-foreground">Inventory</p>
              <p className="mt-1 text-2xl font-semibold text-foreground">{inventorySummary.total}</p>
              <p className="text-xs text-muted-foreground">named references</p>
            </div>
            <div className="rounded-lg border border-emerald-400/40 bg-emerald-400/10 p-3">
              <p className="text-xs font-medium uppercase tracking-[0.08em] text-emerald-200">Configured</p>
              <p className="mt-1 text-2xl font-semibold text-foreground">{inventorySummary.configured}</p>
              <p className="text-xs text-muted-foreground">ready or runtime-backed</p>
            </div>
            <div className="rounded-lg border border-border/70 bg-background/40 p-3">
              <p className="text-xs font-medium uppercase tracking-[0.08em] text-muted-foreground">Linked</p>
              <p className="mt-1 text-2xl font-semibold text-foreground">{inventorySummary.linked}</p>
              <p className="text-xs text-muted-foreground">used by integrations</p>
            </div>
            <div className="rounded-lg border border-destructive/50 bg-destructive/10 p-3">
              <p className="text-xs font-medium uppercase tracking-[0.08em] text-destructive">Needs attention</p>
              <p className="mt-1 text-2xl font-semibold text-foreground">{inventorySummary.missing + inventorySummary.unused}</p>
              <p className="text-xs text-muted-foreground">missing or unused</p>
            </div>
          </div>

          <div className="rounded-lg border border-border/70 bg-background/40 p-3 text-sm text-muted-foreground">
            MAPPO stores only references here. Put actual values in the backend environment or MAPPO Azure Key Vault, then select these named references from Release Sources and Deployment Connections.
          </div>

          {errorMessage ? (
            <div className="rounded-md border border-destructive/60 bg-destructive/10 p-3 text-sm text-destructive-foreground">
              {errorMessage}
            </div>
          ) : null}

          {isLoading && sortedSecretReferences.length === 0 ? (
            <div className="rounded-md border border-border/60 bg-background/40 p-4 text-sm text-muted-foreground">
              Loading secret references...
            </div>
          ) : null}

          {!isLoading && sortedSecretReferences.length === 0 ? (
            <div className="rounded-md border border-border/60 bg-background/40 p-4 text-sm text-muted-foreground">
              No secret references are configured yet. Add a named reference for each external credential MAPPO needs, such as an Azure DevOps PAT or webhook verification secret.
            </div>
          ) : null}

          {sortedSecretReferences.map((secretReference) => {
            const status = inventoryStatus(secretReference);
            const token = referenceToken(secretReference);
            return (
            <div
              key={secretReference.id ?? secretReference.name}
              className="rounded-xl border border-border/70 bg-background/40 p-4"
            >
              <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
                <div className="space-y-2">
                  <div>
                    <div className="flex flex-wrap items-center gap-2">
                      <p className="text-lg font-semibold text-foreground">
                        {secretReference.name || secretReference.id}
                      </p>
                      <Badge variant="outline" className={inventoryStatusClassName(status)}>
                        {STATUS_LABELS[status]}
                      </Badge>
                    </div>
                    <p className="text-sm text-muted-foreground">
                      Reference ID: <span className="font-mono">{secretReference.id}</span>
                    </p>
                  </div>
                  <div className="flex flex-wrap items-center gap-2">
                    <Badge variant="outline">
                      {PROVIDER_LABELS[(secretReference.provider ?? "azure_devops") as SecretReferenceProvider]}
                    </Badge>
                    <Badge variant="outline">
                      {USAGE_LABELS[(secretReference.usage ?? "deployment_api_credential") as SecretReferenceUsage]}
                    </Badge>
                    <Badge variant="outline">
                      {MODE_LABELS[(secretReference.mode ?? "mappo_default") as SecretReferenceMode]}
                    </Badge>
                  </div>
                  <div className="grid gap-3 md:grid-cols-3">
                    <div>
                      <p className="text-xs font-medium uppercase tracking-[0.08em] text-muted-foreground">
                        Secret source
                      </p>
                      <p className="text-sm font-medium text-foreground">{describeBackendRef(secretReference)}</p>
                    </div>
                    <div>
                      <p className="text-xs font-medium uppercase tracking-[0.08em] text-muted-foreground">
                        Used by
                      </p>
                      <p className="text-sm font-medium text-foreground">
                        {linkedUsageCount(secretReference)} integration{linkedUsageCount(secretReference) === 1 ? "" : "s"}
                      </p>
                    </div>
                    <div>
                      <p className="text-xs font-medium uppercase tracking-[0.08em] text-muted-foreground">
                        Last updated
                      </p>
                      <p className="text-sm font-medium text-foreground">{formatDateTime(secretReference.updatedAt)}</p>
                    </div>
                  </div>
                  {token !== "" ? (
                    <div className="space-y-1">
                      <p className="text-xs font-medium uppercase tracking-[0.08em] text-muted-foreground">
                        Reference token
                      </p>
                      <div className="flex flex-col gap-2 sm:flex-row">
                        <Input
                          value={token}
                          readOnly
                          onFocus={(event) => event.target.select()}
                          className="font-mono text-xs"
                        />
                        <Button
                          type="button"
                          variant="outline"
                          onClick={() => {
                            void copyText(token, "Copied secret reference token.");
                          }}
                        >
                          Copy token
                        </Button>
                      </div>
                    </div>
                  ) : null}
                  {(secretReference.linkedDeploymentConnections?.length ?? 0) > 0 ? (
                    <div className="space-y-1">
                      <p className="text-xs font-medium uppercase tracking-[0.08em] text-muted-foreground">
                        Linked Deployment Connections
                      </p>
                      <div className="flex flex-wrap gap-2">
                        {secretReference.linkedDeploymentConnections?.map((connection) => (
                          <Badge key={`${connection.id}-${connection.name}`} variant="secondary">
                            {connection.name || connection.id}
                          </Badge>
                        ))}
                      </div>
                    </div>
                  ) : null}
                  {(secretReference.linkedReleaseSources?.length ?? 0) > 0 ? (
                    <div className="space-y-1">
                      <p className="text-xs font-medium uppercase tracking-[0.08em] text-muted-foreground">
                        Linked Release Sources
                      </p>
                      <div className="flex flex-wrap gap-2">
                        {secretReference.linkedReleaseSources?.map((source) => (
                          <Badge key={`${source.id}-${source.name}`} variant="secondary">
                            {source.name || source.id}
                          </Badge>
                        ))}
                      </div>
                    </div>
                  ) : null}
                </div>
                <div className="flex flex-wrap gap-2">
                  <Button type="button" variant="outline" onClick={() => openEditDrawer(secretReference)}>
                    Edit
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    className="border-destructive/60 text-destructive hover:bg-destructive/10 hover:text-destructive"
                    disabled={deletingId === normalize(secretReference.id)}
                    onClick={() => {
                      void handleDelete(secretReference);
                    }}
                  >
                    {deletingId === normalize(secretReference.id) ? "Deleting..." : "Delete"}
                  </Button>
                </div>
              </div>
            </div>
            );
          })}
        </CardContent>
      </Card>

      <Drawer direction="top" open={drawerOpen} onOpenChange={setDrawerOpen}>
        <DrawerContent className="glass-card">
          <DrawerHeader>
            <DrawerTitle>{editingId ? `Edit ${draft.name || editingId}` : "New Secret Reference"}</DrawerTitle>
            <DrawerDescription>
              Define a named secret once, then select it from Deployment Connections and Release Sources. MAPPO still resolves the real value server-side.
            </DrawerDescription>
          </DrawerHeader>
          <form id="secret-reference-form" className="grid grid-cols-1 gap-3 px-4 sm:grid-cols-2" onSubmit={handleSubmit}>
            <div className="space-y-1">
              <div className="flex items-center gap-1">
                <Label htmlFor="secret-reference-name">Display name</Label>
                <FieldHelpTooltip content="Friendly name shown to operators when selecting this secret reference elsewhere in MAPPO." />
              </div>
              <Input
                id="secret-reference-name"
                value={draft.name}
                onChange={(event) => setDraft((current) => ({ ...current, name: event.target.value }))}
                placeholder="Azure DevOps Runtime PAT"
              />
            </div>
            <div className="space-y-1">
              <div className="flex items-center gap-1">
                <Label htmlFor="secret-reference-provider">Provider</Label>
                <FieldHelpTooltip content="External system this secret belongs to." />
              </div>
              <Select
                value={draft.provider}
                onValueChange={(value) => setDraft((current) => ({ ...current, provider: value as SecretReferenceProvider }))}
              >
                <SelectTrigger id="secret-reference-provider">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="azure_devops">Azure DevOps</SelectItem>
                  <SelectItem value="github">GitHub</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1">
              <div className="flex items-center gap-1">
                <Label htmlFor="secret-reference-usage">Usage</Label>
                <FieldHelpTooltip content="Whether this secret is used to call an external deployment API or to verify inbound webhooks." />
              </div>
              <Select
                value={draft.usage}
                onValueChange={(value) => setDraft((current) => ({ ...current, usage: value as SecretReferenceUsage }))}
              >
                <SelectTrigger id="secret-reference-usage">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="deployment_api_credential">Deployment API credential</SelectItem>
                  <SelectItem value="webhook_verification">Webhook verification</SelectItem>
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1 sm:col-span-2">
              <div className="flex items-center gap-1">
                <Label htmlFor="secret-reference-mode">Secret storage</Label>
                <FieldHelpTooltip content="Where MAPPO resolves this secret at runtime. Secret values stay outside the browser." />
              </div>
              <Select
                value={draft.mode}
                onValueChange={(value) =>
                  setDraft((current) => ({
                    ...current,
                    mode: value as SecretReferenceMode,
                    environmentVariable: value === "environment_variable" ? current.environmentVariable : "",
                    keyVaultSecretName: value === "key_vault_secret" ? current.keyVaultSecretName : "",
                  }))
                }
              >
                <SelectTrigger id="secret-reference-mode">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="mappo_default">Use MAPPO backend secret</SelectItem>
                  <SelectItem value="environment_variable">Use backend environment variable</SelectItem>
                  <SelectItem value="key_vault_secret">Use Azure Key Vault secret</SelectItem>
                </SelectContent>
              </Select>
            </div>
            {draft.mode === "environment_variable" ? (
              <div className="space-y-1 sm:col-span-2">
                <div className="flex items-center gap-1">
                  <Label htmlFor="secret-reference-env">Environment variable name</Label>
                  <FieldHelpTooltip content="Name of the backend environment variable that holds this secret. Enter only the environment variable name." />
                </div>
                <Input
                  id="secret-reference-env"
                  value={draft.environmentVariable}
                  onChange={(event) => setDraft((current) => ({ ...current, environmentVariable: event.target.value }))}
                  placeholder="MAPPO_AZURE_DEVOPS_PERSONAL_ACCESS_TOKEN"
                />
              </div>
            ) : null}
            {draft.mode === "key_vault_secret" ? (
              <div className="space-y-1 sm:col-span-2">
                <div className="flex items-center gap-1">
                  <Label htmlFor="secret-reference-kv">Azure Key Vault secret name</Label>
                  <FieldHelpTooltip content="Secret name in MAPPO's Azure Key Vault. Enter only the secret name; MAPPO resolves it as kv:secret-name." />
                </div>
                <Input
                  id="secret-reference-kv"
                  value={draft.keyVaultSecretName}
                  onChange={(event) => setDraft((current) => ({ ...current, keyVaultSecretName: event.target.value }))}
                  placeholder="mappo-ado-org-pat"
                />
              </div>
            ) : null}
          </form>
          <DrawerFooter>
            <DrawerClose asChild>
              <Button type="button" variant="outline" disabled={isSubmitting}>
                Cancel
              </Button>
            </DrawerClose>
            <Button type="submit" form="secret-reference-form" disabled={isSubmitting}>
              {isSubmitting
                ? editingId
                  ? "Updating..."
                  : "Creating..."
                : editingId
                  ? "Update secret reference"
                  : "Create secret reference"}
            </Button>
          </DrawerFooter>
        </DrawerContent>
      </Drawer>
    </section>
  );
}
