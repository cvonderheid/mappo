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
  createProject: vi.fn(),
  createRun: vi.fn(),
  deleteProject: vi.fn(),
  discoverProjectAdoBranches: vi.fn(),
  discoverProjectAdoPipelines: vi.fn(),
  discoverProjectAdoRepositories: vi.fn(),
  getRun: vi.fn(),
  listProviderConnections: vi.fn(),
  listProjectAudit: vi.fn(),
  listProjects: vi.fn(),
  listReleases: vi.fn(),
  listReleaseIngestEndpoints: vi.fn(),
  listRuns: vi.fn(),
  listTargetsPage: vi.fn(),
  patchProjectConfiguration: vi.fn(),
  previewRun: vi.fn(),
  resumeRun: vi.fn(),
  retryFailed: vi.fn(),
  validateProjectConfiguration: vi.fn(),
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
    emit: (type: string, data: string) => {
      const current = listeners.get(type) ?? [];
      const event = { data } as MessageEvent<string>;
      current.forEach((listener) => {
        if (typeof listener === "function") {
          listener(event);
          return;
        }
        listener.handleEvent(event);
      });
    },
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
    apiMock.createProject.mockReset();
    apiMock.deleteProject.mockReset();
    apiMock.discoverProjectAdoBranches.mockReset();
    apiMock.discoverProjectAdoPipelines.mockReset();
    apiMock.discoverProjectAdoRepositories.mockReset();
    apiMock.createRun.mockReset();
    apiMock.previewRun.mockReset();
    apiMock.resumeRun.mockReset();
    apiMock.retryFailed.mockReset();
    apiMock.listProjectAudit.mockReset();
    apiMock.listProviderConnections.mockResolvedValue([]);
    apiMock.listProjects.mockResolvedValue(mockProjects);
    apiMock.listReleases.mockResolvedValue(mockReleases);
    apiMock.listReleaseIngestEndpoints.mockResolvedValue([]);
    apiMock.listRuns.mockResolvedValue(mockRunPage);
    apiMock.getRun.mockResolvedValue(mockRunDetail);
    apiMock.listTargetsPage.mockResolvedValue(mockTargetPage);
    apiMock.patchProjectConfiguration.mockReset();
    apiMock.validateProjectConfiguration.mockReset();
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

    expect(screen.getByRole("heading", { name: /^MAPPO Control Plane$/i })).toBeInTheDocument();
    expect(screen.queryByText(/Attention Needed/i)).not.toBeInTheDocument();

    await waitFor(() => {
      expect(screen.getByText("Fleet Targets")).toBeInTheDocument();
      expect(screen.getByText("Demo Customer A")).toBeInTheDocument();
      expect(screen.getByTestId("project-switcher-trigger")).toBeInTheDocument();
      expect(screen.getAllByText("Managed App Demo").length).toBeGreaterThan(0);
      expect(screen.getByRole("link", { name: /Config/i })).toBeInTheDocument();
      expect(screen.getByRole("link", { name: /Fleet/i })).toBeInTheDocument();
      expect(screen.getByRole("link", { name: /Deployments/i })).toBeInTheDocument();
      expect(screen.getByRole("link", { name: /Targets/i })).toBeInTheDocument();
      expect(screen.getByRole("link", { name: /Registration Events/i })).toBeInTheDocument();
      expect(screen.getByRole("link", { name: /Releases/i })).toBeInTheDocument();
      expect(screen.getByRole("link", { name: /^Managed App$/i })).toBeInTheDocument();
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
    window.history.replaceState({}, "", "/targets");
    render(<App />);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: "Registered Targets" })).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: /Onboard Targets/i })).not.toBeInTheDocument();
      expect(screen.getByText(/Refresh Targets/i)).toBeInTheDocument();
    });

    cleanup();
    window.history.replaceState({}, "", "/onboarding");
    render(<App />);

    await waitFor(() => {
      expect(screen.getByRole("heading", { name: /Registration Events/i })).toBeInTheDocument();
      expect(screen.queryByRole("button", { name: /Add Targets/i })).not.toBeInTheDocument();
      expect(screen.getByRole("columnheader", { name: /Event ID/i })).toBeInTheDocument();
    });
  });

  it("does not open a live event stream on fleet routes", async () => {
    render(<App />);

    await waitFor(() => {
      expect(screen.getByText("Fleet Targets")).toBeInTheDocument();
    });

    expect(liveUpdatesMock.createLiveUpdatesEventSource).not.toHaveBeenCalled();
  });

  it("opens project settings create drawer from project switcher", async () => {
    render(<App />);

    await waitFor(() => {
      expect(screen.getByTestId("project-switcher-trigger")).toBeInTheDocument();
    });

    fireEvent.pointerDown(screen.getByTestId("project-switcher-trigger"));

    const newProjectAction = await screen.findByTestId("project-switcher-new-project");
    fireEvent.click(newProjectAction);

    await waitFor(() => {
      expect(screen.getByText("Project Settings")).toBeInTheDocument();
      expect(screen.getByRole("heading", { name: "New Project" })).toBeInTheDocument();
    });
  });

  it("hides Azure DevOps controls for MAPPO Azure API projects", async () => {
    apiMock.listProjects.mockResolvedValue([
      {
        ...mockProjects[0],
        deploymentDriver: "azure_deployment_stack",
        providerConnectionId: "ado-default",
        deploymentDriverConfig: {
          organization: "https://dev.azure.com/pg123",
          project: "demo-app-service",
          repository: "demo-app-service",
          pipelineId: "42",
          branch: "main",
        },
      },
    ]);
    window.history.replaceState({}, "", "/projects");

    render(<App />);

    await waitFor(() => {
      expect(screen.getByText("Project Settings")).toBeInTheDocument();
    });

    fireEvent.click(screen.getByRole("button", { name: /Deployment\s+Azure/i }));

    await waitFor(() => {
      expect(screen.getByRole("button", { name: /Check Azure access/i })).toBeInTheDocument();
    });

    expect(screen.queryByLabelText("Deployment connection")).not.toBeInTheDocument();
    expect(screen.queryByText("Azure DevOps Project")).not.toBeInTheDocument();
    expect(screen.queryByText("Azure DevOps Repository")).not.toBeInTheDocument();
    expect(screen.queryByText("Azure DevOps Pipeline")).not.toBeInTheDocument();
    expect(screen.queryByText("Azure Service Connection")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Load pipelines/i })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: /Load service connections/i })).not.toBeInTheDocument();
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

  it("fetches runs immediately when navigating from fleet to deployments", async () => {
    render(<App />);

    await waitFor(() => {
      expect(screen.getByText("Fleet Targets")).toBeInTheDocument();
    });

    apiMock.listRuns.mockClear();
    liveUpdatesMock.createLiveUpdatesEventSource.mockClear();

    fireEvent.click(screen.getByRole("link", { name: /Deployments/i }));

    await waitFor(() => {
      expect(apiMock.listRuns).toHaveBeenCalledWith({
        page: 0,
        size: 25,
        projectId: "managed-app-demo",
        releaseId: undefined,
        runId: undefined,
        status: undefined,
      });
      expect(liveUpdatesMock.createLiveUpdatesEventSource).toHaveBeenCalledWith([
        "targets",
        "runs",
        "releases",
      ], "managed-app-demo");
      expect(screen.getByText("Deployment Runs")).toBeInTheDocument();
    });
  });

  it("refreshes deployments immediately after the live stream connects", async () => {
    window.history.replaceState({}, "", "/deployments");
    render(<App />);

    await waitFor(() => {
      expect(liveUpdatesMock.createLiveUpdatesEventSource).toHaveBeenCalledWith([
        "targets",
        "runs",
        "releases",
      ], "managed-app-demo");
    });

    apiMock.listRuns.mockClear();

    eventSourceMock.emit("connected", JSON.stringify({
      type: "connected",
      projectId: "managed-app-demo",
    }));

    await waitFor(() => {
      expect(apiMock.listRuns).toHaveBeenCalledWith({
        page: 0,
        size: 25,
        projectId: "managed-app-demo",
        releaseId: undefined,
        runId: undefined,
        status: undefined,
      });
    });
  });

});
