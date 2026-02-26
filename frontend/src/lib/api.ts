import type { CreateRunRequest, Release, RunDetail, RunSummary, Target } from "./types";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8000/api/v1";

async function fetchJson<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {}),
    },
    ...init,
  });

  if (!response.ok) {
    const body = await response.text();
    throw new Error(`API request failed (${response.status}): ${body}`);
  }

  return (await response.json()) as T;
}

export function listTargets(ringFilter: string): Promise<Target[]> {
  const query = ringFilter === "all" ? "" : `?ring=${encodeURIComponent(ringFilter)}`;
  return fetchJson<Target[]>(`/targets${query}`);
}

export function listReleases(): Promise<Release[]> {
  return fetchJson<Release[]>("/releases");
}

export function listRuns(): Promise<RunSummary[]> {
  return fetchJson<RunSummary[]>("/runs");
}

export function getRun(runId: string): Promise<RunDetail> {
  return fetchJson<RunDetail>(`/runs/${runId}`);
}

export function createRun(request: CreateRunRequest): Promise<RunDetail> {
  return fetchJson<RunDetail>("/runs", {
    method: "POST",
    body: JSON.stringify(request),
  });
}

export function resumeRun(runId: string): Promise<RunDetail> {
  return fetchJson<RunDetail>(`/runs/${runId}/resume`, {
    method: "POST",
  });
}

export function retryFailed(runId: string): Promise<RunDetail> {
  return fetchJson<RunDetail>(`/runs/${runId}/retry-failed`, {
    method: "POST",
  });
}
