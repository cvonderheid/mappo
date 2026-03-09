import { useMemo, useState } from "react";
import type { ReactNode } from "react";
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
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import ColumnVisibilityMenu from "@/components/ColumnVisibilityMenu";
import {
  targetLastDeploymentTone,
  targetLatestReleaseStatus,
  targetRuntimeStatus,
} from "@/lib/fleet";
import { usePersistentColumnVisibility } from "@/lib/table-visibility";
import type { Release, Target } from "@/lib/types";

type FleetTableProps = {
  targets: Target[];
  latestRelease: Release | null;
};

type FleetRow = {
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

function runtimeVariant(runtimeStatus: string): "default" | "secondary" | "destructive" | "outline" {
  if (runtimeStatus === "healthy") {
    return "default";
  }
  if (runtimeStatus === "degraded") {
    return "secondary";
  }
  if (runtimeStatus === "registered" || runtimeStatus === "unknown") {
    return "outline";
  }
  return "destructive";
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

export default function FleetTable({ targets, latestRelease }: FleetTableProps) {
  const latestVersion = latestRelease?.sourceVersion ?? "";
  const rows = useMemo<FleetRow[]>(
    () =>
      targets.map((target) => ({
        targetId: target.id ?? "unknown",
        customerName: target.customerName ?? "unknown",
        tenantId: target.tenantId ?? "unknown",
        subscriptionId: target.subscriptionId ?? "unknown",
        targetGroup: target.tags?.ring ?? "unassigned",
        region: target.tags?.region ?? "unknown",
        tier: target.tags?.tier ?? "unknown",
        version: target.lastDeployedRelease ?? "unknown",
        runtimeStatus: targetRuntimeStatus(target),
        runtimeCheckedAt: target.lastCheckInAt ?? "",
        lastDeploymentStatus: targetLastDeploymentTone(target),
        lastDeploymentAt: target.lastDeploymentAt ?? "",
        latestStatus: targetLatestReleaseStatus(target, latestVersion),
      })),
    [latestVersion, targets]
  );

  const [sorting, setSorting] = useState<SortingState>([]);
  const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>([]);
  const [columnVisibility, setColumnVisibility] =
    usePersistentColumnVisibility("fleet-targets");

  const columns = useMemo<ColumnDef<FleetRow>[]>(
    () => [
      { accessorKey: "targetId", header: "Target" },
      { accessorKey: "customerName", header: "Customer" },
      { accessorKey: "tenantId", header: "Tenant" },
      { accessorKey: "subscriptionId", header: "Subscription" },
      {
        accessorKey: "targetGroup",
        header: "Target Group",
        filterFn: (row, id, value) => !value || row.getValue<string>(id) === value,
      },
      {
        accessorKey: "region",
        header: "Region",
        filterFn: (row, id, value) => !value || row.getValue<string>(id) === value,
      },
      {
        accessorKey: "tier",
        header: "Tier",
        filterFn: (row, id, value) => !value || row.getValue<string>(id) === value,
      },
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
        filterFn: (row, id, value) => !value || row.getValue<string>(id) === value,
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
        filterFn: (row, id, value) => !value || row.getValue<string>(id) === value,
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
    ],
    []
  );

  const table = useReactTable({
    data: rows,
    columns,
    state: {
      sorting,
      columnFilters,
      columnVisibility,
    },
    onSortingChange: setSorting,
    onColumnFiltersChange: setColumnFilters,
    onColumnVisibilityChange: setColumnVisibility,
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });

  const filteredCount = table.getFilteredRowModel().rows.length;
  const uniqueTargetGroups = useMemo(
    () => [...new Set(rows.map((row) => row.targetGroup))].sort(),
    [rows]
  );
  const uniqueRegions = useMemo(
    () => [...new Set(rows.map((row) => row.region))].sort(),
    [rows]
  );
  const uniqueTiers = useMemo(
    () => [...new Set(rows.map((row) => row.tier))].sort(),
    [rows]
  );
  const uniqueRuntimeStatuses = useMemo(
    () => [...new Set(rows.map((row) => row.runtimeStatus))].sort(),
    [rows]
  );
  const uniqueLastDeploymentStatuses = useMemo(
    () => [...new Set(rows.map((row) => row.lastDeploymentStatus))].sort(),
    [rows]
  );

  const targetIdFilter = (table.getColumn("targetId")?.getFilterValue() as string | undefined) ?? "";
  const customerNameFilter =
    (table.getColumn("customerName")?.getFilterValue() as string | undefined) ?? "";
  const tenantIdFilter = (table.getColumn("tenantId")?.getFilterValue() as string | undefined) ?? "";
  const subscriptionIdFilter =
    (table.getColumn("subscriptionId")?.getFilterValue() as string | undefined) ?? "";
  const targetGroupFilter =
    (table.getColumn("targetGroup")?.getFilterValue() as string | undefined) ?? "";
  const regionFilter = (table.getColumn("region")?.getFilterValue() as string | undefined) ?? "";
  const tierFilter = (table.getColumn("tier")?.getFilterValue() as string | undefined) ?? "";
  const versionFilter = (table.getColumn("version")?.getFilterValue() as string | undefined) ?? "";
  const runtimeFilter =
    (table.getColumn("runtimeStatus")?.getFilterValue() as string | undefined) ?? "";
  const lastDeploymentFilter =
    (table.getColumn("lastDeploymentStatus")?.getFilterValue() as string | undefined) ?? "";
  const visibleColumnCount = table.getVisibleLeafColumns().length;

  function renderFilterCell(columnId: string): ReactNode {
    if (columnId === "targetId") {
      return (
        <Input
          value={targetIdFilter}
          onChange={(event) => table.getColumn("targetId")?.setFilterValue(event.target.value)}
          placeholder="Filter target"
          className="h-8"
        />
      );
    }
    if (columnId === "customerName") {
      return (
        <Input
          value={customerNameFilter}
          onChange={(event) => table.getColumn("customerName")?.setFilterValue(event.target.value)}
          placeholder="Filter customer"
          className="h-8"
        />
      );
    }
    if (columnId === "tenantId") {
      return (
        <Input
          value={tenantIdFilter}
          onChange={(event) => table.getColumn("tenantId")?.setFilterValue(event.target.value)}
          placeholder="Filter tenant"
          className="h-8"
        />
      );
    }
    if (columnId === "subscriptionId") {
      return (
        <Input
          value={subscriptionIdFilter}
          onChange={(event) =>
            table.getColumn("subscriptionId")?.setFilterValue(event.target.value)
          }
          placeholder="Filter subscription"
          className="h-8"
        />
      );
    }
    if (columnId === "targetGroup") {
      return (
        <Select
          value={targetGroupFilter || "all"}
          onValueChange={(value) =>
            table.getColumn("targetGroup")?.setFilterValue(value === "all" ? undefined : value)
          }
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
          value={regionFilter || "all"}
          onValueChange={(value) =>
            table.getColumn("region")?.setFilterValue(value === "all" ? undefined : value)
          }
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
          value={tierFilter || "all"}
          onValueChange={(value) =>
            table.getColumn("tier")?.setFilterValue(value === "all" ? undefined : value)
          }
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
          onChange={(event) => table.getColumn("version")?.setFilterValue(event.target.value)}
          placeholder="Filter version"
          className="h-8"
        />
      );
    }
    if (columnId === "runtimeStatus") {
      return (
        <Select
          value={runtimeFilter || "all"}
          onValueChange={(value) =>
            table.getColumn("runtimeStatus")?.setFilterValue(value === "all" ? undefined : value)
          }
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
          value={lastDeploymentFilter || "all"}
          onValueChange={(value) =>
            table
              .getColumn("lastDeploymentStatus")
              ?.setFilterValue(value === "all" ? undefined : value)
          }
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
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <CardTitle>Fleet Targets</CardTitle>
        <div className="flex items-center gap-2">
          <Badge variant="outline" className="font-mono text-[11px]">
            {filteredCount}/{rows.length} subscriptions
          </Badge>
          <ColumnVisibilityMenu table={table} />
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={() => table.resetColumnFilters()}
          >
            Clear filters
          </Button>
        </div>
      </CardHeader>
      <CardContent>
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
            {table.getRowModel().rows.length === 0 ? (
              <TableRow>
                <TableCell className="text-sm text-muted-foreground" colSpan={visibleColumnCount}>
                  No targets match current column filters.
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
      </CardContent>
    </Card>
  );
}
