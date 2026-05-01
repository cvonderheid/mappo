import type { FormEvent } from "react";

import FieldHelpTooltip from "@/features/shared/FieldHelpTooltip";
import { Accordion, AccordionContent, AccordionItem, AccordionTrigger } from "@/components/ui/accordion";
import { Button } from "@/components/ui/button";
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
import type { SecretReference } from "@/lib/types";

import {
  providerDefaultSecretRef,
  type EndpointDraft,
  type ReleaseIngestProvider,
  type WebhookSecretMode,
} from "@/features/admin/release-sources/shared";

type ReleaseSourceDrawerProps = {
  open: boolean;
  editingId: string;
  editingProvider: ReleaseIngestProvider | null;
  draft: EndpointDraft;
  webhookUrlPreview: string;
  webhookSecretReferences: SecretReference[];
  isAzureDevOpsProvider: boolean;
  isSubmitting: boolean;
  onOpenChange: (open: boolean) => void;
  onDraftChange: (updater: (current: EndpointDraft) => EndpointDraft) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
};

export default function ReleaseSourceDrawer({
  open,
  editingId,
  editingProvider,
  draft,
  webhookUrlPreview,
  webhookSecretReferences,
  isAzureDevOpsProvider,
  isSubmitting,
  onOpenChange,
  onDraftChange,
  onSubmit,
}: ReleaseSourceDrawerProps) {
  return (
    <Drawer open={open} onOpenChange={onOpenChange} direction="top">
      <DrawerContent>
        <DrawerHeader>
          <DrawerTitle>{editingId ? `Edit ${draft.name.trim() || "release source"}` : "New Release Source"}</DrawerTitle>
          <DrawerDescription>
            Configure how an external system notifies MAPPO about new releases. This is separate from Deployment Connections, which handle outbound API access.
          </DrawerDescription>
        </DrawerHeader>
        <form
          id="release-ingest-endpoint-form"
          onSubmit={onSubmit}
          className="space-y-3 px-4 pb-4"
        >
          <div className="grid gap-3 md:grid-cols-2">
            <div className="space-y-1.5">
              <div className="flex items-center gap-1">
                <Label htmlFor="endpoint-name">Display name</Label>
                <FieldHelpTooltip content="Display name shown in the Admin > Release Sources list." />
              </div>
              <Input
                id="endpoint-name"
                value={draft.name}
                onChange={(event) => onDraftChange((current) => ({ ...current, name: event.target.value }))}
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
                  onDraftChange((current) => {
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
                onValueChange={(value) => onDraftChange((current) => ({ ...current, enabled: value === "true" }))}
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
                  onDraftChange((current) => ({
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
                  Use a named secret from <span className="font-medium text-foreground">Admin → Secret Inventory</span> so webhook verification is reusable and easier to explain.
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
                MAPPO will generate the webhook URL after this release source is created.
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
                  onDraftChange((current) => ({
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
                <FieldHelpTooltip content="Named webhook-verification secret from Admin → Secret Inventory. MAPPO still resolves the real secret value server-side." />
              </div>
              <Select
                value={draft.webhookSecretReferenceId.trim() === "" ? "__none" : draft.webhookSecretReferenceId}
                onValueChange={(value) =>
                  onDraftChange((current) => ({
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
                  No {draft.provider === "azure_devops" ? "Azure DevOps" : "GitHub"} webhook secret references exist yet. Create one in <span className="font-medium text-foreground">Admin → Secret Inventory</span>.
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
                  onDraftChange((current) => ({
                    ...current,
                    webhookSecretKeyVaultSecret: event.target.value,
                  }))
                }
                placeholder={draft.provider === "azure_devops" ? "mappo-ado-webhook-secret" : "mappo-github-webhook-secret"}
              />
              <p className="text-xs text-muted-foreground">
                MAPPO will resolve this as <span className="font-mono text-foreground">kv:{draft.webhookSecretKeyVaultSecret.trim() || "secret-name"}</span> using the Azure Key Vault configured on the backend runtime. To keep this linked to Admin → Secret Inventory, choose <span className="font-medium text-foreground">Use secret reference</span> above instead.
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
                        onChange={(event) => onDraftChange((current) => ({ ...current, branchFilter: event.target.value }))}
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
                          onDraftChange((current) => ({ ...current, pipelineIdFilter: event.target.value }))
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
                        onChange={(event) => onDraftChange((current) => ({ ...current, repoFilter: event.target.value }))}
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
                        onChange={(event) => onDraftChange((current) => ({ ...current, branchFilter: event.target.value }))}
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
                        onChange={(event) => onDraftChange((current) => ({ ...current, manifestPath: event.target.value }))}
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
  );
}
