import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import App from "@/App";

const mockTargets = [
  {
    id: "target-01",
    tenant_id: "tenant-001",
    subscription_id: "sub-0001",
    managed_app_id: "managed-app-1",
    tags: { ring: "canary", region: "eastus", tier: "gold", environment: "prod" },
    last_deployed_release: "2026.02.20.1",
    health_status: "healthy",
    last_check_in_at: "2026-02-25T00:00:00Z",
    simulated_failure_mode: "none",
  },
];

const mockReleases = [
  {
    id: "rel-2026-02-25",
    template_spec_id: "template-spec-id",
    template_spec_version: "2026.02.25.3",
    parameter_defaults: { imageTag: "1.5.0" },
    release_notes: "Release notes",
    verification_hints: [],
    created_at: "2026-02-25T00:00:00Z",
  },
];

const mockRuns = [
  {
    id: "run-1",
    release_id: "rel-2026-02-25",
    status: "running",
    strategy_mode: "waves",
    created_at: "2026-02-25T00:00:00Z",
    started_at: "2026-02-25T00:00:00Z",
    ended_at: null,
    total_targets: 1,
    succeeded_targets: 0,
    failed_targets: 0,
    in_progress_targets: 1,
    queued_targets: 0,
    halt_reason: null,
  },
];

const mockRunDetail = {
  id: "run-1",
  release_id: "rel-2026-02-25",
  status: "running",
  strategy_mode: "waves",
  wave_tag: "ring",
  wave_order: ["canary", "prod"],
  concurrency: 1,
  stop_policy: {},
  created_at: "2026-02-25T00:00:00Z",
  started_at: "2026-02-25T00:00:00Z",
  ended_at: null,
  updated_at: "2026-02-25T00:00:00Z",
  halt_reason: null,
  target_records: [
    {
      target_id: "target-01",
      subscription_id: "sub-0001",
      tenant_id: "tenant-001",
      status: "DEPLOYING",
      attempt: 1,
      updated_at: "2026-02-25T00:00:00Z",
      stages: [],
      logs: [],
    },
  ],
};

const apiMock = vi.hoisted(() => ({
  createRun: vi.fn(),
  getRun: vi.fn(),
  listReleases: vi.fn(),
  listRuns: vi.fn(),
  listTargets: vi.fn(),
  resumeRun: vi.fn(),
  retryFailed: vi.fn(),
}));

vi.mock("@/lib/api", () => apiMock);

describe("App", () => {
  beforeEach(() => {
    apiMock.listTargets.mockResolvedValue(mockTargets);
    apiMock.listReleases.mockResolvedValue(mockReleases);
    apiMock.listRuns.mockResolvedValue(mockRuns);
    apiMock.getRun.mockResolvedValue(mockRunDetail);
    apiMock.createRun.mockReset();
    apiMock.resumeRun.mockReset();
    apiMock.retryFailed.mockReset();
  });

  it("renders dashboard data from API", async () => {
    render(<App />);

    expect(screen.getByRole("heading", { name: /MAPPO Control Plane/i })).toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText("target-01")).toBeInTheDocument();
      expect(screen.getByText("run-1")).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Start Run/i })).toBeInTheDocument();
    });
  });
});
