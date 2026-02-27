import type { Page, Route } from "@playwright/test";

type Target = {
  id: string;
  tenant_id: string;
  subscription_id: string;
  managed_app_id: string;
  tags: Record<string, string>;
  last_deployed_release: string;
  health_status: string;
  last_check_in_at: string;
  simulated_failure_mode: string;
};

type Release = {
  id: string;
  template_spec_id: string;
  template_spec_version: string;
  parameter_defaults: Record<string, string>;
  release_notes: string;
  verification_hints: string[];
  created_at: string;
};

type RunSummary = {
  id: string;
  release_id: string;
  status: "running" | "succeeded" | "failed" | "partial" | "halted";
  strategy_mode: "all_at_once" | "waves";
  created_at: string;
  started_at: string | null;
  ended_at: string | null;
  total_targets: number;
  succeeded_targets: number;
  failed_targets: number;
  in_progress_targets: number;
  queued_targets: number;
  halt_reason: string | null;
};

type TargetExecutionRecord = {
  target_id: string;
  subscription_id: string;
  tenant_id: string;
  status: "QUEUED" | "VALIDATING" | "DEPLOYING" | "VERIFYING" | "SUCCEEDED" | "FAILED";
  attempt: number;
  updated_at: string;
  stages: unknown[];
  logs: unknown[];
};

type RunDetail = {
  id: string;
  release_id: string;
  status: "running" | "succeeded" | "failed" | "partial" | "halted";
  strategy_mode: "all_at_once" | "waves";
  wave_tag: string;
  wave_order: string[];
  concurrency: number;
  stop_policy: Record<string, unknown>;
  created_at: string;
  started_at: string | null;
  ended_at: string | null;
  updated_at: string;
  halt_reason: string | null;
  target_records: TargetExecutionRecord[];
};

type CreateRunRequest = {
  release_id?: string;
  strategy_mode?: "all_at_once" | "waves";
  wave_tag?: string;
  wave_order?: string[];
  concurrency?: number;
  stop_policy?: Record<string, unknown>;
  target_ids?: string[];
  target_tags?: Record<string, string>;
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
    makeTarget("target-01", "canary", "eastus", "gold"),
    makeTarget("target-02", "canary", "westus", "gold"),
    makeTarget("target-03", "prod", "eastus", "silver"),
    makeTarget("target-04", "prod", "westus", "silver"),
  ];

  const releases: Release[] = [
    {
      id: "rel-2026-02-25",
      template_spec_id: "template-spec-id",
      template_spec_version: "2026.02.25.3",
      parameter_defaults: { imageTag: "1.5.0", featureFlag: "on" },
      release_notes: "Canary-first rollout.",
      verification_hints: ["health endpoint 200"],
      created_at: NOW,
    },
    {
      id: "rel-2026-02-20",
      template_spec_id: "template-spec-id",
      template_spec_version: "2026.02.20.1",
      parameter_defaults: { imageTag: "1.4.2", featureFlag: "off" },
      release_notes: "Stable baseline.",
      verification_hints: ["startup under 60s"],
      created_at: "2026-02-20T00:00:00Z",
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

  if (method === "GET" && path === "/api/v1/targets") {
    const ring = url.searchParams.get("ring");
    const payload = ring
      ? state.targets.filter((target) => target.tags.ring === ring)
      : state.targets;
    await respond(route, 200, payload);
    return;
  }

  if (method === "GET" && path === "/api/v1/releases") {
    await respond(route, 200, state.releases);
    return;
  }

  if (method === "GET" && path === "/api/v1/runs") {
    await respond(route, 200, state.runs);
    return;
  }

  if (method === "POST" && path === "/api/v1/runs") {
    const body = request.postDataJSON() as CreateRunRequest;
    state.createRunRequests.push(body);

    if (!body.release_id) {
      await respond(route, 400, { detail: "release_id is required" });
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
      releaseId: body.release_id,
      strategyMode: body.strategy_mode ?? "waves",
      status: "running",
      targetIds: selectedTargetIds,
      targetStatus: "QUEUED",
      concurrency: body.concurrency ?? 3,
      stopPolicy: body.stop_policy ?? {},
      waveTag: body.wave_tag ?? "ring",
      waveOrder: body.wave_order ?? ["canary", "prod"],
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

async function respond(route: Route, status: number, payload: unknown): Promise<void> {
  await route.fulfill({
    status,
    contentType: "application/json",
    body: JSON.stringify(payload),
  });
}

function selectTargetIds(request: CreateRunRequest, targets: Target[]): string[] {
  const targetMap = new Map(targets.map((target) => [target.id, target]));
  const filters = request.target_tags ?? {};

  const candidates = request.target_ids ?? targets.map((target) => target.id);
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
  id: string,
  ring: "canary" | "prod",
  region: string,
  tier: string
): Target {
  return {
    id,
    tenant_id: `tenant-${id}`,
    subscription_id: `sub-${id}`,
    managed_app_id: `/subscriptions/sub-${id}/resourceGroups/rg-${id}`,
    tags: {
      ring,
      region,
      tier,
      environment: "prod",
    },
    last_deployed_release: "2026.02.20.1",
    health_status: "healthy",
    last_check_in_at: NOW,
    simulated_failure_mode: "none",
  };
}

function makeRunDetail(input: {
  id: string;
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
  const targetRecords = input.targetIds.map((targetId) => ({
    target_id: targetId,
    subscription_id: `sub-${targetId}`,
    tenant_id: `tenant-${targetId}`,
    status: input.targetStatus,
    attempt: input.targetStatus === "QUEUED" ? 0 : 1,
    updated_at: NOW,
    stages: [],
    logs: [],
  }));

  return {
    id: input.id,
    release_id: input.releaseId,
    status: input.status,
    strategy_mode: input.strategyMode,
    wave_tag: input.waveTag ?? "ring",
    wave_order: input.waveOrder ?? ["canary", "prod"],
    concurrency: input.concurrency ?? 3,
    stop_policy: input.stopPolicy ?? {},
    created_at: NOW,
    started_at: NOW,
    ended_at: input.status === "running" ? null : NOW,
    updated_at: NOW,
    halt_reason: input.status === "halted" ? "halted by policy" : null,
    target_records: targetRecords,
  };
}

function toRunSummary(detail: RunDetail): RunSummary {
  const counts = {
    queued: 0,
    inProgress: 0,
    succeeded: 0,
    failed: 0,
  };
  for (const record of detail.target_records) {
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
    release_id: detail.release_id,
    status: detail.status,
    strategy_mode: detail.strategy_mode,
    created_at: detail.created_at,
    started_at: detail.started_at,
    ended_at: detail.ended_at,
    total_targets: detail.target_records.length,
    succeeded_targets: counts.succeeded,
    failed_targets: counts.failed,
    in_progress_targets: counts.inProgress,
    queued_targets: counts.queued,
    halt_reason: detail.halt_reason,
  };
}
