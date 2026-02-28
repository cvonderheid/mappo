import { apiClient } from "@/lib/api/client";
import type {
  AdminDiscoverImportRequest,
  AdminDiscoverImportResponse,
  CreateRunRequest,
  Release,
  RunDetail,
  RunSummary,
  Target,
} from "@/lib/types";

type ApiResult<T> = {
  data?: T;
  error?: unknown;
  response: Response;
};

function formatError(error: unknown): string {
  if (typeof error === "string") {
    return error;
  }
  if (error && typeof error === "object" && "detail" in error) {
    return String((error as { detail: unknown }).detail);
  }
  if (error instanceof Error) {
    return error.message;
  }
  return JSON.stringify(error);
}

function requireData<T>(label: string, result: ApiResult<T>): T {
  if (result.data !== undefined) {
    return result.data;
  }
  const status = result.response.status;
  const errorText = result.error !== undefined ? formatError(result.error) : "unknown error";
  throw new Error(`${label} failed (${status}): ${errorText}`);
}

export async function listTargets(ringFilter: string): Promise<Target[]> {
  const { data, error, response } = await apiClient.GET("/api/v1/targets", {
    params: {
      query: ringFilter === "all" ? undefined : { ring: ringFilter },
    },
  });
  return requireData("listTargets", { data, error, response });
}

export async function listReleases(): Promise<Release[]> {
  const { data, error, response } = await apiClient.GET("/api/v1/releases");
  return requireData("listReleases", { data, error, response });
}

export async function listRuns(): Promise<RunSummary[]> {
  const { data, error, response } = await apiClient.GET("/api/v1/runs");
  return requireData("listRuns", { data, error, response });
}

export async function getRun(runId: string): Promise<RunDetail> {
  const { data, error, response } = await apiClient.GET("/api/v1/runs/{run_id}", {
    params: { path: { run_id: runId } },
  });
  return requireData("getRun", { data, error, response });
}

export async function createRun(request: CreateRunRequest): Promise<RunDetail> {
  const { data, error, response } = await apiClient.POST("/api/v1/runs", {
    body: request,
  });
  return requireData("createRun", { data, error, response });
}

export async function resumeRun(runId: string): Promise<RunDetail> {
  const { data, error, response } = await apiClient.POST("/api/v1/runs/{run_id}/resume", {
    params: { path: { run_id: runId } },
  });
  return requireData("resumeRun", { data, error, response });
}

export async function retryFailed(runId: string): Promise<RunDetail> {
  const { data, error, response } = await apiClient.POST("/api/v1/runs/{run_id}/retry-failed", {
    params: { path: { run_id: runId } },
  });
  return requireData("retryFailed", { data, error, response });
}

export async function adminDiscoverImport(
  request: AdminDiscoverImportRequest
): Promise<AdminDiscoverImportResponse> {
  const { data, error, response } = await apiClient.POST("/api/v1/admin/discover-import", {
    body: request,
  });
  return requireData("adminDiscoverImport", { data, error, response });
}
