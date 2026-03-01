import { useState, type FormEvent } from "react";

import {
  EventsDataTable,
  ForwarderLogsDataTable,
  RegistrationsDataTable,
} from "@/components/AdminTables";
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type {
  AdminOnboardingSnapshotResponse,
  MarketplaceEventIngestRequest,
  MarketplaceEventIngestResponse,
  TargetRegistrationRecord,
  UpdateTargetRegistrationRequest,
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
  onUpdateTargetRegistration: (
    targetId: string,
    request: UpdateTargetRegistrationRequest
  ) => Promise<void>;
  onDeleteTargetRegistration: (targetId: string) => Promise<void>;
  onRefreshSnapshot: () => Promise<void>;
};

function nextEventId(): string {
  return `evt-${Date.now()}`;
}

function normalizeTagValue(value: unknown, fallback: string): string {
  return typeof value === "string" && value.trim() !== "" ? value : fallback;
}

export default function AdminPanel({
  adminErrorMessage,
  adminIsSubmitting,
  adminResult,
  adminSnapshot,
  onIngestMarketplaceEvent,
  onUpdateTargetRegistration,
  onDeleteTargetRegistration,
  onRefreshSnapshot,
}: AdminPanelProps) {
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [editDrawerOpen, setEditDrawerOpen] = useState(false);

  const [editingTargetId, setEditingTargetId] = useState<string>("");
  const [editDisplayName, setEditDisplayName] = useState<string>("");
  const [editCustomerName, setEditCustomerName] = useState<string>("");
  const [editManagedApplicationId, setEditManagedApplicationId] = useState<string>("");
  const [editManagedResourceGroupId, setEditManagedResourceGroupId] = useState<string>("");
  const [editContainerAppResourceId, setEditContainerAppResourceId] = useState<string>("");
  const [editTargetGroup, setEditTargetGroup] = useState<string>("prod");
  const [editRegion, setEditRegion] = useState<string>("eastus");
  const [editEnvironment, setEditEnvironment] = useState<string>("prod");
  const [editTier, setEditTier] = useState<string>("standard");
  const [editIsSubmitting, setEditIsSubmitting] = useState<boolean>(false);

  const [deletingTargetId, setDeletingTargetId] = useState<string | null>(null);
  const [registrationErrorMessage, setRegistrationErrorMessage] = useState<string>("");
  const [registrationResultMessage, setRegistrationResultMessage] = useState<string>("");

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
  const forwarderLogs = adminSnapshot?.forwarder_logs ?? [];

  const canSubmit =
    eventId.trim() !== "" &&
    tenantId.trim() !== "" &&
    subscriptionId.trim() !== "" &&
    containerAppResourceId.trim() !== "";

  const canSubmitEdit =
    editingTargetId.trim() !== "" &&
    editDisplayName.trim() !== "" &&
    editContainerAppResourceId.trim() !== "";

  function openEditDrawer(registration: TargetRegistrationRecord): void {
    setEditingTargetId(registration.target_id);
    setEditDisplayName(registration.display_name);
    setEditCustomerName(registration.customer_name ?? "");
    setEditManagedApplicationId(registration.managed_application_id ?? "");
    setEditManagedResourceGroupId(registration.managed_resource_group_id ?? "");
    setEditContainerAppResourceId(registration.container_app_resource_id);
    setEditTargetGroup(normalizeTagValue(registration.tags?.ring, "prod"));
    setEditRegion(normalizeTagValue(registration.tags?.region, "unknown"));
    setEditEnvironment(normalizeTagValue(registration.tags?.environment, "prod"));
    setEditTier(normalizeTagValue(registration.tags?.tier, "standard"));
    setRegistrationErrorMessage("");
    setRegistrationResultMessage("");
    setEditDrawerOpen(true);
  }

  async function handleDeleteRegistration(
    registration: TargetRegistrationRecord
  ): Promise<void> {
    const confirmed = window.confirm(
      `Delete registered target ${registration.target_id}? This removes it from fleet and admin registration state.`
    );
    if (!confirmed) {
      return;
    }

    setDeletingTargetId(registration.target_id);
    try {
      await onDeleteTargetRegistration(registration.target_id);
      setRegistrationResultMessage(`Deleted target ${registration.target_id}.`);
      setRegistrationErrorMessage("");
    } catch (error) {
      setRegistrationErrorMessage((error as Error).message);
    } finally {
      setDeletingTargetId(null);
    }
  }

  async function handleUpdateRegistration(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    if (!canSubmitEdit) {
      return;
    }

    const request: UpdateTargetRegistrationRequest = {
      display_name: editDisplayName.trim(),
      customer_name: editCustomerName.trim() || null,
      managed_application_id: editManagedApplicationId.trim() || null,
      container_app_resource_id: editContainerAppResourceId.trim(),
      target_group: editTargetGroup.trim() || "prod",
      region: editRegion.trim() || "unknown",
      environment: editEnvironment.trim() || "prod",
      tier: editTier.trim() || "standard",
    };
    if (editManagedResourceGroupId.trim() !== "") {
      request.managed_resource_group_id = editManagedResourceGroupId.trim();
    }

    setEditIsSubmitting(true);
    try {
      await onUpdateTargetRegistration(editingTargetId, request);
      setRegistrationResultMessage(`Updated target ${editingTargetId}.`);
      setRegistrationErrorMessage("");
      setEditDrawerOpen(false);
    } catch (error) {
      setRegistrationErrorMessage((error as Error).message);
    } finally {
      setEditIsSubmitting(false);
    }
  }

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
    setRegistrationErrorMessage("");
    setRegistrationResultMessage("");
  }

  return (
    <div className="space-y-4">
      <div className="flex animate-fade-up items-center justify-between [animation-delay:60ms] [animation-fill-mode:forwards]">
        <p className="text-xs text-muted-foreground">
          Marketplace onboarding events and registration state.
        </p>
        <div className="flex items-center gap-2">
          <Button type="button" variant="outline" onClick={() => void onRefreshSnapshot()}>
            Refresh Snapshot
          </Button>

          <Drawer direction="top" open={drawerOpen} onOpenChange={setDrawerOpen}>
            <DrawerTrigger asChild>
              <Button data-testid="open-admin-onboarding-drawer" variant="outline">
                New Onboarding Event
              </Button>
            </DrawerTrigger>
            <DrawerContent className="glass-card">
              <DrawerHeader>
                <DrawerTitle>New Onboarding Event</DrawerTitle>
                <DrawerDescription>
                  Register a managed app target from marketplace lifecycle data.
                </DrawerDescription>
              </DrawerHeader>
              <div className="max-h-[74vh] overflow-y-auto px-4 pb-2">
                <form
                  id="admin-onboarding-form"
                  className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3"
                  onSubmit={handleSubmit}
                >
                  <div className="space-y-1">
                    <Label htmlFor="event-id">Event ID</Label>
                    <Input
                      id="event-id"
                      value={eventId}
                      onChange={(item) => setEventId(item.target.value)}
                      placeholder="evt-123"
                      required
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="tenant-id">Tenant ID</Label>
                    <Input
                      id="tenant-id"
                      value={tenantId}
                      onChange={(item) => setTenantId(item.target.value)}
                      placeholder="tenant-guid"
                      required
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="subscription-id">Subscription ID</Label>
                    <Input
                      id="subscription-id"
                      value={subscriptionId}
                      onChange={(item) => setSubscriptionId(item.target.value)}
                      placeholder="subscription-guid"
                      required
                    />
                  </div>
                  <div className="space-y-1 lg:col-span-2">
                    <Label htmlFor="container-app-id">Container App ID</Label>
                    <Input
                      id="container-app-id"
                      value={containerAppResourceId}
                      onChange={(item) => setContainerAppResourceId(item.target.value)}
                      placeholder="/subscriptions/.../providers/Microsoft.App/containerApps/..."
                      required
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
                  <div className="space-y-1 sm:col-span-2">
                    <Label htmlFor="ingest-token">Ingest Token (optional)</Label>
                    <Input
                      id="ingest-token"
                      value={ingestToken}
                      onChange={(item) => setIngestToken(item.target.value)}
                      placeholder="x-mappo-ingest-token"
                    />
                  </div>
                </form>
              </div>
              <DrawerFooter className="border-t border-border/70">
                <DrawerClose asChild>
                  <Button type="button" variant="outline">
                    Close
                  </Button>
                </DrawerClose>
                <Button
                  type="submit"
                  form="admin-onboarding-form"
                  disabled={adminIsSubmitting || !canSubmit}
                >
                  {adminIsSubmitting ? "Submitting..." : "Register from Event"}
                </Button>
              </DrawerFooter>
            </DrawerContent>
          </Drawer>

          <Drawer direction="top" open={editDrawerOpen} onOpenChange={setEditDrawerOpen}>
            <DrawerContent className="glass-card">
              <DrawerHeader>
                <DrawerTitle>Edit Registered Target</DrawerTitle>
                <DrawerDescription>
                  Update target metadata and managed app references.
                </DrawerDescription>
              </DrawerHeader>
              <div className="max-h-[74vh] overflow-y-auto px-4 pb-2">
                <form
                  id="admin-edit-registration-form"
                  className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-3"
                  onSubmit={handleUpdateRegistration}
                >
                  <div className="space-y-1">
                    <Label htmlFor="edit-target-id">Target ID</Label>
                    <Input id="edit-target-id" value={editingTargetId} disabled />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="edit-display-name">Display Name</Label>
                    <Input
                      id="edit-display-name"
                      value={editDisplayName}
                      onChange={(item) => setEditDisplayName(item.target.value)}
                      placeholder="Contoso - Prod"
                      required
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="edit-customer-name">Customer Name</Label>
                    <Input
                      id="edit-customer-name"
                      value={editCustomerName}
                      onChange={(item) => setEditCustomerName(item.target.value)}
                      placeholder="Contoso"
                    />
                  </div>
                  <div className="space-y-1 lg:col-span-2">
                    <Label htmlFor="edit-container-app-id">Container App ID</Label>
                    <Input
                      id="edit-container-app-id"
                      value={editContainerAppResourceId}
                      onChange={(item) => setEditContainerAppResourceId(item.target.value)}
                      placeholder="/subscriptions/.../providers/Microsoft.App/containerApps/..."
                      required
                    />
                  </div>
                  <div className="space-y-1 lg:col-span-2">
                    <Label htmlFor="edit-managed-application-id">Managed Application ID</Label>
                    <Input
                      id="edit-managed-application-id"
                      value={editManagedApplicationId}
                      onChange={(item) => setEditManagedApplicationId(item.target.value)}
                      placeholder="/subscriptions/.../providers/Microsoft.Solutions/applications/..."
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="edit-managed-rg-id">Managed RG ID</Label>
                    <Input
                      id="edit-managed-rg-id"
                      value={editManagedResourceGroupId}
                      onChange={(item) => setEditManagedResourceGroupId(item.target.value)}
                      placeholder="/subscriptions/.../resourceGroups/..."
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="edit-target-group">Target Group</Label>
                    <Input
                      id="edit-target-group"
                      value={editTargetGroup}
                      onChange={(item) => setEditTargetGroup(item.target.value)}
                      placeholder="prod"
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="edit-region">Region</Label>
                    <Input
                      id="edit-region"
                      value={editRegion}
                      onChange={(item) => setEditRegion(item.target.value)}
                      placeholder="eastus"
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="edit-environment">Environment</Label>
                    <Input
                      id="edit-environment"
                      value={editEnvironment}
                      onChange={(item) => setEditEnvironment(item.target.value)}
                      placeholder="prod"
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="edit-tier">Tier</Label>
                    <Input
                      id="edit-tier"
                      value={editTier}
                      onChange={(item) => setEditTier(item.target.value)}
                      placeholder="standard"
                    />
                  </div>
                </form>
              </div>
              <DrawerFooter className="border-t border-border/70">
                <DrawerClose asChild>
                  <Button type="button" variant="outline">
                    Close
                  </Button>
                </DrawerClose>
                <Button
                  type="submit"
                  form="admin-edit-registration-form"
                  disabled={editIsSubmitting || !canSubmitEdit}
                >
                  {editIsSubmitting ? "Saving..." : "Save Changes"}
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
            Event {adminResult.event_id}: <strong>{adminResult.status}</strong>
          </p>
          <p className="mt-1 text-xs">{adminResult.message}</p>
          {adminResult.target_id ? (
            <p className="mt-1 font-mono text-xs text-primary">target: {adminResult.target_id}</p>
          ) : null}
        </div>
      ) : null}

      {registrationErrorMessage ? (
        <div className="rounded-md border border-destructive/60 bg-destructive/10 p-2 text-xs text-destructive-foreground">
          {registrationErrorMessage}
        </div>
      ) : null}

      {registrationResultMessage ? (
        <div className="rounded-md border border-border/70 bg-card/70 p-3 text-sm text-foreground">
          {registrationResultMessage}
        </div>
      ) : null}

      <Card className="glass-card animate-fade-up [animation-delay:120ms] [animation-fill-mode:forwards]">
        <CardHeader>
          <CardTitle>Admin</CardTitle>
        </CardHeader>
        <CardContent>
          <Tabs defaultValue="registrations">
            <TabsList>
              <TabsTrigger value="registrations">
                Registered Targets ({registrations.length})
              </TabsTrigger>
              <TabsTrigger value="events">
                Recent Onboarding Events ({events.length})
              </TabsTrigger>
              <TabsTrigger value="forwarder-logs">
                Forwarder Logs ({forwarderLogs.length})
              </TabsTrigger>
            </TabsList>
            <TabsContent value="registrations">
              <RegistrationsDataTable
                registrations={registrations}
                onEditRegistration={openEditDrawer}
                onDeleteRegistration={(registration) => {
                  void handleDeleteRegistration(registration);
                }}
                deletingTargetId={deletingTargetId}
              />
            </TabsContent>
            <TabsContent value="events">
              <EventsDataTable events={events} />
            </TabsContent>
            <TabsContent value="forwarder-logs">
              <ForwarderLogsDataTable logs={forwarderLogs} />
            </TabsContent>
          </Tabs>
        </CardContent>
      </Card>
    </div>
  );
}
