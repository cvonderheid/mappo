import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

const mockTargets = [
  {
    id: "target-01",
    tenantId: "tenant-001",
    subscriptionId: "sub-0001",
    managedAppId: "managed-app-1",
    customerName: "Demo Customer A",
    tags: { ring: "canary", region: "eastus", tier: "gold", environment: "prod" },
    lastDeployedRelease: "2026.02.20.1",
    healthStatus: "healthy",
    runtimeStatus: "healthy",
    runtimeCheckedAt: "2026-02-25T00:00:00Z",
    runtimeSummary: "Runtime responded with HTTP 200.",
    lastDeploymentStatus: "SUCCEEDED",
    lastDeploymentAt: "2026-02-25T00:05:00Z",
    lastCheckInAt: "2026-02-25T00:00:00Z",
    simulatedFailureMode: "none",
  },
];

const mockReleases = [
  {
    id: "rel-2026-02-25",
    projectId: "managed-app-demo",
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
    projectId: "managed-app-demo",
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

const mockRunPage = {
  items: mockRuns,
  page: {
    page: 0,
    size: 25,
    totalItems: mockRuns.length,
    totalPages: 1,
  },
  activeRunCount: 1,
};

const mockRunDetail = {
  id: "run-1",
  projectId: "managed-app-demo",
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

const mockTargetPage = {
  items: mockTargets,
  page: {
    page: 0,
    size: 200,
    totalItems: mockTargets.length,
    totalPages: 1,
  },
};

const mockRegistrationPage = {
  items: [],
  page: {
    page: 0,
    size: 200,
    totalItems: 0,
    totalPages: 0,
  },
};

const mockProjects = [
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
];

const apiMock = vi.hoisted(() => ({
  adminListForwarderLogs: vi.fn(),
  adminListMarketplaceEvents: vi.fn(),
  adminListReleaseWebhookDeliveries: vi.fn(),
  adminListTargetRegistrations: vi.fn(),
  adminIngestGithubReleaseManifest: vi.fn(),
  adminIngestMarketplaceEvent: vi.fn(),
  createRun: vi.fn(),
  getRun: vi.fn(),
  listProjects: vi.fn(),
  listReleases: vi.fn(),
  listRuns: vi.fn(),
  listTargetsPage: vi.fn(),
  previewRun: vi.fn(),
  resumeRun: vi.fn(),
  retryFailed: vi.fn(),
}));

vi.mock("@/lib/api", () => apiMock);

const eventSourceMock = vi.hoisted(() => {
  const listeners = new Map<string, EventListenerOrEventListenerObject[]>();
  return {
    addEventListener: vi.fn((type: string, listener: EventListenerOrEventListenerObject) => {
      const current = listeners.get(type) ?? [];
      current.push(listener);
      listeners.set(type, current);
    }),
    close: vi.fn(),
  };
});

const liveUpdatesMock = vi.hoisted(() => ({
  createLiveUpdatesEventSource: vi.fn(() => eventSourceMock as unknown as EventSource),
  parseLiveUpdateEvent: vi.fn((rawData: string) => JSON.parse(rawData)),
}));

vi.mock("@/lib/live-updates", () => liveUpdatesMock);

vi.mock("@/components/FleetTable", () => ({
  default: () => (
    <section>
      <h2>Fleet Targets</h2>
      <p>Demo Customer A</p>
    </section>
  ),
}));

import App from "@/App";

describe("App", () => {
  beforeEach(() => {
    vi.stubGlobal("EventSource", vi.fn());
    window.history.replaceState({}, "", "/fleet");
    apiMock.adminListForwarderLogs.mockReset();
    apiMock.adminListMarketplaceEvents.mockReset();
    apiMock.adminListReleaseWebhookDeliveries.mockReset();
    apiMock.adminListTargetRegistrations.mockReset();
    apiMock.adminIngestGithubReleaseManifest.mockReset();
    apiMock.adminIngestMarketplaceEvent.mockReset();
    apiMock.createRun.mockReset();
    apiMock.previewRun.mockReset();
    apiMock.resumeRun.mockReset();
    apiMock.retryFailed.mockReset();
    apiMock.listProjects.mockResolvedValue(mockProjects);
    apiMock.listReleases.mockResolvedValue(mockReleases);
    apiMock.listRuns.mockResolvedValue(mockRunPage);
    apiMock.getRun.mockResolvedValue(mockRunDetail);
    apiMock.listTargetsPage.mockResolvedValue(mockTargetPage);
    apiMock.adminListTargetRegistrations.mockResolvedValue(mockRegistrationPage);
    apiMock.adminListMarketplaceEvents.mockResolvedValue(mockRegistrationPage);
    apiMock.adminListForwarderLogs.mockResolvedValue(mockRegistrationPage);
    apiMock.adminListReleaseWebhookDeliveries.mockResolvedValue(mockRegistrationPage);
    apiMock.previewRun.mockResolvedValue({
      projectId: "managed-app-demo",
      releaseVersion: "2026.02.25.3",
      mode: "ARM_WHAT_IF",
      warnings: [],
      caveat: "",
      targets: [],
    });
    liveUpdatesMock.createLiveUpdatesEventSource.mockClear();
    eventSourceMock.addEventListener.mockClear();
    eventSourceMock.close.mockClear();
  });

  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
  });

  it("renders dashboard data from API", async () => {
    const fleetView = render(<App />);

    expect(screen.getByText("MAPPO Control Plane")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: /Multi-tenant Managed App Orchestrator/i })).toBeInTheDocument();
    expect(screen.queryByText(/Attention Needed/i)).not.toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText("Fleet Targets")).toBeInTheDocument();
      expect(screen.getByText("Demo Customer A")).toBeInTheDocument();
      expect(screen.getByText("Project")).toBeInTheDocument();
      expect(screen.getByText("Managed App Demo")).toBeInTheDocument();
      expect(screen.getByRole("link", { name: /Fleet/i })).toBeInTheDocument();
      expect(screen.getByRole("link", { name: /Deployments/i })).toBeInTheDocument();
      expect(screen.getByRole("link", { name: /Admin/i })).toBeInTheDocument();
      expect(screen.getByText(/New release 2026.02.25.3 is available/i)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Deploy 2026.02.25.3/i })).toBeInTheDocument();
    });

    fleetView.unmount();
    window.history.replaceState({}, "", "/deployments");
    render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId("open-deployment-controls")).toBeInTheDocument();
      expect(screen.getByText("Deployment Runs")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByTestId("open-deployment-controls"));

    await waitFor(() => {
      expect(screen.getByText("Deployment Controls")).toBeInTheDocument();
      expect(screen.getByLabelText("Release version")).toBeInTheDocument();
      expect(screen.getByText(/Specific targets selected: 0/i)).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /Close/i }));

    await waitFor(() => {
      expect(screen.getByText("run-1")).toBeInTheDocument();
      expect(screen.getByTestId("run-row-run-1")).toBeInTheDocument();
      expect(screen.getByTestId("run-actions-trigger-run-1")).toBeInTheDocument();
    });

    cleanup();
    window.history.replaceState({}, "", "/admin");
    render(<App />);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Admin" })).toBeInTheDocument();
      expect(screen.getByText(/Recent Onboarding Events/i)).toBeInTheDocument();
      expect(screen.getByRole("button", { name: /Ingest Managed-App Releases/i })).toBeInTheDocument();
    });
  });

  it("does not open a live event stream on fleet routes", async () => {
    render(<App />);

    await waitFor(() => {
      expect(screen.getByText("Fleet Targets")).toBeInTheDocument();
    });

    expect(liveUpdatesMock.createLiveUpdatesEventSource).not.toHaveBeenCalled();
  });

  it("includes run live topics on deployments routes", async () => {
    window.history.replaceState({}, "", "/deployments");
    render(<App />);

    await waitFor(() => {
      expect(liveUpdatesMock.createLiveUpdatesEventSource).toHaveBeenCalledWith([
        "targets",
        "runs",
        "releases",
      ], "managed-app-demo");
    });
  });

});
