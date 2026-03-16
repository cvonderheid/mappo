import { useEffect, useMemo, useState } from "react";
import type { ReactNode } from "react";
import {
  type ColumnDef,
  type SortingState,
  flexRender,
  getCoreRowModel,
  getSortedRowModel,
  useReactTable,
} from "@tanstack/react-table";

import ColumnVisibilityMenu from "@/components/ColumnVisibilityMenu";
import DataTablePagination from "@/components/DataTablePagination";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import {
  adminListForwarderLogs,
  adminListMarketplaceEvents,
  adminListReleaseWebhookDeliveries,
  adminListTargetRegistrations,
} from "@/lib/api";
import { usePersistentColumnVisibility } from "@/lib/table-visibility";
import type {
  ForwarderLogPage,
  ForwarderLogLevel,
  ForwarderLogRecord,
  MarketplaceEventPage,
  MarketplaceEventRecord,
  MarketplaceEventStatus,
  PageMetadata,
  ReleaseWebhookDeliveryPage,
  ReleaseWebhookDeliveryRecord,
  ReleaseWebhookStatus,
  TargetRegistrationPage,
  TargetRegistrationRecord,
} from "@/lib/types";

type RegistrationRow = {
  record: TargetRegistrationRecord;
  targetId: string;
  displayName: string;
  customerName: string;
  deploymentStackName: string;
  registryAuthMode: string;
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

type ReleaseWebhookRow = {
  id: string;
  externalDeliveryId: string;
  eventType: string;
  repo: string;
  ref: string;
  status: string;
  message: string;
  createdCount: string;
  receivedAt: string;
};

const EMPTY_PAGE: PageMetadata = {
  page: 0,
  size: 10,
  totalItems: 0,
  totalPages: 0,
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
  if (status === "duplicate" || status === "skipped") {
    return "secondary";
  }
  if (status === "rejected" || status === "failed") {
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

function releaseWebhookStatusVariant(
  status: string
): "default" | "secondary" | "destructive" | "outline" {
  if (status === "applied") {
    return "default";
  }
  if (status === "skipped") {
    return "secondary";
  }
  if (status === "failed") {
    return "destructive";
  }
  return "outline";
}

function optionValues(values: string[], selectedValue: string): string[] {
  const uniqueValues = new Set(values.filter((value) => value.trim() !== ""));
  if (selectedValue.trim() !== "" && selectedValue !== "all") {
    uniqueValues.add(selectedValue);
  }
  return [...uniqueValues].sort();
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

type TableFrameProps = {
  table: ReturnType<typeof useReactTable<any>>;
  page: PageMetadata;
  noun: string;
  loading: boolean;
  refreshing?: boolean;
  errorMessage: string;
  emptyMessage: string;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  onClearFilters: () => void;
  headerActions?: ReactNode;
  renderFilterCell: (columnId: string) => ReactNode;
};

function TableFrame({
  table,
  page,
  noun,
  loading,
  refreshing = false,
  errorMessage,
  emptyMessage,
  onPageChange,
  onPageSizeChange,
  onClearFilters,
  headerActions,
  renderFilterCell,
}: TableFrameProps) {
  const visibleColumnCount = table.getVisibleLeafColumns().length;

  return (
    <div className="space-y-2">
      <div className="flex items-center justify-end gap-2">
        {refreshing ? <span className="text-xs text-muted-foreground">Syncing…</span> : null}
        {headerActions}
        <ColumnVisibilityMenu table={table} />
        <Button type="button" variant="outline" size="sm" onClick={onClearFilters}>
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
            {table.getVisibleLeafColumns().map((column) => (
              <TableHead key={`filter-${column.id}`}>{renderFilterCell(column.id)}</TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {loading ? (
            <TableRow>
              <TableCell colSpan={visibleColumnCount} className="text-sm text-muted-foreground">
                Loading...
              </TableCell>
            </TableRow>
          ) : errorMessage ? (
            <TableRow>
              <TableCell colSpan={visibleColumnCount} className="text-sm text-destructive">
                {errorMessage}
              </TableCell>
            </TableRow>
          ) : table.getRowModel().rows.length === 0 ? (
            <TableRow>
              <TableCell colSpan={visibleColumnCount} className="text-sm text-muted-foreground">
                {emptyMessage}
              </TableCell>
            </TableRow>
          ) : (
            table.getRowModel().rows.map((row) => (
              <TableRow key={row.id}>
                {row.getVisibleCells().map((cell) => (
                  <TableCell key={cell.id}>{flexRender(cell.column.columnDef.cell, cell.getContext())}</TableCell>
                ))}
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
      <DataTablePagination
        page={page.page ?? 0}
        pageSize={page.size ?? 10}
        totalItems={page.totalItems ?? 0}
        totalPages={page.totalPages ?? 0}
        onPageChange={onPageChange}
        onPageSizeChange={onPageSizeChange}
        noun={noun}
      />
    </div>
  );
}

type RegistrationsDataTableProps = {
  refreshKey: number;
  projectId?: string;
  headerActions?: ReactNode;
  onEditRegistration: (registration: TargetRegistrationRecord) => void;
  onDeleteRegistration: (registration: TargetRegistrationRecord) => void;
  deletingTargetId: string | null;
};

export function RegistrationsDataTable({
  refreshKey,
  projectId,
  headerActions,
  onEditRegistration,
  onDeleteRegistration,
  deletingTargetId,
}: RegistrationsDataTableProps) {
  const [pageData, setPageData] = useState<TargetRegistrationPage>({ items: [], page: EMPTY_PAGE });
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [targetIdFilter, setTargetIdFilter] = useState("");
  const [groupFilter, setGroupFilter] = useState("all");
  const [regionFilter, setRegionFilter] = useState("all");
  const [tierFilter, setTierFilter] = useState("all");
  const [sorting, setSorting] = useState<SortingState>([]);
  const [columnVisibility, setColumnVisibility] = usePersistentColumnVisibility("admin-registrations");

  useEffect(() => {
    let active = true;
    const hasVisibleData =
      (pageData.items?.length ?? 0) > 0 || (pageData.page?.totalItems ?? 0) > 0;
    if (hasVisibleData) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }
    void adminListTargetRegistrations({
      page,
      size: pageSize,
      targetId: targetIdFilter || undefined,
      projectId: projectId || undefined,
      ring: groupFilter === "all" ? undefined : groupFilter,
      region: regionFilter === "all" ? undefined : regionFilter,
      tier: tierFilter === "all" ? undefined : tierFilter,
    })
      .then((result) => {
        if (!active) return;
        setPageData(result);
        setErrorMessage("");
      })
      .catch((error) => {
        if (!active) return;
        if (hasVisibleData) {
          setErrorMessage("");
        } else {
          setPageData({ items: [], page: EMPTY_PAGE });
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
  }, [groupFilter, page, pageSize, projectId, refreshKey, regionFilter, targetIdFilter, tierFilter]);

  const rows = useMemo<RegistrationRow[]>(
    () =>
      (pageData.items ?? []).map((record) => ({
        record,
        targetId: record.targetId ?? "unknown",
        displayName: record.displayName ?? "unknown",
        customerName: record.customerName ?? "unknown",
        deploymentStackName: record.metadata?.deploymentStackName ?? "auto",
        registryAuthMode: record.metadata?.registryAuthMode ?? "none",
        targetGroup: normalizeTagValue(record.tags?.ring, "unassigned"),
        region: normalizeTagValue(record.tags?.region, "unknown"),
        tier: normalizeTagValue(record.tags?.tier, "unknown"),
        environment: normalizeTagValue(record.tags?.environment, "unknown"),
        subscriptionId: record.subscriptionId ?? "unknown",
        tenantId: record.tenantId ?? "unknown",
        updatedAt: record.updatedAt ?? "",
      })),
    [pageData.items]
  );

  const columns = useMemo<ColumnDef<RegistrationRow>[]>(
    () => [
      { accessorKey: "targetId", header: "Target" },
      { accessorKey: "displayName", header: "Display Name" },
      { accessorKey: "customerName", header: "Customer" },
      { accessorKey: "deploymentStackName", header: "Stack" },
      { accessorKey: "registryAuthMode", header: "Registry Auth" },
      { accessorKey: "targetGroup", header: "Group" },
      { accessorKey: "region", header: "Region" },
      { accessorKey: "tier", header: "Tier" },
      { accessorKey: "environment", header: "Env" },
      { accessorKey: "tenantId", header: "Tenant" },
      { accessorKey: "subscriptionId", header: "Subscription" },
      {
        accessorKey: "updatedAt",
        header: "Updated",
        cell: ({ row }) => (
          <span className="text-xs text-muted-foreground">{formatTimestamp(row.original.updatedAt)}</span>
        ),
      },
      {
        id: "actions",
        header: "Actions",
        enableSorting: false,
        enableHiding: false,
        cell: ({ row }) => {
          const isDeleting = deletingTargetId === row.original.targetId;
          return (
            <div className="flex items-center justify-end gap-2">
              <Button type="button" size="sm" variant="outline" onClick={() => onEditRegistration(row.original.record)}>
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
    state: { sorting, columnVisibility },
    onSortingChange: setSorting,
    onColumnVisibilityChange: setColumnVisibility,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });

  const groups = optionValues(rows.map((row) => row.targetGroup), groupFilter);
  const regions = optionValues(rows.map((row) => row.region), regionFilter);
  const tiers = optionValues(rows.map((row) => row.tier), tierFilter);

  return (
    <TableFrame
      table={table}
      page={pageData.page ?? EMPTY_PAGE}
      noun="registrations"
      loading={loading}
      refreshing={refreshing}
      errorMessage={errorMessage}
      emptyMessage="No registered targets match current filters."
      onPageChange={setPage}
      onPageSizeChange={(size) => {
        setPageSize(size);
        setPage(0);
      }}
      onClearFilters={() => {
        setTargetIdFilter("");
        setGroupFilter("all");
        setRegionFilter("all");
        setTierFilter("all");
        setPage(0);
      }}
      headerActions={headerActions}
      renderFilterCell={(columnId) => {
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
        if (columnId === "targetGroup") {
          return (
            <Select value={groupFilter} onValueChange={(value) => { setGroupFilter(value); setPage(0); }}>
              <SelectTrigger className="h-8 w-full bg-background/90 px-2 text-xs"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All groups</SelectItem>
                {groups.map((value) => <SelectItem key={value} value={value}>{value}</SelectItem>)}
              </SelectContent>
            </Select>
          );
        }
        if (columnId === "region") {
          return (
            <Select value={regionFilter} onValueChange={(value) => { setRegionFilter(value); setPage(0); }}>
              <SelectTrigger className="h-8 w-full bg-background/90 px-2 text-xs"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All regions</SelectItem>
                {regions.map((value) => <SelectItem key={value} value={value}>{value}</SelectItem>)}
              </SelectContent>
            </Select>
          );
        }
        if (columnId === "tier") {
          return (
            <Select value={tierFilter} onValueChange={(value) => { setTierFilter(value); setPage(0); }}>
              <SelectTrigger className="h-8 w-full bg-background/90 px-2 text-xs"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All tiers</SelectItem>
                {tiers.map((value) => <SelectItem key={value} value={value}>{value}</SelectItem>)}
              </SelectContent>
            </Select>
          );
        }
        return null;
      }}
    />
  );
}

type EventsDataTableProps = {
  refreshKey: number;
};

export function EventsDataTable({ refreshKey }: EventsDataTableProps) {
  const [pageData, setPageData] = useState<MarketplaceEventPage>({ items: [], page: EMPTY_PAGE });
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [eventIdFilter, setEventIdFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState<MarketplaceEventStatus | "all">("all");
  const [sorting, setSorting] = useState<SortingState>([]);
  const [columnVisibility, setColumnVisibility] = usePersistentColumnVisibility("admin-events");

  useEffect(() => {
    let active = true;
    const hasVisibleData =
      (pageData.items?.length ?? 0) > 0 || (pageData.page?.totalItems ?? 0) > 0;
    if (hasVisibleData) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }
    void adminListMarketplaceEvents({
      page,
      size: pageSize,
      eventId: eventIdFilter || undefined,
      status: statusFilter === "all" ? undefined : statusFilter,
    })
      .then((result) => {
        if (!active) return;
        setPageData(result);
        setErrorMessage("");
      })
      .catch((error) => {
        if (!active) return;
        if (hasVisibleData) {
          setErrorMessage("");
        } else {
          setPageData({ items: [], page: EMPTY_PAGE });
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
  }, [eventIdFilter, page, pageSize, refreshKey, statusFilter]);

  const rows = useMemo<EventRow[]>(
    () =>
      (pageData.items ?? []).map((record: MarketplaceEventRecord) => ({
        eventId: record.eventId ?? "unknown",
        status: record.status ?? "unknown",
        eventType: record.eventType ?? "unknown",
        targetId: record.targetId ?? "n/a",
        subscriptionId: record.subscriptionId ?? "unknown",
        tenantId: record.tenantId ?? "unknown",
        createdAt: record.createdAt ?? "",
        message: record.message ?? "",
      })),
    [pageData.items]
  );

  const columns = useMemo<ColumnDef<EventRow>[]>(
    () => [
      { accessorKey: "eventId", header: "Event ID" },
      {
        accessorKey: "status",
        header: "Status",
        cell: ({ row }) => (
          <Badge variant={eventStatusVariant(row.original.status)} className="capitalize">
            {row.original.status}
          </Badge>
        ),
      },
      { accessorKey: "eventType", header: "Event Type" },
      { accessorKey: "targetId", header: "Target" },
      { accessorKey: "tenantId", header: "Tenant" },
      { accessorKey: "subscriptionId", header: "Subscription" },
      {
        accessorKey: "createdAt",
        header: "Created",
        cell: ({ row }) => (
          <span className="text-xs text-muted-foreground">{formatTimestamp(row.original.createdAt)}</span>
        ),
      },
      { accessorKey: "message", header: "Message" },
    ],
    []
  );

  const table = useReactTable({
    data: rows,
    columns,
    state: { sorting, columnVisibility },
    onSortingChange: setSorting,
    onColumnVisibilityChange: setColumnVisibility,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });

  const statuses = optionValues(rows.map((row) => row.status), statusFilter);

  return (
    <TableFrame
      table={table}
      page={pageData.page ?? EMPTY_PAGE}
      noun="events"
      loading={loading}
      refreshing={refreshing}
      errorMessage={errorMessage}
      emptyMessage="No onboarding events match current filters."
      onPageChange={setPage}
      onPageSizeChange={(size) => {
        setPageSize(size);
        setPage(0);
      }}
      onClearFilters={() => {
        setEventIdFilter("");
        setStatusFilter("all");
        setPage(0);
      }}
      renderFilterCell={(columnId) => {
        if (columnId === "eventId") {
          return (
            <Input
              value={eventIdFilter}
              onChange={(event) => {
                setEventIdFilter(event.target.value);
                setPage(0);
              }}
              placeholder="Filter event"
              className="h-8"
            />
          );
        }
        if (columnId === "status") {
          return (
            <Select
              value={statusFilter}
              onValueChange={(value) => {
                setStatusFilter(value as MarketplaceEventStatus | "all");
                setPage(0);
              }}
            >
              <SelectTrigger className="h-8 w-full bg-background/90 px-2 text-xs"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All statuses</SelectItem>
                {statuses.map((value) => <SelectItem key={value} value={value}>{value}</SelectItem>)}
              </SelectContent>
            </Select>
          );
        }
        return null;
      }}
    />
  );
}

type ForwarderLogsDataTableProps = {
  refreshKey: number;
};

export function ForwarderLogsDataTable({ refreshKey }: ForwarderLogsDataTableProps) {
  const [pageData, setPageData] = useState<ForwarderLogPage>({ items: [], page: EMPTY_PAGE });
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [logIdFilter, setLogIdFilter] = useState("");
  const [levelFilter, setLevelFilter] = useState<ForwarderLogLevel | "all">("all");
  const [sorting, setSorting] = useState<SortingState>([]);
  const [columnVisibility, setColumnVisibility] = usePersistentColumnVisibility("admin-forwarder-logs");

  useEffect(() => {
    let active = true;
    const hasVisibleData =
      (pageData.items?.length ?? 0) > 0 || (pageData.page?.totalItems ?? 0) > 0;
    if (hasVisibleData) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }
    void adminListForwarderLogs({
      page,
      size: pageSize,
      logId: logIdFilter || undefined,
      level: levelFilter === "all" ? undefined : levelFilter,
    })
      .then((result) => {
        if (!active) return;
        setPageData(result);
        setErrorMessage("");
      })
      .catch((error) => {
        if (!active) return;
        if (hasVisibleData) {
          setErrorMessage("");
        } else {
          setPageData({ items: [], page: EMPTY_PAGE });
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
  }, [levelFilter, logIdFilter, page, pageSize, refreshKey]);

  const rows = useMemo<ForwarderLogRow[]>(
    () =>
      (pageData.items ?? []).map((record: ForwarderLogRecord) => ({
        logId: record.logId ?? "unknown",
        level: record.level ?? "unknown",
        message: record.message ?? "",
        eventId: record.eventId ?? "n/a",
        targetId: record.targetId ?? "n/a",
        subscriptionId: record.subscriptionId ?? "n/a",
        backendStatusCode:
          record.backendStatusCode === undefined || record.backendStatusCode === null
            ? "n/a"
            : String(record.backendStatusCode),
        createdAt: record.createdAt ?? "",
      })),
    [pageData.items]
  );

  const columns = useMemo<ColumnDef<ForwarderLogRow>[]>(
    () => [
      { accessorKey: "logId", header: "Log ID" },
      {
        accessorKey: "level",
        header: "Level",
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
          <span className="text-xs text-muted-foreground">{formatTimestamp(row.original.createdAt)}</span>
        ),
      },
    ],
    []
  );

  const table = useReactTable({
    data: rows,
    columns,
    state: { sorting, columnVisibility },
    onSortingChange: setSorting,
    onColumnVisibilityChange: setColumnVisibility,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });

  const levels = optionValues(rows.map((row) => row.level), levelFilter);

  return (
    <TableFrame
      table={table}
      page={pageData.page ?? EMPTY_PAGE}
      noun="logs"
      loading={loading}
      refreshing={refreshing}
      errorMessage={errorMessage}
      emptyMessage="No forwarder logs match current filters."
      onPageChange={setPage}
      onPageSizeChange={(size) => {
        setPageSize(size);
        setPage(0);
      }}
      onClearFilters={() => {
        setLogIdFilter("");
        setLevelFilter("all");
        setPage(0);
      }}
      renderFilterCell={(columnId) => {
        if (columnId === "logId") {
          return (
            <Input
              value={logIdFilter}
              onChange={(event) => {
                setLogIdFilter(event.target.value);
                setPage(0);
              }}
              placeholder="Filter log"
              className="h-8"
            />
          );
        }
        if (columnId === "level") {
          return (
            <Select
              value={levelFilter}
              onValueChange={(value) => {
                setLevelFilter(value as ForwarderLogLevel | "all");
                setPage(0);
              }}
            >
              <SelectTrigger className="h-8 w-full bg-background/90 px-2 text-xs"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All levels</SelectItem>
                {levels.map((value) => <SelectItem key={value} value={value}>{value}</SelectItem>)}
              </SelectContent>
            </Select>
          );
        }
        return null;
      }}
    />
  );
}

type ReleaseWebhookDeliveriesDataTableProps = {
  refreshKey: number;
};

export function ReleaseWebhookDeliveriesDataTable({ refreshKey }: ReleaseWebhookDeliveriesDataTableProps) {
  const [pageData, setPageData] = useState<ReleaseWebhookDeliveryPage>({ items: [], page: EMPTY_PAGE });
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [errorMessage, setErrorMessage] = useState("");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [deliveryIdFilter, setDeliveryIdFilter] = useState("");
  const [statusFilter, setStatusFilter] = useState<ReleaseWebhookStatus | "all">("all");
  const [sorting, setSorting] = useState<SortingState>([]);
  const [columnVisibility, setColumnVisibility] = usePersistentColumnVisibility("admin-release-webhooks");

  useEffect(() => {
    let active = true;
    const hasVisibleData =
      (pageData.items?.length ?? 0) > 0 || (pageData.page?.totalItems ?? 0) > 0;
    if (hasVisibleData) {
      setRefreshing(true);
    } else {
      setLoading(true);
    }
    void adminListReleaseWebhookDeliveries({
      page,
      size: pageSize,
      deliveryId: deliveryIdFilter || undefined,
      status: statusFilter === "all" ? undefined : statusFilter,
    })
      .then((result) => {
        if (!active) return;
        setPageData(result);
        setErrorMessage("");
      })
      .catch((error) => {
        if (!active) return;
        if (hasVisibleData) {
          setErrorMessage("");
        } else {
          setPageData({ items: [], page: EMPTY_PAGE });
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
  }, [deliveryIdFilter, page, pageSize, refreshKey, statusFilter]);

  const rows = useMemo<ReleaseWebhookRow[]>(
    () =>
      (pageData.items ?? []).map((record: ReleaseWebhookDeliveryRecord) => ({
        id: record.id ?? "unknown",
        externalDeliveryId: record.externalDeliveryId ?? "unknown",
        eventType: record.eventType ?? "unknown",
        repo: record.repo ?? "unknown",
        ref: record.ref ?? "unknown",
        status: record.status ?? "unknown",
        message: record.message ?? "",
        createdCount: String(record.createdCount ?? 0),
        receivedAt: record.receivedAt ?? "",
      })),
    [pageData.items]
  );

  const columns = useMemo<ColumnDef<ReleaseWebhookRow>[]>(
    () => [
      { accessorKey: "externalDeliveryId", header: "Delivery ID" },
      { accessorKey: "repo", header: "Repo" },
      { accessorKey: "ref", header: "Ref" },
      {
        accessorKey: "status",
        header: "Status",
        cell: ({ row }) => (
          <Badge variant={releaseWebhookStatusVariant(row.original.status)} className="uppercase">
            {row.original.status}
          </Badge>
        ),
      },
      { accessorKey: "eventType", header: "Event" },
      { accessorKey: "createdCount", header: "Created" },
      { accessorKey: "message", header: "Message" },
      {
        accessorKey: "receivedAt",
        header: "Received",
        cell: ({ row }) => (
          <span className="text-xs text-muted-foreground">{formatTimestamp(row.original.receivedAt)}</span>
        ),
      },
    ],
    []
  );

  const table = useReactTable({
    data: rows,
    columns,
    state: { sorting, columnVisibility },
    onSortingChange: setSorting,
    onColumnVisibilityChange: setColumnVisibility,
    getCoreRowModel: getCoreRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });

  const statuses = optionValues(rows.map((row) => row.status), statusFilter);

  return (
    <TableFrame
      table={table}
      page={pageData.page ?? EMPTY_PAGE}
      noun="deliveries"
      loading={loading}
      refreshing={refreshing}
      errorMessage={errorMessage}
      emptyMessage="No release webhook deliveries match current filters."
      onPageChange={setPage}
      onPageSizeChange={(size) => {
        setPageSize(size);
        setPage(0);
      }}
      onClearFilters={() => {
        setDeliveryIdFilter("");
        setStatusFilter("all");
        setPage(0);
      }}
      renderFilterCell={(columnId) => {
        if (columnId === "externalDeliveryId") {
          return (
            <Input
              value={deliveryIdFilter}
              onChange={(event) => {
                setDeliveryIdFilter(event.target.value);
                setPage(0);
              }}
              placeholder="Filter delivery"
              className="h-8"
            />
          );
        }
        if (columnId === "status") {
          return (
            <Select
              value={statusFilter}
              onValueChange={(value) => {
                setStatusFilter(value as ReleaseWebhookStatus | "all");
                setPage(0);
              }}
            >
              <SelectTrigger className="h-8 w-full bg-background/90 px-2 text-xs"><SelectValue /></SelectTrigger>
              <SelectContent>
                <SelectItem value="all">All statuses</SelectItem>
                {statuses.map((value) => <SelectItem key={value} value={value}>{value}</SelectItem>)}
              </SelectContent>
            </Select>
          );
        }
        return null;
      }}
    />
  );
}
