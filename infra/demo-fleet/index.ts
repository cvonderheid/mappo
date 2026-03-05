import * as pulumi from "@pulumi/pulumi";
import * as azureNative from "@pulumi/azure-native/index";
import * as app from "@pulumi/azure-native/app";
import * as operationalinsights from "@pulumi/azure-native/operationalinsights";
import * as resources from "@pulumi/azure-native/resources";

interface DemoFleetTargetConfig {
  id: string;
  tenantId: string;
  subscriptionId: string;
  region?: string;
  targetGroup?: string;
  environment?: string;
  tier?: string;
  customerName?: string;
  managedApplicationId?: string;
  softwareVersion?: string;
  dataModelVersion?: string;
  tags?: Record<string, string>;
}

interface SubscriptionContext {
  provider: azureNative.Provider;
  location: string;
  observabilityResourceGroup: resources.ResourceGroup;
  workspace: operationalinsights.Workspace;
  environment: app.ManagedEnvironment;
}

interface DeploymentOutput {
  targetId: string;
  tenantId: string;
  subscriptionId: string;
  region: string;
  targetGroup: string;
  tier: string;
  environmentTag: string;
  customerName: string | null;
  managedApplicationId: string | null;
  managedResourceGroupId: pulumi.Output<string>;
  managedResourceGroupName: pulumi.Output<string>;
  containerAppName: string;
  containerAppId: pulumi.Output<string>;
}

const config = new pulumi.Config("demoFleet");
const stackToken = normalizeName(pulumi.getStack(), "demo", 20);

const defaultLocation = config.get("defaultLocation") ?? "eastus";
const defaultImage =
  config.get("defaultImage") ?? "docker.io/library/python:3.11-alpine";
const defaultSoftwareVersion = config.get("defaultSoftwareVersion") ?? "2026.02.20.1";
const defaultDataModelVersion = config.get("defaultDataModelVersion") ?? "1";
const defaultCpu = config.getNumber("defaultCpu") ?? 0.25;
const defaultMemory = config.get("defaultMemory") ?? "0.5Gi";

const environmentResourceGroupPrefix =
  config.get("environmentResourceGroupPrefix") ?? "rg-mappo-demo-fleet";
const logWorkspaceNamePrefix =
  config.get("logWorkspaceNamePrefix") ?? "law-mappo-demo-fleet";
const managedEnvironmentNamePrefix =
  config.get("managedEnvironmentNamePrefix") ?? "cae-mappo-demo-fleet";
const targetResourceGroupPrefix =
  config.get("targetResourceGroupPrefix") ?? "rg-mappo-demo-target";
const containerAppNamePrefix =
  config.get("containerAppNamePrefix") ?? "ca-mappo-demo-target";

const targets = config.getObject<DemoFleetTargetConfig[]>("targets") ?? [];
assertUniqueTargetIds(targets);

if (targets.length === 0) {
  pulumi.log.warn(
    "No demo fleet targets configured. Set demoFleet:targets in your stack config.",
  );
}

const providersBySubscription = new Map<string, azureNative.Provider>();
const contextBySubscription = new Map<string, SubscriptionContext>();

const targetDemoServerScript = [
  "import json",
  "import os",
  "from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer",
  "",
  "class Handler(BaseHTTPRequestHandler):",
  "    def do_GET(self):",
  "        payload = {",
  "            'service': 'mappo-target-demo',",
  "            'softwareVersion': os.getenv('MAPPO_SOFTWARE_VERSION', 'unknown'),",
  "            'dataModelVersion': os.getenv('MAPPO_DATA_MODEL_VERSION', 'unknown'),",
  "        }",
  "        body = json.dumps(payload).encode('utf-8')",
  "        self.send_response(200)",
  "        self.send_header('Content-Type', 'application/json')",
  "        self.send_header('Content-Length', str(len(body)))",
  "        self.end_headers()",
  "        self.wfile.write(body)",
  "",
  "    def log_message(self, _fmt: str, *_args: object) -> None:",
  "        return",
  "",
  "port = int(os.getenv('PORT', '8080'))",
  "ThreadingHTTPServer(('0.0.0.0', port), Handler).serve_forever()",
].join("\\n");

const deployments = targets.map((target, index) => {
  const region = target.region ?? defaultLocation;
  const targetGroup = normalizeTagValue(target.targetGroup, "prod");
  const environmentTag = normalizeTagValue(target.environment, "demo");
  const tier = normalizeTagValue(target.tier, "standard");
  const customerName = normalizeNullableValue(target.customerName);

  const provider = getProvider(target.subscriptionId);
  const subscriptionContext = getOrCreateSubscriptionContext({
    provider,
    subscriptionId: target.subscriptionId,
    location: region,
  });

  const targetToken = normalizeName(target.id, `target-${index + 1}`, 28);
  const managedResourceGroupName = normalizeName(
    `${targetResourceGroupPrefix}-${targetToken}`,
    `rg-mappo-demo-target-${targetToken}`,
    90,
  );
  const containerAppName = normalizeName(
    `${containerAppNamePrefix}-${targetToken}`,
    `ca-mappo-demo-target-${targetToken}`,
    32,
  );
  const managedApplicationId = normalizeNullableResourceId(target.managedApplicationId);

  const managedResourceGroup = new resources.ResourceGroup(
    `demo-fleet-target-rg-${targetToken}`,
    {
      resourceGroupName: managedResourceGroupName,
      location: region,
      tags: {
        managedBy: "pulumi",
        system: "mappo",
        scope: "demo-fleet-target",
        targetId: target.id,
        ring: targetGroup,
        environment: environmentTag,
        tier,
      },
    },
    { provider },
  );

  const mergedTags: Record<string, pulumi.Input<string>> = {
    managedBy: "pulumi",
    system: "mappo",
    scope: "demo-fleet-target",
    targetId: target.id,
    ring: targetGroup,
    region,
    environment: environmentTag,
    tier,
    tenantId: target.tenantId,
    ...(target.tags ?? {}),
  };
  if (customerName) {
    mergedTags.customer = customerName;
  }

  const containerApp = new app.ContainerApp(
    `demo-fleet-target-app-${targetToken}`,
    {
      resourceGroupName: managedResourceGroup.name,
      containerAppName,
      location: region,
      managedEnvironmentId: subscriptionContext.environment.id,
      configuration: {
        ingress: {
          external: true,
          targetPort: 8080,
          transport: "auto",
          allowInsecure: false,
        },
      },
      template: {
        containers: [
          {
            name: "demo",
            image: defaultImage,
            command: ["python", "-c", targetDemoServerScript],
            env: [
              { name: "PORT", value: "8080" },
              {
                name: "MAPPO_SOFTWARE_VERSION",
                value: normalizeTagValue(
                  target.softwareVersion,
                  defaultSoftwareVersion,
                ),
              },
              {
                name: "MAPPO_DATA_MODEL_VERSION",
                value: normalizeTagValue(
                  target.dataModelVersion,
                  defaultDataModelVersion,
                ),
              },
            ],
            resources: {
              cpu: defaultCpu,
              memory: defaultMemory,
            },
          },
        ],
        scale: {
          minReplicas: 1,
          maxReplicas: 1,
        },
      },
      tags: mergedTags,
    },
    { provider },
  );

  const managedResourceGroupId = pulumi.interpolate`/subscriptions/${target.subscriptionId}/resourceGroups/${managedResourceGroup.name}`;

  const deployment: DeploymentOutput = {
    targetId: target.id,
    tenantId: target.tenantId,
    subscriptionId: target.subscriptionId,
    region,
    targetGroup,
    tier,
    environmentTag,
    customerName,
    managedApplicationId,
    managedResourceGroupId,
    managedResourceGroupName: managedResourceGroup.name,
    containerAppName,
    containerAppId: containerApp.id,
  };

  return deployment;
});

const targetInventory = pulumi.all(
  deployments.map((deployment) =>
    pulumi
      .all([
        deployment.containerAppId,
        deployment.managedResourceGroupId,
        deployment.managedResourceGroupName,
      ])
      .apply(([containerAppResourceId, managedResourceGroupId, managedResourceGroupName]) => {
        const tags: Record<string, string> = {
          ring: deployment.targetGroup,
          region: deployment.region,
          environment: deployment.environmentTag,
          tier: deployment.tier,
        };
        if (deployment.customerName) {
          tags.customer = deployment.customerName;
        }

        const metadata: Record<string, string> = {
          source: "demo-fleet-pulumi",
          managed_resource_group_id: managedResourceGroupId,
          managed_resource_group_name: managedResourceGroupName,
          container_app_name: deployment.containerAppName,
        };
        if (deployment.customerName) {
          metadata.customer_name = deployment.customerName;
        }
        if (deployment.managedApplicationId) {
          metadata.managed_application_id = deployment.managedApplicationId;
          metadata.managed_application_name =
            deployment.managedApplicationId.split("/").at(-1) ?? deployment.targetId;
        }

        return {
          id: deployment.targetId,
          tenant_id: deployment.tenantId,
          subscription_id: deployment.subscriptionId,
          managed_app_id: containerAppResourceId,
          tags,
          metadata,
        };
      }),
  ),
);

export const targetCount = deployments.length;
export const mappoTargetInventory = targetInventory;
export const mappoTargetInventoryJson = targetInventory.apply((rows) =>
  JSON.stringify(rows, null, 2),
);
export const tenantBySubscription = buildTenantBySubscriptionMap(targets);

function getProvider(subscriptionId: string): azureNative.Provider {
  const existing = providersBySubscription.get(subscriptionId);
  if (existing) {
    return existing;
  }
  const provider = new azureNative.Provider(`demo-fleet-provider-${subscriptionKey(subscriptionId)}`, {
    subscriptionId,
  });
  providersBySubscription.set(subscriptionId, provider);
  return provider;
}

function getOrCreateSubscriptionContext(input: {
  provider: azureNative.Provider;
  subscriptionId: string;
  location: string;
}): SubscriptionContext {
  const existing = contextBySubscription.get(input.subscriptionId);
  if (existing) {
    if (existing.location !== input.location) {
      pulumi.log.warn(
        `demoFleet target regions differ in subscription ${input.subscriptionId}; using managed environment in ${existing.location}.`,
      );
    }
    return existing;
  }

  const subscriptionToken = subscriptionKey(input.subscriptionId);
  const observabilityResourceGroupName = normalizeName(
    `${environmentResourceGroupPrefix}-${stackToken}-${subscriptionToken}`,
    `rg-mappo-demo-fleet-${stackToken}-${subscriptionToken}`,
    90,
  );
  const workspaceName = normalizeName(
    `${logWorkspaceNamePrefix}-${stackToken}-${subscriptionToken}`,
    `law-mappo-demo-fleet-${stackToken}-${subscriptionToken}`,
    63,
  );
  const managedEnvironmentName = normalizeName(
    `${managedEnvironmentNamePrefix}-${stackToken}-${subscriptionToken}`,
    `cae-mappo-demo-fleet-${stackToken}-${subscriptionToken}`,
    32,
  );

  const observabilityResourceGroup = new resources.ResourceGroup(
    `demo-fleet-observability-rg-${subscriptionToken}`,
    {
      resourceGroupName: observabilityResourceGroupName,
      location: input.location,
      tags: {
        managedBy: "pulumi",
        system: "mappo",
        scope: "demo-fleet-shared",
      },
    },
    { provider: input.provider },
  );

  const workspace = new operationalinsights.Workspace(
    `demo-fleet-workspace-${subscriptionToken}`,
    {
      workspaceName,
      resourceGroupName: observabilityResourceGroup.name,
      location: input.location,
      sku: {
        name: "PerGB2018",
      },
      retentionInDays: 30,
      tags: {
        managedBy: "pulumi",
        system: "mappo",
        scope: "demo-fleet-shared",
      },
    },
    { provider: input.provider },
  );

  const workspaceSharedKeys = operationalinsights.getSharedKeysOutput(
    {
      resourceGroupName: observabilityResourceGroup.name,
      workspaceName: workspace.name,
    },
    { provider: input.provider },
  );

  const environment = new app.ManagedEnvironment(
    `demo-fleet-environment-${subscriptionToken}`,
    {
      resourceGroupName: observabilityResourceGroup.name,
      environmentName: managedEnvironmentName,
      location: input.location,
      appLogsConfiguration: {
        destination: "log-analytics",
        logAnalyticsConfiguration: {
          customerId: workspace.customerId,
          sharedKey: workspaceSharedKeys.apply(
            (keys) => keys.primarySharedKey ?? "",
          ),
        },
      },
      tags: {
        managedBy: "pulumi",
        system: "mappo",
        scope: "demo-fleet-shared",
      },
    },
    { provider: input.provider },
  );

  const context: SubscriptionContext = {
    provider: input.provider,
    location: input.location,
    observabilityResourceGroup,
    workspace,
    environment,
  };
  contextBySubscription.set(input.subscriptionId, context);
  return context;
}

function assertUniqueTargetIds(targetsToCheck: DemoFleetTargetConfig[]): void {
  const seen = new Set<string>();
  for (const target of targetsToCheck) {
    const value = target.id.trim();
    if (!value) {
      throw new Error("demoFleet target id must not be empty.");
    }
    if (seen.has(value)) {
      throw new Error(`Duplicate demoFleet target id: '${value}'.`);
    }
    seen.add(value);
  }
}

function buildTenantBySubscriptionMap(
  targetsToConvert: DemoFleetTargetConfig[],
): Record<string, string> {
  const rows: Record<string, string> = {};
  for (const target of targetsToConvert) {
    const subscriptionId = target.subscriptionId.trim();
    const tenantId = target.tenantId.trim();
    if (!subscriptionId || !tenantId) {
      continue;
    }
    rows[subscriptionId] = tenantId;
  }
  return rows;
}

function subscriptionKey(subscriptionId: string): string {
  const normalized = subscriptionId.trim().toLowerCase().replace(/[^a-z0-9]/g, "");
  if (!normalized) {
    return "nosub";
  }
  return normalized.slice(0, 8);
}

function normalizeTagValue(value: string | undefined, fallback: string): string {
  const normalized = value?.trim();
  return normalized && normalized !== "" ? normalized : fallback;
}

function normalizeNullableValue(value: string | undefined): string | null {
  const normalized = value?.trim();
  if (!normalized) {
    return null;
  }
  return normalized;
}

function normalizeNullableResourceId(value: string | undefined): string | null {
  const normalized = normalizeNullableValue(value);
  if (normalized === null) {
    return null;
  }
  return normalized.startsWith("/") ? normalized : `/${normalized}`;
}

function normalizeName(value: string, fallback: string, maxLength: number): string {
  const normalized = value
    .toLowerCase()
    .replace(/[^a-z0-9-]/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-+|-+$/g, "");
  const candidate = normalized || fallback;
  if (candidate.length <= maxLength) {
    return candidate;
  }
  return candidate.slice(0, maxLength).replace(/-+$/g, "") || fallback.slice(0, maxLength);
}

