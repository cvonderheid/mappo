import { useMemo, useState, type FormEvent } from "react";
import {
  type ColumnDef,
  type ColumnFiltersState,
  type SortingState,
  flexRender,
  getCoreRowModel,
  getFilteredRowModel,
  getSortedRowModel,
  useReactTable,
} from "@tanstack/react-table";

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
  DrawerTrigger,
} from "@/components/ui/drawer";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import type {
  AdminOnboardingSnapshotResponse,
  MarketplaceEventIngestRequest,
  MarketplaceEventIngestResponse,
  MarketplaceEventRecord,
  TargetRegistrationRecord,
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

type RegistrationRow = {
  targetId: string;
  displayName: string;
  customerName: string;
  targetGroup: string;
  region: string;
  tier: string;
  environment: string;
  subscriptionId: string;
  tenantId: string;
  updatedAt: string;
};

type EventRow = {
  eventId: string;
  status: string;
  eventType: string;
  targetId: string;
  subscriptionId: string;
  tenantId: string;
  createdAt: string;
  message: string;
};

function nextEventId(): string {
  return `evt-${Date.now()}`;
}

function normalizeTagValue(value: unknown, fallback: string): string {
  return typeof value === "string" && value.trim() !== "" ? value : fallback;
}

function eventStatusVariant(
  status: string
): "default" | "secondary" | "destructive" | "outline" {
  if (status === "applied") {
    return "default";
  }
  if (status === "duplicate") {
    return "secondary";
  }
  if (status === "rejected") {
    return "destructive";
  }
  return "outline";
}

function RegistrationsDataTable({
  registrations,
}: {
  registrations: TargetRegistrationRecord[];
}) {
  const rows = useMemo<RegistrationRow[]>(
    () =>
      registrations.map((record) => ({
        targetId: record.target_id,
        displayName: record.display_name,
        customerName: record.customer_name ?? "unknown",
        targetGroup: normalizeTagValue(record.tags?.ring, "unassigned"),
        region: normalizeTagValue(record.tags?.region, "unknown"),
        tier: normalizeTagValue(record.tags?.tier, "unknown"),
        environment: normalizeTagValue(record.tags?.environment, "unknown"),
        subscriptionId: record.subscription_id,
        tenantId: record.tenant_id,
        updatedAt: record.updated_at,
      })),
    [registrations]
  );

  const [sorting, setSorting] = useState<SortingState>([]);
  const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>([]);

  const columns = useMemo<ColumnDef<RegistrationRow>[]>(
    () => [
      { accessorKey: "targetId", header: "Target" },
      { accessorKey: "displayName", header: "Display Name" },
      { accessorKey: "customerName", header: "Customer" },
      {
        accessorKey: "targetGroup",
        header: "Group",
        filterFn: (row, id, value) => {
          if (!value) {
            return true;
          }
          return row.getValue<string>(id) === value;
        },
      },
      {
        accessorKey: "region",
        header: "Region",
        filterFn: (row, id, value) => {
          if (!value) {
            return true;
          }
          return row.getValue<string>(id) === value;
        },
      },
      {
        accessorKey: "tier",
        header: "Tier",
        filterFn: (row, id, value) => {
          if (!value) {
            return true;
          }
          return row.getValue<string>(id) === value;
        },
      },
      { accessorKey: "environment", header: "Env" },
      { accessorKey: "subscriptionId", header: "Subscription" },
      { accessorKey: "tenantId", header: "Tenant" },
      {
        accessorKey: "updatedAt",
        header: "Updated",
        cell: ({ row }) => (
          <span className="text-xs text-muted-foreground">
            {new Date(row.original.updatedAt).toLocaleString()}
          </span>
        ),
      },
    ],
    []
  );

  const table = useReactTable({
    data: rows,
    columns,
    state: { sorting, columnFilters },
    onSortingChange: setSorting,
    onColumnFiltersChange: setColumnFilters,
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });

  const targetIdFilter =
    (table.getColumn("targetId")?.getFilterValue() as string | undefined) ?? "";
  const groupFilter =
    (table.getColumn("targetGroup")?.getFilterValue() as string | undefined) ?? "";
  const regionFilter =
    (table.getColumn("region")?.getFilterValue() as string | undefined) ?? "";
  const tierFilter =
    (table.getColumn("tier")?.getFilterValue() as string | undefined) ?? "";
  const groups = [...new Set(rows.map((row) => row.targetGroup))].sort();
  const regions = [...new Set(rows.map((row) => row.region))].sort();
  const tiers = [...new Set(rows.map((row) => row.tier))].sort();

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between gap-2">
        <Badge variant="outline" className="font-mono text-[11px]">
          {table.getFilteredRowModel().rows.length}/{rows.length}
        </Badge>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => table.resetColumnFilters()}
        >
          Clear filters
        </Button>
      </div>
      <Table>
        <TableHeader>
          <TableRow>
            {table.getFlatHeaders().map((header) => (
              <TableHead key={header.id}>
                {header.column.getCanSort() ? (
                  <button
                    type="button"
                    className="inline-flex items-center gap-1 text-left"
                    onClick={header.column.getToggleSortingHandler()}
                  >
                    {flexRender(header.column.columnDef.header, header.getContext())}
                    {header.column.getIsSorted() === "asc" ? "▲" : null}
                    {header.column.getIsSorted() === "desc" ? "▼" : null}
                  </button>
                ) : (
                  flexRender(header.column.columnDef.header, header.getContext())
                )}
              </TableHead>
            ))}
          </TableRow>
          <TableRow>
            <TableHead>
              <Input
                value={targetIdFilter}
                onChange={(event) =>
                  table.getColumn("targetId")?.setFilterValue(event.target.value)
                }
                placeholder="Filter target"
                className="h-8"
              />
            </TableHead>
            <TableHead />
            <TableHead />
            <TableHead>
              <select
                className="h-8 w-full rounded-md border border-input bg-background/90 px-2 text-xs"
                value={groupFilter}
                onChange={(event) =>
                  table
                    .getColumn("targetGroup")
                    ?.setFilterValue(event.target.value === "all" ? undefined : event.target.value)
                }
              >
                <option value="all">All groups</option>
                {groups.map((value) => (
                  <option key={value} value={value}>
                    {value}
                  </option>
                ))}
              </select>
            </TableHead>
            <TableHead>
              <select
                className="h-8 w-full rounded-md border border-input bg-background/90 px-2 text-xs"
                value={regionFilter}
                onChange={(event) =>
                  table
                    .getColumn("region")
                    ?.setFilterValue(event.target.value === "all" ? undefined : event.target.value)
                }
              >
                <option value="all">All regions</option>
                {regions.map((value) => (
                  <option key={value} value={value}>
                    {value}
                  </option>
                ))}
              </select>
            </TableHead>
            <TableHead>
              <select
                className="h-8 w-full rounded-md border border-input bg-background/90 px-2 text-xs"
                value={tierFilter}
                onChange={(event) =>
                  table
                    .getColumn("tier")
                    ?.setFilterValue(event.target.value === "all" ? undefined : event.target.value)
                }
              >
                <option value="all">All tiers</option>
                {tiers.map((value) => (
                  <option key={value} value={value}>
                    {value}
                  </option>
                ))}
              </select>
            </TableHead>
            <TableHead />
            <TableHead />
            <TableHead />
            <TableHead />
          </TableRow>
        </TableHeader>
        <TableBody>
          {table.getRowModel().rows.length === 0 ? (
            <TableRow>
              <TableCell colSpan={10} className="text-sm text-muted-foreground">
                No registered targets match current filters.
              </TableCell>
            </TableRow>
          ) : (
            table.getRowModel().rows.map((row) => (
              <TableRow key={row.id}>
                {row.getVisibleCells().map((cell) => (
                  <TableCell
                    key={cell.id}
                    className={
                      cell.column.id === "targetId" || cell.column.id === "subscriptionId"
                        ? "font-mono text-xs"
                        : undefined
                    }
                  >
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </TableCell>
                ))}
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </div>
  );
}

function EventsDataTable({ events }: { events: MarketplaceEventRecord[] }) {
  const rows = useMemo<EventRow[]>(
    () =>
      events.map((record) => ({
        eventId: record.event_id,
        status: record.status,
        eventType: record.event_type,
        targetId: record.target_id ?? "n/a",
        subscriptionId: record.subscription_id,
        tenantId: record.tenant_id,
        createdAt: record.created_at,
        message: record.message,
      })),
    [events]
  );

  const [sorting, setSorting] = useState<SortingState>([]);
  const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>([]);

  const columns = useMemo<ColumnDef<EventRow>[]>(
    () => [
      { accessorKey: "eventId", header: "Event ID" },
      {
        accessorKey: "status",
        header: "Status",
        filterFn: (row, id, value) => {
          if (!value) {
            return true;
          }
          return row.getValue<string>(id) === value;
        },
        cell: ({ row }) => (
          <Badge variant={eventStatusVariant(row.original.status)} className="capitalize">
            {row.original.status}
          </Badge>
        ),
      },
      { accessorKey: "eventType", header: "Event Type" },
      { accessorKey: "targetId", header: "Target" },
      { accessorKey: "subscriptionId", header: "Subscription" },
      { accessorKey: "tenantId", header: "Tenant" },
      {
        accessorKey: "createdAt",
        header: "Created",
        cell: ({ row }) => (
          <span className="text-xs text-muted-foreground">
            {new Date(row.original.createdAt).toLocaleString()}
          </span>
        ),
      },
      { accessorKey: "message", header: "Message" },
    ],
    []
  );

  const table = useReactTable({
    data: rows,
    columns,
    state: { sorting, columnFilters },
    onSortingChange: setSorting,
    onColumnFiltersChange: setColumnFilters,
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });

  const eventIdFilter =
    (table.getColumn("eventId")?.getFilterValue() as string | undefined) ?? "";
  const statusFilter =
    (table.getColumn("status")?.getFilterValue() as string | undefined) ?? "";
  const statuses = [...new Set(rows.map((row) => row.status))].sort();

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between gap-2">
        <Badge variant="outline" className="font-mono text-[11px]">
          {table.getFilteredRowModel().rows.length}/{rows.length}
        </Badge>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => table.resetColumnFilters()}
        >
          Clear filters
        </Button>
      </div>
      <Table>
        <TableHeader>
          <TableRow>
            {table.getFlatHeaders().map((header) => (
              <TableHead key={header.id}>
                {header.column.getCanSort() ? (
                  <button
                    type="button"
                    className="inline-flex items-center gap-1 text-left"
                    onClick={header.column.getToggleSortingHandler()}
                  >
                    {flexRender(header.column.columnDef.header, header.getContext())}
                    {header.column.getIsSorted() === "asc" ? "▲" : null}
                    {header.column.getIsSorted() === "desc" ? "▼" : null}
                  </button>
                ) : (
                  flexRender(header.column.columnDef.header, header.getContext())
                )}
              </TableHead>
            ))}
          </TableRow>
          <TableRow>
            <TableHead>
              <Input
                value={eventIdFilter}
                onChange={(event) =>
                  table.getColumn("eventId")?.setFilterValue(event.target.value)
                }
                placeholder="Filter event"
                className="h-8"
              />
            </TableHead>
            <TableHead>
              <select
                className="h-8 w-full rounded-md border border-input bg-background/90 px-2 text-xs"
                value={statusFilter}
                onChange={(event) =>
                  table
                    .getColumn("status")
                    ?.setFilterValue(event.target.value === "all" ? undefined : event.target.value)
                }
              >
                <option value="all">All statuses</option>
                {statuses.map((value) => (
                  <option key={value} value={value}>
                    {value}
                  </option>
                ))}
              </select>
            </TableHead>
            <TableHead />
            <TableHead />
            <TableHead />
            <TableHead />
            <TableHead />
            <TableHead />
          </TableRow>
        </TableHeader>
        <TableBody>
          {table.getRowModel().rows.length === 0 ? (
            <TableRow>
              <TableCell colSpan={8} className="text-sm text-muted-foreground">
                No onboarding events match current filters.
              </TableCell>
            </TableRow>
          ) : (
            table.getRowModel().rows.map((row) => (
              <TableRow key={row.id}>
                {row.getVisibleCells().map((cell) => (
                  <TableCell
                    key={cell.id}
                    className={
                      cell.column.id === "eventId" || cell.column.id === "subscriptionId"
                        ? "font-mono text-xs"
                        : undefined
                    }
                  >
                    {flexRender(cell.column.columnDef.cell, cell.getContext())}
                  </TableCell>
                ))}
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </div>
  );
}

export default function AdminPanel({
  adminErrorMessage,
  adminIsSubmitting,
  adminResult,
  adminSnapshot,
  onIngestMarketplaceEvent,
  onRefreshSnapshot,
}: AdminPanelProps) {
  const [drawerOpen, setDrawerOpen] = useState(false);
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
  const canSubmit =
    eventId.trim() !== "" &&
    tenantId.trim() !== "" &&
    subscriptionId.trim() !== "" &&
    containerAppResourceId.trim() !== "";

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
            </TabsList>
            <TabsContent value="registrations">
              <RegistrationsDataTable registrations={registrations} />
            </TabsContent>
            <TabsContent value="events">
              <EventsDataTable events={events} />
            </TabsContent>
          </Tabs>
        </CardContent>
      </Card>
    </div>
  );
}
