import { apiBaseUrl } from "@/lib/api/client";

export type LiveUpdateEventType =
  | "connected"
  | "heartbeat"
  | "targets-updated"
  | "releases-updated"
  | "admin-updated"
  | "runs-updated"
  | "run-updated";

export type LiveUpdateEvent = {
  type?: LiveUpdateEventType;
  projectId?: string | null;
  subjectId?: string | null;
  occurredAt?: string | null;
};

export function createLiveUpdatesEventSource(
  topics: string[] = [],
  projectId?: string
): EventSource {
  const url = new URL(`${apiBaseUrl}/api/v1/events/stream`);
  if (topics.length > 0) {
    url.searchParams.set("topics", topics.join(","));
  }
  if (projectId && projectId.trim() !== "") {
    url.searchParams.set("projectId", projectId.trim());
  }
  return new EventSource(url.toString(), {
    withCredentials: true,
  });
}

export function parseLiveUpdateEvent(rawData: string): LiveUpdateEvent | null {
  try {
    const parsed = JSON.parse(rawData) as LiveUpdateEvent;
    return parsed && typeof parsed === "object" ? parsed : null;
  } catch {
    return null;
  }
}
