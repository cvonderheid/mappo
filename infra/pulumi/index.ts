import { execSync } from "node:child_process";

import * as pulumi from "@pulumi/pulumi";
import * as azureNative from "@pulumi/azure-native/index";
import * as resources from "@pulumi/azure-native/resources";
import * as solutions from "@pulumi/azure-native/solutions";

import { TargetConfig, parseProfileName, targetsFromProfile } from "./targets";

interface SubscriptionContext {
  provider: azureNative.Provider;
  location: string;
  definitionResourceGroup: resources.ResourceGroup;
  applicationResourceGroup: resources.ResourceGroup;
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

interface ControlPlanePostgresResources {
  subscriptionId: string;
  resourceGroupName: pulumi.Output<string>;
  serverName: pulumi.Output<string>;
  host: pulumi.Output<string>;
  port: number;
  databaseName: string;
  adminLogin: string;
  connectionUsername: pulumi.Output<string>;
  password: pulumi.Output<string>;
  databaseUrl: pulumi.Output<string>;
}

interface FirewallIpRange {
  startIpAddress: string;
  endIpAddress: string;
}

const CONTRIBUTOR_ROLE_DEFINITION_ID = "b24988ac-6180-42a0-ab88-20f7382dd24c";

const config = new pulumi.Config("mappo");
const defaultLocation = config.get("defaultLocation") ?? "eastus";
const defaultImage =
  config.get("defaultImage") ?? "docker.io/library/python:3.11-alpine";
const defaultSoftwareVersion = config.get("defaultSoftwareVersion") ?? "2026.02.20.1";
const defaultDataModelVersion = config.get("defaultDataModelVersion") ?? "1";
const defaultCpu = config.getNumber("defaultCpu") ?? 0.25;
const defaultMemory = config.get("defaultMemory") ?? "0.5Gi";
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
  "            'dataModelVersion': os.getenv('MAPPO_DATA_MODEL_VERSION', os.getenv('MAPPO_FEATURE_FLAG', 'unknown')),",
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

const definitionNamePrefix = config.get("definitionNamePrefix") ?? "mappo-ma-def";
const definitionResourceGroupPrefix =
  config.get("definitionResourceGroupPrefix") ?? "rg-mappo-ma-def";
const applicationResourceGroupPrefix =
  config.get("applicationResourceGroupPrefix") ?? "rg-mappo-ma-apps";
const managedAppNamePrefix = config.get("managedAppNamePrefix") ?? "mappo-ma";
const managedResourceGroupPrefix = config.get("managedResourceGroupPrefix") ?? "rg-mappo-ma-mrg";
const managedEnvironmentNamePrefix =
  config.get("managedEnvironmentNamePrefix") ?? "cae-mappo-ma";
const containerAppNamePrefix = config.get("containerAppNamePrefix") ?? "ca-mappo-ma";
const controlPlaneResourceGroupPrefix =
  config.get("controlPlaneResourceGroupPrefix") ?? "rg-mappo-control-plane";
const controlPlanePostgresServerNamePrefix =
  config.get("controlPlanePostgresServerNamePrefix") ?? "pg-mappo";
const controlPlanePostgresDatabaseName =
  config.get("controlPlanePostgresDatabaseName") ?? "mappo";
const controlPlanePostgresAdminLogin = normalizePostgresLogin(
  optionalConfigWithEnvFallback(
    config,
    "controlPlanePostgresAdminLogin",
    "MAPPO_CONTROL_PLANE_DB_ADMIN_LOGIN",
  ) ?? "mappoadmin",
);
const controlPlanePostgresVersion = config.get("controlPlanePostgresVersion") ?? "16";
const controlPlanePostgresSkuName =
  config.get("controlPlanePostgresSkuName") ?? "Standard_B1ms";
const controlPlanePostgresStorageSizeGb = numberConfigWithEnvFallback(
  config,
  "controlPlanePostgresStorageSizeGb",
  "MAPPO_CONTROL_PLANE_DB_STORAGE_GB",
  32,
);
const controlPlanePostgresBackupRetentionDays = numberConfigWithEnvFallback(
  config,
  "controlPlanePostgresBackupRetentionDays",
  "MAPPO_CONTROL_PLANE_DB_BACKUP_RETENTION_DAYS",
  7,
);
const controlPlanePostgresPublicNetworkAccess = booleanConfigWithEnvFallback(
  config,
  "controlPlanePostgresPublicNetworkAccess",
  "MAPPO_CONTROL_PLANE_DB_PUBLIC_NETWORK_ACCESS",
  true,
);
const controlPlanePostgresAllowAzureServices = booleanConfigWithEnvFallback(
  config,
  "controlPlanePostgresAllowAzureServices",
  "MAPPO_CONTROL_PLANE_DB_ALLOW_AZURE_SERVICES",
  true,
);
const controlPlanePostgresProvisioningEnabled = booleanConfigWithEnvFallback(
  config,
  "controlPlanePostgresEnabled",
  "MAPPO_CONTROL_PLANE_DB_ENABLED",
  false,
);
const controlPlaneSubscriptionId =
  optionalConfigWithEnvFallback(
    config,
    "controlPlaneSubscriptionId",
    "MAPPO_CONTROL_PLANE_SUBSCRIPTION_ID",
  ) ?? resolveDemoSubscriptionId(config.get("demoSubscriptionId"));
const controlPlaneLocation = config.get("controlPlaneLocation") ?? defaultLocation;
const controlPlanePostgresAdminPassword = optionalSecretConfigWithEnvFallback(
  config,
  "controlPlanePostgresAdminPassword",
  "MAPPO_CONTROL_PLANE_DB_ADMIN_PASSWORD",
);
const controlPlanePostgresFirewallIpRanges = parseFirewallIpRanges(
  config,
  "controlPlanePostgresAllowedIpRanges",
  "MAPPO_CONTROL_PLANE_DB_ALLOWED_IPS",
);

if (controlPlanePostgresProvisioningEnabled && !controlPlanePostgresAdminPassword) {
  throw new Error(
    "Managed Postgres is enabled, but control plane DB admin password is missing. "
      + "Set mappo:controlPlanePostgresAdminPassword (secret) or "
      + "MAPPO_CONTROL_PLANE_DB_ADMIN_PASSWORD.",
  );
}

const publisherPrincipalObjectId = optionalConfigWithEnvFallback(
  config,
  "publisherPrincipalObjectId",
  "MAPPO_PUBLISHER_PRINCIPAL_OBJECT_ID",
);
const publisherPrincipalObjectIds = normalizePrincipalMap(
  config.getObject<Record<string, string>>("publisherPrincipalObjectIds"),
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
const controlPlanePostgres = createControlPlanePostgresResources();

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
    authorizationPrincipalObjectId: resolveAuthorizationPrincipalObjectId(
      target.subscriptionId,
    ),
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
  const managedEnvironmentName = normalizeName(
    `${managedEnvironmentNamePrefix}-${normalizedTargetId}`,
    `cae-mappo-ma-${normalizedTargetId}`,
    32,
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
        managedEnvironmentName: { value: managedEnvironmentName },
        containerAppName: { value: containerAppName },
        containerImage: { value: defaultImage },
        softwareVersion: { value: defaultSoftwareVersion },
        dataModelVersion: { value: defaultDataModelVersion },
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

export const controlPlanePostgresEnabled = controlPlanePostgresProvisioningEnabled;
export const controlPlanePostgresSubscriptionId = controlPlanePostgres
  ? controlPlanePostgres.subscriptionId
  : null;
export const controlPlanePostgresResourceGroupName = controlPlanePostgres
  ? controlPlanePostgres.resourceGroupName
  : null;
export const controlPlanePostgresServerName = controlPlanePostgres
  ? controlPlanePostgres.serverName
  : null;
export const controlPlanePostgresHost = controlPlanePostgres
  ? controlPlanePostgres.host
  : null;
export const controlPlanePostgresPort = controlPlanePostgres
  ? controlPlanePostgres.port
  : null;
export const controlPlanePostgresDatabase = controlPlanePostgres
  ? controlPlanePostgres.databaseName
  : null;
export const controlPlanePostgresAdmin = controlPlanePostgres
  ? controlPlanePostgres.adminLogin
  : null;
export const controlPlanePostgresConnectionUsername = controlPlanePostgres
  ? controlPlanePostgres.connectionUsername
  : null;
export const controlPlanePostgresPassword = controlPlanePostgres
  ? controlPlanePostgres.password
  : null;
export const controlPlaneDatabaseUrl = controlPlanePostgres ? controlPlanePostgres.databaseUrl : null;

function createControlPlanePostgresResources(): ControlPlanePostgresResources | null {
  if (!controlPlanePostgresProvisioningEnabled) {
    return null;
  }

  if (!controlPlanePostgresAdminPassword) {
    throw new Error(
      "Managed Postgres provisioning requested without admin password. "
        + "Set mappo:controlPlanePostgresAdminPassword (secret).",
    );
  }

  const provider = getProvider(controlPlaneSubscriptionId);
  const subscriptionKeyValue = subscriptionKey(controlPlaneSubscriptionId);
  const resourceGroupName = normalizeName(
    `${controlPlaneResourceGroupPrefix}-${subscriptionKeyValue}`,
    `rg-mappo-control-plane-${subscriptionKeyValue}`,
    90,
  );
  const serverName = normalizeName(
    `${controlPlanePostgresServerNamePrefix}-${subscriptionKeyValue}`,
    `pg-mappo-${subscriptionKeyValue}`,
    63,
  );

  const resourceGroup = new resources.ResourceGroup(
    `control-plane-rg-${subscriptionKeyValue}`,
    {
      resourceGroupName,
      location: controlPlaneLocation,
      tags: {
        managedBy: "pulumi",
        system: "mappo",
        scope: "control-plane",
      },
    },
    { provider },
  );

  const server = new azureNative.dbforpostgresql.Server(
    `control-plane-postgres-${subscriptionKeyValue}`,
    {
      resourceGroupName: resourceGroup.name,
      serverName,
      location: controlPlaneLocation,
      createMode: "Create",
      administratorLogin: controlPlanePostgresAdminLogin,
      administratorLoginPassword: controlPlanePostgresAdminPassword,
      version: controlPlanePostgresVersion,
      backup: {
        backupRetentionDays: controlPlanePostgresBackupRetentionDays,
        geoRedundantBackup: "Disabled",
      },
      highAvailability: {
        mode: "Disabled",
      },
      network: {
        publicNetworkAccess: controlPlanePostgresPublicNetworkAccess ? "Enabled" : "Disabled",
      },
      sku: {
        name: controlPlanePostgresSkuName,
        tier: inferPostgresSkuTier(controlPlanePostgresSkuName),
      },
      storage: {
        storageSizeGB: controlPlanePostgresStorageSizeGb,
        autoGrow: "Enabled",
      },
      tags: {
        managedBy: "pulumi",
        system: "mappo",
        scope: "control-plane-postgres",
      },
    },
    {
      provider,
      customTimeouts: {
        create: "30m",
        update: "30m",
        delete: "30m",
      },
    },
  );

  const database = new azureNative.dbforpostgresql.Database(
    `control-plane-postgres-db-${subscriptionKeyValue}`,
    {
      resourceGroupName: resourceGroup.name,
      serverName: server.name,
      databaseName: controlPlanePostgresDatabaseName,
      charset: "UTF8",
      collation: "en_US.utf8",
    },
    { provider },
  );

  new azureNative.dbforpostgresql.Configuration(
    `control-plane-postgres-ext-${subscriptionKeyValue}`,
    {
      resourceGroupName: resourceGroup.name,
      serverName: server.name,
      configurationName: "azure.extensions",
      source: "user-override",
      value: "PGCRYPTO",
    },
    {
      provider,
      dependsOn: [database],
      customTimeouts: {
        create: "10m",
        update: "10m",
      },
    },
  );

  if (controlPlanePostgresPublicNetworkAccess) {
    if (controlPlanePostgresAllowAzureServices) {
      new azureNative.dbforpostgresql.FirewallRule(
        `control-plane-postgres-fw-azure-${subscriptionKeyValue}`,
        {
          resourceGroupName: resourceGroup.name,
          serverName: server.name,
          firewallRuleName: "allow-azure-services",
          startIpAddress: "0.0.0.0",
          endIpAddress: "0.0.0.0",
        },
        { provider },
      );
    }

    controlPlanePostgresFirewallIpRanges.forEach((range, index) => {
      new azureNative.dbforpostgresql.FirewallRule(
        `control-plane-postgres-fw-custom-${subscriptionKeyValue}-${index + 1}`,
        {
          resourceGroupName: resourceGroup.name,
          serverName: server.name,
          firewallRuleName: `allow-ip-${index + 1}`,
          startIpAddress: range.startIpAddress,
          endIpAddress: range.endIpAddress,
        },
        { provider },
      );
    });
  }

  const host = server.fullyQualifiedDomainName;
  const connectionUsername = pulumi.output(controlPlanePostgresAdminLogin);
  const databaseUrl = pulumi
    .all([connectionUsername, controlPlanePostgresAdminPassword, host, database.name])
    .apply(
      ([username, password, fqdn, databaseName]) =>
        "postgresql+psycopg://"
          + `${encodeURIComponent(username)}:${encodeURIComponent(password)}`
          + `@${fqdn}:5432/${databaseName}?sslmode=require`,
    );

  return {
    subscriptionId: controlPlaneSubscriptionId,
    resourceGroupName: resourceGroup.name,
    serverName: server.name,
    host,
    port: 5432,
    databaseName: controlPlanePostgresDatabaseName,
    adminLogin: controlPlanePostgresAdminLogin,
    connectionUsername,
    password: controlPlanePostgresAdminPassword,
    databaseUrl,
  };
}

function getOrCreateSubscriptionContext(input: {
  provider: azureNative.Provider;
  subscriptionId: string;
  location: string;
  authorizationPrincipalObjectId: string;
  managedAppMainTemplate: Record<string, unknown>;
  createUiDefinition: Record<string, unknown>;
}): SubscriptionContext {
  const existing = contextBySubscription.get(input.subscriptionId);
  if (existing) {
    if (existing.location !== input.location) {
      pulumi.log.warn(
        `Targets in one subscription use multiple regions for ${input.subscriptionId}.`,
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
  const definitionName = normalizeName(
    `${definitionNamePrefix}-${key}`,
    `mappo-ma-def-${key}`,
    64,
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
          principalId: input.authorizationPrincipalObjectId,
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
      managedEnvironmentName: { type: "string" },
      containerAppName: { type: "string" },
      containerImage: { type: "string" },
      softwareVersion: { type: "string" },
      dataModelVersion: { type: "string" },
      targetGroup: { type: "string" },
      tenantId: { type: "string" },
      targetId: { type: "string" },
      tier: { type: "string" },
      environment: { type: "string" },
    },
    resources: [
      {
        type: "Microsoft.App/managedEnvironments",
        apiVersion: "2024-03-01",
        name: "[parameters('managedEnvironmentName')]",
        location: "[parameters('location')]",
        properties: {
          appLogsConfiguration: {},
        },
        tags: {
          managedBy: "managedApp",
          system: "mappo",
        },
      },
      {
        type: "Microsoft.App/containerApps",
        apiVersion: "2024-03-01",
        name: "[parameters('containerAppName')]",
        location: "[parameters('location')]",
        dependsOn: [
          "[resourceId('Microsoft.App/managedEnvironments', parameters('managedEnvironmentName'))]",
        ],
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
          managedEnvironmentId:
            "[resourceId('Microsoft.App/managedEnvironments', parameters('managedEnvironmentName'))]",
          configuration: {
            ingress: {
              external: true,
              targetPort: 8080,
              transport: "Auto",
            },
          },
          template: {
            containers: [
              {
                name: "app",
                image: "[parameters('containerImage')]",
                command: ["python"],
                args: ["-c", targetDemoServerScript],
                env: [
                  {
                    name: "MAPPO_SOFTWARE_VERSION",
                    value: "[parameters('softwareVersion')]",
                  },
                  {
                    name: "MAPPO_DATA_MODEL_VERSION",
                    value: "[parameters('dataModelVersion')]",
                  },
                  {
                    name: "MAPPO_FEATURE_FLAG",
                    value: "[parameters('dataModelVersion')]",
                  },
                ],
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

function resolveAuthorizationPrincipalObjectId(subscriptionId: string): string {
  const mappedValue = publisherPrincipalObjectIds[subscriptionId];
  if (mappedValue) {
    return mappedValue;
  }
  if (publisherPrincipalObjectId) {
    return publisherPrincipalObjectId;
  }
  throw new Error(
    "Missing managed-app authorization principal object ID for subscription "
      + `${subscriptionId}. Set mappo:publisherPrincipalObjectIds["${subscriptionId}"] `
      + "or mappo:publisherPrincipalObjectId.",
  );
}

function normalizePrincipalMap(
  value: Record<string, string> | undefined,
): Record<string, string> {
  if (!value) {
    return {};
  }
  const result: Record<string, string> = {};
  for (const [key, raw] of Object.entries(value)) {
    const subscriptionId = key.trim();
    const principalObjectId = raw.trim();
    if (!subscriptionId || !principalObjectId) {
      continue;
    }
    result[subscriptionId] = principalObjectId;
  }
  return result;
}

function optionalConfigWithEnvFallback(
  pulumiConfig: pulumi.Config,
  configKey: string,
  envKey: string,
): string | undefined {
  const fromConfig = pulumiConfig.get(configKey);
  const fromEnv = process.env[envKey];
  const value = (fromConfig ?? fromEnv ?? "").trim();
  if (value.length > 0) {
    return value;
  }
  return undefined;
}

function optionalSecretConfigWithEnvFallback(
  pulumiConfig: pulumi.Config,
  configKey: string,
  envKey: string,
): pulumi.Output<string> | undefined {
  const fromConfig = pulumiConfig.getSecret(configKey);
  if (fromConfig) {
    return fromConfig;
  }
  const fromEnv = (process.env[envKey] ?? "").trim();
  if (fromEnv.length > 0) {
    return pulumi.secret(fromEnv);
  }
  return undefined;
}

function booleanConfigWithEnvFallback(
  pulumiConfig: pulumi.Config,
  configKey: string,
  envKey: string,
  defaultValue: boolean,
): boolean {
  const fromConfig = pulumiConfig.getBoolean(configKey);
  if (fromConfig !== undefined) {
    return fromConfig;
  }
  const fromEnv = (process.env[envKey] ?? "").trim().toLowerCase();
  if (fromEnv.length === 0) {
    return defaultValue;
  }
  if (["1", "true", "yes", "on"].includes(fromEnv)) {
    return true;
  }
  if (["0", "false", "no", "off"].includes(fromEnv)) {
    return false;
  }
  throw new Error(
    `Invalid boolean for ${configKey}/${envKey}. Use one of: 1,true,yes,on,0,false,no,off.`,
  );
}

function numberConfigWithEnvFallback(
  pulumiConfig: pulumi.Config,
  configKey: string,
  envKey: string,
  defaultValue: number,
): number {
  const fromConfig = pulumiConfig.getNumber(configKey);
  if (fromConfig !== undefined) {
    return fromConfig;
  }
  const fromEnv = (process.env[envKey] ?? "").trim();
  if (fromEnv.length === 0) {
    return defaultValue;
  }
  const parsed = Number(fromEnv);
  if (!Number.isFinite(parsed)) {
    throw new Error(`Invalid numeric value for ${configKey}/${envKey}: '${fromEnv}'.`);
  }
  return parsed;
}

function parseFirewallIpRanges(
  pulumiConfig: pulumi.Config,
  configKey: string,
  envKey: string,
): FirewallIpRange[] {
  const fromConfig = pulumiConfig.getObject<string[]>(configKey);
  const fromEnvRaw = (process.env[envKey] ?? "").trim();
  const fromEnv = fromEnvRaw.length
    ? fromEnvRaw.split(",").map((item) => item.trim()).filter((item) => item.length > 0)
    : [];

  const raw = (fromConfig && fromConfig.length > 0 ? fromConfig : fromEnv)
    .map((item) => item.trim())
    .filter((item) => item.length > 0);

  return raw.map((value) => {
    const parts = value.split("-", 2).map((segment) => segment.trim());
    if (parts.length === 1 || parts[1] === "") {
      validateIpv4(parts[0], `${configKey}/${envKey}`);
      return {
        startIpAddress: parts[0],
        endIpAddress: parts[0],
      };
    }
    validateIpv4(parts[0], `${configKey}/${envKey}`);
    validateIpv4(parts[1], `${configKey}/${envKey}`);
    return {
      startIpAddress: parts[0],
      endIpAddress: parts[1],
    };
  });
}

function validateIpv4(value: string, source: string): void {
  const segments = value.split(".");
  if (segments.length !== 4) {
    throw new Error(`Invalid IPv4 address in ${source}: '${value}'.`);
  }
  for (const segment of segments) {
    if (!/^\d+$/.test(segment)) {
      throw new Error(`Invalid IPv4 address in ${source}: '${value}'.`);
    }
    const numeric = Number(segment);
    if (!Number.isInteger(numeric) || numeric < 0 || numeric > 255) {
      throw new Error(`Invalid IPv4 address in ${source}: '${value}'.`);
    }
  }
}

function inferPostgresSkuTier(skuName: string): string {
  const normalized = skuName.trim().toLowerCase();
  if (
    normalized.startsWith("standard_b")
    || normalized.startsWith("b_")
    || normalized.startsWith("burstable")
  ) {
    return "Burstable";
  }
  if (
    normalized.startsWith("standard_d")
    || normalized.startsWith("standard_ds")
    || normalized.startsWith("gp_")
    || normalized.startsWith("generalpurpose")
  ) {
    return "GeneralPurpose";
  }
  if (
    normalized.startsWith("standard_e")
    || normalized.startsWith("mo_")
    || normalized.startsWith("memoryoptimized")
  ) {
    return "MemoryOptimized";
  }
  throw new Error(
    `Unable to infer PostgreSQL sku tier from '${skuName}'. Set a known Burstable/GeneralPurpose/MemoryOptimized SKU.`,
  );
}

function normalizePostgresLogin(value: string): string {
  const normalized = value.toLowerCase().replace(/[^a-z0-9]/g, "");
  if (normalized.length < 3) {
    return `mappoadmin${normalized}`.slice(0, 16);
  }
  return normalized.slice(0, 63);
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
