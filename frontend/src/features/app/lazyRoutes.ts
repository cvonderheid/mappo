import { lazy, type ComponentType } from "react";

export const ROUTER_FUTURE_FLAGS = {
  v7_relativeSplatPath: true,
  v7_startTransition: true,
} as const;

const LAZY_ROUTE_RETRY_STORAGE_KEY = "mappo.lazy-route-reload";

function isStaleLazyImportError(error: unknown): boolean {
  if (!(error instanceof Error)) {
    return false;
  }
  const message = error.message.toLowerCase();
  return (
    message.includes("failed to fetch dynamically imported module")
    || message.includes("importing a module script failed")
    || message.includes("dynamically imported module")
    || message.includes("unable to preload css")
  );
}

function lazyWithRouteReload<T extends ComponentType<any>>(loader: () => Promise<{ default: T }>) {
  return lazy(async () => {
    const retryKey =
      typeof window === "undefined"
        ? LAZY_ROUTE_RETRY_STORAGE_KEY
        : `${LAZY_ROUTE_RETRY_STORAGE_KEY}:${window.location.pathname}`;

    try {
      const module = await loader();
      if (typeof window !== "undefined") {
        window.sessionStorage.removeItem(retryKey);
      }
      return module;
    } catch (error) {
      if (
        typeof window !== "undefined"
        && isStaleLazyImportError(error)
        && window.sessionStorage.getItem(retryKey) !== "1"
      ) {
        window.sessionStorage.setItem(retryKey, "1");
        window.location.reload();
        return new Promise<never>(() => {});
      }
      if (typeof window !== "undefined") {
        window.sessionStorage.removeItem(retryKey);
      }
      throw error;
    }
  });
}

export const DemoPanel = lazyWithRouteReload(() => import("@/routes/DemoPanel"));
export const DeploymentsPage = lazyWithRouteReload(() => import("@/routes/DeploymentsPage"));
export const ForwarderLogsPage = lazyWithRouteReload(() => import("@/routes/ForwarderLogsPage"));
export const ProjectSettingsPage = lazyWithRouteReload(() => import("@/routes/ProjectSettingsPage"));
export const ProviderConnectionsConfigPage = lazyWithRouteReload(
  () => import("@/routes/ProviderConnectionsConfigPage")
);
export const ProjectSwitcherMenu = lazyWithRouteReload(() => import("@/features/project/ProjectSwitcherMenu"));
export const ReleaseIngestConfigPage = lazyWithRouteReload(() => import("@/routes/ReleaseIngestConfigPage"));
export const ReleasesPage = lazyWithRouteReload(() => import("@/routes/ReleasesPage"));
export const RunDetailPanel = lazyWithRouteReload(() =>
  import("@/features/deployments/RunPanels").then((module) => ({ default: module.RunDetailPanel }))
);
export const SecretReferencesConfigPage = lazyWithRouteReload(() => import("@/routes/SecretReferencesConfigPage"));
export const TargetsPage = lazyWithRouteReload(() => import("@/routes/TargetsPage"));
