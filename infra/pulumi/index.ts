import { execSync } from "node:child_process";

import * as pulumi from "@pulumi/pulumi";
import * as app from "@pulumi/azure-native/app";
import * as azureNative from "@pulumi/azure-native/index";
import * as resources from "@pulumi/azure-native/resources";

import { TargetConfig, parseProfileName, targetsFromProfile } from "./targets";

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

interface ManagedEnvironmentRef {
  id: pulumi.Output<string>;
  name: pulumi.Output<string>;
  location: string;
}

type EnvironmentMode = "shared_per_subscription" | "per_target";

const config = new pulumi.Config("mappo");
const defaultLocation = config.get("defaultLocation") ?? "eastus";
const defaultImage =
  config.get("defaultImage") ?? "mcr.microsoft.com/azuredocs/containerapps-helloworld:latest";
const defaultCpu = config.getNumber("defaultCpu") ?? 0.25;
const defaultMemory = config.get("defaultMemory") ?? "0.5Gi";
const environmentMode = parseEnvironmentMode(config.get("environmentMode") ?? "shared_per_subscription");
const sharedEnvironmentNamePrefix = config.get("sharedEnvironmentNamePrefix") ?? "cae-mappo-shared";
const sharedEnvironmentResourceGroupPrefix =
  config.get("sharedEnvironmentResourceGroupPrefix") ?? "rg-mappo-shared-env";
const inlineTargets = config.getObject<TargetConfig[]>("targets");
const targetProfile = parseProfileName(config.get("targetProfile") ?? "demo10");
const demoSubscriptionId = resolveDemoSubscriptionId(config.get("demoSubscriptionId"));

const targets =
  inlineTargets ??
  targetsFromProfile(targetProfile, {
    defaultLocation,
    defaultSubscriptionId: demoSubscriptionId,
  });

assertUniqueTargetIds(targets);

if (targets.length === 0) {
  pulumi.log.info(
    "No targets configured. Set mappo:targetProfile=demo10 or provide mappo:targets.",
  );
}

const providersBySubscription = new Map<string, azureNative.Provider>();
const sharedEnvironmentsBySubscription = new Map<string, ManagedEnvironmentRef>();

const targetDeployments = targets.map((target, index) => {
  const normalizedTargetId = normalizeName(target.id, `target-${index + 1}`);
  const tenantId = target.tenantId ?? `tenant-${String(index + 1).padStart(3, "0")}`;
  const targetGroup = target.targetGroup ?? "prod";
  const region = target.region ?? defaultLocation;
  const targetResourceGroupName = target.resourceGroupName ?? `rg-mappo-${normalizedTargetId}`;
  const containerAppName = target.containerAppName ?? `ca-mappo-${normalizedTargetId}`;
  const image = target.image ?? defaultImage;
  const targetPort = target.targetPort ?? 80;
  const minReplicas = Math.max(0, target.minReplicas ?? 1);
  const maxReplicas = Math.max(minReplicas, target.maxReplicas ?? 1);

  const provider = getProvider(target.subscriptionId);

  const tags: Record<string, string> = {
    managedBy: "pulumi",
    system: "mappo",
    ring: targetGroup,
    tenantId,
    targetId: target.id,
    ...(target.tags ?? {}),
  };

  const managedEnvironment = getManagedEnvironmentForTarget({
    target,
    normalizedTargetId,
    tags,
    provider,
  });

  const targetResourceGroup = new resources.ResourceGroup(
    `resource-group-${normalizedTargetId}`,
    {
      resourceGroupName: targetResourceGroupName,
      location: managedEnvironment.location,
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
      resourceGroupName: targetResourceGroup.name,
      containerAppName,
      location: managedEnvironment.location,
      managedEnvironmentId: managedEnvironment.id,
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
    resourceGroupName: targetResourceGroup.name,
    managedEnvironmentName: managedEnvironment.name,
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

function getProvider(subscriptionId: string): azureNative.Provider {
  const existing = providersBySubscription.get(subscriptionId);
  if (existing) {
    return existing;
  }
  const key = subscriptionKey(subscriptionId);
  const provider = new azureNative.Provider(`provider-sub-${key}`, {
    subscriptionId,
  });
  providersBySubscription.set(subscriptionId, provider);
  return provider;
}

function getManagedEnvironmentForTarget(input: {
  target: TargetConfig;
  normalizedTargetId: string;
  tags: Record<string, string>;
  provider: azureNative.Provider;
}): ManagedEnvironmentRef {
  if (environmentMode === "per_target") {
    return createPerTargetEnvironment(input);
  }
  return getOrCreateSharedEnvironment(input);
}

function createPerTargetEnvironment(input: {
  target: TargetConfig;
  normalizedTargetId: string;
  tags: Record<string, string>;
  provider: azureNative.Provider;
}): ManagedEnvironmentRef {
  const region = input.target.region ?? defaultLocation;
  const resourceGroupName = input.target.resourceGroupName ?? `rg-mappo-${input.normalizedTargetId}`;
  const environmentName =
    input.target.managedEnvironmentName ?? `cae-mappo-${input.normalizedTargetId}`;

  const resourceGroup = new resources.ResourceGroup(
    `environment-resource-group-${input.normalizedTargetId}`,
    {
      resourceGroupName,
      location: region,
      tags: input.tags,
    },
    { provider: input.provider },
  );

  const environment = new app.ManagedEnvironment(
    `managed-environment-${input.normalizedTargetId}`,
    {
      resourceGroupName: resourceGroup.name,
      environmentName,
      location: region,
      tags: input.tags,
    },
    { provider: input.provider },
  );

  return {
    id: environment.id,
    name: environment.name,
    location: region,
  };
}

function getOrCreateSharedEnvironment(input: {
  target: TargetConfig;
  normalizedTargetId: string;
  tags: Record<string, string>;
  provider: azureNative.Provider;
}): ManagedEnvironmentRef {
  const existing = sharedEnvironmentsBySubscription.get(input.target.subscriptionId);
  if (existing) {
    return existing;
  }

  const key = subscriptionKey(input.target.subscriptionId);
  const sharedResourceGroupName = `${sharedEnvironmentResourceGroupPrefix}-${key}`;
  const sharedEnvironmentName = `${sharedEnvironmentNamePrefix}-${key}`;

  const sharedResourceGroup = new resources.ResourceGroup(
    `shared-environment-resource-group-${key}`,
    {
      resourceGroupName: sharedResourceGroupName,
      location: defaultLocation,
      tags: {
        managedBy: "pulumi",
        system: "mappo",
        scope: "shared-environment",
      },
    },
    { provider: input.provider },
  );

  const sharedEnvironment = new app.ManagedEnvironment(
    `shared-managed-environment-${key}`,
    {
      resourceGroupName: sharedResourceGroup.name,
      environmentName: sharedEnvironmentName,
      location: defaultLocation,
      tags: {
        managedBy: "pulumi",
        system: "mappo",
        scope: "shared-environment",
      },
    },
    { provider: input.provider },
  );

  const shared: ManagedEnvironmentRef = {
    id: sharedEnvironment.id,
    name: sharedEnvironment.name,
    location: defaultLocation,
  };
  sharedEnvironmentsBySubscription.set(input.target.subscriptionId, shared);
  return shared;
}

function resolveDemoSubscriptionId(configuredValue: string | undefined): string {
  if (configuredValue && configuredValue.trim().length > 0) {
    return configuredValue;
  }

  const envValue =
    process.env.ARM_SUBSCRIPTION_ID ??
    process.env.AZURE_SUBSCRIPTION_ID ??
    process.env.PULUMI_AZURE_NATIVE_SUBSCRIPTION_ID;
  if (envValue && envValue.trim().length > 0) {
    return envValue;
  }

  const azValue = detectActiveAzSubscriptionId();
  if (azValue) {
    return azValue;
  }

  throw new Error(
    "Unable to determine demo subscription ID. Set mappo:demoSubscriptionId or ARM_SUBSCRIPTION_ID.",
  );
}

function detectActiveAzSubscriptionId(): string | undefined {
  try {
    const output = execSync("az account show --query id -o tsv", {
      stdio: ["ignore", "pipe", "ignore"],
      encoding: "utf-8",
    });
    const subscriptionId = output.trim();
    if (subscriptionId.length > 0) {
      return subscriptionId;
    }
    return undefined;
  } catch {
    return undefined;
  }
}

function parseEnvironmentMode(value: string): EnvironmentMode {
  if (value === "shared_per_subscription" || value === "per_target") {
    return value;
  }
  throw new Error(
    "Unknown mappo:environmentMode. Expected one of: shared_per_subscription, per_target.",
  );
}

function subscriptionKey(subscriptionId: string): string {
  return subscriptionId.replace(/[^a-zA-Z0-9]/g, "").slice(0, 8).toLowerCase();
}
