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
  subjectId?: string | null;
  occurredAt?: string | null;
};

export function createLiveUpdatesEventSource(): EventSource {
  return new EventSource(`${apiBaseUrl}/api/v1/events/stream`, {
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
