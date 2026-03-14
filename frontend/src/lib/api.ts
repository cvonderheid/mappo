import { apiClient } from "@/lib/api/client";
import type {
  CreateRunRequest,
  DeleteTargetRegistrationResponse,
  ForwarderLogPage,
  ListForwarderLogsQuery,
  ListMarketplaceEventsQuery,
  ListReleaseWebhookDeliveriesQuery,
  ListRunsQuery,
  ListTargetRegistrationsQuery,
  ListTargetsPageQuery,
  MarketplaceEventIngestRequest,
  MarketplaceEventIngestResponse,
  MarketplaceEventPage,
  ListProjectAuditQuery,
  ProjectDefinition,
  ProjectCreateRequest,
  ProjectConfigurationAuditPage,
  ProjectConfigurationPatchRequest,
  ProjectValidationRequest,
  ProjectValidationResult,
  Release,
  ReleaseManifestIngestRequest,
  ReleaseManifestIngestResponse,
  ReleaseWebhookDeliveryPage,
  RunDetail,
  RunPreview,
  RunSummaryPage,
  Target,
  TargetPage,
  TargetRegistrationPage,
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

export async function listTargetsPage(query: ListTargetsPageQuery = {}): Promise<TargetPage> {
  const { data, error, response } = await apiClient.GET("/api/v1/targets/page", {
    params: {
      query: {
        page: query.page,
        size: query.size,
        projectId: query.projectId,
        targetId: query.targetId,
        customerName: query.customerName,
        tenantId: query.tenantId,
        subscriptionId: query.subscriptionId,
        ring: query.ring,
        region: query.region,
        tier: query.tier,
        version: query.version,
        runtimeStatus: query.runtimeStatus,
        lastDeploymentStatus: query.lastDeploymentStatus,
      },
    },
  });
  return requireData("listTargetsPage", { data, error, response });
}

export async function listProjects(): Promise<ProjectDefinition[]> {
  const { data, error, response } = await apiClient.GET("/api/v1/projects");
  return requireData("listProjects", { data, error, response });
}

export async function createProject(
  request: ProjectCreateRequest
): Promise<ProjectDefinition> {
  const { data, error, response } = await apiClient.POST("/api/v1/projects", {
    body: request,
  });
  return requireData("createProject", { data, error, response });
}

export async function patchProjectConfiguration(
  projectId: string,
  request: ProjectConfigurationPatchRequest
): Promise<ProjectDefinition> {
  const { data, error, response } = await apiClient.PATCH("/api/v1/projects/{projectId}", {
    params: { path: { projectId } },
    body: request,
  });
  return requireData("patchProjectConfiguration", { data, error, response });
}

export async function validateProjectConfiguration(
  projectId: string,
  request: ProjectValidationRequest
): Promise<ProjectValidationResult> {
  const { data, error, response } = await apiClient.POST("/api/v1/projects/{projectId}/validate", {
    params: { path: { projectId } },
    body: request,
  });
  return requireData("validateProjectConfiguration", { data, error, response });
}

export async function listProjectAudit(
  projectId: string,
  query: ListProjectAuditQuery = {}
): Promise<ProjectConfigurationAuditPage> {
  const { data, error, response } = await apiClient.GET("/api/v1/projects/{projectId}/audit", {
    params: {
      path: { projectId },
      query: {
        page: query.page,
        size: query.size,
        action: query.action,
      },
    },
  });
  return requireData("listProjectAudit", { data, error, response });
}

export async function listReleases(): Promise<Release[]> {
  const { data, error, response } = await apiClient.GET("/api/v1/releases");
  return requireData("listReleases", { data, error, response });
}

export async function listRuns(query: ListRunsQuery = {}): Promise<RunSummaryPage> {
  const { data, error, response } = await apiClient.GET("/api/v1/runs", {
    params: {
      query: {
        page: query.page,
        size: query.size,
        projectId: query.projectId,
        runId: query.runId,
        releaseId: query.releaseId,
        status: query.status,
      },
    },
  });
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

export async function previewRun(request: CreateRunRequest, signal?: AbortSignal): Promise<RunPreview> {
  const { data, error, response } = await apiClient.POST("/api/v1/runs/preview", {
    body: request,
    signal,
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

export async function adminListTargetRegistrations(
  query: ListTargetRegistrationsQuery = {}
): Promise<TargetRegistrationPage> {
  const { data, error, response } = await apiClient.GET("/api/v1/admin/onboarding/registrations", {
    params: {
      query: {
        page: query.page,
        size: query.size,
        targetId: query.targetId,
        ring: query.ring,
        region: query.region,
        tier: query.tier,
      },
    },
  });
  return requireData("adminListTargetRegistrations", { data, error, response });
}

export async function adminListMarketplaceEvents(
  query: ListMarketplaceEventsQuery = {}
): Promise<MarketplaceEventPage> {
  const { data, error, response } = await apiClient.GET("/api/v1/admin/onboarding/events", {
    params: {
      query: {
        page: query.page,
        size: query.size,
        eventId: query.eventId,
        status: query.status,
      },
    },
  });
  return requireData("adminListMarketplaceEvents", { data, error, response });
}

export async function adminListForwarderLogs(
  query: ListForwarderLogsQuery = {}
): Promise<ForwarderLogPage> {
  const { data, error, response } = await apiClient.GET("/api/v1/admin/onboarding/forwarder-logs/page", {
    params: {
      query: {
        page: query.page,
        size: query.size,
        logId: query.logId,
        level: query.level,
      },
    },
  });
  return requireData("adminListForwarderLogs", { data, error, response });
}

export async function adminListReleaseWebhookDeliveries(
  query: ListReleaseWebhookDeliveriesQuery = {}
): Promise<ReleaseWebhookDeliveryPage> {
  const { data, error, response } = await apiClient.GET("/api/v1/admin/releases/webhook-deliveries", {
    params: {
      query: {
        page: query.page,
        size: query.size,
        deliveryId: query.deliveryId,
        status: query.status,
      },
    },
  });
  return requireData("adminListReleaseWebhookDeliveries", { data, error, response });
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
