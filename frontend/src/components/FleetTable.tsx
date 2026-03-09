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
import { compareReleaseVersionsDesc } from "@/lib/releases";
import { usePersistentColumnVisibility } from "@/lib/table-visibility";
import type { Release, Target } from "@/lib/types";

type FleetTableProps = {
  targets: Target[];
  latestRelease: Release | null;
  onDeployLatestRelease: (releaseId: string) => void;
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
  health: string;
  latestStatus: "current" | "outdated" | "unknown";
};

function healthVariant(healthStatus: string): "default" | "secondary" | "destructive" | "outline" {
  if (healthStatus === "healthy") {
    return "default";
  }
  if (healthStatus === "degraded") {
    return "secondary";
  }
  return "destructive";
}

export default function FleetTable({ targets, latestRelease, onDeployLatestRelease }: FleetTableProps) {
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
        health: target.healthStatus ?? "registered",
        latestStatus:
          latestVersion.trim() === ""
            ? "unknown"
            : !target.lastDeployedRelease
              ? "unknown"
              : compareReleaseVersionsDesc(target.lastDeployedRelease, latestVersion) === 0
                ? "current"
                : "outdated",
      })),
    [latestVersion, targets]
  );

  const outdatedTargets = useMemo(
    () => rows.filter((row) => row.latestStatus === "outdated"),
    [rows]
  );
  const unknownVersionTargets = useMemo(
    () => rows.filter((row) => row.latestStatus === "unknown"),
    [rows]
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
      {
        accessorKey: "version",
        header: "Version",
        cell: ({ row }) => (
          <div className="flex flex-wrap items-center gap-2">
            <span>{row.original.version}</span>
            {row.original.latestStatus === "current" ? (
              <Badge variant="default">Latest</Badge>
            ) : null}
            {row.original.latestStatus === "outdated" ? (
              <Badge variant="secondary">Update available</Badge>
            ) : null}
            {row.original.latestStatus === "unknown" && latestVersion ? (
              <Badge variant="outline">Unknown vs latest</Badge>
            ) : null}
          </div>
        ),
      },
      {
        accessorKey: "health",
        header: "Health",
        filterFn: (row, id, value) => {
          if (!value) {
            return true;
          }
          return row.getValue<string>(id) === value;
        },
        cell: ({ row }) => (
          <Badge variant={healthVariant(row.original.health)} className="capitalize">
            {row.original.health}
          </Badge>
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
  const uniqueHealth = useMemo(
    () => [...new Set(rows.map((row) => row.health))].sort(),
    [rows]
  );

  const targetIdFilter = (table.getColumn("targetId")?.getFilterValue() as string | undefined) ?? "";
  const customerNameFilter =
    (table.getColumn("customerName")?.getFilterValue() as string | undefined) ?? "";
  const tenantIdFilter = (table.getColumn("tenantId")?.getFilterValue() as string | undefined) ?? "";
  const subscriptionIdFilter =
    (table.getColumn("subscriptionId")?.getFilterValue() as string | undefined) ?? "";
  const targetGroupFilter = (table.getColumn("targetGroup")?.getFilterValue() as string | undefined) ?? "";
  const regionFilter = (table.getColumn("region")?.getFilterValue() as string | undefined) ?? "";
  const tierFilter = (table.getColumn("tier")?.getFilterValue() as string | undefined) ?? "";
  const versionFilter = (table.getColumn("version")?.getFilterValue() as string | undefined) ?? "";
  const healthFilter = (table.getColumn("health")?.getFilterValue() as string | undefined) ?? "";
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
          onChange={(event) =>
            table.getColumn("customerName")?.setFilterValue(event.target.value)
          }
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
    if (columnId === "health") {
      return (
        <Select
          value={healthFilter || "all"}
          onValueChange={(value) =>
            table.getColumn("health")?.setFilterValue(value === "all" ? undefined : value)
          }
        >
          <SelectTrigger className="h-8 w-full bg-background/90 px-2 text-xs">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All health</SelectItem>
            {uniqueHealth.map((value) => (
              <SelectItem key={value} value={value}>
                {value}
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
        {latestRelease && outdatedTargets.length > 0 ? (
          <div className="mb-4 rounded-lg border border-primary/40 bg-primary/10 p-4">
            <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
              <div className="space-y-1">
                <p className="text-sm font-semibold">
                  New release {latestRelease.sourceVersion} is available.
                </p>
                <p className="text-sm text-muted-foreground">
                  {outdatedTargets.length} target{outdatedTargets.length === 1 ? "" : "s"} are behind the latest release.
                  {unknownVersionTargets.length > 0
                    ? ` ${unknownVersionTargets.length} target${unknownVersionTargets.length === 1 ? "" : "s"} have no known version yet.`
                    : ""}
                </p>
              </div>
              <Button
                type="button"
                onClick={() => {
                  if (latestRelease.id) {
                    onDeployLatestRelease(latestRelease.id);
                  }
                }}
                disabled={!latestRelease.id}
              >
                Deploy {latestRelease.sourceVersion}
              </Button>
            </div>
          </div>
        ) : null}
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
