import { apiBaseUrl, apiClient } from "@/lib/api/client";
import type {
  CreateRunRequest,
  DiscoverProjectAdoBranchesRequest,
  DiscoverProjectAdoRepositoriesRequest,
  DiscoverProjectAdoPipelinesRequest,
  DiscoverProjectAdoServiceConnectionsRequest,
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
  ProjectAdoBranchDiscoveryResult,
  MarketplaceEventPage,
  ListProjectAuditQuery,
  ProjectDefinition,
  ProjectCreateRequest,
  ProjectConfigurationAuditPage,
  ProjectConfigurationPatchRequest,
  ProjectValidationRequest,
  ProjectValidationResult,
  ProjectAdoPipelineDiscoveryResult,
  ProjectAdoRepositoryDiscoveryResult,
  ProjectAdoServiceConnectionDiscoveryResult,
  ProviderConnection,
  ProviderConnectionAdoProjectDiscoveryResult,
  ProviderConnectionCreateRequest,
  ProviderConnectionPatchRequest,
  ProviderConnectionVerifyRequest,
  Release,
  ReleaseIngestEndpoint,
  ReleaseIngestEndpointCreateRequest,
  ReleaseIngestEndpointPatchRequest,
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

function httpErrorMessage(action: string, status: number, error: unknown): string {
  const detail = formatError(error);
  return `${action} (${status}): ${detail}`;
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
  return requireData("Could not load fleet targets", { data, error, response });
}

export async function listProjects(): Promise<ProjectDefinition[]> {
  const { data, error, response } = await apiClient.GET("/api/v1/projects");
  return requireData("Could not load projects", { data, error, response });
}

export async function createProject(
  request: ProjectCreateRequest
): Promise<ProjectDefinition> {
  const { data, error, response } = await apiClient.POST("/api/v1/projects", {
    body: request,
  });
  return requireData("Could not create project", { data, error, response });
}

export async function patchProjectConfiguration(
  projectId: string,
  request: ProjectConfigurationPatchRequest
): Promise<ProjectDefinition> {
  const { data, error, response } = await apiClient.PATCH("/api/v1/projects/{projectId}", {
    params: { path: { projectId } },
    body: request,
  });
  return requireData("Could not update project configuration", { data, error, response });
}

export async function deleteProject(projectId: string): Promise<void> {
  const response = await fetch(`${apiBaseUrl}/api/v1/projects/${encodeURIComponent(projectId)}`, {
    method: "DELETE",
  });
  if (!response.ok) {
    const payload = (await response.json().catch(() => ({}))) as Record<string, unknown>;
    throw new Error(httpErrorMessage("Could not delete project", response.status, payload));
  }
}

export async function validateProjectConfiguration(
  projectId: string,
  request: ProjectValidationRequest
): Promise<ProjectValidationResult> {
  const { data, error, response } = await apiClient.POST("/api/v1/projects/{projectId}/validate", {
    params: { path: { projectId } },
    body: request,
  });
  return requireData("Could not validate project configuration", { data, error, response });
}

export async function discoverProjectAdoPipelines(
  projectId: string,
  request: DiscoverProjectAdoPipelinesRequest
): Promise<ProjectAdoPipelineDiscoveryResult> {
  const response = await fetch(
    `${apiBaseUrl}/api/v1/projects/${encodeURIComponent(projectId)}/deployment-driver/ado/pipelines/discover`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request ?? {}),
    }
  );

  const payload = (await response.json().catch(() => ({}))) as Record<string, unknown>;
  if (!response.ok) {
    throw new Error(httpErrorMessage("Could not load Azure DevOps pipelines", response.status, payload));
  }
  return payload as ProjectAdoPipelineDiscoveryResult;
}

export async function discoverProjectAdoRepositories(
  projectId: string,
  request: DiscoverProjectAdoRepositoriesRequest
): Promise<ProjectAdoRepositoryDiscoveryResult> {
  const response = await fetch(
    `${apiBaseUrl}/api/v1/projects/${encodeURIComponent(projectId)}/deployment-driver/ado/repositories/discover`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request ?? {}),
    }
  );

  const payload = (await response.json().catch(() => ({}))) as Record<string, unknown>;
  if (!response.ok) {
    throw new Error(httpErrorMessage("Could not load Azure DevOps repositories", response.status, payload));
  }
  return payload as ProjectAdoRepositoryDiscoveryResult;
}

export async function discoverProjectAdoBranches(
  projectId: string,
  request: DiscoverProjectAdoBranchesRequest
): Promise<ProjectAdoBranchDiscoveryResult> {
  const response = await fetch(
    `${apiBaseUrl}/api/v1/projects/${encodeURIComponent(projectId)}/deployment-driver/ado/branches/discover`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request ?? {}),
    }
  );

  const payload = (await response.json().catch(() => ({}))) as Record<string, unknown>;
  if (!response.ok) {
    throw new Error(httpErrorMessage("Could not load Azure DevOps branches", response.status, payload));
  }
  return payload as ProjectAdoBranchDiscoveryResult;
}

export async function discoverProjectAdoServiceConnections(
  projectId: string,
  request: DiscoverProjectAdoServiceConnectionsRequest
): Promise<ProjectAdoServiceConnectionDiscoveryResult> {
  const response = await fetch(
    `${apiBaseUrl}/api/v1/projects/${encodeURIComponent(projectId)}/deployment-driver/ado/service-connections/discover`,
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request ?? {}),
    }
  );

  const payload = (await response.json().catch(() => ({}))) as Record<string, unknown>;
  if (!response.ok) {
    throw new Error(httpErrorMessage("Could not load Azure service connections", response.status, payload));
  }
  return payload as ProjectAdoServiceConnectionDiscoveryResult;
}

export async function listProviderConnections(): Promise<ProviderConnection[]> {
  const response = await fetch(`${apiBaseUrl}/api/v1/provider-connections`, {
    method: "GET",
    headers: {
      "Content-Type": "application/json",
    },
  });
  const payload = (await response.json().catch(() => ([]))) as unknown;
  if (!response.ok) {
    throw new Error(httpErrorMessage("Could not load deployment connections", response.status, payload));
  }
  return Array.isArray(payload) ? (payload as ProviderConnection[]) : [];
}

export async function createProviderConnection(
  request: ProviderConnectionCreateRequest
): Promise<ProviderConnection> {
  const response = await fetch(`${apiBaseUrl}/api/v1/provider-connections`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request),
  });
  const payload = (await response.json().catch(() => ({}))) as Record<string, unknown>;
  if (!response.ok) {
    throw new Error(httpErrorMessage("Could not create deployment connection", response.status, payload));
  }
  return payload as ProviderConnection;
}

export async function patchProviderConnection(
  connectionId: string,
  request: ProviderConnectionPatchRequest
): Promise<ProviderConnection> {
  const response = await fetch(
    `${apiBaseUrl}/api/v1/provider-connections/${encodeURIComponent(connectionId)}`,
    {
      method: "PATCH",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify(request),
    }
  );
  const payload = (await response.json().catch(() => ({}))) as Record<string, unknown>;
  if (!response.ok) {
    throw new Error(httpErrorMessage("Could not update deployment connection", response.status, payload));
  }
  return payload as ProviderConnection;
}

export async function verifyProviderConnectionAdoProjects(
  request: ProviderConnectionVerifyRequest
): Promise<ProviderConnectionAdoProjectDiscoveryResult> {
  const response = await fetch(`${apiBaseUrl}/api/v1/provider-connections/ado/verify`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    body: JSON.stringify(request ?? {}),
  });
  const payload = (await response.json().catch(() => ({}))) as Record<string, unknown>;
  if (!response.ok) {
    throw new Error(httpErrorMessage("Could not verify deployment connection", response.status, payload));
  }
  return payload as ProviderConnectionAdoProjectDiscoveryResult;
}

export async function discoverProviderConnectionAdoProjects(
  connectionId: string,
  nameContains?: string
): Promise<ProviderConnectionAdoProjectDiscoveryResult> {
  const query = nameContains && nameContains.trim() !== ""
    ? `?nameContains=${encodeURIComponent(nameContains.trim())}`
    : "";
  const response = await fetch(
    `${apiBaseUrl}/api/v1/provider-connections/${encodeURIComponent(connectionId)}/ado/projects/discover${query}`,
    {
      method: "GET",
      headers: {
        "Content-Type": "application/json",
      },
    }
  );
  const payload = (await response.json().catch(() => ({}))) as Record<string, unknown>;
  if (!response.ok) {
    throw new Error(httpErrorMessage("Could not load Azure DevOps projects", response.status, payload));
  }
  return payload as ProviderConnectionAdoProjectDiscoveryResult;
}

export async function deleteProviderConnection(connectionId: string): Promise<void> {
  const response = await fetch(
    `${apiBaseUrl}/api/v1/provider-connections/${encodeURIComponent(connectionId)}`,
    {
      method: "DELETE",
      headers: {
        "Content-Type": "application/json",
      },
    }
  );
  if (response.status >= 200 && response.status < 300) {
    return;
  }
  const payload = (await response.json().catch(() => ({}))) as Record<string, unknown>;
  throw new Error(httpErrorMessage("Could not delete deployment connection", response.status, payload));
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
  return requireData("Could not load project audit", { data, error, response });
}

export async function listReleases(): Promise<Release[]> {
  const { data, error, response } = await apiClient.GET("/api/v1/releases");
  return requireData("Could not load releases", { data, error, response });
}

export async function listReleaseIngestEndpoints(): Promise<ReleaseIngestEndpoint[]> {
  const { data, error, response } = await apiClient.GET("/api/v1/release-ingest/endpoints");
  return requireData("Could not load release sources", { data, error, response });
}

export async function createReleaseIngestEndpoint(
  request: ReleaseIngestEndpointCreateRequest
): Promise<ReleaseIngestEndpoint> {
  const { data, error, response } = await apiClient.POST("/api/v1/release-ingest/endpoints", {
    body: request,
  });
  return requireData("Could not create release source", { data, error, response });
}

export async function patchReleaseIngestEndpoint(
  endpointId: string,
  request: ReleaseIngestEndpointPatchRequest
): Promise<ReleaseIngestEndpoint> {
  const { data, error, response } = await apiClient.PATCH("/api/v1/release-ingest/endpoints/{endpointId}", {
    params: { path: { endpointId } },
    body: request,
  });
  return requireData("Could not update release source", { data, error, response });
}

export async function deleteReleaseIngestEndpoint(endpointId: string): Promise<void> {
  const { error, response } = await apiClient.DELETE("/api/v1/release-ingest/endpoints/{endpointId}", {
    params: { path: { endpointId } },
  });
  if (response.status >= 200 && response.status < 300) {
    return;
  }
  const errorText = error !== undefined ? formatError(error) : "unknown error";
  throw new Error(`Could not delete release source (${response.status}): ${errorText}`);
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
  return requireData("Could not load deployment runs", { data, error, response });
}

export async function getRun(runId: string): Promise<RunDetail> {
  const { data, error, response } = await apiClient.GET("/api/v1/runs/{runId}", {
    params: { path: { runId } },
  });
  return requireData("Could not load run details", { data, error, response });
}

export async function createRun(request: CreateRunRequest): Promise<RunDetail> {
  const { data, error, response } = await apiClient.POST("/api/v1/runs", {
    body: request,
  });
  return requireData("Could not start deployment run", { data, error, response });
}

export async function previewRun(request: CreateRunRequest, signal?: AbortSignal): Promise<RunPreview> {
  const { data, error, response } = await apiClient.POST("/api/v1/runs/preview", {
    body: request,
    signal,
  });
  return requireData("Could not preview deployment changes", { data, error, response });
}

export async function resumeRun(runId: string): Promise<RunDetail> {
  const { data, error, response } = await apiClient.POST("/api/v1/runs/{runId}/resume", {
    params: { path: { runId } },
  });
  return requireData("Could not resume deployment run", { data, error, response });
}

export async function retryFailed(runId: string): Promise<RunDetail> {
  const { data, error, response } = await apiClient.POST("/api/v1/runs/{runId}/retry-failed", {
    params: { path: { runId } },
  });
  return requireData("Could not retry failed targets", { data, error, response });
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
        projectId: query.projectId,
        ring: query.ring,
        region: query.region,
        tier: query.tier,
      },
    },
  });
  return requireData("Could not load registered targets", { data, error, response });
}

export async function adminListMarketplaceEvents(
  query: ListMarketplaceEventsQuery = {}
): Promise<MarketplaceEventPage> {
  const { data, error, response } = await apiClient.GET("/api/v1/admin/onboarding/events", {
    params: {
      query: {
        page: query.page,
        size: query.size,
        projectId: query.projectId,
        eventId: query.eventId,
        status: query.status,
      },
    },
  });
  return requireData("Could not load onboarding events", { data, error, response });
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
  return requireData("Could not load managed app logs", { data, error, response });
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
  return requireData("Could not load release source events", { data, error, response });
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
  return requireData("Could not onboard targets from event", { data, error, response });
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
  return requireData("Could not update target registration", { data, error, response });
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
  return requireData("Could not delete target registration", { data, error, response });
}

export async function adminIngestGithubReleaseManifest(
  request?: ReleaseManifestIngestRequest
): Promise<ReleaseManifestIngestResponse> {
  const { data, error, response } = await apiClient.POST("/api/v1/admin/releases/ingest/github", {
    body: request,
  });
  return requireData("Could not ingest managed-app releases", { data, error, response });
}
