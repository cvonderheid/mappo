import type { Table } from "@tanstack/react-table";

import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuCheckboxItem,
  DropdownMenuContent,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

type ColumnVisibilityMenuProps<TData> = {
  table: Table<TData>;
};

function columnLabel<TData>(table: Table<TData>, columnId: string): string {
  const header = table.getColumn(columnId)?.columnDef.header;
  if (typeof header === "string" && header.trim() !== "") {
    return header;
  }
  return columnId;
}

export default function ColumnVisibilityMenu<TData>({
  table,
}: ColumnVisibilityMenuProps<TData>) {
  const hideableColumns = table.getAllLeafColumns().filter((column) => column.getCanHide());

  if (hideableColumns.length === 0) {
    return null;
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button type="button" variant="outline" size="sm">
          Columns
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="max-h-72 overflow-y-auto">
        {hideableColumns.map((column) => (
          <DropdownMenuCheckboxItem
            key={column.id}
            checked={column.getIsVisible()}
            onCheckedChange={(value) => column.toggleVisibility(Boolean(value))}
          >
            {columnLabel(table, column.id)}
          </DropdownMenuCheckboxItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
