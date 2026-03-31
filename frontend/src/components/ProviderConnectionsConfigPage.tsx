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
  listProviderConnections,
  patchProviderConnection,
} from "@/lib/api";
import type {
  ProviderConnection,
  ProviderConnectionCreateRequest,
  ProviderConnectionPatchRequest,
  ProviderConnectionProvider,
} from "@/lib/types";

type ProviderConnectionsConfigPageProps = {
  selectedProjectId: string;
};

type ProviderConnectionDraft = {
  id: string;
  name: string;
  provider: ProviderConnectionProvider;
  enabled: boolean;
  personalAccessTokenRef: string;
};

const DEFAULT_AZURE_DEVOPS_PAT_REF = "mappo.azure-devops.personal-access-token";

function emptyDraft(): ProviderConnectionDraft {
  return {
    id: "",
    name: "",
    provider: "azure_devops",
    enabled: true,
    personalAccessTokenRef: DEFAULT_AZURE_DEVOPS_PAT_REF,
  };
}

function toDraft(connection: ProviderConnection): ProviderConnectionDraft {
  return {
    id: connection.id ?? "",
    name: connection.name ?? "",
    provider: (connection.provider ?? "azure_devops") as ProviderConnectionProvider,
    enabled: connection.enabled ?? true,
    personalAccessTokenRef: connection.personalAccessTokenRef ?? DEFAULT_AZURE_DEVOPS_PAT_REF,
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

  function openCreateDrawer(): void {
    setEditingId("");
    setDraft(emptyDraft());
    setDrawerOpen(true);
  }

  function openEditDrawer(connection: ProviderConnection): void {
    setEditingId(connection.id ?? "");
    setDraft(toDraft(connection));
    setDrawerOpen(true);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    const connectionId = normalize(draft.id);
    const name = normalize(draft.name);
    if (connectionId === "" || name === "") {
      toast.error("Connection ID and name are required.");
      return;
    }

    const personalAccessTokenRef = normalize(draft.personalAccessTokenRef);
    if (personalAccessTokenRef.startsWith("literal:")) {
      toast.error("Literal PAT values are not supported. Use a secret reference key or env:VAR_NAME.");
      return;
    }

    setIsSubmitting(true);
    try {
      if (editingId) {
        const patchRequest: ProviderConnectionPatchRequest = {
          name,
          provider: draft.provider,
          enabled: draft.enabled,
          organizationFilter: "",
          personalAccessTokenRef: personalAccessTokenRef || undefined,
        };
        await patchProviderConnection(editingId, patchRequest);
        toast.success(`Updated provider connection ${editingId}.`);
      } else {
        const createRequest: ProviderConnectionCreateRequest = {
          id: connectionId,
          name,
          provider: draft.provider,
          enabled: draft.enabled,
          organizationFilter: undefined,
          personalAccessTokenRef: personalAccessTokenRef || undefined,
        };
        await createProviderConnection(createRequest);
        toast.success(`Created provider connection ${connectionId}.`);
      }
      setDrawerOpen(false);
      await loadConnections(true);
    } catch (error) {
      toast.error((error as Error).message);
    } finally {
      setIsSubmitting(false);
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
          Configure provider API credentials and scope used by deployment drivers.
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
                  </div>
                </div>
                <div className="mt-2 grid gap-2 text-xs text-muted-foreground md:grid-cols-2">
                  <p>
                    PAT secret reference:{" "}
                    <span className="font-mono text-foreground">
                      {maskedSecretRef(connection.personalAccessTokenRef ?? DEFAULT_AZURE_DEVOPS_PAT_REF)}
                    </span>
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
                <div className="mt-3 flex flex-wrap items-center gap-2">
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
                      personalAccessTokenRef:
                        value === "azure_devops"
                          ? normalize(current.personalAccessTokenRef) || DEFAULT_AZURE_DEVOPS_PAT_REF
                          : current.personalAccessTokenRef,
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
                  <Label htmlFor="provider-connection-pat-ref">PAT secret reference</Label>
                  <FieldHelpTooltip content="Backend secret key or env reference for Azure DevOps PAT. Allowed formats: mappo.azure-devops.personal-access-token or env:VAR_NAME." />
                </div>
                <Input
                  id="provider-connection-pat-ref"
                  value={draft.personalAccessTokenRef}
                  onChange={(event) =>
                    setDraft((current) => ({ ...current, personalAccessTokenRef: event.target.value }))
                  }
                  placeholder={DEFAULT_AZURE_DEVOPS_PAT_REF}
                />
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
              form="provider-connection-form"
              type="submit"
              disabled={isSubmitting}
            >
              {isSubmitting ? "Saving..." : editingId ? "Update connection" : "Create connection"}
            </Button>
          </DrawerFooter>
        </DrawerContent>
      </Drawer>
    </div>
  );
}
