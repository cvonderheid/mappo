import { execSync } from "node:child_process";

import * as pulumi from "@pulumi/pulumi";
import * as app from "@pulumi/azure-native/app";
import * as azureNative from "@pulumi/azure-native/index";
import * as resources from "@pulumi/azure-native/resources";
import * as solutions from "@pulumi/azure-native/solutions";

import { TargetConfig, parseProfileName, targetsFromProfile } from "./targets";

interface SubscriptionContext {
  provider: azureNative.Provider;
  location: string;
  definitionResourceGroup: resources.ResourceGroup;
  applicationResourceGroup: resources.ResourceGroup;
  sharedEnvironmentResourceGroup: resources.ResourceGroup;
  sharedEnvironment: app.ManagedEnvironment;
  definition: solutions.ApplicationDefinition;
}

interface DeploymentOutput {
  id: string;
  tenantId: string;
  subscriptionId: string;
  targetGroup: string;
  tier: string;
  environment: string;
  region: string;
  managedApplicationName: string;
  managedResourceGroupName: string;
  managedResourceGroupId: pulumi.Output<string>;
  containerAppName: string;
  containerAppResourceId: pulumi.Output<string>;
  managedApplication: solutions.Application;
}

const CONTRIBUTOR_ROLE_DEFINITION_ID = "b24988ac-6180-42a0-ab88-20f7382dd24c";

const config = new pulumi.Config("mappo");
const defaultLocation = config.get("defaultLocation") ?? "eastus";
const defaultImage =
  config.get("defaultImage") ?? "mcr.microsoft.com/azuredocs/containerapps-helloworld:latest";
const defaultCpu = config.getNumber("defaultCpu") ?? 0.25;
const defaultMemory = config.get("defaultMemory") ?? "0.5Gi";

const definitionNamePrefix = config.get("definitionNamePrefix") ?? "mappo-ma-def";
const definitionResourceGroupPrefix =
  config.get("definitionResourceGroupPrefix") ?? "rg-mappo-ma-def";
const applicationResourceGroupPrefix =
  config.get("applicationResourceGroupPrefix") ?? "rg-mappo-ma-apps";
const sharedEnvironmentNamePrefix =
  config.get("sharedEnvironmentNamePrefix") ?? "cae-mappo-ma-shared";
const sharedEnvironmentResourceGroupPrefix =
  config.get("sharedEnvironmentResourceGroupPrefix") ?? "rg-mappo-ma-shared-env";
const managedAppNamePrefix = config.get("managedAppNamePrefix") ?? "mappo-ma";
const managedResourceGroupPrefix = config.get("managedResourceGroupPrefix") ?? "rg-mappo-ma-mrg";
const containerAppNamePrefix = config.get("containerAppNamePrefix") ?? "ca-mappo-ma";

const publisherPrincipalObjectId = requireConfigWithEnvFallback(
  config,
  "publisherPrincipalObjectId",
  "MAPPO_PUBLISHER_PRINCIPAL_OBJECT_ID",
);
const publisherRoleDefinitionId =
  config.get("publisherRoleDefinitionId") ?? CONTRIBUTOR_ROLE_DEFINITION_ID;

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
const contextBySubscription = new Map<string, SubscriptionContext>();

const managedAppMainTemplate = buildManagedAppMainTemplate(defaultCpu, defaultMemory);
const createUiDefinition = buildCreateUiDefinition();

const targetDeployments = targets.map((target, index) => {
  const normalizedTargetId = normalizeName(target.id, `target-${index + 1}`, 40);
  const tenantId = target.tenantId ?? `tenant-${String(index + 1).padStart(3, "0")}`;
  const targetGroup = target.targetGroup ?? target.tags?.ring ?? "prod";
  const region = target.region ?? defaultLocation;
  const tier = target.tier ?? target.tags?.tier ?? "standard";
  const environment = target.environment ?? target.tags?.environment ?? "demo";

  const provider = getProvider(target.subscriptionId);
  const context = getOrCreateSubscriptionContext({
    provider,
    subscriptionId: target.subscriptionId,
    location: region,
    managedAppMainTemplate,
    createUiDefinition,
  });

  const managedApplicationName =
    target.managedApplicationName ??
    normalizeName(
      `${managedAppNamePrefix}-${normalizedTargetId}`,
      `mappo-ma-${normalizedTargetId}`,
      60,
    );
  const managedResourceGroupName =
    target.managedResourceGroupName ??
    normalizeName(
      `${managedResourceGroupPrefix}-${normalizedTargetId}`,
      `rg-mappo-ma-mrg-${normalizedTargetId}`,
      90,
    );
  const containerAppName =
    target.containerAppName ??
    normalizeName(
      `${containerAppNamePrefix}-${normalizedTargetId}`,
      `ca-mappo-ma-${normalizedTargetId}`,
      32,
    );
  const managedResourceGroupId = pulumi.output(
    `/subscriptions/${target.subscriptionId}/resourceGroups/${managedResourceGroupName}`,
  );
  const containerAppResourceId = pulumi.output(
    `/subscriptions/${target.subscriptionId}/resourceGroups/${managedResourceGroupName}/providers/Microsoft.App/containerApps/${containerAppName}`,
  );

  const tags: Record<string, pulumi.Input<string>> = {
    ...(target.tags ?? {}),
    managedBy: "pulumi",
    system: "mappo",
    ring: targetGroup,
    region,
    tier,
    environment,
    tenantId,
    targetId: target.id,
  };

  const managedApplication = new solutions.Application(
    `managed-app-${normalizedTargetId}`,
    {
      resourceGroupName: context.applicationResourceGroup.name,
      applicationName: managedApplicationName,
      applicationDefinitionId: context.definition.id,
      kind: "ServiceCatalog",
      location: region,
      managedResourceGroupId,
      parameters: {
        location: { value: region },
        sharedEnvironmentResourceId: { value: context.sharedEnvironment.id },
        containerAppName: { value: containerAppName },
        containerImage: { value: defaultImage },
        targetGroup: { value: targetGroup },
        tenantId: { value: tenantId },
        targetId: { value: target.id },
        tier: { value: tier },
        environment: { value: environment },
      },
      tags,
    },
    { provider },
  );

  const output: DeploymentOutput = {
    id: managedApplicationName,
    tenantId,
    subscriptionId: target.subscriptionId,
    targetGroup,
    tier,
    environment,
    region,
    managedApplicationName,
    managedResourceGroupName,
    managedResourceGroupId,
    containerAppName,
    containerAppResourceId,
    managedApplication,
  };

  return output;
});

export const targetCount = targets.length;
export const managedAppCount = targetDeployments.length;

export const managedApplicationIds = targetDeployments.map((deployment) => deployment.managedApplication.id);

export const mappoTargetInventory = pulumi.all(
  targetDeployments.map((deployment) =>
    pulumi
      .all([
        deployment.managedApplication.id,
        deployment.managedResourceGroupId,
        deployment.containerAppResourceId,
        deployment.managedApplication.outputs,
      ])
      .apply(
        ([managedApplicationId, managedResourceGroupId, containerAppResourceId, appOutputs]) => ({
          id: deployment.id,
          tenant_id: deployment.tenantId,
          subscription_id: deployment.subscriptionId,
          managed_app_id: containerAppResourceId,
          tags: {
            ring: deployment.targetGroup,
            region: deployment.region,
            environment: deployment.environment,
            tier: deployment.tier,
          },
          metadata: {
            managed_application_id: managedApplicationId,
            managed_application_name: deployment.managedApplicationName,
            managed_resource_group_id: managedResourceGroupId,
            managed_resource_group_name: deployment.managedResourceGroupName,
            container_app_name: deployment.containerAppName,
            container_app_fqdn: extractManagedAppOutputValue(appOutputs, "containerAppFqdn"),
          },
        }),
      ),
  ),
);

export const mappoTargetInventoryJson = mappoTargetInventory.apply((rows) =>
  JSON.stringify(rows, null, 2),
);

function getOrCreateSubscriptionContext(input: {
  provider: azureNative.Provider;
  subscriptionId: string;
  location: string;
  managedAppMainTemplate: Record<string, unknown>;
  createUiDefinition: Record<string, unknown>;
}): SubscriptionContext {
  const existing = contextBySubscription.get(input.subscriptionId);
  if (existing) {
    if (existing.location !== input.location) {
      pulumi.log.warn(
        "Targets in one subscription use multiple regions. Shared resources are pinned to "
          + `${existing.location} for subscription ${input.subscriptionId}.`,
      );
    }
    return existing;
  }

  const key = subscriptionKey(input.subscriptionId);
  const definitionResourceGroupName = normalizeName(
    `${definitionResourceGroupPrefix}-${key}`,
    `rg-mappo-ma-def-${key}`,
    90,
  );
  const applicationResourceGroupName = normalizeName(
    `${applicationResourceGroupPrefix}-${key}`,
    `rg-mappo-ma-apps-${key}`,
    90,
  );
  const sharedEnvironmentResourceGroupName = normalizeName(
    `${sharedEnvironmentResourceGroupPrefix}-${key}`,
    `rg-mappo-ma-shared-env-${key}`,
    90,
  );
  const definitionName = normalizeName(
    `${definitionNamePrefix}-${key}`,
    `mappo-ma-def-${key}`,
    64,
  );
  const sharedEnvironmentName = normalizeName(
    `${sharedEnvironmentNamePrefix}-${key}`,
    `cae-mappo-ma-shared-${key}`,
    32,
  );

  const definitionResourceGroup = new resources.ResourceGroup(
    `managed-app-definition-rg-${key}`,
    {
      resourceGroupName: definitionResourceGroupName,
      location: input.location,
      tags: {
        managedBy: "pulumi",
        system: "mappo",
        scope: "managed-app-definition",
      },
    },
    { provider: input.provider },
  );

  const applicationResourceGroup = new resources.ResourceGroup(
    `managed-app-rg-${key}`,
    {
      resourceGroupName: applicationResourceGroupName,
      location: input.location,
      tags: {
        managedBy: "pulumi",
        system: "mappo",
        scope: "managed-app-instances",
      },
    },
    { provider: input.provider },
  );

  const sharedEnvironmentResourceGroup = new resources.ResourceGroup(
    `shared-environment-rg-${key}`,
    {
      resourceGroupName: sharedEnvironmentResourceGroupName,
      location: input.location,
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
      resourceGroupName: sharedEnvironmentResourceGroup.name,
      environmentName: sharedEnvironmentName,
      location: input.location,
      tags: {
        managedBy: "pulumi",
        system: "mappo",
        scope: "shared-environment",
      },
    },
    { provider: input.provider },
  );

  const definition = new solutions.ApplicationDefinition(
    `managed-app-definition-${key}`,
    {
      resourceGroupName: definitionResourceGroup.name,
      applicationDefinitionName: definitionName,
      displayName: `MAPPO Managed App Definition (${key})`,
      description:
        "MAPPO service catalog managed application definition for marketplace-accurate demo rollout.",
      location: input.location,
      lockLevel: "None",
      deploymentPolicy: {
        deploymentMode: "Incremental",
      },
      managementPolicy: {
        mode: "Managed",
      },
      authorizations: [
        {
          principalId: publisherPrincipalObjectId,
          roleDefinitionId: publisherRoleDefinitionId,
        },
      ],
      createUiDefinition: input.createUiDefinition,
      mainTemplate: input.managedAppMainTemplate,
      tags: {
        managedBy: "pulumi",
        system: "mappo",
      },
    },
    { provider: input.provider },
  );

  const context: SubscriptionContext = {
    provider: input.provider,
    location: input.location,
    definitionResourceGroup,
    applicationResourceGroup,
    sharedEnvironmentResourceGroup,
    sharedEnvironment,
    definition,
  };
  contextBySubscription.set(input.subscriptionId, context);
  return context;
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

function buildCreateUiDefinition(): Record<string, unknown> {
  return {
    $schema:
      "https://schema.management.azure.com/schemas/0.1.2-preview/CreateUIDefinition.MultiVm.json#",
    handler: "Microsoft.Azure.CreateUIDef",
    version: "0.1.2-preview",
    parameters: {
      basics: [],
      steps: [],
      outputs: {},
    },
  };
}

function buildManagedAppMainTemplate(
  defaultContainerCpu: number,
  defaultContainerMemory: string,
): Record<string, unknown> {
  return {
    $schema: "https://schema.management.azure.com/schemas/2019-04-01/deploymentTemplate.json#",
    contentVersion: "1.0.0.0",
    parameters: {
      location: { type: "string" },
      sharedEnvironmentResourceId: { type: "string" },
      containerAppName: { type: "string" },
      containerImage: { type: "string" },
      targetGroup: { type: "string" },
      tenantId: { type: "string" },
      targetId: { type: "string" },
      tier: { type: "string" },
      environment: { type: "string" },
    },
    resources: [
      {
        type: "Microsoft.App/containerApps",
        apiVersion: "2024-03-01",
        name: "[parameters('containerAppName')]",
        location: "[parameters('location')]",
        tags: {
          managedBy: "managedApp",
          system: "mappo",
          ring: "[parameters('targetGroup')]",
          tenantId: "[parameters('tenantId')]",
          targetId: "[parameters('targetId')]",
          tier: "[parameters('tier')]",
          environment: "[parameters('environment')]",
        },
        properties: {
          managedEnvironmentId: "[parameters('sharedEnvironmentResourceId')]",
          configuration: {
            ingress: {
              external: true,
              targetPort: 80,
              transport: "Auto",
            },
          },
          template: {
            containers: [
              {
                name: "app",
                image: "[parameters('containerImage')]",
                resources: {
                  cpu: defaultContainerCpu,
                  memory: defaultContainerMemory,
                },
              },
            ],
            scale: {
              minReplicas: 1,
              maxReplicas: 1,
            },
          },
        },
      },
    ],
    outputs: {
      containerAppResourceId: {
        type: "string",
        value: "[resourceId('Microsoft.App/containerApps', parameters('containerAppName'))]",
      },
      containerAppFqdn: {
        type: "string",
        value:
          "[reference(resourceId('Microsoft.App/containerApps', parameters('containerAppName')), '2024-03-01').properties.latestRevisionFqdn]",
      },
    },
  };
}

function extractManagedAppOutputValue(outputs: unknown, key: string): string {
  if (!outputs || typeof outputs !== "object") {
    return "";
  }
  const row = (outputs as Record<string, unknown>)[key];
  if (typeof row === "string") {
    return row;
  }
  if (row && typeof row === "object") {
    const value = (row as Record<string, unknown>).value;
    if (typeof value === "string") {
      return value;
    }
  }
  return "";
}

function normalizeName(value: string | undefined, fallback: string, maxLen: number): string {
  const source = (value ?? fallback).toLowerCase();
  const normalized = source
    .replace(/[^a-z0-9-]/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "");

  if (normalized.length === 0) {
    return fallback.slice(0, maxLen);
  }
  return normalized.slice(0, maxLen).replace(/-+$/g, "");
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

function requireConfigWithEnvFallback(
  pulumiConfig: pulumi.Config,
  configKey: string,
  envKey: string,
): string {
  const fromConfig = pulumiConfig.get(configKey);
  const fromEnv = process.env[envKey];
  const value = (fromConfig ?? fromEnv ?? "").trim();
  if (value.length > 0) {
    return value;
  }

  throw new Error(
    `Missing mappo:${configKey}. Set it in the Pulumi stack config or via ${envKey}.`,
  );
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

function subscriptionKey(subscriptionId: string): string {
  return subscriptionId.replace(/[^a-zA-Z0-9]/g, "").slice(0, 8).toLowerCase();
}
