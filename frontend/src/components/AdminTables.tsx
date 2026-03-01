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
import { Input } from "@/components/ui/input";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type {
  ForwarderLogRecord,
  MarketplaceEventRecord,
  TargetRegistrationRecord,
} from "@/lib/types";

type RegistrationRow = {
  record: TargetRegistrationRecord;
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

type ForwarderLogRow = {
  logId: string;
  level: string;
  message: string;
  eventId: string;
  targetId: string;
  subscriptionId: string;
  backendStatusCode: string;
  createdAt: string;
};

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

function logLevelVariant(
  level: string
): "default" | "secondary" | "destructive" | "outline" {
  if (level === "error") {
    return "destructive";
  }
  if (level === "warning") {
    return "secondary";
  }
  if (level === "info") {
    return "outline";
  }
  return "outline";
}

type RegistrationsDataTableProps = {
  registrations: TargetRegistrationRecord[];
  onEditRegistration: (registration: TargetRegistrationRecord) => void;
  onDeleteRegistration: (registration: TargetRegistrationRecord) => void;
  deletingTargetId: string | null;
};

export function RegistrationsDataTable({
  registrations,
  onEditRegistration,
  onDeleteRegistration,
  deletingTargetId,
}: RegistrationsDataTableProps) {
  const rows = useMemo<RegistrationRow[]>(
    () =>
      registrations.map((record) => ({
        record,
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
      {
        id: "actions",
        header: "Actions",
        enableSorting: false,
        enableColumnFilter: false,
        cell: ({ row }) => {
          const isDeleting = deletingTargetId === row.original.targetId;
          return (
            <div className="flex items-center justify-end gap-2">
              <Button
                type="button"
                size="sm"
                variant="outline"
                onClick={() => onEditRegistration(row.original.record)}
              >
                Edit
              </Button>
              <Button
                type="button"
                size="sm"
                variant="outline"
                className="border-destructive/70 text-destructive hover:bg-destructive/10"
                disabled={isDeleting}
                onClick={() => onDeleteRegistration(row.original.record)}
              >
                {isDeleting ? "Deleting..." : "Delete"}
              </Button>
            </div>
          );
        },
      },
    ],
    [deletingTargetId, onDeleteRegistration, onEditRegistration]
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
            <TableHead />
          </TableRow>
        </TableHeader>
        <TableBody>
          {table.getRowModel().rows.length === 0 ? (
            <TableRow>
              <TableCell colSpan={11} className="text-sm text-muted-foreground">
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

type EventsDataTableProps = {
  events: MarketplaceEventRecord[];
};

export function EventsDataTable({ events }: EventsDataTableProps) {
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

type ForwarderLogsDataTableProps = {
  logs: ForwarderLogRecord[];
};

export function ForwarderLogsDataTable({ logs }: ForwarderLogsDataTableProps) {
  const rows = useMemo<ForwarderLogRow[]>(
    () =>
      logs.map((record) => ({
        logId: record.log_id,
        level: record.level,
        message: record.message,
        eventId: record.event_id ?? "n/a",
        targetId: record.target_id ?? "n/a",
        subscriptionId: record.subscription_id ?? "n/a",
        backendStatusCode:
          record.backend_status_code === undefined || record.backend_status_code === null
            ? "n/a"
            : String(record.backend_status_code),
        createdAt: record.created_at,
      })),
    [logs]
  );

  const [sorting, setSorting] = useState<SortingState>([]);
  const [columnFilters, setColumnFilters] = useState<ColumnFiltersState>([]);

  const columns = useMemo<ColumnDef<ForwarderLogRow>[]>(
    () => [
      { accessorKey: "logId", header: "Log ID" },
      {
        accessorKey: "level",
        header: "Level",
        filterFn: (row, id, value) => {
          if (!value) {
            return true;
          }
          return row.getValue<string>(id) === value;
        },
        cell: ({ row }) => (
          <Badge variant={logLevelVariant(row.original.level)} className="uppercase">
            {row.original.level}
          </Badge>
        ),
      },
      { accessorKey: "message", header: "Message" },
      { accessorKey: "eventId", header: "Event" },
      { accessorKey: "targetId", header: "Target" },
      { accessorKey: "subscriptionId", header: "Subscription" },
      { accessorKey: "backendStatusCode", header: "Backend Status" },
      {
        accessorKey: "createdAt",
        header: "Created",
        cell: ({ row }) => (
          <span className="text-xs text-muted-foreground">
            {new Date(row.original.createdAt).toLocaleString()}
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

  const logIdFilter =
    (table.getColumn("logId")?.getFilterValue() as string | undefined) ?? "";
  const levelFilter =
    (table.getColumn("level")?.getFilterValue() as string | undefined) ?? "";
  const levels = [...new Set(rows.map((row) => row.level))].sort();

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
                value={logIdFilter}
                onChange={(event) =>
                  table.getColumn("logId")?.setFilterValue(event.target.value)
                }
                placeholder="Filter log"
                className="h-8"
              />
            </TableHead>
            <TableHead>
              <select
                className="h-8 w-full rounded-md border border-input bg-background/90 px-2 text-xs"
                value={levelFilter}
                onChange={(event) =>
                  table
                    .getColumn("level")
                    ?.setFilterValue(event.target.value === "all" ? undefined : event.target.value)
                }
              >
                <option value="all">All levels</option>
                {levels.map((value) => (
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
                No forwarder logs match current filters.
              </TableCell>
            </TableRow>
          ) : (
            table.getRowModel().rows.map((row) => (
              <TableRow key={row.id}>
                {row.getVisibleCells().map((cell) => (
                  <TableCell
                    key={cell.id}
                    className={
                      cell.column.id === "logId" || cell.column.id === "eventId"
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
