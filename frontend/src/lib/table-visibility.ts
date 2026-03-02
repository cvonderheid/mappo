import { useEffect, useState } from "react";
import type { Dispatch, SetStateAction } from "react";
import type { VisibilityState } from "@tanstack/react-table";

const STORAGE_PREFIX = "mappo:table-columns:";

function storageKey(tableKey: string): string {
  return `${STORAGE_PREFIX}${tableKey}`;
}

function loadColumnVisibility(tableKey: string): VisibilityState {
  if (typeof window === "undefined") {
    return {};
  }
  const raw = window.localStorage.getItem(storageKey(tableKey));
  if (raw === null || raw.trim() === "") {
    return {};
  }
  try {
    const parsed = JSON.parse(raw) as Record<string, unknown>;
    const visibility: VisibilityState = {};
    for (const [key, value] of Object.entries(parsed)) {
      if (typeof value === "boolean") {
        visibility[key] = value;
      }
    }
    return visibility;
  } catch {
    return {};
  }
}

function saveColumnVisibility(tableKey: string, visibility: VisibilityState): void {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(storageKey(tableKey), JSON.stringify(visibility));
}

export function usePersistentColumnVisibility(
  tableKey: string
): [VisibilityState, Dispatch<SetStateAction<VisibilityState>>] {
  const [columnVisibility, setColumnVisibility] = useState<VisibilityState>(() =>
    loadColumnVisibility(tableKey)
  );

  useEffect(() => {
    saveColumnVisibility(tableKey, columnVisibility);
  }, [tableKey, columnVisibility]);

  return [columnVisibility, setColumnVisibility];
}
