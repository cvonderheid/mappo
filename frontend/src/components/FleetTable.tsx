import { useEffect, useMemo, useRef, useState } from "react";
import type { ReactNode } from "react";
import {
  type ColumnDef,
  type SortingState,
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  useReactTable,
} from "@tanstack/react-table";
import { toast } from "sonner";

import ColumnVisibilityMenu from "@/components/ColumnVisibilityMenu";
import DataTablePagination from "@/components/DataTablePagination";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardAction, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { checkTargetRuntimeHealth, listTargetsPage } from "@/lib/api";
import {
  targetLastDeploymentTone,
  targetLatestReleaseStatus,
  targetRuntimeStatus,
} from "@/lib/fleet";
import { usePersistentColumnVisibility } from "@/lib/table-visibility";
import type {
  PageMetadata,
  Release,
  Target,
  TargetPage,
  TargetRegistrationRecord,
  TargetRuntimeStatus,
  TargetStage,
} from "@/lib/types";

type FleetTableProps = {
  latestRelease: Release | null;
  registrations?: TargetRegistrationRecord[];
  refreshKey: number;
  selectedProjectId: string;
  deletingTargetId?: string | null;
  onAddTargets?: () => void;
  onDeleteRegistration?: (registration: TargetRegistrationRecord) => void;
  onEditRegistration?: (registration: TargetRegistrationRecord) => void;
  onRefreshRegistrations?: () => Promise<void>;
};

type FleetRow = {
  registration: TargetRegistrationRecord | null;
  targetId: string;
  customerName: string;
  tenantId: string;
  subscriptionId: string;
  targetGroup: string;
  region: string;
  tier: string;
  version: string;
  runtimeStatus: string;
  runtimeCheckedAt: string;
  lastDeploymentStatus: string;
  lastDeploymentAt: string;
  latestStatus: "current" | "outdated" | "unknown";
};

const EMPTY_PAGE: TargetPage = {
  items: [],
  page: {
    page: 0,
    size: 10,
    totalItems: 0,
    totalPages: 0,
  },
};

const EMPTY_PAGE_METADATA: PageMetadata = {
  page: 0,
  size: 10,
  totalItems: 0,
  totalPages: 0,
};

function runtimeVariant(runtimeStatus: string): "default" | "secondary" | "destructive" | "outline" {
  if (runtimeStatus === "healthy") {
    return "default";
  }
  if (runtimeStatus === "unhealthy") {
    return "destructive";
  }
  if (runtimeStatus === "unreachable") {
    return "secondary";
  }
  if (runtimeStatus === "unknown") {
    return "outline";
  }
  return "outline";
}

function lastDeploymentVariant(status: string): "default" | "secondary" | "destructive" | "outline" {
  if (status === "succeeded") {
    return "default";
  }
  if (status === "running" || status === "queued") {
    return "secondary";
  }
  if (status === "failed") {
    return "destructive";
  }
  return "outline";
}

function latestStatusLabel(status: FleetRow["latestStatus"]): string {
  if (status === "current") {
    return "Latest";
  }
  if (status === "outdated") {
    return "Update available";
  }
  return "Unknown vs latest";
}

function lastDeploymentLabel(status: string): string {
  if (status === "unknown") {
    return "No recent run";
  }
  if (status === "running") {
    return "In progress";
  }
  if (status === "queued") {
    return "Queued";
  }
  return status;
}

function formatTimestamp(value: string): string {
  if (!value) {
    return "";
  }
  const timestamp = new Date(value);
  if (Number.isNaN(timestamp.getTime())) {
    return "";
  }
  return timestamp.toLocaleString();
}

function optionValues(values: string[], selectedValue: string): string[] {
  const uniqueValues = new Set(values.filter((value) => value.trim() !== ""));
  if (selectedValue.trim() !== "" && selectedValue !== "all") {
    uniqueValues.add(selectedValue);
  }
  return [...uniqueValues].sort();
}

export default function FleetTable({
  latestRelease,
  registrations = [],
  refreshKey,
  selectedProjectId,
  deletingTargetId,
  onAddTargets,
  onDeleteRegistration,
  onEditRegistration,
  onRefreshRegistrations,
}: FleetTableProps) {
  const latestVersion = latestRelease?.sourceVersion ?? "";
  const [pageData, setPageData] = useState<TargetPage>(EMPTY_PAGE);
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [checkingHealth, setCheckingHealth] = useState(false);
  const [refreshingRegistrations, setRefreshingRegistrations] = useState(false);
  const [manualRefreshKey, setManualRefreshKey] = useState(0);
  const [errorMessage, setErrorMessage] = useState("");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [targetIdFilter, setTargetIdFilter] = useState("");
  const [customerNameFilter, setCustomerNameFilter] = useState("");
  const [tenantIdFilter, setTenantIdFilter] = useState("");
  const [subscriptionIdFilter, setSubscriptionIdFilter] = useState("");
  const [targetGroupFilter, setTargetGroupFilter] = useState("all");
  const [regionFilter, setRegionFilter] = useState("all");
  const [tierFilter, setTierFilter] = useState("all");
  const [versionFilter, setVersionFilter] = useState("");
  const [runtimeFilter, setRuntimeFilter] = useState<TargetRuntimeStatus | "all">("all");
  const [lastDeploymentFilter, setLastDeploymentFilter] = useState<TargetStage | "all">("all");
  const [sorting, setSorting] = useState<SortingState>([]);
  const [columnVisibility, setColumnVisibility] =
    usePersistentColumnVisibility("fleet-targets");
  const previousQuerySignatureRef = useRef<string>("");
  const registrationByTargetId = useMemo(() => {
    return new Map(
      registrations
        .filter((registration) => (registration.targetId ?? "").trim() !== "")
        .map((registration) => [registration.targetId ?? "", registration])
    );
  }, [registrations]);

  const querySignature = useMemo(
    () =>
      JSON.stringify({
        selectedProjectId,
        manualRefreshKey,
        page,
        pageSize,
        targetIdFilter,
        customerNameFilter,
        tenantIdFilter,
        subscriptionIdFilter,
        targetGroupFilter,
        regionFilter,
        tierFilter,
        versionFilter,
        runtimeFilter,
        lastDeploymentFilter,
      }),
    [
      customerNameFilter,
      lastDeploymentFilter,
      manualRefreshKey,
      page,
      pageSize,
      regionFilter,
      runtimeFilter,
      selectedProjectId,
      subscriptionIdFilter,
      targetGroupFilter,
      targetIdFilter,
      tenantIdFilter,
      tierFilter,
      versionFilter,
    ]
  );

  useEffect(() => {
    let active = true;
    const hasVisibleData =
      (pageData.items?.length ?? 0) > 0 || (pageData.page?.totalItems ?? 0) > 0;
    const isBackgroundRefresh = previousQuerySignatureRef.current === querySignature;
    previousQuerySignatureRef.current = querySignature;

    if (!selectedProjectId) {
      setPageData(EMPTY_PAGE);
      setLoading(false);
      setRefreshing(false);
      setErrorMessage("");
      return () => {
        active = false;
      };
    }

    if (hasVisibleData && !isBackgroundRefresh) {
      setRefreshing(true);
    } else if (!hasVisibleData) {
      setLoading(true);
    }
    void listTargetsPage({
      page,
      size: pageSize,
      projectId: selectedProjectId || undefined,
      targetId: targetIdFilter || undefined,
      customerName: customerNameFilter || undefined,
      tenantId: tenantIdFilter || undefined,
      subscriptionId: subscriptionIdFilter || undefined,
      ring: targetGroupFilter === "all" ? undefined : targetGroupFilter,
      region: regionFilter === "all" ? undefined : regionFilter,
      tier: tierFilter === "all" ? undefined : tierFilter,
      version: versionFilter || undefined,
      runtimeStatus: runtimeFilter === "all" ? undefined : runtimeFilter,
      lastDeploymentStatus:
        lastDeploymentFilter === "all" ? undefined : lastDeploymentFilter,
    })
      .then((result) => {
        if (!active) {
          return;
        }
        setPageData(result);
        setErrorMessage("");
      })
      .catch((error) => {
        if (!active) {
          return;
        }
        if (hasVisibleData) {
          setErrorMessage("");
        } else {
          setPageData(EMPTY_PAGE);
          setErrorMessage((error as Error).message);
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
          setRefreshing(false);
        }
      });

    return () => {
      active = false;
    };
  }, [
    querySignature,
    refreshKey,
  ]);

  async function handleCheckHealth(): Promise<void> {
    if (!selectedProjectId || checkingHealth) {
      return;
    }

    setCheckingHealth(true);
    try {
      const result = await checkTargetRuntimeHealth(selectedProjectId);
      if (result.inProgress) {
        toast.info("Target health check is already running.");
        return;
      }
      const checkedCount = result.checkedCount ?? 0;
      toast.success(
        checkedCount === 1
          ? "Checked health for 1 target."
          : `Checked health for ${checkedCount} targets.`
      );
      setManualRefreshKey((value) => value + 1);
    } catch (error) {
      toast.error((error as Error).message);
    } finally {
      setCheckingHealth(false);
    }
  }

  async function handleRefreshTargets(): Promise<void> {
    setRefreshingRegistrations(true);
    try {
      await onRefreshRegistrations?.();
      setManualRefreshKey((value) => value + 1);
    } catch (error) {
      toast.error((error as Error).message);
    } finally {
      setRefreshingRegistrations(false);
    }
  }

  const rows = useMemo<FleetRow[]>(
    () =>
      (pageData.items ?? []).map((target: Target) => ({
        registration: registrationByTargetId.get(target.id ?? "") ?? null,
        targetId: target.id ?? "unknown",
        customerName: target.customerName ?? "unknown",
        tenantId: target.tenantId ?? "unknown",
        subscriptionId: target.subscriptionId ?? "unknown",
        targetGroup: target.tags?.ring ?? "unassigned",
        region: target.tags?.region ?? "unknown",
        tier: target.tags?.tier ?? "unknown",
        version: target.lastDeployedRelease ?? "unknown",
        runtimeStatus: targetRuntimeStatus(target),
        runtimeCheckedAt: target.runtimeCheckedAt ?? "",
        lastDeploymentStatus: targetLastDeploymentTone(target),
        lastDeploymentAt: target.lastDeploymentAt ?? "",
        latestStatus: targetLatestReleaseStatus(target, latestVersion),
      })),
    [latestVersion, pageData.items, registrationByTargetId]
  );

  const columns = useMemo<ColumnDef<FleetRow>[]>(
    () => [
      { accessorKey: "targetId", header: "Target" },
      { accessorKey: "customerName", header: "Customer" },
      { accessorKey: "tenantId", header: "Tenant" },
      { accessorKey: "subscriptionId", header: "Subscription" },
      { accessorKey: "targetGroup", header: "Target Group" },
      { accessorKey: "region", header: "Region" },
      { accessorKey: "tier", header: "Tier" },
      {
        accessorKey: "version",
        header: "Version",
        cell: ({ row }) => (
          <div className="space-y-1">
            <p>{row.original.version}</p>
            <Badge
              variant={
                row.original.latestStatus === "current"
                  ? "default"
                  : row.original.latestStatus === "outdated"
                    ? "secondary"
                    : "outline"
              }
            >
              {latestStatusLabel(row.original.latestStatus)}
            </Badge>
          </div>
        ),
      },
      {
        accessorKey: "runtimeStatus",
        header: "Runtime",
        cell: ({ row }) => (
          <div className="space-y-1">
            <Badge variant={runtimeVariant(row.original.runtimeStatus)} className="capitalize">
              {row.original.runtimeStatus}
            </Badge>
            {row.original.runtimeCheckedAt ? (
              <p className="text-[11px] text-muted-foreground">
                {formatTimestamp(row.original.runtimeCheckedAt)}
              </p>
            ) : null}
          </div>
        ),
      },
      {
        accessorKey: "lastDeploymentStatus",
        header: "Last Deployment",
        cell: ({ row }) => (
          <div className="space-y-1">
            <Badge
              variant={lastDeploymentVariant(row.original.lastDeploymentStatus)}
              className="capitalize"
            >
              {lastDeploymentLabel(row.original.lastDeploymentStatus)}
            </Badge>
            {row.original.lastDeploymentAt ? (
              <p className="text-[11px] text-muted-foreground">
                {formatTimestamp(row.original.lastDeploymentAt)}
              </p>
            ) : null}
          </div>
        ),
      },
      {
        id: "actions",
        header: "Actions",
        enableHiding: false,
        enableSorting: false,
        cell: ({ row }) => {
          const registration = row.original.registration;
          const isDeleting = deletingTargetId === row.original.targetId;
          return (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button
                  type="button"
                  variant="outline"
                  size="sm"
                  className="h-7 w-7 p-0 font-mono"
                  aria-label={`Actions for ${row.original.targetId}`}
                  disabled={!registration}
                >
                  ...
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end">
                <DropdownMenuItem
                  disabled={!registration}
                  onSelect={() => {
                    if (registration) {
                      onEditRegistration?.(registration);
                    }
                  }}
                >
                  Edit
                </DropdownMenuItem>
                <DropdownMenuItem
                  className="text-destructive focus:text-destructive"
                  disabled={!registration || isDeleting}
                  onSelect={() => {
                    if (registration) {
                      onDeleteRegistration?.(registration);
                    }
                  }}
                >
                  {isDeleting ? "Deleting..." : "Delete"}
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          );
        },
      },
    ],
    [deletingTargetId, onDeleteRegistration, onEditRegistration]
  );

  const table = useReactTable({
    data: rows,
    columns,
    state: {
      sorting,
      columnVisibility,
    },
    onSortingChange: setSorting,
    onColumnVisibilityChange: setColumnVisibility,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });

  const visibleColumnCount = table.getVisibleLeafColumns().length;
  const pageMetadata: PageMetadata = pageData.page ?? EMPTY_PAGE_METADATA;
  const uniqueTargetGroups = optionValues(rows.map((row) => row.targetGroup), targetGroupFilter);
  const uniqueRegions = optionValues(rows.map((row) => row.region), regionFilter);
  const uniqueTiers = optionValues(rows.map((row) => row.tier), tierFilter);
  const uniqueRuntimeStatuses = optionValues(rows.map((row) => row.runtimeStatus), runtimeFilter);
  const uniqueLastDeploymentStatuses = optionValues(
    rows.map((row) => row.lastDeploymentStatus),
    lastDeploymentFilter
  );

  function clearFilters(): void {
    setTargetIdFilter("");
    setCustomerNameFilter("");
    setTenantIdFilter("");
    setSubscriptionIdFilter("");
    setTargetGroupFilter("all");
    setRegionFilter("all");
    setTierFilter("all");
    setVersionFilter("");
    setRuntimeFilter("all");
    setLastDeploymentFilter("all");
    setPage(0);
  }

  function renderFilterCell(columnId: string): ReactNode {
    if (columnId === "targetId") {
      return (
        <Input
          value={targetIdFilter}
          onChange={(event) => {
            setTargetIdFilter(event.target.value);
            setPage(0);
          }}
          placeholder="Filter target"
          className="h-8"
        />
      );
    }
    if (columnId === "customerName") {
      return (
        <Input
          value={customerNameFilter}
          onChange={(event) => {
            setCustomerNameFilter(event.target.value);
            setPage(0);
          }}
          placeholder="Filter customer"
          className="h-8"
        />
      );
    }
    if (columnId === "tenantId") {
      return (
        <Input
          value={tenantIdFilter}
          onChange={(event) => {
            setTenantIdFilter(event.target.value);
            setPage(0);
          }}
          placeholder="Filter tenant"
          className="h-8"
        />
      );
    }
    if (columnId === "subscriptionId") {
      return (
        <Input
          value={subscriptionIdFilter}
          onChange={(event) => {
            setSubscriptionIdFilter(event.target.value);
            setPage(0);
          }}
          placeholder="Filter subscription"
          className="h-8"
        />
      );
    }
    if (columnId === "targetGroup") {
      return (
        <Select
          value={targetGroupFilter}
          onValueChange={(value) => {
            setTargetGroupFilter(value);
            setPage(0);
          }}
        >
          <SelectTrigger className="h-8 w-full bg-background/90 px-2 text-xs">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All groups</SelectItem>
            {uniqueTargetGroups.map((value) => (
              <SelectItem key={value} value={value}>
                {value}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      );
    }
    if (columnId === "region") {
      return (
        <Select
          value={regionFilter}
          onValueChange={(value) => {
            setRegionFilter(value);
            setPage(0);
          }}
        >
          <SelectTrigger className="h-8 w-full bg-background/90 px-2 text-xs">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All regions</SelectItem>
            {uniqueRegions.map((value) => (
              <SelectItem key={value} value={value}>
                {value}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      );
    }
    if (columnId === "tier") {
      return (
        <Select
          value={tierFilter}
          onValueChange={(value) => {
            setTierFilter(value);
            setPage(0);
          }}
        >
          <SelectTrigger className="h-8 w-full bg-background/90 px-2 text-xs">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All tiers</SelectItem>
            {uniqueTiers.map((value) => (
              <SelectItem key={value} value={value}>
                {value}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      );
    }
    if (columnId === "version") {
      return (
        <Input
          value={versionFilter}
          onChange={(event) => {
            setVersionFilter(event.target.value);
            setPage(0);
          }}
          placeholder="Filter version"
          className="h-8"
        />
      );
    }
    if (columnId === "runtimeStatus") {
      return (
        <Select
          value={runtimeFilter}
          onValueChange={(value) => {
            setRuntimeFilter(value as TargetRuntimeStatus | "all");
            setPage(0);
          }}
        >
          <SelectTrigger className="h-8 w-full bg-background/90 px-2 text-xs">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All runtimes</SelectItem>
            {uniqueRuntimeStatuses.map((value) => (
              <SelectItem key={value} value={value}>
                {value}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      );
    }
    if (columnId === "lastDeploymentStatus") {
      return (
        <Select
          value={lastDeploymentFilter}
          onValueChange={(value) => {
            setLastDeploymentFilter(value as TargetStage | "all");
            setPage(0);
          }}
        >
          <SelectTrigger className="h-8 w-full bg-background/90 px-2 text-xs">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All deployments</SelectItem>
            {uniqueLastDeploymentStatuses.map((value) => (
              <SelectItem key={value} value={value}>
                {lastDeploymentLabel(value)}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      );
    }
    return null;
  }

  return (
    <Card className="glass-card animate-fade-up [animation-delay:80ms] [animation-fill-mode:forwards]">
      <CardHeader className="flex-row items-start justify-between space-y-0">
        <CardTitle>Target Inventory</CardTitle>
        <CardAction>
          <span
            className={`inline-flex min-w-[4.5rem] justify-end text-xs text-muted-foreground transition-opacity ${
              refreshing ? "opacity-100" : "opacity-0"
            }`}
          >
            Syncing…
          </span>
          <Button
            type="button"
            variant="outline"
            size="sm"
            disabled={!selectedProjectId || checkingHealth}
            onClick={() => void handleCheckHealth()}
          >
            {checkingHealth ? "Checking..." : "Check health"}
          </Button>
          {onAddTargets ? (
            <Button type="button" size="sm" onClick={onAddTargets}>
              Add Targets
            </Button>
          ) : null}
          {onRefreshRegistrations ? (
            <Button
              type="button"
              variant="outline"
              size="sm"
              disabled={refreshingRegistrations}
              onClick={() => void handleRefreshTargets()}
            >
              {refreshingRegistrations ? "Refreshing..." : "Refresh Targets"}
            </Button>
          ) : null}
          <ColumnVisibilityMenu table={table} />
          <Button type="button" variant="outline" size="sm" onClick={clearFilters}>
            Clear filters
          </Button>
        </CardAction>
      </CardHeader>
      <CardContent className="space-y-3">
        <Table>
          <TableHeader>
            <TableRow>
              {table.getFlatHeaders().map((header) => (
                <TableHead key={header.id}>
                  <button
                    type="button"
                    className="inline-flex items-center gap-1 text-left"
                    onClick={header.column.getToggleSortingHandler()}
                  >
                    {flexRender(header.column.columnDef.header, header.getContext())}
                    {header.column.getIsSorted() === "asc" ? "▲" : null}
                    {header.column.getIsSorted() === "desc" ? "▼" : null}
                  </button>
                </TableHead>
              ))}
            </TableRow>
            <TableRow>
              {table.getVisibleLeafColumns().map((column) => (
                <TableHead key={`filter-${column.id}`}>{renderFilterCell(column.id)}</TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {loading ? (
              <TableRow>
                <TableCell className="text-sm text-muted-foreground" colSpan={visibleColumnCount}>
                  Loading targets...
                </TableCell>
              </TableRow>
            ) : errorMessage ? (
              <TableRow>
                <TableCell className="text-sm text-destructive" colSpan={visibleColumnCount}>
                  {errorMessage}
                </TableCell>
              </TableRow>
            ) : table.getRowModel().rows.length === 0 ? (
              <TableRow>
                <TableCell className="text-sm text-muted-foreground" colSpan={visibleColumnCount}>
                  No targets match current filters.
                </TableCell>
              </TableRow>
            ) : (
              table.getRowModel().rows.map((row) => (
                <TableRow key={row.id}>
                  {row.getVisibleCells().map((cell) => (
                    <TableCell
                      key={cell.id}
                      className={cell.column.id === "targetId" ? "font-mono text-xs" : undefined}
                    >
                      {flexRender(cell.column.columnDef.cell, cell.getContext())}
                    </TableCell>
                  ))}
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
        <DataTablePagination
          page={pageMetadata.page ?? page}
          pageSize={pageMetadata.size ?? pageSize}
          totalItems={pageMetadata.totalItems ?? 0}
          totalPages={pageMetadata.totalPages ?? 0}
          onPageChange={setPage}
          onPageSizeChange={(size) => {
            setPageSize(size);
            setPage(0);
          }}
          noun="targets"
        />
      </CardContent>
    </Card>
  );
}
