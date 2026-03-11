import type { Page, Route } from "@playwright/test";

type Target = {
  id: string;
  projectId: string;
  tenantId: string;
  subscriptionId: string;
  managedAppId: string;
  customerName?: string;
  tags: Record<string, string>;
  lastDeployedRelease: string;
  healthStatus: string;
  runtimeStatus?: string;
  runtimeCheckedAt?: string;
  runtimeSummary?: string;
  lastDeploymentStatus?: string;
  lastDeploymentAt?: string;
  lastCheckInAt: string;
  simulatedFailureMode: string;
};

type Release = {
  id: string;
  projectId: string;
  sourceRef: string;
  sourceVersion: string;
  sourceType: "template_spec" | "bicep" | "deployment_stack";
  parameterDefaults: Record<string, string>;
  releaseNotes: string;
  verificationHints: string[];
  createdAt: string;
};

type RunSummary = {
  id: string;
  projectId: string;
  releaseId: string;
  executionSourceType: "template_spec" | "bicep" | "deployment_stack";
  status: "running" | "succeeded" | "failed" | "partial" | "halted";
  strategyMode: "all_at_once" | "waves";
  createdAt: string;
  startedAt: string | null;
  endedAt: string | null;
  totalTargets: number;
  succeededTargets: number;
  failedTargets: number;
  inProgressTargets: number;
  queuedTargets: number;
  haltReason: string | null;
};

type RunSummaryPage = {
  items: RunSummary[];
  page: {
    page: number;
    size: number;
    totalItems: number;
    totalPages: number;
  };
  activeRunCount: number;
};

type TargetExecutionRecord = {
  targetId: string;
  subscriptionId: string;
  tenantId: string;
  status: "QUEUED" | "VALIDATING" | "DEPLOYING" | "VERIFYING" | "SUCCEEDED" | "FAILED";
  attempt: number;
  updatedAt: string;
  stages: unknown[];
  logs: unknown[];
};

type RunDetail = {
  id: string;
  projectId: string;
  releaseId: string;
  executionSourceType: "template_spec" | "bicep" | "deployment_stack";
  status: "running" | "succeeded" | "failed" | "partial" | "halted";
  strategyMode: "all_at_once" | "waves";
  waveTag: string;
  waveOrder: string[];
  concurrency: number;
  stopPolicy: Record<string, unknown>;
  createdAt: string;
  startedAt: string | null;
  endedAt: string | null;
  updatedAt: string;
  haltReason: string | null;
  targetRecords: TargetExecutionRecord[];
};

type CreateRunRequest = {
  releaseId?: string;
  strategyMode?: "all_at_once" | "waves";
  waveTag?: string;
  waveOrder?: string[];
  concurrency?: number;
  stopPolicy?: Record<string, unknown>;
  targetIds?: string[];
  targetTags?: Record<string, string>;
};

export type MockApiState = {
  targets: Target[];
  releases: Release[];
  runs: RunSummary[];
  runDetails: Map<string, RunDetail>;
  nextRunId: number;
  createRunRequests: CreateRunRequest[];
};

const NOW = "2026-02-26T00:00:00Z";

export function createMockApiState(): MockApiState {
  const targets: Target[] = [
    makeTarget("managed-app-demo", "target-01", "canary", "eastus", "gold"),
    makeTarget("managed-app-demo", "target-02", "canary", "westus", "gold"),
    makeTarget("managed-app-demo", "target-03", "prod", "eastus", "silver"),
    makeTarget("managed-app-demo", "target-04", "prod", "westus", "silver"),
  ];

  const releases: Release[] = [
    {
      id: "rel-2026-02-25",
      projectId: "managed-app-demo",
      sourceRef: "template-spec-id",
      sourceVersion: "2026.02.25.3",
      sourceType: "template_spec",
      parameterDefaults: { imageTag: "1.5.0", featureFlag: "on" },
      releaseNotes: "Canary-first rollout.",
      verificationHints: ["health endpoint 200"],
      createdAt: NOW,
    },
    {
      id: "rel-2026-02-20",
      projectId: "managed-app-demo",
      sourceRef: "template-spec-id",
      sourceVersion: "2026.02.20.1",
      sourceType: "template_spec",
      parameterDefaults: { imageTag: "1.4.2", featureFlag: "off" },
      releaseNotes: "Stable baseline.",
      verificationHints: ["startup under 60s"],
      createdAt: "2026-02-20T00:00:00Z",
    },
  ];

  const succeededDetail = makeRunDetail({
    id: "run-succeeded",
    releaseId: "rel-2026-02-25",
    strategyMode: "waves",
    status: "succeeded",
    targetIds: ["target-01", "target-02"],
    targetStatus: "SUCCEEDED",
  });
  const failedDetail = makeRunDetail({
    id: "run-failed",
    releaseId: "rel-2026-02-25",
    strategyMode: "all_at_once",
    status: "failed",
    targetIds: ["target-03"],
    targetStatus: "FAILED",
  });

  const runs: RunSummary[] = [
    toRunSummary(succeededDetail),
    toRunSummary(failedDetail),
  ];

  return {
    targets,
    releases,
    runs,
    runDetails: new Map<string, RunDetail>([
      [succeededDetail.id, succeededDetail],
      [failedDetail.id, failedDetail],
    ]),
    nextRunId: 1,
    createRunRequests: [],
  };
}

export async function installMockApi(page: Page, state: MockApiState): Promise<void> {
  await page.route("**/api/v1/**", async (route) => {
    await handleRoute(route, state);
  });
}

async function handleRoute(route: Route, state: MockApiState): Promise<void> {
  const request = route.request();
  const url = new URL(request.url());
  const path = url.pathname;
  const method = request.method();

  if (method === "GET" && path === "/api/v1/targets/page") {
    const projectIdFilter = (url.searchParams.get("projectId") ?? "").toLowerCase();
    const targetIdFilter = (url.searchParams.get("targetId") ?? "").toLowerCase();
    const customerNameFilter = (url.searchParams.get("customerName") ?? "").toLowerCase();
    const tenantIdFilter = (url.searchParams.get("tenantId") ?? "").toLowerCase();
    const subscriptionIdFilter = (url.searchParams.get("subscriptionId") ?? "").toLowerCase();
    const ringFilter = (url.searchParams.get("ring") ?? "").toLowerCase();
    const regionFilter = (url.searchParams.get("region") ?? "").toLowerCase();
    const tierFilter = (url.searchParams.get("tier") ?? "").toLowerCase();
    const versionFilter = (url.searchParams.get("version") ?? "").toLowerCase();
    const runtimeFilter = (url.searchParams.get("runtimeStatus") ?? "").toLowerCase();
    const page = Math.max(0, Number.parseInt(url.searchParams.get("page") ?? "0", 10) || 0);
    const size = Math.max(1, Number.parseInt(url.searchParams.get("size") ?? "10", 10) || 10);

    const filtered = state.targets.filter((target) => {
      if (projectIdFilter && target.projectId.toLowerCase() !== projectIdFilter) return false;
      if (targetIdFilter && !target.id.toLowerCase().includes(targetIdFilter)) return false;
      if (customerNameFilter && !(target.customerName ?? "").toLowerCase().includes(customerNameFilter)) return false;
      if (tenantIdFilter && !target.tenantId.toLowerCase().includes(tenantIdFilter)) return false;
      if (subscriptionIdFilter && !target.subscriptionId.toLowerCase().includes(subscriptionIdFilter)) return false;
      if (ringFilter && (target.tags.ring ?? "").toLowerCase() !== ringFilter) return false;
      if (regionFilter && (target.tags.region ?? "").toLowerCase() !== regionFilter) return false;
      if (tierFilter && (target.tags.tier ?? "").toLowerCase() !== tierFilter) return false;
      if (versionFilter && !(target.lastDeployedRelease ?? "").toLowerCase().includes(versionFilter)) return false;
      if (runtimeFilter && (target.runtimeStatus ?? "").toLowerCase() !== runtimeFilter) return false;
      return true;
    });

    await respond(route, 200, paginateItems(filtered, page, size));
    return;
  }

  if (method === "GET" && path === "/api/v1/releases") {
    await respond(route, 200, state.releases);
    return;
  }

  if (method === "GET" && path === "/api/v1/projects") {
    await respond(route, 200, [
      {
        id: "managed-app-demo",
        name: "Managed App Demo",
        description: "Demo project",
        accessStrategy: "marketplace_publisher_access",
        deploymentDriver: "azure_deployment_stack",
        releaseMaterializer: "github_blob_arm_template",
        runtimeHealthProvider: "container_app_probe",
        enabled: true,
      },
    ]);
    return;
  }

  if (method === "GET" && path === "/api/v1/admin/onboarding/registrations") {
    const page = Math.max(0, Number.parseInt(url.searchParams.get("page") ?? "0", 10) || 0);
    const size = Math.max(1, Number.parseInt(url.searchParams.get("size") ?? "10", 10) || 10);
    await respond(route, 200, paginateItems([], page, size));
    return;
  }

  if (method === "GET" && path === "/api/v1/admin/onboarding/events") {
    const page = Math.max(0, Number.parseInt(url.searchParams.get("page") ?? "0", 10) || 0);
    const size = Math.max(1, Number.parseInt(url.searchParams.get("size") ?? "10", 10) || 10);
    await respond(route, 200, paginateItems([], page, size));
    return;
  }

  if (method === "GET" && path === "/api/v1/admin/onboarding/forwarder-logs/page") {
    const page = Math.max(0, Number.parseInt(url.searchParams.get("page") ?? "0", 10) || 0);
    const size = Math.max(1, Number.parseInt(url.searchParams.get("size") ?? "10", 10) || 10);
    await respond(route, 200, paginateItems([], page, size));
    return;
  }

  if (method === "GET" && path === "/api/v1/admin/releases/webhook-deliveries") {
    const page = Math.max(0, Number.parseInt(url.searchParams.get("page") ?? "0", 10) || 0);
    const size = Math.max(1, Number.parseInt(url.searchParams.get("size") ?? "10", 10) || 10);
    await respond(route, 200, paginateItems([], page, size));
    return;
  }

  if (method === "GET" && path === "/api/v1/runs") {
    const projectIdFilter = (url.searchParams.get("projectId") ?? "").toLowerCase();
    const runIdFilter = (url.searchParams.get("runId") ?? "").toLowerCase();
    const releaseFilter = (url.searchParams.get("releaseId") ?? "").toLowerCase();
    const statusFilter = (url.searchParams.get("status") ?? "").toLowerCase();
    const page = Math.max(0, Number.parseInt(url.searchParams.get("page") ?? "0", 10) || 0);
    const size = Math.max(1, Number.parseInt(url.searchParams.get("size") ?? "25", 10) || 25);
    const filtered = state.runs.filter((run) => {
      if (projectIdFilter && run.projectId.toLowerCase() !== projectIdFilter) {
        return false;
      }
      if (runIdFilter && !run.id.toLowerCase().includes(runIdFilter)) {
        return false;
      }
      if (releaseFilter && !run.releaseId.toLowerCase().includes(releaseFilter)) {
        return false;
      }
      if (statusFilter && run.status.toLowerCase() !== statusFilter) {
        return false;
      }
      return true;
    });
    const paged = paginateRuns(filtered, page, size, state.runs);
    await respond(route, 200, paged);
    return;
  }

  if (method === "POST" && path === "/api/v1/runs") {
    const body = request.postDataJSON() as CreateRunRequest;
    state.createRunRequests.push(body);

    if (!body.releaseId) {
      await respond(route, 400, { detail: "releaseId is required" });
      return;
    }

    const selectedTargetIds = selectTargetIds(body, state.targets);
    if (selectedTargetIds.length === 0) {
      await respond(route, 400, { detail: "no targets matched target selection" });
      return;
    }

    const runId = `run-e2e-${state.nextRunId}`;
    state.nextRunId += 1;

    const detail = makeRunDetail({
      id: runId,
      releaseId: body.releaseId,
      strategyMode: body.strategyMode ?? "waves",
      status: "running",
      targetIds: selectedTargetIds,
      targetStatus: "QUEUED",
      concurrency: body.concurrency ?? 3,
      stopPolicy: body.stopPolicy ?? {},
      waveTag: body.waveTag ?? "ring",
      waveOrder: body.waveOrder ?? ["canary", "prod"],
    });

    state.runDetails.set(runId, detail);
    state.runs = [toRunSummary(detail), ...state.runs];

    await respond(route, 201, detail);
    return;
  }

  const runDetailMatch = path.match(/^\/api\/v1\/runs\/([^/]+)$/);
  if (method === "GET" && runDetailMatch) {
    const runId = decodeURIComponent(runDetailMatch[1]);
    const run = state.runDetails.get(runId);
    if (!run) {
      await respond(route, 404, { detail: `run not found: ${runId}` });
      return;
    }
    await respond(route, 200, run);
    return;
  }

  const resumeMatch = path.match(/^\/api\/v1\/runs\/([^/]+)\/resume$/);
  if (method === "POST" && resumeMatch) {
    const runId = decodeURIComponent(resumeMatch[1]);
    const run = state.runDetails.get(runId);
    if (!run) {
      await respond(route, 404, { detail: `run not found: ${runId}` });
      return;
    }
    await respond(route, 200, run);
    return;
  }

  const retryMatch = path.match(/^\/api\/v1\/runs\/([^/]+)\/retry-failed$/);
  if (method === "POST" && retryMatch) {
    const runId = decodeURIComponent(retryMatch[1]);
    const run = state.runDetails.get(runId);
    if (!run) {
      await respond(route, 404, { detail: `run not found: ${runId}` });
      return;
    }
    await respond(route, 200, run);
    return;
  }

  await respond(route, 404, { detail: `unhandled route: ${method} ${path}` });
}

function paginateRuns(runs: RunSummary[], page: number, size: number, allRuns: RunSummary[]): RunSummaryPage {
  const totalItems = runs.length;
  const totalPages = totalItems === 0 ? 0 : Math.ceil(totalItems / size);
  const start = page * size;
  const items = runs.slice(start, start + size);

  return {
    items,
    page: {
      page,
      size,
      totalItems,
      totalPages,
    },
    activeRunCount: allRuns.filter((run) => run.status === "running").length,
  };
}

function paginateItems<T>(items: T[], page: number, size: number) {
  const totalItems = items.length;
  const totalPages = totalItems === 0 ? 0 : Math.ceil(totalItems / size);
  const start = page * size;
  return {
    items: items.slice(start, start + size),
    page: {
      page,
      size,
      totalItems,
      totalPages,
    },
  };
}

async function respond(route: Route, status: number, payload: unknown): Promise<void> {
  await route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(payload),
  });
}

function selectTargetIds(request: CreateRunRequest, targets: Target[]): string[] {
  const targetMap = new Map(targets.map((target) => [target.id, target]));
  const filters = request.targetTags ?? {};

  const candidates = request.targetIds ?? targets.map((target) => target.id);
  const selected = candidates.filter((targetId) => {
    const target = targetMap.get(targetId);
    if (!target) {
      return false;
    }
    return matchesTags(target.tags, filters);
  });

  return [...new Set(selected)].sort();
}

function matchesTags(targetTags: Record<string, string>, requestedTags: Record<string, string>): boolean {
  for (const [key, value] of Object.entries(requestedTags)) {
    if (targetTags[key] !== value) {
      return false;
    }
  }
  return true;
}

function makeTarget(
  projectId: string,
  id: string,
  ring: "canary" | "prod",
  region: string,
  tier: string
): Target {
  return {
    id,
    projectId,
    tenantId: `tenant-${id}`,
    subscriptionId: `sub-${id}`,
    managedAppId: `/subscriptions/sub-${id}/resourceGroups/rg-${id}`,
    customerName: `Customer ${id}`,
    tags: {
      ring,
      region,
      tier,
      environment: "prod",
    },
    lastDeployedRelease: "2026.02.20.1",
    healthStatus: "healthy",
    runtimeStatus: "healthy",
    runtimeCheckedAt: NOW,
    runtimeSummary: "Runtime responded with HTTP 200.",
    lastDeploymentStatus: "SUCCEEDED",
    lastDeploymentAt: NOW,
    lastCheckInAt: NOW,
    simulatedFailureMode: "none",
  };
}

function makeRunDetail(input: {
  id: string;
  projectId?: string;
  releaseId: string;
  strategyMode: "all_at_once" | "waves";
  status: "running" | "succeeded" | "failed" | "partial" | "halted";
  targetIds: string[];
  targetStatus: TargetExecutionRecord["status"];
  concurrency?: number;
  stopPolicy?: Record<string, unknown>;
  waveTag?: string;
  waveOrder?: string[];
}): RunDetail {
  const targetRecords = input.targetIds.map((targetId) => {
    const portalLink = `https://portal.azure.com/#resource/sub-${targetId}/rg-${targetId}/ca-${targetId}`;

    if (input.targetStatus === "FAILED") {
      return {
        targetId,
        subscriptionId: `sub-${targetId}`,
        tenantId: `tenant-${targetId}`,
        status: input.targetStatus,
        attempt: 1,
        updatedAt: NOW,
        stages: [
          {
            stage: "VALIDATING",
            startedAt: NOW,
            endedAt: NOW,
            message: `Validated target ca-${targetId}.`,
            error: null,
            correlationId: "corr-e2e-validating",
            portalLink,
          },
          {
            stage: "DEPLOYING",
            startedAt: NOW,
            endedAt: NOW,
            message: "Azure Container App update failed.",
            error: {
              code: "azure_deploy_failed",
              message: "Azure Container App update failed.",
              details: {
                statusCode: 200,
                error:
                  "(ContainerAppOperationError) MANIFEST_UNKNOWN: manifest tagged by \"1.5.0\" is not found.",
                desiredImage: "mcr.microsoft.com/azuredocs/containerapps-helloworld:1.5.0",
              },
            },
            correlationId: "corr-e2e-deploying",
            portalLink,
          },
        ],
        logs: [
          {
            timestamp: NOW,
            level: "INFO",
            stage: "VALIDATING",
            message: "Validating started.",
            correlationId: "corr-e2e-validating",
          },
          {
            timestamp: NOW,
            level: "INFO",
            stage: "VALIDATING",
            message: `Validated target ca-${targetId}.`,
            correlationId: "corr-e2e-validating",
          },
          {
            timestamp: NOW,
            level: "INFO",
            stage: "DEPLOYING",
            message: "Deploying started.",
            correlationId: "corr-e2e-deploying",
          },
          {
            timestamp: NOW,
            level: "ERROR",
            stage: "DEPLOYING",
            message: "Azure Container App update failed.",
            correlationId: "corr-e2e-deploying",
          },
        ],
      };
    }

    const terminalStage = input.targetStatus === "SUCCEEDED" ? "SUCCEEDED" : input.targetStatus;
    const stageList =
      input.targetStatus === "QUEUED"
        ? []
        : [
            {
              stage: "VALIDATING",
              startedAt: NOW,
              endedAt: NOW,
              message: `Validated target ca-${targetId}.`,
              error: null,
              correlationId: "corr-e2e-validating",
              portalLink,
            },
            {
              stage: terminalStage,
              startedAt: NOW,
              endedAt: NOW,
              message: input.targetStatus === "SUCCEEDED" ? "Target deployment succeeded." : `${terminalStage} completed.`,
              error: null,
              correlationId: "corr-e2e-terminal",
              portalLink,
            },
          ];

    return {
      targetId,
      subscriptionId: `sub-${targetId}`,
      tenantId: `tenant-${targetId}`,
      status: input.targetStatus,
      attempt: input.targetStatus === "QUEUED" ? 0 : 1,
      updatedAt: NOW,
      stages: stageList,
      logs: [],
    };
  });

  return {
    id: input.id,
    projectId: input.projectId ?? "managed-app-demo",
    releaseId: input.releaseId,
    executionSourceType: "template_spec",
    status: input.status,
    strategyMode: input.strategyMode,
    waveTag: input.waveTag ?? "ring",
    waveOrder: input.waveOrder ?? ["canary", "prod"],
    concurrency: input.concurrency ?? 3,
    stopPolicy: input.stopPolicy ?? {},
    createdAt: NOW,
    startedAt: NOW,
    endedAt: input.status === "running" ? null : NOW,
    updatedAt: NOW,
    haltReason: input.status === "halted" ? "halted by policy" : null,
    targetRecords: targetRecords,
  };
}

function toRunSummary(detail: RunDetail): RunSummary {
  const counts = {
    queued: 0,
    inProgress: 0,
    succeeded: 0,
    failed: 0,
  };
  for (const record of detail.targetRecords) {
    if (record.status === "QUEUED") {
      counts.queued += 1;
      continue;
    }
    if (record.status === "SUCCEEDED") {
      counts.succeeded += 1;
      continue;
    }
    if (record.status === "FAILED") {
      counts.failed += 1;
      continue;
    }
    counts.inProgress += 1;
  }

  return {
    id: detail.id,
    projectId: detail.projectId,
    releaseId: detail.releaseId,
    executionSourceType: detail.executionSourceType,
    status: detail.status,
    strategyMode: detail.strategyMode,
    createdAt: detail.createdAt,
    startedAt: detail.startedAt,
    endedAt: detail.endedAt,
    totalTargets: detail.targetRecords.length,
    succeededTargets: counts.succeeded,
    failedTargets: counts.failed,
    inProgressTargets: counts.inProgress,
    queuedTargets: counts.queued,
    haltReason: detail.haltReason,
  };
}
