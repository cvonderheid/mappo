import type { FormEvent } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { AdminDiscoverImportResponse } from "@/lib/types";

type AdminPanelProps = {
  adminAutoEnumerateSubscriptions: boolean;
  adminClearRuns: boolean;
  adminErrorMessage: string;
  adminIsSubmitting: boolean;
  adminManagedAppPrefix: string;
  adminPreferredContainerAppName: string;
  adminResult: AdminDiscoverImportResponse | null;
  adminSubscriptionIds: string;
  onAdminAutoEnumerateSubscriptionsChange: (enabled: boolean) => void;
  onAdminClearRunsChange: (clearRuns: boolean) => void;
  onAdminManagedAppPrefixChange: (prefix: string) => void;
  onAdminPreferredContainerAppNameChange: (name: string) => void;
  onAdminSubscriptionIdsChange: (subscriptionIds: string) => void;
  onDiscoverImport: (event: FormEvent<HTMLFormElement>) => Promise<void>;
};

export default function AdminPanel({
  adminAutoEnumerateSubscriptions,
  adminClearRuns,
  adminErrorMessage,
  adminIsSubmitting,
  adminManagedAppPrefix,
  adminPreferredContainerAppName,
  adminResult,
  adminSubscriptionIds,
  onAdminAutoEnumerateSubscriptionsChange,
  onAdminClearRunsChange,
  onAdminManagedAppPrefixChange,
  onAdminPreferredContainerAppNameChange,
  onAdminSubscriptionIdsChange,
  onDiscoverImport,
}: AdminPanelProps) {
  const blockedScopes = adminResult?.blocked_enumeration ?? [];
  const autoDiscoveredSubscriptions = adminResult?.auto_discovered_subscription_ids ?? [];
  const scannedSubscriptions = adminResult?.scanned_subscription_ids ?? [];
  const warnings = adminResult?.warnings ?? [];

  return (
    <Card className="glass-card animate-fade-up [animation-delay:60ms] [animation-fill-mode:forwards]">
      <CardHeader>
        <CardTitle>Admin</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4 text-sm text-muted-foreground">
        <p>Use this area for control-plane administration and fleet discovery workflows.</p>
        <p>
          Production path: MAPPO runs with Managed Identity on ACA and discovers/imports targets where that identity
          has delegated access.
        </p>
        <form className="grid gap-3 lg:grid-cols-3" onSubmit={onDiscoverImport}>
          <div className="space-y-1 lg:col-span-2">
            <Label htmlFor="admin-subscription-ids">
              Subscription IDs (optional, comma-separated)
            </Label>
            <Input
              id="admin-subscription-ids"
              value={adminSubscriptionIds}
              onChange={(event) => onAdminSubscriptionIdsChange(event.target.value)}
              placeholder="sub-id-1,sub-id-2"
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="admin-managed-app-prefix">Managed app name prefix</Label>
            <Input
              id="admin-managed-app-prefix"
              value={adminManagedAppPrefix}
              onChange={(event) => onAdminManagedAppPrefixChange(event.target.value)}
              placeholder="mappo-ma"
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="admin-container-app-name">Preferred container app name</Label>
            <Input
              id="admin-container-app-name"
              value={adminPreferredContainerAppName}
              onChange={(event) => onAdminPreferredContainerAppNameChange(event.target.value)}
              placeholder="optional"
            />
          </div>
          <label className="flex items-center gap-2 text-xs text-muted-foreground">
            <input
              id="admin-auto-enumerate-subscriptions"
              type="checkbox"
              checked={adminAutoEnumerateSubscriptions}
              onChange={(event) => onAdminAutoEnumerateSubscriptionsChange(event.target.checked)}
              className="h-3.5 w-3.5 accent-primary"
            />
            Auto-enumerate accessible subscriptions
          </label>
          <label className="flex items-center gap-2 text-xs text-muted-foreground">
            <input
              id="admin-clear-runs"
              type="checkbox"
              checked={adminClearRuns}
              onChange={(event) => onAdminClearRunsChange(event.target.checked)}
              className="h-3.5 w-3.5 accent-primary"
            />
            Clear run history when replacing targets
          </label>
          <div className="flex items-end lg:col-span-3">
            <Button type="submit" disabled={adminIsSubmitting}>
              {adminIsSubmitting ? "Discovering..." : "Discover + Import Targets"}
            </Button>
          </div>
        </form>
        {adminErrorMessage ? (
          <div className="rounded-md border border-destructive/60 bg-destructive/10 p-2 text-xs text-destructive-foreground">
            {adminErrorMessage}
          </div>
        ) : null}
        {adminResult ? (
          <div className="rounded-md border border-border/70 bg-card/70 p-3">
            <p className="text-sm text-foreground">
              Imported {adminResult.imported_targets} targets from {adminResult.discovered_managed_apps} managed
              applications across {adminResult.subscriptions_scanned} subscriptions.
            </p>
            {scannedSubscriptions.length > 0 ? (
              <p className="mt-1 text-xs text-muted-foreground">
                Scanned: {scannedSubscriptions.join(", ")}
              </p>
            ) : null}
            {autoDiscoveredSubscriptions.length > 0 ? (
              <p className="mt-1 text-xs text-muted-foreground">
                Auto-discovered: {autoDiscoveredSubscriptions.join(", ")}
              </p>
            ) : null}
            {blockedScopes.length > 0 ? (
              <div className="mt-2 space-y-1 text-xs text-rose-300">
                {blockedScopes.map((item) => (
                  <p key={`${item.scope_type}:${item.scope_id}:${item.reason}`}>
                    Blocked `{item.scope_type}` `{item.scope_id}`: {item.reason}
                  </p>
                ))}
              </div>
            ) : null}
            {warnings.length > 0 ? (
              <div className="mt-2 space-y-1 text-xs text-amber-300">
                {warnings.map((warning) => (
                  <p key={warning}>{warning}</p>
                ))}
              </div>
            ) : null}
          </div>
        ) : null}
      </CardContent>
    </Card>
  );
}
