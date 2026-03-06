import { FormEvent, useMemo, useState } from "react";

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
  DrawerTrigger,
} from "@/components/ui/drawer";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type {
  AdminOnboardingSnapshotResponse,
  MarketplaceEventIngestRequest,
  MarketplaceEventIngestResponse,
  TargetRegistrationRecord,
} from "@/lib/types";

type DemoEventType =
  | "subscription_purchased"
  | "subscription_suspended"
  | "subscription_deleted";

type DemoPanelProps = {
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
  return `evt-demo-${Date.now()}`;
}

export default function DemoPanel({
  adminErrorMessage,
  adminIsSubmitting,
  adminResult,
  adminSnapshot,
  onIngestMarketplaceEvent,
  onRefreshSnapshot,
}: DemoPanelProps) {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [eventId, setEventId] = useState(nextEventId());
  const [eventType, setEventType] = useState<DemoEventType>("subscription_purchased");
  const [selectedRegistrationTargetId, setSelectedRegistrationTargetId] = useState("__manual__");
  const [targetId, setTargetId] = useState("");
  const [tenantId, setTenantId] = useState("");
  const [subscriptionId, setSubscriptionId] = useState("");
  const [managedApplicationId, setManagedApplicationId] = useState("");
  const [managedResourceGroupId, setManagedResourceGroupId] = useState("");
  const [containerAppResourceId, setContainerAppResourceId] = useState("");
  const [containerAppName, setContainerAppName] = useState("");
  const [customerName, setCustomerName] = useState("");
  const [displayName, setDisplayName] = useState("");
  const [targetGroup, setTargetGroup] = useState("prod");
  const [region, setRegion] = useState("eastus");
  const [environment, setEnvironment] = useState("prod");
  const [tier, setTier] = useState("standard");
  const [ingestToken, setIngestToken] = useState("");

  const registrations = useMemo(
    () => adminSnapshot?.registrations ?? [],
    [adminSnapshot]
  );
  const registrationByTargetId = useMemo(
    () =>
      new Map(
        registrations.flatMap((row: TargetRegistrationRecord) =>
          row.targetId ? [[row.targetId, row] as const] : []
        )
      ),
    [registrations]
  );

  const selectedRegistration =
    selectedRegistrationTargetId === "__manual__"
      ? null
      : registrationByTargetId.get(selectedRegistrationTargetId) ?? null;

  const hasLocator =
    targetId.trim() !== "" ||
    managedApplicationId.trim() !== "" ||
    containerAppResourceId.trim() !== "" ||
    containerAppName.trim() !== "";

  const canSubmit =
    eventId.trim() !== "" &&
    tenantId.trim() !== "" &&
    subscriptionId.trim() !== "" &&
    (eventType === "subscription_purchased"
      ? containerAppResourceId.trim() !== ""
      : hasLocator);

  const expectedOutcome = useMemo(() => {
    const knownTarget =
      targetId.trim() !== ""
        ? registrationByTargetId.has(targetId.trim())
        : selectedRegistration !== null;
    if (eventType === "subscription_purchased") {
      return knownTarget
        ? "Expected: target registration will be updated/upserted and kept active."
        : "Expected: target will be registered in Fleet and Admin registration state.";
    }
    if (eventType === "subscription_suspended") {
      return knownTarget
        ? "Expected: target health status will be marked degraded."
        : "Expected: event will be rejected because target is not currently registered.";
    }
    return knownTarget
      ? "Expected: target will be deregistered and removed from Fleet."
      : "Expected: idempotent no-op (target already absent).";
  }, [eventType, registrationByTargetId, selectedRegistration, targetId]);

  function applyRegistrationPreset(targetKey: string): void {
    setSelectedRegistrationTargetId(targetKey);
    if (targetKey === "__manual__") {
      return;
    }
    const registration = registrationByTargetId.get(targetKey);
    if (!registration) {
      return;
    }
    setTargetId(registration.targetId ?? "");
    setTenantId(registration.tenantId ?? "");
    setSubscriptionId(registration.subscriptionId ?? "");
    setManagedApplicationId(registration.managedApplicationId ?? "");
    setManagedResourceGroupId(registration.managedResourceGroupId ?? "");
    setContainerAppResourceId(registration.containerAppResourceId ?? "");
    setContainerAppName(
      registration.metadata?.containerAppName ??
        registration.containerAppResourceId?.split("/").at(-1) ??
        ""
    );
    setCustomerName(registration.customerName ?? "");
    setDisplayName(registration.displayName ?? "");
    setTargetGroup(registration.tags?.ring ?? "prod");
    setRegion(registration.tags?.region ?? "eastus");
    setEnvironment(registration.tags?.environment ?? "prod");
    setTier(registration.tags?.tier ?? "standard");
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    const request: MarketplaceEventIngestRequest = {
      eventId: eventId.trim(),
      eventType,
      tenantId: tenantId.trim(),
      subscriptionId: subscriptionId.trim(),
      targetGroup: targetGroup.trim() || "prod",
      region: region.trim() || undefined,
      environment: environment.trim() || "prod",
      tier: tier.trim() || "standard",
      tags: {},
      metadata: {
        source: "demo-panel",
      },
      healthStatus: eventType === "subscription_suspended" ? "degraded" : "registered",
      lastDeployedRelease: "unknown",
    };
    if (targetId.trim() !== "") {
      request.targetId = targetId.trim();
    }
    if (managedApplicationId.trim() !== "") {
      request.managedApplicationId = managedApplicationId.trim();
    }
    if (managedResourceGroupId.trim() !== "") {
      request.managedResourceGroupId = managedResourceGroupId.trim();
    }
    if (containerAppResourceId.trim() !== "") {
      request.containerAppResourceId = containerAppResourceId.trim();
    }
    if (containerAppName.trim() !== "") {
      request.containerAppName = containerAppName.trim();
    }
    if (customerName.trim() !== "") {
      request.customerName = customerName.trim();
    }
    if (displayName.trim() !== "") {
      request.displayName = displayName.trim();
    }

    await onIngestMarketplaceEvent(request, ingestToken.trim() || undefined);
    setEventId(nextEventId());
  }

  return (
    <div className="space-y-4">
      <div className="flex animate-fade-up items-center justify-between [animation-delay:60ms] [animation-fill-mode:forwards]">
        <p className="text-xs text-muted-foreground">
          Simulate marketplace lifecycle events without Partner Center.
        </p>
        <div className="flex items-center gap-2">
          <Button type="button" variant="outline" onClick={() => void onRefreshSnapshot()}>
            Refresh Snapshot
          </Button>
          <Drawer direction="top" open={drawerOpen} onOpenChange={setDrawerOpen}>
            <DrawerTrigger asChild>
              <Button data-testid="open-demo-marketplace-drawer" variant="outline">
                Simulate Marketplace Event
              </Button>
            </DrawerTrigger>
            <DrawerContent className="glass-card">
              <DrawerHeader>
                <DrawerTitle>Simulate Marketplace Event</DrawerTitle>
                <DrawerDescription>
                  Submit lifecycle payloads through MAPPO onboarding to mirror marketplace behavior.
                </DrawerDescription>
              </DrawerHeader>
              <div className="max-h-[74vh] overflow-y-auto px-4 pb-2">
                <form
                  id="demo-marketplace-event-form"
                  className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3"
                  onSubmit={handleSubmit}
                >
                  <div className="space-y-1">
                    <Label htmlFor="demo-event-type">Event type</Label>
                    <Select value={eventType} onValueChange={(value) => setEventType(value as DemoEventType)}>
                      <SelectTrigger id="demo-event-type" className="h-10 w-full bg-background/90 text-sm">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="subscription_purchased">subscription_purchased</SelectItem>
                        <SelectItem value="subscription_suspended">subscription_suspended</SelectItem>
                        <SelectItem value="subscription_deleted">subscription_deleted</SelectItem>
                      </SelectContent>
                    </Select>
                  </div>

                  <div className="space-y-1 lg:col-span-2">
                    <Label htmlFor="demo-known-target">Use registered target (optional)</Label>
                    <Select value={selectedRegistrationTargetId} onValueChange={applyRegistrationPreset}>
                      <SelectTrigger id="demo-known-target" className="h-10 w-full bg-background/90 text-sm">
                        <SelectValue />
                      </SelectTrigger>
                      <SelectContent>
                        <SelectItem value="__manual__">Manual input</SelectItem>
                        {registrations.map((registration: TargetRegistrationRecord) => (
                          <SelectItem
                            key={registration.targetId ?? "unknown"}
                            value={registration.targetId ?? "unknown"}
                          >
                            {registration.targetId ?? "unknown"}
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                  </div>

                  <div className="space-y-1">
                    <Label htmlFor="demo-event-id">Event ID</Label>
                    <Input
                      id="demo-event-id"
                      value={eventId}
                      onChange={(item) => setEventId(item.target.value)}
                      placeholder="evt-demo-123"
                      required
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="demo-target-id">Target ID (optional)</Label>
                    <Input
                      id="demo-target-id"
                      value={targetId}
                      onChange={(item) => setTargetId(item.target.value)}
                      placeholder="mappo-ma-target-01"
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="demo-tenant-id">Tenant ID</Label>
                    <Input
                      id="demo-tenant-id"
                      value={tenantId}
                      onChange={(item) => setTenantId(item.target.value)}
                      placeholder="tenant-guid"
                      required
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="demo-subscription-id">Subscription ID</Label>
                    <Input
                      id="demo-subscription-id"
                      value={subscriptionId}
                      onChange={(item) => setSubscriptionId(item.target.value)}
                      placeholder="subscription-guid"
                      required
                    />
                  </div>
                  <div className="space-y-1 lg:col-span-2">
                    <Label htmlFor="demo-container-app-id">Container App ID</Label>
                    <Input
                      id="demo-container-app-id"
                      value={containerAppResourceId}
                      onChange={(item) => setContainerAppResourceId(item.target.value)}
                      placeholder="/subscriptions/.../providers/Microsoft.App/containerApps/..."
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="demo-container-app-name">Container App Name</Label>
                    <Input
                      id="demo-container-app-name"
                      value={containerAppName}
                      onChange={(item) => setContainerAppName(item.target.value)}
                      placeholder="ca-mappo-demo-target-01"
                    />
                  </div>
                  <div className="space-y-1 lg:col-span-2">
                    <Label htmlFor="demo-managed-app-id">Managed Application ID</Label>
                    <Input
                      id="demo-managed-app-id"
                      value={managedApplicationId}
                      onChange={(item) => setManagedApplicationId(item.target.value)}
                      placeholder="/subscriptions/.../providers/Microsoft.Solutions/applications/..."
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="demo-managed-rg-id">Managed RG ID</Label>
                    <Input
                      id="demo-managed-rg-id"
                      value={managedResourceGroupId}
                      onChange={(item) => setManagedResourceGroupId(item.target.value)}
                      placeholder="/subscriptions/.../resourceGroups/..."
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="demo-customer-name">Customer Name</Label>
                    <Input
                      id="demo-customer-name"
                      value={customerName}
                      onChange={(item) => setCustomerName(item.target.value)}
                      placeholder="Contoso"
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="demo-display-name">Display Name</Label>
                    <Input
                      id="demo-display-name"
                      value={displayName}
                      onChange={(item) => setDisplayName(item.target.value)}
                      placeholder="Contoso - Prod"
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="demo-target-group">Target Group</Label>
                    <Input
                      id="demo-target-group"
                      value={targetGroup}
                      onChange={(item) => setTargetGroup(item.target.value)}
                      placeholder="prod"
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="demo-region">Region</Label>
                    <Input
                      id="demo-region"
                      value={region}
                      onChange={(item) => setRegion(item.target.value)}
                      placeholder="eastus"
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="demo-environment">Environment</Label>
                    <Input
                      id="demo-environment"
                      value={environment}
                      onChange={(item) => setEnvironment(item.target.value)}
                      placeholder="prod"
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="demo-tier">Tier</Label>
                    <Input
                      id="demo-tier"
                      value={tier}
                      onChange={(item) => setTier(item.target.value)}
                      placeholder="standard"
                    />
                  </div>
                  <div className="space-y-1 sm:col-span-2">
                    <Label htmlFor="demo-ingest-token">Ingest Token (optional)</Label>
                    <Input
                      id="demo-ingest-token"
                      value={ingestToken}
                      onChange={(item) => setIngestToken(item.target.value)}
                      placeholder="x-mappo-ingest-token"
                    />
                  </div>
                </form>
                <div className="mt-3 rounded-md border border-border/70 bg-muted/20 p-3">
                  <p className="text-xs uppercase tracking-wide text-muted-foreground">Expected outcome</p>
                  <p className="mt-1 text-sm">{expectedOutcome}</p>
                </div>
              </div>
              <DrawerFooter className="border-t border-border/70">
                <DrawerClose asChild>
                  <Button type="button" variant="outline">
                    Close
                  </Button>
                </DrawerClose>
                <Button
                  type="submit"
                  form="demo-marketplace-event-form"
                  disabled={adminIsSubmitting || !canSubmit}
                >
                  {adminIsSubmitting ? "Submitting..." : "Send Simulated Event"}
                </Button>
              </DrawerFooter>
            </DrawerContent>
          </Drawer>
        </div>
      </div>

      {adminErrorMessage ? (
        <div className="rounded-md border border-destructive/60 bg-destructive/10 p-2 text-xs text-destructive-foreground">
          {adminErrorMessage}
        </div>
      ) : null}

      {adminResult ? (
        <div className="rounded-md border border-border/70 bg-card/70 p-3">
          <p className="text-sm text-foreground">
            Event {adminResult.eventId}: <strong>{adminResult.status}</strong>
          </p>
          <p className="mt-1 text-xs">{adminResult.message}</p>
          {adminResult.targetId ? (
            <p className="mt-1 font-mono text-xs text-primary">target: {adminResult.targetId}</p>
          ) : null}
        </div>
      ) : null}

      <Card className="glass-card animate-fade-up [animation-delay:120ms] [animation-fill-mode:forwards]">
        <CardHeader>
          <CardTitle>Demo Event Simulator</CardTitle>
        </CardHeader>
        <CardContent className="text-sm text-muted-foreground">
          Use this panel to test marketplace lifecycle paths without Partner Center:
          <code className="ml-1">subscription_purchased</code>,
          <code className="ml-1">subscription_suspended</code>, and
          <code className="ml-1">subscription_deleted</code>.
        </CardContent>
      </Card>
    </div>
  );
}
