import { apiClient } from "@/lib/api/client";
import type {
  AdminOnboardingSnapshotResponse,
  CreateRunRequest,
  DeleteTargetRegistrationResponse,
  MarketplaceEventIngestRequest,
  MarketplaceEventIngestResponse,
  Release,
  ReleaseManifestIngestRequest,
  ReleaseManifestIngestResponse,
  RunDetail,
  RunPreview,
  RunSummary,
  Target,
  TargetRegistrationRecord,
  UpdateTargetRegistrationRequest,
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

export async function listTargets(ringFilter = "all"): Promise<Target[]> {
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
  const { data, error, response } = await apiClient.GET("/api/v1/runs/{runId}", {
    params: { path: { runId } },
  });
  return requireData("getRun", { data, error, response });
}

export async function createRun(request: CreateRunRequest): Promise<RunDetail> {
  const { data, error, response } = await apiClient.POST("/api/v1/runs", {
    body: request,
  });
  return requireData("createRun", { data, error, response });
}

export async function previewRun(request: CreateRunRequest): Promise<RunPreview> {
  const { data, error, response } = await apiClient.POST("/api/v1/runs/preview", {
    body: request,
  });
  return requireData("previewRun", { data, error, response });
}

export async function resumeRun(runId: string): Promise<RunDetail> {
  const { data, error, response } = await apiClient.POST("/api/v1/runs/{runId}/resume", {
    params: { path: { runId } },
  });
  return requireData("resumeRun", { data, error, response });
}

export async function retryFailed(runId: string): Promise<RunDetail> {
  const { data, error, response } = await apiClient.POST("/api/v1/runs/{runId}/retry-failed", {
    params: { path: { runId } },
  });
  return requireData("retryFailed", { data, error, response });
}

export async function getAdminOnboardingSnapshot(
  eventLimit = 50
): Promise<AdminOnboardingSnapshotResponse> {
  const { data, error, response } = await apiClient.GET("/api/v1/admin/onboarding", {
    params: { query: { event_limit: eventLimit } },
  });
  return requireData("getAdminOnboardingSnapshot", { data, error, response });
}

export async function adminIngestMarketplaceEvent(
  request: MarketplaceEventIngestRequest,
  ingestToken?: string
): Promise<MarketplaceEventIngestResponse> {
  const { data, error, response } = await apiClient.POST("/api/v1/admin/onboarding/events", {
    body: request,
    headers:
      ingestToken === undefined || ingestToken.trim() === ""
        ? undefined
        : { "x-mappo-ingest-token": ingestToken.trim() },
  });
  return requireData("adminIngestMarketplaceEvent", { data, error, response });
}

export async function adminUpdateTargetRegistration(
  targetId: string,
  request: UpdateTargetRegistrationRequest
): Promise<TargetRegistrationRecord> {
  const { data, error, response } = await apiClient.PATCH(
    "/api/v1/admin/onboarding/registrations/{targetId}",
    {
      params: { path: { targetId } },
      body: request,
    }
  );
  return requireData("adminUpdateTargetRegistration", { data, error, response });
}

export async function adminDeleteTargetRegistration(
  targetId: string
): Promise<DeleteTargetRegistrationResponse> {
  const { data, error, response } = await apiClient.DELETE(
    "/api/v1/admin/onboarding/registrations/{targetId}",
    {
      params: { path: { targetId } },
    }
  );
  return requireData("adminDeleteTargetRegistration", { data, error, response });
}

export async function adminIngestGithubReleaseManifest(
  request: ReleaseManifestIngestRequest
): Promise<ReleaseManifestIngestResponse> {
  const { data, error, response } = await apiClient.POST("/api/v1/admin/releases/ingest/github", {
    body: request,
  });
  return requireData("adminIngestGithubReleaseManifest", { data, error, response });
}
