import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import App from "@/App";

const mockTargets = [
  {
    id: "target-01",
    tenantId: "tenant-001",
    subscriptionId: "sub-0001",
    managedAppId: "managed-app-1",
    tags: { ring: "canary", region: "eastus", tier: "gold", environment: "prod" },
    lastDeployedRelease: "2026.02.20.1",
    healthStatus: "healthy",
    lastCheckInAt: "2026-02-25T00:00:00Z",
    simulatedFailureMode: "none",
  },
];

const mockReleases = [
  {
    id: "rel-2026-02-25",
    sourceRef: "template-spec-id",
    sourceVersion: "2026.02.25.3",
    sourceType: "template_spec",
    parameterDefaults: { imageTag: "1.5.0" },
    releaseNotes: "Release notes",
    verificationHints: [],
    createdAt: "2026-02-25T00:00:00Z",
  },
];

const mockRuns = [
  {
    id: "run-1",
    releaseId: "rel-2026-02-25",
    status: "running",
    strategyMode: "waves",
    createdAt: "2026-02-25T00:00:00Z",
    startedAt: "2026-02-25T00:00:00Z",
    endedAt: null,
    totalTargets: 1,
    succeededTargets: 0,
    failedTargets: 0,
    inProgressTargets: 1,
    queuedTargets: 0,
    haltReason: null,
  },
];

const mockRunDetail = {
  id: "run-1",
  releaseId: "rel-2026-02-25",
  executionSourceType: "template_spec",
  status: "running",
  strategyMode: "waves",
  waveTag: "ring",
  waveOrder: ["canary", "prod"],
  concurrency: 1,
  stopPolicy: {},
  createdAt: "2026-02-25T00:00:00Z",
  startedAt: "2026-02-25T00:00:00Z",
  endedAt: null,
  updatedAt: "2026-02-25T00:00:00Z",
  haltReason: null,
  targetRecords: [
    {
      targetId: "target-01",
      subscriptionId: "sub-0001",
      tenantId: "tenant-001",
      status: "DEPLOYING",
      attempt: 1,
      updatedAt: "2026-02-25T00:00:00Z",
      stages: [
        {
          stage: "VALIDATING",
          startedAt: "2026-02-25T00:00:00Z",
          endedAt: "2026-02-25T00:00:01Z",
          message: "Validated target ca-target-01.",
          error: null,
          correlationId: "corr-unit-validating",
          portalLink: "https://portal.azure.com/#resource/sub-0001/rg-target-01/ca-target-01",
        },
      ],
      logs: [],
    },
  ],
};

const mockAdminSnapshot = {
  registrations: [],
  events: [],
  forwarderLogs: [],
};

const apiMock = vi.hoisted(() => ({
  adminIngestGithubReleaseManifest: vi.fn(),
  adminIngestMarketplaceEvent: vi.fn(),
  createRun: vi.fn(),
  getAdminOnboardingSnapshot: vi.fn(),
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
    apiMock.getAdminOnboardingSnapshot.mockResolvedValue(mockAdminSnapshot);
    apiMock.adminIngestGithubReleaseManifest.mockReset();
    apiMock.adminIngestMarketplaceEvent.mockReset();
    apiMock.createRun.mockReset();
    apiMock.resumeRun.mockReset();
    apiMock.retryFailed.mockReset();
  });

  it("renders dashboard data from API", async () => {
    render(<App />);

    expect(screen.getByText("MAPPO Control Plane")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /Multi-tenant Managed App Orchestrator/i })).toBeInTheDocument();
    expect(screen.queryByText(/Attention Needed/i)).not.toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText("target-01")).toBeInTheDocument();
      expect(screen.getByRole("link", { name: /Fleet/i })).toBeInTheDocument();
      expect(screen.getByRole("link", { name: /Deployments/i })).toBeInTheDocument();
      expect(screen.getByRole("link", { name: /Admin/i })).toBeInTheDocument();
    });

    fireEvent.click(screen.getAllByRole("link", { name: /Deployments/i })[0]);

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /New Deployment/i })).toBeInTheDocument();
      expect(screen.getByText("run-1")).toBeInTheDocument();
      expect(screen.getByTestId("run-row-run-1")).toBeInTheDocument();
      expect(screen.getByTestId("run-actions-trigger-run-1")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /New Deployment/i }));

    await waitFor(() => {
      expect(screen.getByLabelText("Release version")).toBeInTheDocument();
      expect(screen.getByText(/Specific targets selected: 0/i)).toBeInTheDocument();
    });

    fireEvent.click(screen.getByTestId("select-run-run-1"));

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /Back To Deployments/i })).toBeInTheDocument();
      expect(screen.getByRole("heading", { name: "Run Detail" })).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("link", { name: /Admin/i }));

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Admin" })).toBeInTheDocument();
      expect(screen.getByText(/Recent Onboarding Events/i)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Ingest Managed-App Releases/i })).toBeInTheDocument();
    });
  });

});
