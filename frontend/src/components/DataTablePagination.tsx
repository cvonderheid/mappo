import { Button } from "@/components/ui/button";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";

type DataTablePaginationProps = {
  page: number;
  pageSize: number;
  totalItems: number;
  totalPages: number;
  onPageChange: (page: number) => void;
  onPageSizeChange: (size: number) => void;
  noun: string;
};

export default function DataTablePagination({
  page,
  pageSize,
  totalItems,
  totalPages,
  onPageChange,
  onPageSizeChange,
  noun,
}: DataTablePaginationProps) {
  const hasItems = totalItems > 0;
  const displayPage = hasItems ? page + 1 : 0;
  const canPrevious = page > 0;
  const canNext = hasItems && page + 1 < totalPages;
  const startItem = hasItems ? page * pageSize + 1 : 0;
  const endItem = hasItems ? Math.min(totalItems, (page + 1) * pageSize) : 0;

  return (
    <div className="flex flex-col gap-2 border-t border-border/60 pt-3 text-xs text-muted-foreground md:flex-row md:items-center md:justify-between">
      <div className="flex flex-wrap items-center gap-2">
        <span>
          Showing {startItem}-{endItem} of {totalItems} {noun}
        </span>
        <div className="flex items-center gap-2">
          <span>Rows</span>
          <Select
            value={String(pageSize)}
            onValueChange={(value) => onPageSizeChange(Number(value))}
          >
            <SelectTrigger className="h-8 w-[88px] bg-background/90 px-2 text-xs">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="10">10</SelectItem>
              <SelectItem value="25">25</SelectItem>
              <SelectItem value="50">50</SelectItem>
            </SelectContent>
          </Select>
        </div>
      </div>
      <div className="flex items-center gap-2">
        <span>
          Page {displayPage} of {Math.max(totalPages, 1)}
        </span>
        <Button type="button" variant="outline" size="sm" disabled={!canPrevious} onClick={() => onPageChange(page - 1)}>
          Previous
        </Button>
        <Button type="button" variant="outline" size="sm" disabled={!canNext} onClick={() => onPageChange(page + 1)}>
          Next
        </Button>
      </div>
    </div>
  );
}
