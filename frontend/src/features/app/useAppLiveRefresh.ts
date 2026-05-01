import { useEffect, useMemo, type MutableRefObject } from "react";

import { createLiveUpdatesEventSource, parseLiveUpdateEvent } from "@/lib/live-updates";

type UseAppLiveRefreshArgs = {
  isAdminRoute: boolean;
  isDeploymentRoute: boolean;
  isTargetsRoute: boolean;
  selectedProjectId: string;
  selectedProjectIdRef: MutableRefObject<string>;
  selectedRunIdRef: MutableRefObject<string>;
  isAdminRouteRef: MutableRefObject<boolean>;
  isDeploymentRouteRef: MutableRefObject<boolean>;
  isTargetsRouteRef: MutableRefObject<boolean>;
  refreshTargetsRef: MutableRefObject<() => Promise<void>>;
  refreshRunsRef: MutableRefObject<() => Promise<void>>;
  refreshRunDetailRef: MutableRefObject<(runId: string) => Promise<void>>;
  refreshReleasesRef: MutableRefObject<() => Promise<void>>;
  refreshRegistrationOptionsRef: MutableRefObject<() => Promise<void>>;
  refreshRunSummary: () => Promise<void>;
};

export function useAppLiveRefresh({
  isAdminRoute,
  isDeploymentRoute,
  isTargetsRoute,
  selectedProjectId,
  selectedProjectIdRef,
  selectedRunIdRef,
  isAdminRouteRef,
  isDeploymentRouteRef,
  isTargetsRouteRef,
  refreshTargetsRef,
  refreshRunsRef,
  refreshRunDetailRef,
  refreshReleasesRef,
  refreshRegistrationOptionsRef,
  refreshRunSummary,
}: UseAppLiveRefreshArgs) {
  const liveTopics = useMemo(() => {
    if (isDeploymentRoute) {
      return ["targets", "runs", "releases"];
    }
    return isAdminRoute ? ["targets", "releases", "admin"] : [];
  }, [isAdminRoute, isDeploymentRoute]);

  useEffect(() => {
    const intervalMs = isTargetsRoute ? 30000 : 15000;
    const intervalId = window.setInterval(() => {
      if (isDeploymentRouteRef.current) {
        void refreshTargetsRef.current();
        void refreshRunsRef.current();
        if (selectedRunIdRef.current) {
          void refreshRunDetailRef.current(selectedRunIdRef.current);
        }
        void refreshReleasesRef.current();
        return;
      }
      if (isTargetsRouteRef.current) {
        void refreshTargetsRef.current();
        void refreshRegistrationOptionsRef.current();
        void refreshReleasesRef.current();
        void refreshRunSummary();
        return;
      }
      if (isAdminRouteRef.current) {
        void refreshReleasesRef.current();
        void refreshRunSummary();
        void refreshRegistrationOptionsRef.current();
      }
    }, intervalMs);
    return () => window.clearInterval(intervalId);
  }, [isTargetsRoute, refreshRunSummary]);

  useEffect(() => {
    if (liveTopics.length === 0 || !selectedProjectId || typeof window === "undefined" || typeof EventSource === "undefined") {
      return;
    }
    const eventSource = createLiveUpdatesEventSource(liveTopics, selectedProjectId);
    const scheduledRefreshes = new Map<string, number>();
    const scheduleRefresh = (key: string, action: () => void) => {
      if (scheduledRefreshes.has(key)) {
        return;
      }
      const timeoutId = window.setTimeout(() => {
        scheduledRefreshes.delete(key);
        action();
      }, 200);
      scheduledRefreshes.set(key, timeoutId);
    };
    const refreshSelectedRun = (runId: string | null | undefined) => {
      if (runId && runId === selectedRunIdRef.current) {
        scheduleRefresh(`run:${runId}`, () => {
          void refreshRunDetailRef.current(runId);
        });
      }
    };
    const matchesSelectedProject = (projectId: string | null | undefined) =>
      !projectId || projectId === selectedProjectIdRef.current;

    eventSource.addEventListener("targets-updated", (event: MessageEvent<string>) => {
      const payload = parseLiveUpdateEvent(event.data);
      if (matchesSelectedProject(payload?.projectId)) {
        scheduleRefresh("targets", () => void refreshTargetsRef.current());
      }
    });
    eventSource.addEventListener("releases-updated", (event: MessageEvent<string>) => {
      const payload = parseLiveUpdateEvent(event.data);
      if (matchesSelectedProject(payload?.projectId)) {
        scheduleRefresh("releases", () => void refreshReleasesRef.current());
      }
    });
    eventSource.addEventListener("admin-updated", () => {
      if (!isAdminRouteRef.current) {
        return;
      }
      if (isTargetsRouteRef.current) {
        scheduleRefresh("targets", () => void refreshTargetsRef.current());
      }
      scheduleRefresh("admin", () => void refreshRegistrationOptionsRef.current());
    });
    eventSource.addEventListener("connected", (event: MessageEvent<string>) => {
      const payload = parseLiveUpdateEvent(event.data);
      if (!matchesSelectedProject(payload?.projectId)) {
        return;
      }
      if (isDeploymentRouteRef.current) {
        scheduleRefresh("targets", () => void refreshTargetsRef.current());
        scheduleRefresh("releases", () => void refreshReleasesRef.current());
        scheduleRefresh("runs", () => void refreshRunsRef.current());
        refreshSelectedRun(selectedRunIdRef.current);
        return;
      }
      if (isTargetsRouteRef.current) {
        scheduleRefresh("targets", () => void refreshTargetsRef.current());
        scheduleRefresh("admin", () => void refreshRegistrationOptionsRef.current());
        scheduleRefresh("releases", () => void refreshReleasesRef.current());
        return;
      }
      if (isAdminRouteRef.current) {
        scheduleRefresh("admin", () => void refreshRegistrationOptionsRef.current());
        scheduleRefresh("releases", () => void refreshReleasesRef.current());
      }
    });
    eventSource.addEventListener("runs-updated", (event: MessageEvent<string>) => {
      if (!isDeploymentRouteRef.current) {
        return;
      }
      const payload = parseLiveUpdateEvent(event.data);
      if (matchesSelectedProject(payload?.projectId)) {
        scheduleRefresh("runs", () => void refreshRunsRef.current());
        refreshSelectedRun(selectedRunIdRef.current);
      }
    });
    eventSource.addEventListener("run-updated", (event: MessageEvent<string>) => {
      if (!isDeploymentRouteRef.current) {
        return;
      }
      const payload = parseLiveUpdateEvent(event.data);
      if (matchesSelectedProject(payload?.projectId)) {
        scheduleRefresh("runs", () => void refreshRunsRef.current());
        refreshSelectedRun(payload?.subjectId);
      }
    });
    eventSource.onerror = () => {};
    return () => {
      scheduledRefreshes.forEach((timeoutId) => window.clearTimeout(timeoutId));
      scheduledRefreshes.clear();
      eventSource.close();
    };
  }, [liveTopics, selectedProjectId]);
}
