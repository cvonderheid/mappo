import { useState, type FormEvent } from "react";

import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type {
  AdminOnboardingSnapshotResponse,
  MarketplaceEventIngestRequest,
  MarketplaceEventIngestResponse,
} from "@/lib/types";

type AdminPanelProps = {
  adminErrorMessage: string;
  adminIsSubmitting: boolean;
  adminResult: MarketplaceEventIngestResponse | null;
  adminSnapshot: AdminOnboardingSnapshotResponse | null;
  onIngestMarketplaceEvent: (
    request: MarketplaceEventIngestRequest,
    ingestToken?: string
  ) => Promise<void>;
  onRefreshSnapshot: () => Promise<void>;
};

function nextEventId(): string {
  return `evt-${Date.now()}`;
}

export default function AdminPanel({
  adminErrorMessage,
  adminIsSubmitting,
  adminResult,
  adminSnapshot,
  onIngestMarketplaceEvent,
  onRefreshSnapshot,
}: AdminPanelProps) {
  const [eventId, setEventId] = useState<string>(nextEventId());
  const [tenantId, setTenantId] = useState<string>("");
  const [subscriptionId, setSubscriptionId] = useState<string>("");
  const [managedApplicationId, setManagedApplicationId] = useState<string>("");
  const [managedResourceGroupId, setManagedResourceGroupId] = useState<string>("");
  const [containerAppResourceId, setContainerAppResourceId] = useState<string>("");
  const [containerAppName, setContainerAppName] = useState<string>("");
  const [customerName, setCustomerName] = useState<string>("");
  const [displayName, setDisplayName] = useState<string>("");
  const [targetGroup, setTargetGroup] = useState<string>("prod");
  const [region, setRegion] = useState<string>("eastus");
  const [environment, setEnvironment] = useState<string>("prod");
  const [tier, setTier] = useState<string>("standard");
  const [ingestToken, setIngestToken] = useState<string>("");
  const registrations = adminSnapshot?.registrations ?? [];
  const events = adminSnapshot?.events ?? [];

  async function handleSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();

    const request: MarketplaceEventIngestRequest = {
      event_id: eventId.trim(),
      event_type: "subscription_purchased",
      tenant_id: tenantId.trim(),
      subscription_id: subscriptionId.trim(),
      target_group: targetGroup.trim() || "prod",
      region: region.trim() || undefined,
      environment: environment.trim() || "prod",
      tier: tier.trim() || "standard",
      tags: {},
      metadata: {},
      health_status: "registered",
      last_deployed_release: "unknown",
    };

    if (managedApplicationId.trim() !== "") {
      request.managed_application_id = managedApplicationId.trim();
    }
    if (managedResourceGroupId.trim() !== "") {
      request.managed_resource_group_id = managedResourceGroupId.trim();
    }
    if (containerAppResourceId.trim() !== "") {
      request.container_app_resource_id = containerAppResourceId.trim();
    }
    if (containerAppName.trim() !== "") {
      request.container_app_name = containerAppName.trim();
    }
    if (customerName.trim() !== "") {
      request.customer_name = customerName.trim();
    }
    if (displayName.trim() !== "") {
      request.display_name = displayName.trim();
    }

    await onIngestMarketplaceEvent(request, ingestToken.trim() || undefined);
    setEventId(nextEventId());
  }

  return (
    <Card className="glass-card animate-fade-up [animation-delay:60ms] [animation-fill-mode:forwards]">
      <CardHeader>
        <CardTitle>Admin</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4 text-sm text-muted-foreground">
        <p>Use this area for marketplace onboarding events and registration state.</p>
        <p>
          Auto-discovery has been removed. Targets are now registered from event payloads (API or function), then
          managed by MAPPO runs.
        </p>
        <form className="grid gap-3 lg:grid-cols-3" onSubmit={handleSubmit}>
          <div className="space-y-1">
            <Label htmlFor="event-id">Event ID</Label>
            <Input
              id="event-id"
              value={eventId}
              onChange={(item) => setEventId(item.target.value)}
              placeholder="evt-123"
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="tenant-id">Tenant ID</Label>
            <Input
              id="tenant-id"
              value={tenantId}
              onChange={(item) => setTenantId(item.target.value)}
              placeholder="tenant-guid"
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="subscription-id">Subscription ID</Label>
            <Input
              id="subscription-id"
              value={subscriptionId}
              onChange={(item) => setSubscriptionId(item.target.value)}
              placeholder="subscription-guid"
            />
          </div>
          <div className="space-y-1 lg:col-span-2">
            <Label htmlFor="managed-application-id">Managed Application ID (optional)</Label>
            <Input
              id="managed-application-id"
              value={managedApplicationId}
              onChange={(item) => setManagedApplicationId(item.target.value)}
              placeholder="/subscriptions/.../providers/Microsoft.Solutions/applications/..."
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="managed-rg-id">Managed RG ID (optional)</Label>
            <Input
              id="managed-rg-id"
              value={managedResourceGroupId}
              onChange={(item) => setManagedResourceGroupId(item.target.value)}
              placeholder="/subscriptions/.../resourceGroups/..."
            />
          </div>
          <div className="space-y-1 lg:col-span-2">
            <Label htmlFor="container-app-id">Container App ID</Label>
            <Input
              id="container-app-id"
              value={containerAppResourceId}
              onChange={(item) => setContainerAppResourceId(item.target.value)}
              placeholder="/subscriptions/.../providers/Microsoft.App/containerApps/..."
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="container-app-name">Container App Name (optional)</Label>
            <Input
              id="container-app-name"
              value={containerAppName}
              onChange={(item) => setContainerAppName(item.target.value)}
              placeholder="ca-mappo-ma-target-01"
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="customer-name">Customer Name (optional)</Label>
            <Input
              id="customer-name"
              value={customerName}
              onChange={(item) => setCustomerName(item.target.value)}
              placeholder="Contoso"
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="display-name">Display Name (optional)</Label>
            <Input
              id="display-name"
              value={displayName}
              onChange={(item) => setDisplayName(item.target.value)}
              placeholder="Contoso - Prod"
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="target-group">Target Group</Label>
            <Input
              id="target-group"
              value={targetGroup}
              onChange={(item) => setTargetGroup(item.target.value)}
              placeholder="prod"
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="region">Region</Label>
            <Input
              id="region"
              value={region}
              onChange={(item) => setRegion(item.target.value)}
              placeholder="eastus"
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="environment">Environment</Label>
            <Input
              id="environment"
              value={environment}
              onChange={(item) => setEnvironment(item.target.value)}
              placeholder="prod"
            />
          </div>
          <div className="space-y-1">
            <Label htmlFor="tier">Tier</Label>
            <Input
              id="tier"
              value={tier}
              onChange={(item) => setTier(item.target.value)}
              placeholder="standard"
            />
          </div>
          <div className="space-y-1 lg:col-span-2">
            <Label htmlFor="ingest-token">Ingest Token (optional)</Label>
            <Input
              id="ingest-token"
              value={ingestToken}
              onChange={(item) => setIngestToken(item.target.value)}
              placeholder="x-mappo-ingest-token"
            />
          </div>
          <div className="flex items-end gap-2 lg:col-span-3">
            <Button type="submit" disabled={adminIsSubmitting}>
              {adminIsSubmitting ? "Submitting..." : "Register from Event"}
            </Button>
            <Button type="button" variant="outline" onClick={() => void onRefreshSnapshot()}>
              Refresh Snapshot
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
              Event {adminResult.event_id}: <strong>{adminResult.status}</strong>
            </p>
            <p className="mt-1 text-xs">{adminResult.message}</p>
            {adminResult.target_id ? (
              <p className="mt-1 font-mono text-xs text-primary">target: {adminResult.target_id}</p>
            ) : null}
          </div>
        ) : null}
        <div className="grid gap-4 lg:grid-cols-2">
          <div className="rounded-md border border-border/70 bg-card/70 p-3">
            <p className="mb-2 text-sm text-foreground">Registered Targets ({registrations.length})</p>
            <div className="space-y-1 text-xs">
              {registrations.length === 0 ? <p className="text-muted-foreground">No registrations yet.</p> : null}
              {registrations.map((item) => (
                <p key={item.target_id}>
                  <span className="font-mono text-primary">{item.target_id}</span> :: {item.display_name} ::{" "}
                  {item.subscription_id}
                </p>
              ))}
            </div>
          </div>
          <div className="rounded-md border border-border/70 bg-card/70 p-3">
            <p className="mb-2 text-sm text-foreground">Recent Onboarding Events ({events.length})</p>
            <div className="space-y-1 text-xs">
              {events.length === 0 ? <p className="text-muted-foreground">No events yet.</p> : null}
              {events.map((item) => (
                <p key={item.event_id}>
                  <span className="font-mono text-primary">{item.event_id}</span> :: {item.status} ::{" "}
                  {item.target_id ?? "n/a"}
                </p>
              ))}
            </div>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}
