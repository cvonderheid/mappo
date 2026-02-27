import * as pulumi from "@pulumi/pulumi";
import * as app from "@pulumi/azure-native/app";
import * as azureNative from "@pulumi/azure-native/index";
import * as resources from "@pulumi/azure-native/resources";

interface TargetConfig {
  id: string;
  tenantId?: string;
  subscriptionId: string;
  targetGroup?: string;
  region?: string;
  resourceGroupName?: string;
  managedEnvironmentName?: string;
  containerAppName?: string;
  image?: string;
  externalIngress?: boolean;
  targetPort?: number;
  cpu?: number;
  memory?: string;
  minReplicas?: number;
  maxReplicas?: number;
  environmentVariables?: Record<string, string>;
  tags?: Record<string, string>;
}

interface DeploymentOutput {
  id: string;
  tenantId: string;
  subscriptionId: string;
  targetGroup: string;
  region: string;
  resourceGroupName: pulumi.Output<string>;
  managedEnvironmentName: pulumi.Output<string>;
  containerAppName: pulumi.Output<string>;
  containerAppId: pulumi.Output<string>;
  latestRevisionFqdn: pulumi.Output<string | undefined>;
}

const config = new pulumi.Config("mappo");
const defaultLocation = config.get("defaultLocation") ?? "eastus";
const defaultImage =
  config.get("defaultImage") ?? "mcr.microsoft.com/azuredocs/containerapps-helloworld:latest";
const defaultCpu = config.getNumber("defaultCpu") ?? 0.25;
const defaultMemory = config.get("defaultMemory") ?? "0.5Gi";
const targets = config.getObject<TargetConfig[]>("targets") ?? [];

assertUniqueTargetIds(targets);

if (targets.length === 0) {
  pulumi.log.info(
    "No targets configured. Set mappo:targets in the stack config to deploy demo tenant apps.",
  );
}

const targetDeployments = targets.map((target, index) => {
  const normalizedTargetId = normalizeName(target.id, `target-${index + 1}`);
  const tenantId = target.tenantId ?? `tenant-${String(index + 1).padStart(3, "0")}`;
  const targetGroup = target.targetGroup ?? "prod";
  const region = target.region ?? defaultLocation;
  const resourceGroupName = target.resourceGroupName ?? `rg-mappo-${normalizedTargetId}`;
  const environmentName = target.managedEnvironmentName ?? `cae-mappo-${normalizedTargetId}`;
  const containerAppName = target.containerAppName ?? `ca-mappo-${normalizedTargetId}`;
  const image = target.image ?? defaultImage;
  const targetPort = target.targetPort ?? 80;
  const minReplicas = Math.max(0, target.minReplicas ?? 1);
  const maxReplicas = Math.max(minReplicas, target.maxReplicas ?? 1);

  const provider = new azureNative.Provider(`provider-${normalizedTargetId}`, {
    subscriptionId: target.subscriptionId,
  });

  const tags: Record<string, string> = {
    managedBy: "pulumi",
    system: "mappo",
    ring: targetGroup,
    tenantId,
    targetId: target.id,
    ...(target.tags ?? {}),
  };

  const resourceGroup = new resources.ResourceGroup(
    `resource-group-${normalizedTargetId}`,
    {
      resourceGroupName,
      location: region,
      tags,
    },
    { provider },
  );

  const environment = new app.ManagedEnvironment(
    `managed-environment-${normalizedTargetId}`,
    {
      resourceGroupName: resourceGroup.name,
      environmentName,
      location: region,
      tags,
    },
    { provider },
  );

  const container = {
    name: "app",
    image,
    resources: {
      cpu: target.cpu ?? defaultCpu,
      memory: target.memory ?? defaultMemory,
    },
    env: Object.entries(target.environmentVariables ?? {}).map(([name, value]) => ({
      name,
      value,
    })),
  };

  const containerApp = new app.ContainerApp(
    `container-app-${normalizedTargetId}`,
    {
      resourceGroupName: resourceGroup.name,
      containerAppName,
      location: region,
      managedEnvironmentId: environment.id,
      configuration: {
        ingress: {
          external: target.externalIngress ?? true,
          targetPort,
          transport: "Auto",
        },
      },
      template: {
        containers: [container],
        scale: {
          minReplicas,
          maxReplicas,
        },
      },
      tags,
    },
    { provider },
  );

  const output: DeploymentOutput = {
    id: target.id,
    tenantId,
    subscriptionId: target.subscriptionId,
    targetGroup,
    region,
    resourceGroupName: resourceGroup.name,
    managedEnvironmentName: environment.name,
    containerAppName: containerApp.name,
    containerAppId: containerApp.id,
    latestRevisionFqdn: containerApp.latestRevisionFqdn,
  };

  return { target, output };
});

export const targetCount = targets.length;
export const deployedTargets = targetDeployments.map((deployment) => deployment.output);
export const mappoTargetInventory = pulumi.all(
  targetDeployments.map((deployment) =>
    pulumi
      .all([
        deployment.output.resourceGroupName,
        deployment.output.containerAppId,
        deployment.output.latestRevisionFqdn,
      ])
      .apply(([resourceGroupName, containerAppId, latestRevisionFqdn]) => ({
        id: deployment.output.id,
        tenant_id: deployment.output.tenantId,
        subscription_id: deployment.output.subscriptionId,
        managed_app_id: containerAppId,
        tags: {
          ring: deployment.output.targetGroup,
          region: deployment.output.region,
          environment: deployment.target.tags?.environment ?? "demo",
          tier: deployment.target.tags?.tier ?? "standard",
        },
        metadata: {
          resource_group_name: resourceGroupName,
          container_app_fqdn: latestRevisionFqdn ?? "",
        },
      })),
  ),
);
export const mappoTargetInventoryJson = mappoTargetInventory.apply((rows) =>
  JSON.stringify(rows, null, 2),
);

function normalizeName(value: string | undefined, fallback: string): string {
  const source = (value ?? fallback).toLowerCase();
  return source
    .replace(/[^a-z0-9-]/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "")
    .slice(0, 40);
}

function assertUniqueTargetIds(targetConfigs: TargetConfig[]): void {
  const seen = new Set<string>();
  for (const target of targetConfigs) {
    if (target.id.trim().length === 0) {
      throw new Error("Every target config entry must include a non-empty id.");
    }
    if (target.subscriptionId.trim().length === 0) {
      throw new Error(`Target ${target.id} must include a non-empty subscriptionId.`);
    }
    if (seen.has(target.id)) {
      throw new Error(`Duplicate target id detected in mappo:targets: ${target.id}`);
    }
    seen.add(target.id);
  }
}
