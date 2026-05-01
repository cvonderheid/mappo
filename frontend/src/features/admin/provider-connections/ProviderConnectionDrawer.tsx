import type { FormEvent } from "react";

import FieldHelpTooltip from "@/features/shared/FieldHelpTooltip";
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
import { Badge } from "@/components/ui/badge";
import type { ProviderConnectionAdoProject, ProviderConnectionProvider, SecretReference } from "@/lib/types";

import {
  buildDraftSignature,
  DEFAULT_AZURE_DEVOPS_PAT_REF,
  deriveDraftAccountUrl,
  normalize,
  type ProviderConnectionDraft,
} from "@/features/admin/provider-connections/shared";

type ProviderConnectionDrawerProps = {
  open: boolean;
  editingId: string;
  draft: ProviderConnectionDraft;
  deploymentApiSecretReferences: SecretReference[];
  draftDiscoveredProjects: ProviderConnectionAdoProject[];
  draftNormalizedOrganizationUrl: string;
  draftVerificationError: string;
  draftVerifiedSignature: string;
  isSubmitting: boolean;
  isVerifyingDraft: boolean;
  canVerifyDraft: boolean;
  canSaveDraft: boolean;
  onOpenChange: (open: boolean) => void;
  onDraftChange: (updater: (current: ProviderConnectionDraft) => ProviderConnectionDraft) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onVerify: () => void;
};

export default function ProviderConnectionDrawer({
  open,
  editingId,
  draft,
  deploymentApiSecretReferences,
  draftDiscoveredProjects,
  draftNormalizedOrganizationUrl,
  draftVerificationError,
  draftVerifiedSignature,
  isSubmitting,
  isVerifyingDraft,
  canVerifyDraft,
  canSaveDraft,
  onOpenChange,
  onDraftChange,
  onSubmit,
  onVerify,
}: ProviderConnectionDrawerProps) {
  return (
    <Drawer direction="top" open={open} onOpenChange={onOpenChange}>
      <DrawerContent className="glass-card">
        <DrawerHeader>
          <DrawerTitle>{editingId ? `Edit ${draft.name.trim() || "deployment connection"}` : "New Deployment Connection"}</DrawerTitle>
          <DrawerDescription>
            Configure how MAPPO authenticates to an external deployment system, then verify that MAPPO can browse the Azure DevOps projects operators will select later.
          </DrawerDescription>
        </DrawerHeader>
        <div className="max-h-[72vh] overflow-y-auto px-4 pb-2">
          <form id="provider-connection-form" className="grid grid-cols-1 gap-3 sm:grid-cols-2" onSubmit={onSubmit}>
            <div className="space-y-1">
              <div className="flex items-center gap-1">
                <Label htmlFor="provider-connection-name">Display name</Label>
                <FieldHelpTooltip content="Friendly name operators will pick later in Project → Config when they choose how MAPPO talks to this deployment system." />
              </div>
              <Input
                id="provider-connection-name"
                value={draft.name}
                onChange={(event) => onDraftChange((current) => ({ ...current, name: event.target.value }))}
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
                  onDraftChange((current) => ({
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
                  onDraftChange((current) => ({ ...current, enabled: value === "enabled" }))
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
                    onDraftChange((current) => ({ ...current, organizationUrl: event.target.value }))
                  }
                  placeholder="https://dev.azure.com/<org>/<project> or https://<org>.visualstudio.com/<project>/_git/<repo>"
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
                    onDraftChange((current) => ({
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
                    Use a named secret from <span className="font-medium text-foreground">Admin → Secret Inventory</span> so operators do not have to type raw Key Vault secret names here.
                  </p>
                ) : null}
              </div>
              {draft.personalAccessTokenMode === "secret_reference" ? (
                <div className="space-y-1">
                  <div className="flex items-center gap-1">
                    <Label htmlFor="provider-connection-pat-secret-reference">Secret reference</Label>
                    <FieldHelpTooltip content="Named Azure DevOps deployment API credential from Admin → Secret Inventory. MAPPO still resolves the real secret value server-side." />
                  </div>
                  <Select
                    value={normalize(draft.personalAccessTokenSecretReferenceId) === "" ? "__none" : draft.personalAccessTokenSecretReferenceId}
                    onValueChange={(value) =>
                      onDraftChange((current) => ({
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
                      No Azure DevOps API secret references exist yet. Create one in <span className="font-medium text-foreground">Admin → Secret Inventory</span>.
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
                      onDraftChange((current) => ({ ...current, personalAccessTokenEnvVar: event.target.value }))
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
                      onDraftChange((current) => ({ ...current, personalAccessTokenKeyVaultSecret: event.target.value }))
                    }
                    placeholder="mappo-ado-org-pat"
                  />
                  <p className="text-xs text-muted-foreground">
                    MAPPO will resolve this as <span className="font-mono text-foreground">kv:{normalize(draft.personalAccessTokenKeyVaultSecret) || "secret-name"}</span> using the Azure Key Vault configured on the backend runtime. To keep this linked to Admin → Secret Inventory, choose <span className="font-medium text-foreground">Use secret reference</span> above instead.
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
              {draftVerifiedSignature !== "" && draftVerifiedSignature === buildDraftSignature(draft) ? (
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
            onClick={onVerify}
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
  );
}
