import { FormEvent, useState } from "react";

import {
  EventsDataTable,
  ForwarderLogsDataTable,
  RegistrationsDataTable,
} from "@/components/AdminTables";
import ReleaseIngestDrawer from "@/components/ReleaseIngestDrawer";
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type {
  AdminOnboardingSnapshotResponse,
  ReleaseManifestIngestRequest,
  ReleaseManifestIngestResponse,
  TargetRegistrationRecord,
  UpdateTargetRegistrationRequest,
} from "@/lib/types";

type AdminPanelProps = {
  adminErrorMessage: string;
  adminSnapshot: AdminOnboardingSnapshotResponse | null;
  releaseIngestErrorMessage: string;
  releaseIngestIsSubmitting: boolean;
  releaseIngestResult: ReleaseManifestIngestResponse | null;
  onIngestManagedAppReleases: (
    request: ReleaseManifestIngestRequest
  ) => Promise<void>;
  onUpdateTargetRegistration: (
    targetId: string,
    request: UpdateTargetRegistrationRequest
  ) => Promise<void>;
  onDeleteTargetRegistration: (targetId: string) => Promise<void>;
  onRefreshSnapshot: () => Promise<void>;
};

function normalizeTagValue(value: unknown, fallback: string): string {
  return typeof value === "string" && value.trim() !== "" ? value : fallback;
}

export default function AdminPanel({
  adminErrorMessage,
  adminSnapshot,
  releaseIngestErrorMessage,
  releaseIngestIsSubmitting,
  releaseIngestResult,
  onIngestManagedAppReleases,
  onUpdateTargetRegistration,
  onDeleteTargetRegistration,
  onRefreshSnapshot,
}: AdminPanelProps) {
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

  const registrations = adminSnapshot?.registrations ?? [];
  const events = adminSnapshot?.events ?? [];
  const forwarderLogs = adminSnapshot?.forwarderLogs ?? [];

  const canSubmitEdit =
    editingTargetId.trim() !== "" &&
    editDisplayName.trim() !== "" &&
    editContainerAppResourceId.trim() !== "";

  function openEditDrawer(registration: TargetRegistrationRecord): void {
    setEditingTargetId(registration.targetId ?? "");
    setEditDisplayName(registration.displayName ?? "");
    setEditCustomerName(registration.customerName ?? "");
    setEditManagedApplicationId(registration.managedApplicationId ?? "");
    setEditManagedResourceGroupId(registration.managedResourceGroupId ?? "");
    setEditContainerAppResourceId(registration.containerAppResourceId ?? "");
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
      `Delete registered target ${registration.targetId}? This removes it from fleet and admin registration state.`
    );
    if (!confirmed) {
      return;
    }

    setDeletingTargetId(registration.targetId ?? "");
    try {
      await onDeleteTargetRegistration(registration.targetId ?? "");
      setRegistrationResultMessage(`Deleted target ${registration.targetId}.`);
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
      displayName: editDisplayName.trim(),
      customerName: editCustomerName.trim() || undefined,
      managedApplicationId: editManagedApplicationId.trim() || undefined,
      containerAppResourceId: editContainerAppResourceId.trim(),
      tags: {
        ring: editTargetGroup.trim() || "prod",
        region: editRegion.trim() || "unknown",
        environment: editEnvironment.trim() || "prod",
        tier: editTier.trim() || "standard",
      },
    };
    if (editManagedResourceGroupId.trim() !== "") {
      request.managedResourceGroupId = editManagedResourceGroupId.trim();
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

  return (
    <div className="space-y-4">
      <div className="flex animate-fade-up items-center justify-between [animation-delay:60ms] [animation-fill-mode:forwards]">
        <p className="text-xs text-muted-foreground">
          Marketplace onboarding registrations and operational logs.
        </p>
        <div className="flex items-center gap-2">
          <Button type="button" variant="outline" onClick={() => void onRefreshSnapshot()}>
            Refresh Snapshot
          </Button>
          <ReleaseIngestDrawer
            isSubmitting={releaseIngestIsSubmitting}
            result={releaseIngestResult}
            onIngest={onIngestManagedAppReleases}
          />
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

      {registrationErrorMessage ? (
        <div className="rounded-md border border-destructive/60 bg-destructive/10 p-2 text-xs text-destructive-foreground">
          {registrationErrorMessage}
        </div>
      ) : null}

      {releaseIngestErrorMessage ? (
        <div className="rounded-md border border-destructive/60 bg-destructive/10 p-2 text-xs text-destructive-foreground">
          {releaseIngestErrorMessage}
        </div>
      ) : null}

      {registrationResultMessage ? (
        <div className="rounded-md border border-border/70 bg-card/70 p-3 text-sm text-foreground">
          {registrationResultMessage}
        </div>
      ) : null}

      {releaseIngestResult ? (
        <div className="rounded-md border border-border/70 bg-card/70 p-3 text-sm text-foreground">
          {`Ingested ${releaseIngestResult.createdCount} new release(s), skipped ${releaseIngestResult.skippedCount}, manifest entries ${releaseIngestResult.manifestReleaseCount}.`}
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
