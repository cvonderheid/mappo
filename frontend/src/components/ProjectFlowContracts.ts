import type { FlowContract } from "@/components/FlowContractDetails";
import type { ReleaseIngestEndpoint } from "@/lib/types";

type ProjectFlowContractInput = {
  releaseSourceProvider: "github" | "azure_devops";
  releaseSourceName: string;
  releaseSourceTypeLabel: string;
  releaseSourceRecord: ReleaseIngestEndpoint | null;
  deploymentSystem: "azure" | "azure_devops";
  deploymentMethodLabel: string;
  deploymentConnectionName: string;
  azureDevOpsProjectName: string;
  repositoryName: string;
  pipelineName: string;
  branchName: string;
  targetCount: number;
  runtimeHealthLabel: string;
  runtimeHealthPath: string;
  runtimeHealthExpectedStatus: string;
  runtimeHealthTimeoutMs: string;
};

export function buildProjectFlowContracts(input: ProjectFlowContractInput): {
  releaseContract: FlowContract;
  deploymentContract: FlowContract;
  healthContract: FlowContract;
} {
  const manifestPath = input.releaseSourceRecord?.manifestPath || "releases/releases.manifest.json";
  const endpointId = input.releaseSourceRecord?.id || "<release-source-id>";
  return {
    releaseContract: input.releaseSourceProvider === "github"
      ? githubReleaseContract(input, endpointId, manifestPath)
      : azureDevOpsReleaseContract(input, endpointId),
    deploymentContract: input.deploymentSystem === "azure_devops"
      ? azureDevOpsDeploymentContract(input)
      : azureSdkDeploymentContract(input),
    healthContract: runtimeHealthContract(input),
  };
}

function githubReleaseContract(
  input: ProjectFlowContractInput,
  endpointId: string,
  manifestPath: string
): FlowContract {
  return {
    title: "GitHub webhook and release manifest",
    description:
      "GitHub posts a webhook when the release manifest changes. MAPPO validates the signature, reads the repo/ref from the webhook body, then fetches releases.manifest.json from GitHub.",
    producer: "GitHub repository",
    consumer: "MAPPO Release Source",
    direction: "Inbound webhook, then manifest fetch",
    facts: [
      { label: "Release source", value: input.releaseSourceName },
      { label: "Manifest path", value: manifestPath },
      { label: "Repository filter", value: input.releaseSourceRecord?.repoFilter || "Any repository" },
      { label: "Branch filter", value: input.releaseSourceRecord?.branchFilter || "Any branch" },
    ],
    exchanges: [
      {
        title: "GitHub -> MAPPO webhook",
        transport: "HTTP",
        method: "POST",
        urlTemplate: `/api/v1/release-ingest/endpoints/${endpointId}/webhooks/github`,
        pathParams: [
          { name: "endpointId", required: true, description: "Release Source ID configured in Admin -> Release Sources." },
        ],
        headers: [
          { name: "x-github-event", required: true, description: "MAPPO processes push events and acknowledges ping events." },
          { name: "x-github-delivery", required: false, description: "Delivery id MAPPO stores in release webhook events." },
          { name: "x-hub-signature-256", required: true, description: "HMAC SHA-256 signature over the raw request body." },
        ],
        bodyDescription: "MAPPO reads only the repository, ref, and changed paths from the GitHub webhook payload.",
        bodyFields: [
          { name: "repository.full_name", required: true, description: "Owner/repo used to enforce the Release Source repository filter and fetch the manifest." },
          { name: "ref", required: true, description: "Git ref. MAPPO strips refs/heads/ or refs/tags/ before matching the branch filter." },
          { name: "commits[].added|modified|removed", required: true, type: "string[]", description: "MAPPO checks whether the configured manifest path changed." },
        ],
        bodyExample: { title: "Webhook fields MAPPO reads", language: "json", code: githubWebhookExample(manifestPath) },
        responseDescription: "Returns the manifest ingest result after MAPPO fetches and applies the manifest.",
        responseFields: [
          { name: "repo", required: false, description: "Repository from the webhook payload." },
          { name: "path", required: false, description: "Manifest path MAPPO fetched." },
          { name: "ref", required: false, description: "Branch or tag MAPPO processed." },
          { name: "createdCount", required: true, type: "number", description: "Number of release records created." },
          { name: "skippedCount", required: true, type: "number", description: "Number of manifest rows already known to MAPPO." },
        ],
      },
      {
        title: "MAPPO -> GitHub manifest fetch",
        transport: "HTTP",
        method: "GET",
        urlTemplate: "https://api.github.com/repos/{owner}/{repo}/contents/{manifestPath}?ref={ref}",
        pathParams: [
          { name: "owner", required: true, description: "Derived from repository.full_name." },
          { name: "repo", required: true, description: "Derived from repository.full_name." },
          { name: "manifestPath", required: true, description: "Configured Release Source manifest path." },
        ],
        queryParams: [
          { name: "ref", required: true, description: "Branch or tag from the webhook body." },
        ],
        headers: [
          { name: "Accept", required: true, description: "application/vnd.github.raw when MAPPO uses a GitHub token." },
          { name: "Authorization", required: false, description: "Bearer token when MAPPO has a GitHub token configured." },
        ],
        bodyDescription: "No request body.",
        responseDescription: "The response body must be a JSON release manifest.",
        responseFields: releaseManifestFields(),
        responseExample: { title: "Minimal releases.manifest.json", language: "json", code: releaseManifestExample() },
      },
    ],
    notes: [
      "Do not include MAPPO project IDs in the publisher manifest.",
      "MAPPO links the release to projects through the configured Release Source.",
    ],
  };
}

function azureDevOpsReleaseContract(input: ProjectFlowContractInput, endpointId: string): FlowContract {
  return {
    title: "Azure DevOps release-ready webhook",
    description:
      "Azure DevOps posts a service-hook event when the release-readiness pipeline changes state. MAPPO authenticates the webhook and creates a release only for successful configured pipeline runs.",
    producer: "Azure DevOps pipeline",
    consumer: "MAPPO Release Source",
    direction: "Inbound service hook",
    facts: [
      { label: "Release source", value: input.releaseSourceName },
      { label: "Pipeline filter", value: input.releaseSourceRecord?.pipelineIdFilter || "Any pipeline" },
      { label: "Branch filter", value: input.releaseSourceRecord?.branchFilter || "Any branch" },
    ],
    exchanges: [
      {
        title: "Azure DevOps -> MAPPO service hook",
        transport: "HTTP",
        method: "POST",
        urlTemplate: `/api/v1/release-ingest/endpoints/${endpointId}/webhooks/ado`,
        pathParams: [
          { name: "endpointId", required: true, description: "Release Source ID configured in Admin -> Release Sources." },
        ],
        queryParams: [
          { name: "token", required: false, description: "Webhook secret. MAPPO also accepts the secret as the Basic auth password." },
          { name: "projectId", required: false, description: "Optional MAPPO project override. Usually resolved from the linked Release Source." },
        ],
        headers: [
          { name: "x-event-type", required: false, description: "Event type header. MAPPO also reads eventType from the body." },
          { name: "x-vss-deliveryid", required: false, description: "Delivery id MAPPO stores in release webhook events." },
          { name: "authorization", required: false, description: "Basic auth header. MAPPO reads the password as the webhook secret." },
        ],
        bodyDescription: "MAPPO reads the pipeline/run identity, branch, result, and run URLs from the Azure DevOps service-hook payload.",
        bodyFields: [
          { name: "eventType", required: true, description: "Must be an actionable pipeline run-state change event." },
          { name: "resource.pipeline.id", required: false, description: "Pipeline id matched against the Release Source pipeline filter." },
          { name: "resource.run.id", required: true, description: "Azure DevOps run id used to identify the external release." },
          { name: "resource.run.name", required: false, description: "Release version when present. MAPPO falls back to run id." },
          { name: "resource.run.result", required: true, description: "MAPPO creates a release only when this is succeeded." },
          { name: "resource.run.resources.repositories.self.refName", required: false, description: "Branch matched against the Release Source branch filter." },
          { name: "resourceContainers.project.baseUrl", required: false, description: "Azure DevOps organization URL." },
          { name: "resourceContainers.project.name", required: false, description: "Azure DevOps project name." },
        ],
        bodyExample: { title: "Service-hook fields MAPPO reads", language: "json", code: azureDevOpsReleaseEventExample() },
        responseDescription: "Returns the release ingest result after MAPPO creates or skips the release record.",
        responseFields: [
          { name: "manifestReleaseCount", required: true, type: "number", description: "Number of release candidates MAPPO considered from the event." },
          { name: "createdCount", required: true, type: "number", description: "Number of release records created." },
          { name: "skippedCount", required: true, type: "number", description: "Number of release candidates skipped as duplicates or non-actionable." },
          { name: "createdReleaseIds", required: false, type: "string[]", description: "Release ids created by this event." },
        ],
      },
    ],
    notes: [
      "The Azure DevOps release-ready path does not require releases.manifest.json.",
      "MAPPO stores external deployment inputs such as artifactVersion and deployedBy from the event.",
    ],
  };
}

function azureDevOpsDeploymentContract(input: ProjectFlowContractInput): FlowContract {
  return {
    title: "MAPPO to Azure DevOps deployment pipeline",
    description:
      "When an operator starts a deployment, MAPPO triggers the configured Azure DevOps pipeline for each selected target.",
    producer: "MAPPO deployment driver",
    consumer: "Azure DevOps pipeline",
    direction: "Outbound API call",
    facts: [
      { label: "Deployment connection", value: input.deploymentConnectionName || "Not linked" },
      { label: "Azure DevOps project", value: input.azureDevOpsProjectName || "Not selected" },
      { label: "Repository", value: input.repositoryName || "Not selected" },
      { label: "Pipeline", value: input.pipelineName || "Not selected" },
      { label: "Branch", value: input.branchName || "main" },
    ],
    exchanges: [
      {
        title: "MAPPO -> Azure DevOps pipeline run",
        transport: "HTTP",
        method: "POST",
        urlTemplate: "{organization}/{project}/_apis/pipelines/{pipelineId}/runs?api-version={apiVersion}",
        pathParams: [
          { name: "organization", required: true, description: "Azure DevOps organization URL from the selected Deployment Connection." },
          { name: "project", required: true, description: "Azure DevOps project selected in Project -> Config." },
          { name: "pipelineId", required: true, description: "Azure DevOps pipeline selected in Project -> Config." },
        ],
        queryParams: [
          { name: "api-version", required: true, description: "Azure DevOps REST API version configured in MAPPO runtime settings." },
        ],
        headers: [
          { name: "Accept", required: true, description: "application/json" },
          { name: "Content-Type", required: true, description: "application/json" },
          { name: "Authorization", required: true, description: "Basic auth using the Deployment Connection PAT." },
        ],
        bodyDescription: "MAPPO sends the selected branch and a templateParameters object assembled from release defaults, external inputs, target execution metadata, target tags, and MAPPO identifiers.",
        bodyFields: [
          { name: "resources.repositories.self.refName", required: true, description: "Branch ref passed to Azure DevOps, for example refs/heads/main." },
          { name: "templateParameters.targetTenantId", required: true, description: "Tenant id for the selected target." },
          { name: "templateParameters.targetSubscriptionId", required: true, description: "Subscription id for the selected target." },
          { name: "templateParameters.targetId", required: true, description: "MAPPO target id." },
          { name: "templateParameters.targetResourceGroup", required: false, description: "Resolved from target executionConfig.resourceGroup or targetResourceGroup." },
          { name: "templateParameters.targetAppName", required: false, description: "Resolved from target executionConfig.appServiceName or targetAppName." },
          { name: "templateParameters.appVersion", required: true, description: "Release app version, falling back to the MAPPO release version." },
          { name: "templateParameters.mappoReleaseId", required: true, description: "MAPPO release id for traceability." },
          { name: "templateParameters.mappoReleaseVersion", required: true, description: "Release version selected by the operator." },
        ],
        bodyExample: { title: "Pipeline run request body", language: "json", code: pipelineRunExample() },
        responseDescription: "MAPPO reads the Azure DevOps run response and stores the external execution handle on each target run.",
        responseFields: [
          { name: "id", required: true, description: "Azure DevOps run id." },
          { name: "name", required: false, description: "Azure DevOps run name." },
          { name: "state", required: false, description: "Run state used while polling." },
          { name: "result", required: false, description: "Final result. MAPPO treats succeeded as successful." },
          { name: "_links.web.href", required: false, description: "Operator-facing run URL shown in MAPPO." },
          { name: "url", required: false, description: "Azure DevOps API URL for subsequent polling." },
        ],
      },
    ],
    notes: [
      "MAPPO does not inspect or manage the Azure service connection inside the pipeline.",
      "Pipeline maintainers own the Azure permissions required by the pipeline steps.",
    ],
  };
}

function azureSdkDeploymentContract(input: ProjectFlowContractInput): FlowContract {
  return {
    title: "MAPPO Azure SDK deployment",
    description:
      "When an operator starts a deployment, MAPPO fetches the release template and calls Azure directly for each selected target.",
    producer: "MAPPO deployment driver",
    consumer: "Azure Resource Manager",
    direction: "Outbound Azure SDK/ARM call",
    facts: [
      { label: "Deployment method", value: input.deploymentMethodLabel },
      { label: "Targets available", value: input.targetCount },
      { label: "Release source type", value: input.releaseSourceTypeLabel },
    ],
    exchanges: [
      {
        title: "MAPPO -> Azure Resource Manager deployment stack",
        transport: "Azure SDK",
        method: "DeploymentStacks create/update",
        urlTemplate: "Azure SDK call; no operator-authored URL",
        bodyDescription: "MAPPO builds a DeploymentStackInner object per selected target. The ARM template comes from source_version_ref and parameters come from the release plus target registration context.",
        bodyFields: [
          { name: "deploymentScope", required: true, description: "Managed resource group id from the target registration." },
          { name: "template", required: true, type: "object", description: "ARM template JSON fetched from source_version_ref." },
          { name: "parameters", required: true, type: "object", description: "Deployment parameters built from release parameter_defaults and target context." },
          { name: "description", required: true, description: "MAPPO-generated deployment stack description containing the target id." },
          { name: "denySettings.mode", required: true, description: "Currently none." },
          { name: "actionOnUnmanage.resources", required: true, description: "Currently detach." },
          { name: "actionOnUnmanage.resourceGroups", required: true, description: "Currently detach." },
        ],
        bodyExample: { title: "Deployment stack request object", language: "json", code: azureSdkDeploymentExample() },
        responseDescription: "MAPPO stores Azure deployment stack diagnostics and correlation ids on the target run.",
      },
    ],
    examples: [{ title: "Release artifact contract", language: "json", code: releaseManifestExample() }],
    notes: [
      "The MAPPO Azure SDK path runs once per selected target.",
      "Registry credentials and target-specific Azure metadata come from MAPPO runtime configuration and target registration, not the release manifest.",
    ],
  };
}

function runtimeHealthContract(input: ProjectFlowContractInput): FlowContract {
  return {
    title: "Runtime health check",
    description:
      "After rollout, MAPPO can probe each target's configured HTTP endpoint and compare the response against the expected status.",
    producer: "Registered target runtime",
    consumer: "MAPPO runtime health check",
    direction: "HTTP request",
    facts: [
      { label: "Check", value: input.runtimeHealthLabel },
      { label: "Path", value: input.runtimeHealthPath || "/" },
      { label: "Expected status", value: input.runtimeHealthExpectedStatus || "200" },
      { label: "Timeout", value: `${input.runtimeHealthTimeoutMs || "5000"} ms` },
    ],
    exchanges: [
      {
        title: "MAPPO -> target runtime probe",
        transport: "HTTP",
        method: "GET",
        urlTemplate: `{target runtime base URL}${input.runtimeHealthPath || "/"}`,
        headers: [
          { name: "Accept", required: true, description: "application/json, text/plain, */*" },
          { name: "User-Agent", required: true, description: "mappo-runtime-probe" },
        ],
        bodyDescription: "No request body.",
        responseDescription: "MAPPO discards the response body and evaluates only the status code.",
        responseFields: [
          { name: "statusCode", required: true, type: "number", description: `Healthy when it equals ${input.runtimeHealthExpectedStatus || "200"}.` },
          { name: "checkedUrl", required: true, description: "URL MAPPO actually probed." },
          { name: "runtimeStatus", required: true, description: "healthy, unhealthy, unreachable, or unknown." },
          { name: "summary", required: true, description: "Short probe result message persisted on the target." },
        ],
        notes: [
          `Timeout is ${input.runtimeHealthTimeoutMs || "5000"} ms.`,
          "Azure Container App probes first resolve the app FQDN from Azure metadata, then perform the same HTTP GET.",
        ],
      },
    ],
  };
}

function releaseManifestFields() {
  return [
    { name: "releases[]", required: true, type: "array", description: "Rows MAPPO parses into release records." },
    { name: "source_ref", required: true, description: "Stable logical identity for the release artifact family." },
    { name: "source_version", required: true, description: "Operator-visible version MAPPO stores in the release catalog." },
    { name: "source_type", required: true, description: "Artifact type, such as deployment_stack." },
    { name: "source_version_ref", required: true, description: "Immutable URI MAPPO can fetch when deploying this release." },
    { name: "parameter_defaults", required: false, type: "object", description: "Release-level deployment values." },
  ];
}

function releaseManifestExample(): string {
  return JSON.stringify(
    {
      releases: [
        {
          source_ref: "github://owner/repo/managed-app/mainTemplate.json",
          source_version: "2026.04.19.1",
          source_type: "deployment_stack",
          source_version_ref: "https://storage.example/releases/2026.04.19.1/mainTemplate.json",
          parameter_defaults: {
            containerImage: "registry.example/app:2026.04.19.1",
            softwareVersion: "2026.04.19.1",
            dataModelVersion: "12",
          },
        },
      ],
    },
    null,
    2
  );
}

function githubWebhookExample(manifestPath: string): string {
  return JSON.stringify(
    {
      ref: "refs/heads/main",
      repository: {
        full_name: "owner/repo",
      },
      commits: [
        {
          added: [],
          modified: [manifestPath],
          removed: [],
        },
      ],
    },
    null,
    2
  );
}

function azureDevOpsReleaseEventExample(): string {
  return JSON.stringify(
    {
      eventType: "ms.vss-pipelines.run-state-changed-event",
      resource: {
        run: {
          id: "1234",
          name: "2026.04.19.1",
          result: "succeeded",
          url: "https://dev.azure.com/org/project/_build/results?buildId=1234",
        },
        pipeline: {
          id: "2",
          name: "release-readiness",
        },
        repository: {
          name: "workload-repo",
          refName: "refs/heads/main",
        },
      },
    },
    null,
    2
  );
}

function pipelineRunExample(): string {
  return JSON.stringify(
    {
      resources: {
        repositories: {
          self: {
            refName: "refs/heads/main",
          },
        },
      },
      templateParameters: {
        targetTenantId: "<target tenant>",
        targetSubscriptionId: "<target subscription>",
        targetId: "<mappo target id>",
        mappoReleaseVersion: "2026.04.19.1",
        appVersion: "2026.04.19.1",
        dataModelVersion: "12",
      },
    },
    null,
    2
  );
}

function azureSdkDeploymentExample(): string {
  return JSON.stringify(
    {
      description: "MAPPO deployment stack for target <targetId>",
      deploymentScope: "/subscriptions/<subscriptionId>/resourceGroups/<managedResourceGroup>",
      template: "{ ARM template fetched from source_version_ref }",
      parameters: {
        containerImage: { value: "registry.example/app:2026.04.19.1" },
        softwareVersion: { value: "2026.04.19.1" },
        dataModelVersion: { value: "12" },
      },
      denySettings: { mode: "none" },
      actionOnUnmanage: {
        resources: "detach",
        resourceGroups: "detach",
      },
      bypassStackOutOfSyncError: true,
    },
    null,
    2
  );
}
