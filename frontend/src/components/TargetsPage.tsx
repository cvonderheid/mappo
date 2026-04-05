import { FormEvent, useEffect, useMemo, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { toast } from "sonner";

import { EventsDataTable, RegistrationsDataTable } from "@/components/AdminTables";
import TargetOnboardingDrawer from "@/components/TargetOnboardingDrawer";
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
import type {
  MarketplaceEventIngestRequest,
  MarketplaceEventIngestResponse,
  ProjectDefinition,
  TargetRegistrationRecord,
  UpdateTargetRegistrationRequest,
} from "@/lib/types";

type TargetsPageProps = {
  adminErrorMessage: string;
  adminIsSubmitting: boolean;
  adminResult: MarketplaceEventIngestResponse | null;
  projects: ProjectDefinition[];
  selectedProjectId: string;
  registrations: TargetRegistrationRecord[];
  refreshKey: number;
  onIngestMarketplaceEvent: (
    request: MarketplaceEventIngestRequest,
    ingestToken?: string
  ) => Promise<void>;
  onUpdateTargetRegistration: (
    targetId: string,
    request: UpdateTargetRegistrationRequest
  ) => Promise<void>;
  onDeleteTargetRegistration: (targetId: string) => Promise<void>;
  onRefreshRegistrations: () => Promise<void>;
  viewMode?: "targets" | "onboarding";
};

function normalizeTagValue(value: unknown, fallback: string): string {
  return typeof value === "string" && value.trim() !== "" ? value : fallback;
}

export default function TargetsPage({
  adminErrorMessage,
  adminIsSubmitting,
  adminResult: _adminResult,
  projects,
  selectedProjectId,
  registrations,
  refreshKey,
  onIngestMarketplaceEvent,
  onUpdateTargetRegistration,
  onDeleteTargetRegistration,
  onRefreshRegistrations,
  viewMode = "targets",
}: TargetsPageProps) {
  const location = useLocation();
  const navigate = useNavigate();

  const [editDrawerOpen, setEditDrawerOpen] = useState(false);
  const [onboardingDrawerOpen, setOnboardingDrawerOpen] = useState(false);
  const [editingTargetId, setEditingTargetId] = useState<string>("");
  const [editDisplayName, setEditDisplayName] = useState<string>("");
  const [editCustomerName, setEditCustomerName] = useState<string>("");
  const [editManagedResourceGroupId, setEditManagedResourceGroupId] = useState<string>("");
  const [editContainerAppResourceId, setEditContainerAppResourceId] = useState<string>("");
  const [editDeploymentStackName, setEditDeploymentStackName] = useState<string>("");
  const [editPipelineTargetResourceGroup, setEditPipelineTargetResourceGroup] =
    useState<string>("");
  const [editPipelineTargetAppName, setEditPipelineTargetAppName] =
    useState<string>("");
  const [editPipelineSlotName, setEditPipelineSlotName] = useState<string>("");
  const [editPipelineHealthPath, setEditPipelineHealthPath] = useState<string>("");
  const [editTargetGroup, setEditTargetGroup] = useState<string>("prod");
  const [editRegion, setEditRegion] = useState<string>("eastus");
  const [editEnvironment, setEditEnvironment] = useState<string>("prod");
  const [editTier, setEditTier] = useState<string>("standard");
  const [editIsSubmitting, setEditIsSubmitting] = useState<boolean>(false);
  const [isRefreshingSnapshot, setIsRefreshingSnapshot] = useState<boolean>(false);
  const [deletingTargetId, setDeletingTargetId] = useState<string | null>(null);

  const selectedProject = useMemo(
    () => projects.find((project) => project.id === selectedProjectId) ?? null,
    [projects, selectedProjectId]
  );
  const selectedDriver = useMemo(() => {
    const driver = (selectedProject?.deploymentDriver ?? "").trim();
    return driver === "pipeline_trigger" ? "pipeline_trigger" : "azure_deployment_stack";
  }, [selectedProject?.deploymentDriver]);
  const isPipelineProject = selectedDriver === "pipeline_trigger";

  const canSubmitEdit =
    editingTargetId.trim() !== "" &&
    editDisplayName.trim() !== "" &&
    (isPipelineProject
      ? editPipelineTargetResourceGroup.trim() !== "" && editPipelineTargetAppName.trim() !== ""
      : editContainerAppResourceId.trim() !== "");

  useEffect(() => {
    if (location.pathname !== "/targets" && location.pathname !== "/onboarding") {
      return;
    }
    const params = new URLSearchParams(location.search);
    const openOnboard = params.get("onboard") === "1";
    if (!openOnboard) {
      return;
    }
    setOnboardingDrawerOpen(true);
    params.delete("onboard");
    const nextSearch = params.toString();
    void navigate(
      {
        pathname: location.pathname,
        search: nextSearch ? `?${nextSearch}` : "",
      },
      { replace: true }
    );
  }, [location.pathname, location.search, navigate]);

  function openEditDrawer(registration: TargetRegistrationRecord): void {
    const executionConfig = registration.metadata?.executionConfig ?? {};
    setEditingTargetId(registration.targetId ?? "");
    setEditDisplayName(registration.displayName ?? "");
    setEditCustomerName(registration.customerName ?? "");
    setEditManagedResourceGroupId(registration.managedResourceGroupId ?? "");
    setEditContainerAppResourceId(registration.containerAppResourceId ?? "");
    setEditDeploymentStackName(registration.metadata?.deploymentStackName ?? "");
    setEditPipelineTargetResourceGroup(
      `${executionConfig.targetResourceGroup ?? executionConfig.resourceGroup ?? ""}`
    );
    setEditPipelineTargetAppName(
      `${executionConfig.targetAppName ?? executionConfig.appServiceName ?? ""}`
    );
    setEditPipelineSlotName(`${executionConfig.slotName ?? ""}`);
    setEditPipelineHealthPath(`${executionConfig.healthPath ?? ""}`);
    setEditTargetGroup(normalizeTagValue(registration.tags?.ring, "prod"));
    setEditRegion(normalizeTagValue(registration.tags?.region, "unknown"));
    setEditEnvironment(normalizeTagValue(registration.tags?.environment, "prod"));
    setEditTier(normalizeTagValue(registration.tags?.tier, "standard"));
    setEditDrawerOpen(true);
  }

  async function handleDeleteRegistration(
    registration: TargetRegistrationRecord
  ): Promise<void> {
    const confirmed = window.confirm(
      `Delete registered target ${registration.targetId}? This removes it from fleet and target registration state.`
    );
    if (!confirmed) {
      return;
    }

    setDeletingTargetId(registration.targetId ?? "");
    try {
      await onDeleteTargetRegistration(registration.targetId ?? "");
      toast.success(`Deleted target ${registration.targetId}.`);
    } catch (error) {
      toast.error((error as Error).message);
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
      metadata: {
        executionConfig: isPipelineProject
          ? {
              targetResourceGroup: editPipelineTargetResourceGroup.trim(),
              resourceGroup: editPipelineTargetResourceGroup.trim(),
              targetAppName: editPipelineTargetAppName.trim(),
              appServiceName: editPipelineTargetAppName.trim(),
              ...(editPipelineSlotName.trim() !== ""
                ? { slotName: editPipelineSlotName.trim() }
                : {}),
              ...(editPipelineHealthPath.trim() !== ""
                ? { healthPath: editPipelineHealthPath.trim() }
                : {}),
            }
          : undefined,
        ...(isPipelineProject
          ? {}
          : {
              deploymentStackName: editDeploymentStackName.trim() || undefined,
            }),
      },
      tags: {
        ring: editTargetGroup.trim() || "prod",
        region: editRegion.trim() || "unknown",
        environment: editEnvironment.trim() || "prod",
        tier: editTier.trim() || "standard",
      },
    };
    if (!isPipelineProject) {
      request.containerAppResourceId = editContainerAppResourceId.trim();
    }
    if (editManagedResourceGroupId.trim() !== "") {
      request.managedResourceGroupId = editManagedResourceGroupId.trim();
    }

    setEditIsSubmitting(true);
    try {
      await onUpdateTargetRegistration(editingTargetId, request);
      toast.success(`Updated target ${editingTargetId}.`);
      setEditDrawerOpen(false);
    } catch (error) {
      toast.error((error as Error).message);
    } finally {
      setEditIsSubmitting(false);
    }
  }

  async function handleRefreshRegistrations(): Promise<void> {
    setIsRefreshingSnapshot(true);
    try {
      await onRefreshRegistrations();
      toast.success("Registered targets refreshed.");
    } catch (error) {
      toast.error((error as Error).message);
    } finally {
      setIsRefreshingSnapshot(false);
    }
  }

  return (
    <div className="space-y-4">
      <div className="flex animate-fade-up items-center justify-between [animation-delay:60ms] [animation-fill-mode:forwards]">
        <p className="text-xs text-muted-foreground">
          {viewMode === "onboarding"
            ? "Review registration events and how targets were added for this project."
            : "Manage the deploy destinations MAPPO can roll out to for this project."}
        </p>
      </div>
      {viewMode === "targets" ? (
        <TargetOnboardingDrawer
          projects={projects}
          selectedProjectId={selectedProjectId}
          isSubmitting={adminIsSubmitting}
          open={onboardingDrawerOpen}
          onOpenChange={setOnboardingDrawerOpen}
          onIngestMarketplaceEvent={onIngestMarketplaceEvent}
          onRefreshRegistrations={onRefreshRegistrations}
        />
      ) : null}

      {adminErrorMessage ? (
        <div className="rounded-md border border-destructive/60 bg-destructive/10 p-2 text-xs text-destructive-foreground">
          {adminErrorMessage}
        </div>
      ) : null}

      <Card className="glass-card animate-fade-up [animation-delay:120ms] [animation-fill-mode:forwards]">
        <CardHeader>
          <CardTitle>{viewMode === "onboarding" ? "Registration Events" : "Registered Targets"}</CardTitle>
        </CardHeader>
        <CardContent>
          {viewMode === "targets" ? (
            <RegistrationsDataTable
              refreshKey={refreshKey}
              projectId={selectedProjectId}
              headerActions={
                <div className="flex items-center gap-2">
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    onClick={() => setOnboardingDrawerOpen(true)}
                  >
                    Add Targets
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    size="sm"
                    disabled={isRefreshingSnapshot}
                    onClick={() => void handleRefreshRegistrations()}
                  >
                    {isRefreshingSnapshot ? "Refreshing..." : "Refresh Targets"}
                  </Button>
                </div>
              }
              onEditRegistration={openEditDrawer}
              onDeleteRegistration={(registration) => {
                void handleDeleteRegistration(registration);
              }}
              deletingTargetId={deletingTargetId}
            />
          ) : (
            <EventsDataTable refreshKey={refreshKey} projectId={selectedProjectId} />
          )}
        </CardContent>
      </Card>

      <Drawer direction="top" open={editDrawerOpen} onOpenChange={setEditDrawerOpen}>
        <DrawerContent className="glass-card">
          <DrawerHeader>
            <DrawerTitle>Edit Registered Target</DrawerTitle>
            <DrawerDescription>
              {isPipelineProject
                ? "Update target metadata and App Service deployment details for this project."
                : "Update target metadata and direct Azure rollout details for this project."}
            </DrawerDescription>
          </DrawerHeader>
          <div className="max-h-[74vh] overflow-y-auto px-4 pb-2">
            <form
              id="targets-edit-registration-form"
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
              {isPipelineProject ? (
                <>
                  <div className="space-y-1">
                    <Label htmlFor="edit-pipeline-rg">Azure Resource Group</Label>
                    <Input
                      id="edit-pipeline-rg"
                      value={editPipelineTargetResourceGroup}
                      onChange={(item) => setEditPipelineTargetResourceGroup(item.target.value)}
                      placeholder="rg-demo-appservice-target-01"
                      required
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="edit-pipeline-app-name">App Service Name</Label>
                    <Input
                      id="edit-pipeline-app-name"
                      value={editPipelineTargetAppName}
                      onChange={(item) => setEditPipelineTargetAppName(item.target.value)}
                      placeholder="appsvc-demo-target-01"
                      required
                    />
                  </div>
                  <div className="space-y-1">
                    <Label htmlFor="edit-pipeline-slot">Deployment Slot</Label>
                    <Input
                      id="edit-pipeline-slot"
                      value={editPipelineSlotName}
                      onChange={(item) => setEditPipelineSlotName(item.target.value)}
                      placeholder="staging"
                    />
                  </div>
                  <div className="space-y-1 sm:col-span-2 lg:col-span-3">
                    <Label htmlFor="edit-pipeline-health-path">Health Check Path</Label>
                    <Input
                      id="edit-pipeline-health-path"
                      value={editPipelineHealthPath}
                      onChange={(item) => setEditPipelineHealthPath(item.target.value)}
                      placeholder="/health"
                    />
                  </div>
                </>
              ) : (
                <>
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
                    <Label htmlFor="edit-deployment-stack-name">Deployment Stack Name</Label>
                    <Input
                      id="edit-deployment-stack-name"
                      value={editDeploymentStackName}
                      onChange={(item) => setEditDeploymentStackName(item.target.value)}
                      placeholder="mappo-stack-target-01"
                    />
                  </div>
                </>
              )}
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
              form="targets-edit-registration-form"
              disabled={editIsSubmitting || !canSubmitEdit}
            >
              {editIsSubmitting ? "Saving..." : "Save Changes"}
            </Button>
          </DrawerFooter>
        </DrawerContent>
      </Drawer>
    </div>
  );
}
