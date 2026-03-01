import { useMemo, useState } from "react";
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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { Target } from "@/lib/types";

type FleetTableProps = {
  targets: Target[];
};

type FleetRow = {
  targetId: string;
  tenantId: string;
  subscriptionId: string;
  targetGroup: string;
  region: string;
  tier: string;
  version: string;
  health: string;
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

export default function FleetTable({ targets }: FleetTableProps) {
  const rows = useMemo<FleetRow[]>(
    () =>
      targets.map((target) => ({
        targetId: target.id,
        tenantId: target.tenant_id,
        subscriptionId: target.subscription_id,
        targetGroup: target.tags.ring ?? "unassigned",
        region: target.tags.region ?? "unknown",
        tier: target.tags.tier ?? "unknown",
        version: target.last_deployed_release,
        health: target.health_status,
      })),
    [targets]
  );

  const [sorting, setSorting] = useState<SortingState>([]);
  const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>([]);

  const columns = useMemo<ColumnDef<FleetRow>[]>(
    () => [
      { accessorKey: "targetId", header: "Target" },
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
      { accessorKey: "version", header: "Version" },
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
    },
    onSortingChange: setSorting,
    onColumnFiltersChange: setColumnFilters,
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
  const tenantIdFilter = (table.getColumn("tenantId")?.getFilterValue() as string | undefined) ?? "";
  const subscriptionIdFilter =
    (table.getColumn("subscriptionId")?.getFilterValue() as string | undefined) ?? "";
  const targetGroupFilter = (table.getColumn("targetGroup")?.getFilterValue() as string | undefined) ?? "";
  const regionFilter = (table.getColumn("region")?.getFilterValue() as string | undefined) ?? "";
  const tierFilter = (table.getColumn("tier")?.getFilterValue() as string | undefined) ?? "";
  const versionFilter = (table.getColumn("version")?.getFilterValue() as string | undefined) ?? "";
  const healthFilter = (table.getColumn("health")?.getFilterValue() as string | undefined) ?? "";

  return (
    <Card className="glass-card animate-fade-up [animation-delay:80ms] [animation-fill-mode:forwards]">
      <CardHeader className="flex-row items-center justify-between space-y-0">
        <CardTitle>Fleet Targets</CardTitle>
        <div className="flex items-center gap-2">
          <Badge variant="outline" className="font-mono text-[11px]">
            {filteredCount}/{rows.length} subscriptions
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
              <TableHead>
                <Input
                  value={targetIdFilter}
                  onChange={(event) => table.getColumn("targetId")?.setFilterValue(event.target.value)}
                  placeholder="Filter target"
                  className="h-8"
                />
              </TableHead>
              <TableHead>
                <Input
                  value={tenantIdFilter}
                  onChange={(event) => table.getColumn("tenantId")?.setFilterValue(event.target.value)}
                  placeholder="Filter tenant"
                  className="h-8"
                />
              </TableHead>
              <TableHead>
                <Input
                  value={subscriptionIdFilter}
                  onChange={(event) =>
                    table.getColumn("subscriptionId")?.setFilterValue(event.target.value)
                  }
                  placeholder="Filter subscription"
                  className="h-8"
                />
              </TableHead>
              <TableHead>
                <select
                  className="h-8 w-full rounded-md border border-input bg-background/90 px-2 text-xs"
                  value={targetGroupFilter}
                  onChange={(event) =>
                    table.getColumn("targetGroup")?.setFilterValue(
                      event.target.value === "all" ? undefined : event.target.value
                    )
                  }
                >
                  <option value="all">All groups</option>
                  {uniqueTargetGroups.map((value) => (
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
                    table.getColumn("region")?.setFilterValue(
                      event.target.value === "all" ? undefined : event.target.value
                    )
                  }
                >
                  <option value="all">All regions</option>
                  {uniqueRegions.map((value) => (
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
                    table.getColumn("tier")?.setFilterValue(
                      event.target.value === "all" ? undefined : event.target.value
                    )
                  }
                >
                  <option value="all">All tiers</option>
                  {uniqueTiers.map((value) => (
                    <option key={value} value={value}>
                      {value}
                    </option>
                  ))}
                </select>
              </TableHead>
              <TableHead>
                <Input
                  value={versionFilter}
                  onChange={(event) => table.getColumn("version")?.setFilterValue(event.target.value)}
                  placeholder="Filter version"
                  className="h-8"
                />
              </TableHead>
              <TableHead>
                <select
                  className="h-8 w-full rounded-md border border-input bg-background/90 px-2 text-xs"
                  value={healthFilter}
                  onChange={(event) =>
                    table.getColumn("health")?.setFilterValue(
                      event.target.value === "all" ? undefined : event.target.value
                    )
                  }
                >
                  <option value="all">All health</option>
                  {uniqueHealth.map((value) => (
                    <option key={value} value={value}>
                      {value}
                    </option>
                  ))}
                </select>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {table.getRowModel().rows.length === 0 ? (
              <TableRow>
                <TableCell className="text-sm text-muted-foreground" colSpan={8}>
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
