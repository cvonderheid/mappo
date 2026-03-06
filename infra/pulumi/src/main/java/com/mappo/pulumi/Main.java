package com.mappo.pulumi;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.pulumi.Config;
import com.pulumi.Context;
import com.pulumi.Pulumi;
import com.pulumi.azurenative.Provider;
import com.pulumi.azurenative.ProviderArgs;
import com.pulumi.azurenative.dbforpostgresql.Configuration;
import com.pulumi.azurenative.dbforpostgresql.ConfigurationArgs;
import com.pulumi.azurenative.dbforpostgresql.Database;
import com.pulumi.azurenative.dbforpostgresql.DatabaseArgs;
import com.pulumi.azurenative.dbforpostgresql.FirewallRule;
import com.pulumi.azurenative.dbforpostgresql.FirewallRuleArgs;
import com.pulumi.azurenative.dbforpostgresql.Server;
import com.pulumi.azurenative.dbforpostgresql.ServerArgs;
import com.pulumi.azurenative.dbforpostgresql.inputs.BackupArgs;
import com.pulumi.azurenative.dbforpostgresql.inputs.HighAvailabilityArgs;
import com.pulumi.azurenative.dbforpostgresql.inputs.NetworkArgs;
import com.pulumi.azurenative.dbforpostgresql.inputs.SkuArgs;
import com.pulumi.azurenative.dbforpostgresql.inputs.StorageArgs;
import com.pulumi.azurenative.resources.ResourceGroup;
import com.pulumi.azurenative.resources.ResourceGroupArgs;
import com.pulumi.azurenative.solutions.Application;
import com.pulumi.azurenative.solutions.ApplicationArgs;
import com.pulumi.azurenative.solutions.ApplicationDefinition;
import com.pulumi.azurenative.solutions.ApplicationDefinitionArgs;
import com.pulumi.azurenative.solutions.enums.ApplicationLockLevel;
import com.pulumi.azurenative.solutions.inputs.ApplicationAuthorizationArgs;
import com.pulumi.azurenative.solutions.inputs.ApplicationDeploymentPolicyArgs;
import com.pulumi.azurenative.solutions.inputs.ApplicationManagementPolicyArgs;
import com.pulumi.core.Output;
import com.pulumi.core.TypeShape;
import com.pulumi.resources.CustomResourceOptions;
import com.pulumi.resources.CustomTimeouts;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class Main {
    private static final String CONTRIBUTOR_ROLE_DEFINITION_ID = "b24988ac-6180-42a0-ab88-20f7382dd24c";
    private static final Gson GSON = new Gson();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern NON_ALPHANUMERIC_DASH = Pattern.compile("[^a-z0-9-]");
    private static final Pattern DASH_COLLAPSE = Pattern.compile("-+");
    private static final Pattern IPV4_SEGMENT_PATTERN = Pattern.compile("^\\d+$");
    private static final String TARGET_DEMO_SERVER_SCRIPT = String.join("\\n", List.of(
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
        "ThreadingHTTPServer(('0.0.0.0', port), Handler).serve_forever()"
    ));

    private final Context ctx;
    private final Config config;

    private final String defaultLocation;
    private final String defaultImage;
    private final String defaultSoftwareVersion;
    private final String defaultDataModelVersion;
    private final double defaultCpu;
    private final String defaultMemory;

    private final String definitionNamePrefix;
    private final String definitionResourceGroupPrefix;
    private final String applicationResourceGroupPrefix;
    private final String managedAppNamePrefix;
    private final String managedResourceGroupPrefix;
    private final String managedEnvironmentNamePrefix;
    private final String containerAppNamePrefix;

    private final String controlPlaneResourceGroupPrefix;
    private final String controlPlanePostgresServerNamePrefix;
    private final String controlPlanePostgresDatabaseName;
    private final String controlPlanePostgresAdminLogin;
    private final String controlPlanePostgresVersion;
    private final String controlPlanePostgresSkuName;
    private final int controlPlanePostgresStorageSizeGb;
    private final int controlPlanePostgresBackupRetentionDays;
    private final boolean controlPlanePostgresPublicNetworkAccess;
    private final boolean controlPlanePostgresAllowAzureServices;
    private final boolean controlPlanePostgresProvisioningEnabled;
    private final String controlPlaneSubscriptionId;
    private final String controlPlaneLocation;
    private final Output<String> controlPlanePostgresAdminPassword;
    private final List<FirewallIpRange> controlPlanePostgresFirewallIpRanges;

    private final String publisherPrincipalObjectId;
    private final Map<String, String> publisherPrincipalObjectIds;
    private final String publisherRoleDefinitionId;

    private final Map<String, Provider> providersBySubscription = new HashMap<>();
    private final Map<String, SubscriptionContext> contextBySubscription = new HashMap<>();

    private final Map<String, Object> managedAppMainTemplate;
    private final Map<String, Object> createUiDefinition;

    private Main(Context ctx) {
        this.ctx = ctx;
        this.config = Config.of("mappo");

        this.defaultLocation = config.get("defaultLocation").orElse("eastus");
        this.defaultImage = config.get("defaultImage").orElse("docker.io/library/python:3.11-alpine");
        this.defaultSoftwareVersion = config.get("defaultSoftwareVersion").orElse("2026.02.20.1");
        this.defaultDataModelVersion = config.get("defaultDataModelVersion").orElse("1");
        this.defaultCpu = config.getDouble("defaultCpu").orElse(0.25);
        this.defaultMemory = config.get("defaultMemory").orElse("0.5Gi");

        this.definitionNamePrefix = config.get("definitionNamePrefix").orElse("mappo-ma-def");
        this.definitionResourceGroupPrefix = config.get("definitionResourceGroupPrefix").orElse("rg-mappo-ma-def");
        this.applicationResourceGroupPrefix = config.get("applicationResourceGroupPrefix").orElse("rg-mappo-ma-apps");
        this.managedAppNamePrefix = config.get("managedAppNamePrefix").orElse("mappo-ma");
        this.managedResourceGroupPrefix = config.get("managedResourceGroupPrefix").orElse("rg-mappo-ma-mrg");
        this.managedEnvironmentNamePrefix = config.get("managedEnvironmentNamePrefix").orElse("cae-mappo-ma");
        this.containerAppNamePrefix = config.get("containerAppNamePrefix").orElse("ca-mappo-ma");

        this.controlPlaneResourceGroupPrefix = config.get("controlPlaneResourceGroupPrefix").orElse("rg-mappo-control-plane");
        this.controlPlanePostgresServerNamePrefix = config.get("controlPlanePostgresServerNamePrefix").orElse("pg-mappo");
        this.controlPlanePostgresDatabaseName = config.get("controlPlanePostgresDatabaseName").orElse("mappo");
        this.controlPlanePostgresAdminLogin = normalizePostgresLogin(
            optionalConfigWithEnvFallback(config, "controlPlanePostgresAdminLogin", "MAPPO_CONTROL_PLANE_DB_ADMIN_LOGIN")
                .orElse("mappoadmin")
        );
        this.controlPlanePostgresVersion = config.get("controlPlanePostgresVersion").orElse("16");
        this.controlPlanePostgresSkuName = config.get("controlPlanePostgresSkuName").orElse("Standard_B1ms");
        this.controlPlanePostgresStorageSizeGb = numberConfigWithEnvFallback(config, "controlPlanePostgresStorageSizeGb", "MAPPO_CONTROL_PLANE_DB_STORAGE_GB", 32);
        this.controlPlanePostgresBackupRetentionDays = numberConfigWithEnvFallback(config, "controlPlanePostgresBackupRetentionDays", "MAPPO_CONTROL_PLANE_DB_BACKUP_RETENTION_DAYS", 7);
        this.controlPlanePostgresPublicNetworkAccess = booleanConfigWithEnvFallback(config, "controlPlanePostgresPublicNetworkAccess", "MAPPO_CONTROL_PLANE_DB_PUBLIC_NETWORK_ACCESS", true);
        this.controlPlanePostgresAllowAzureServices = booleanConfigWithEnvFallback(config, "controlPlanePostgresAllowAzureServices", "MAPPO_CONTROL_PLANE_DB_ALLOW_AZURE_SERVICES", true);
        this.controlPlanePostgresProvisioningEnabled = booleanConfigWithEnvFallback(config, "controlPlanePostgresEnabled", "MAPPO_CONTROL_PLANE_DB_ENABLED", false);
        this.controlPlaneSubscriptionId = optionalConfigWithEnvFallback(config, "controlPlaneSubscriptionId", "MAPPO_CONTROL_PLANE_SUBSCRIPTION_ID")
            .orElse(resolveDemoSubscriptionId(config.get("demoSubscriptionId").orElse(null)));
        this.controlPlaneLocation = config.get("controlPlaneLocation").orElse(defaultLocation);
        this.controlPlanePostgresAdminPassword = optionalSecretConfigWithEnvFallback(config, "controlPlanePostgresAdminPassword", "MAPPO_CONTROL_PLANE_DB_ADMIN_PASSWORD").orElse(null);
        this.controlPlanePostgresFirewallIpRanges = parseFirewallIpRanges(config, "controlPlanePostgresAllowedIpRanges", "MAPPO_CONTROL_PLANE_DB_ALLOWED_IPS");

        if (controlPlanePostgresProvisioningEnabled && controlPlanePostgresAdminPassword == null) {
            throw new IllegalStateException(
                "Managed Postgres is enabled, but control plane DB admin password is missing. "
                    + "Set mappo:controlPlanePostgresAdminPassword (secret) or "
                    + "MAPPO_CONTROL_PLANE_DB_ADMIN_PASSWORD."
            );
        }

        this.publisherPrincipalObjectId = optionalConfigWithEnvFallback(config, "publisherPrincipalObjectId", "MAPPO_PUBLISHER_PRINCIPAL_OBJECT_ID").orElse(null);
        this.publisherPrincipalObjectIds = normalizePrincipalMap(parseConfigStringMap(config, "publisherPrincipalObjectIds"));
        this.publisherRoleDefinitionId = config.get("publisherRoleDefinitionId").orElse(CONTRIBUTOR_ROLE_DEFINITION_ID);

        this.managedAppMainTemplate = buildManagedAppMainTemplate(defaultCpu, defaultMemory);
        this.createUiDefinition = buildCreateUiDefinition();
    }

    public static void main(String[] args) {
        Pulumi.run(ctx -> new Main(ctx).run());
    }

    private void run() {
        List<TargetConfig> inlineTargets = parseConfigObjectList(config, "targets", TargetConfig.class).orElse(null);
        String targetProfileRaw = config.get("targetProfile").orElse("demo10");
        String demoSubscriptionId = resolveDemoSubscriptionId(config.get("demoSubscriptionId").orElse(null));

        List<TargetConfig> targets = inlineTargets != null
            ? inlineTargets
            : targetsFromProfile(parseProfileName(targetProfileRaw), defaultLocation, demoSubscriptionId);

        assertUniqueTargetIds(targets);

        if (targets.isEmpty()) {
            ctx.log().info("No targets configured. Set mappo:targetProfile=demo10 or provide mappo:targets.");
        }

        ControlPlanePostgresResources controlPlanePostgres = createControlPlanePostgresResources();

        List<DeploymentOutput> deployments = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            TargetConfig target = targets.get(i);
            deployments.add(createTargetDeployment(target, i));
        }

        ctx.export("targetCount", targets.size());
        ctx.export("managedAppCount", deployments.size());

        List<Output<String>> managedAppIdOutputs = deployments.stream()
            .map(d -> d.managedApplication.id())
            .toList();
        ctx.export("managedApplicationIds", Output.all(managedAppIdOutputs));

        List<Output<Map<String, Object>>> rows = deployments.stream()
            .map(this::buildInventoryRow)
            .toList();
        Output<List<Map<String, Object>>> inventory = Output.all(rows);
        ctx.export("mappoTargetInventory", inventory);
        ctx.export("mappoTargetInventoryJson", inventory.applyValue(value -> PRETTY_GSON.toJson(value)));

        ctx.export("controlPlanePostgresEnabled", controlPlanePostgresProvisioningEnabled);
        ctx.export(
            "controlPlanePostgresSubscriptionId",
            controlPlanePostgres == null ? Output.ofNullable(null) : Output.of(controlPlanePostgres.subscriptionId)
        );
        ctx.export(
            "controlPlanePostgresResourceGroupName",
            controlPlanePostgres == null ? Output.ofNullable(null) : controlPlanePostgres.resourceGroupName
        );
        ctx.export(
            "controlPlanePostgresServerName",
            controlPlanePostgres == null ? Output.ofNullable(null) : controlPlanePostgres.serverName
        );
        ctx.export(
            "controlPlanePostgresHost",
            controlPlanePostgres == null ? Output.ofNullable(null) : controlPlanePostgres.host
        );
        ctx.export(
            "controlPlanePostgresPort",
            controlPlanePostgres == null ? Output.ofNullable(null) : Output.of(controlPlanePostgres.port)
        );
        ctx.export(
            "controlPlanePostgresDatabase",
            controlPlanePostgres == null ? Output.ofNullable(null) : Output.of(controlPlanePostgres.databaseName)
        );
        ctx.export(
            "controlPlanePostgresAdmin",
            controlPlanePostgres == null ? Output.ofNullable(null) : Output.of(controlPlanePostgres.adminLogin)
        );
        ctx.export(
            "controlPlanePostgresConnectionUsername",
            controlPlanePostgres == null ? Output.ofNullable(null) : controlPlanePostgres.connectionUsername
        );
        ctx.export(
            "controlPlanePostgresPassword",
            controlPlanePostgres == null ? Output.ofNullable(null) : controlPlanePostgres.password
        );
        ctx.export(
            "controlPlaneDatabaseUrl",
            controlPlanePostgres == null ? Output.ofNullable(null) : controlPlanePostgres.databaseUrl
        );
    }

    private DeploymentOutput createTargetDeployment(TargetConfig target, int index) {
        String normalizedTargetId = normalizeName(target.id, "target-" + (index + 1), 40);
        String tenantId = normalizeNullable(target.tenantId).orElse("tenant-%03d".formatted(index + 1));
        String targetGroup = normalizeNullable(target.targetGroup)
            .or(() -> Optional.ofNullable(target.tags).map(tags -> tags.get("ring")).flatMap(Main::normalizeNullable))
            .orElse("prod");
        String region = normalizeNullable(target.region).orElse(defaultLocation);
        String tier = normalizeNullable(target.tier)
            .or(() -> Optional.ofNullable(target.tags).map(tags -> tags.get("tier")).flatMap(Main::normalizeNullable))
            .orElse("standard");
        String environment = normalizeNullable(target.environment)
            .or(() -> Optional.ofNullable(target.tags).map(tags -> tags.get("environment")).flatMap(Main::normalizeNullable))
            .orElse("demo");

        Provider provider = getProvider(target.subscriptionId);
        SubscriptionContext context = getOrCreateSubscriptionContext(provider, target.subscriptionId, region,
            resolveAuthorizationPrincipalObjectId(target.subscriptionId));

        String managedApplicationName = normalizeNullable(target.managedApplicationName)
            .orElse(normalizeName(managedAppNamePrefix + "-" + normalizedTargetId, "mappo-ma-" + normalizedTargetId, 60));
        String managedResourceGroupName = normalizeNullable(target.managedResourceGroupName)
            .orElse(normalizeName(managedResourceGroupPrefix + "-" + normalizedTargetId, "rg-mappo-ma-mrg-" + normalizedTargetId, 90));
        String managedEnvironmentName = normalizeName(managedEnvironmentNamePrefix + "-" + normalizedTargetId, "cae-mappo-ma-" + normalizedTargetId, 32);
        String containerAppName = normalizeNullable(target.containerAppName)
            .orElse(normalizeName(containerAppNamePrefix + "-" + normalizedTargetId, "ca-mappo-ma-" + normalizedTargetId, 32));

        Output<String> managedResourceGroupId = Output.format(
            "/subscriptions/%s/resourceGroups/%s",
            target.subscriptionId,
            managedResourceGroupName
        );

        Output<String> containerAppResourceId = Output.format(
            "/subscriptions/%s/resourceGroups/%s/providers/Microsoft.App/containerApps/%s",
            target.subscriptionId,
            managedResourceGroupName,
            containerAppName
        );

        Map<String, String> tags = new LinkedHashMap<>();
        if (target.tags != null) {
            tags.putAll(target.tags);
        }
        tags.put("managedBy", "pulumi");
        tags.put("system", "mappo");
        tags.put("ring", targetGroup);
        tags.put("region", region);
        tags.put("tier", tier);
        tags.put("environment", environment);
        tags.put("tenantId", tenantId);
        tags.put("targetId", target.id);

        Map<String, Object> parameters = linkedMapOf(
            "location", linkedMapOf("value", region),
            "managedEnvironmentName", linkedMapOf("value", managedEnvironmentName),
            "containerAppName", linkedMapOf("value", containerAppName),
            "containerImage", linkedMapOf("value", defaultImage),
            "softwareVersion", linkedMapOf("value", defaultSoftwareVersion),
            "dataModelVersion", linkedMapOf("value", defaultDataModelVersion),
            "targetGroup", linkedMapOf("value", targetGroup),
            "tenantId", linkedMapOf("value", tenantId),
            "targetId", linkedMapOf("value", target.id),
            "tier", linkedMapOf("value", tier),
            "environment", linkedMapOf("value", environment)
        );

        Application managedApplication = new Application(
            "managed-app-" + normalizedTargetId,
            ApplicationArgs.builder()
                .resourceGroupName(context.applicationResourceGroup.name())
                .applicationName(managedApplicationName)
                .applicationDefinitionId(context.definition.id())
                .kind("ServiceCatalog")
                .location(region)
                .managedResourceGroupId(managedResourceGroupId)
                .parameters(parameters)
                .tags(tags)
                .build(),
            CustomResourceOptions.builder().provider(provider).build()
        );

        return new DeploymentOutput(
            managedApplicationName,
            tenantId,
            target.subscriptionId,
            targetGroup,
            tier,
            environment,
            region,
            managedApplicationName,
            managedResourceGroupName,
            managedResourceGroupId,
            containerAppName,
            containerAppResourceId,
            managedApplication
        );
    }

    private Output<Map<String, Object>> buildInventoryRow(DeploymentOutput deployment) {
        return Output.tuple(
            deployment.managedApplication.id(),
            deployment.managedResourceGroupId,
            deployment.containerAppResourceId,
            deployment.managedApplication.outputs()
        ).applyValue(tuple -> {
            String managedApplicationId = tuple.t1;
            String managedResourceGroupId = tuple.t2;
            String containerAppResourceId = tuple.t3;
            Object appOutputs = tuple.t4;

            Map<String, Object> tags = linkedMapOf(
                "ring", deployment.targetGroup,
                "region", deployment.region,
                "environment", deployment.environment,
                "tier", deployment.tier
            );

            Map<String, Object> metadata = linkedMapOf(
                "managed_application_id", managedApplicationId,
                "managed_application_name", deployment.managedApplicationName,
                "managed_resource_group_id", managedResourceGroupId,
                "managed_resource_group_name", deployment.managedResourceGroupName,
                "container_app_name", deployment.containerAppName,
                "container_app_fqdn", extractManagedAppOutputValue(appOutputs, "containerAppFqdn")
            );

            return linkedMapOf(
                "id", deployment.id,
                "tenant_id", deployment.tenantId,
                "subscription_id", deployment.subscriptionId,
                "managed_app_id", containerAppResourceId,
                "tags", tags,
                "metadata", metadata
            );
        });
    }

    private ControlPlanePostgresResources createControlPlanePostgresResources() {
        if (!controlPlanePostgresProvisioningEnabled) {
            return null;
        }
        if (controlPlanePostgresAdminPassword == null) {
            throw new IllegalStateException(
                "Managed Postgres provisioning requested without admin password. "
                    + "Set mappo:controlPlanePostgresAdminPassword (secret)."
            );
        }

        Provider provider = getProvider(controlPlaneSubscriptionId);
        String subscriptionKeyValue = subscriptionKey(controlPlaneSubscriptionId);
        String resourceGroupName = normalizeName(
            controlPlaneResourceGroupPrefix + "-" + subscriptionKeyValue,
            "rg-mappo-control-plane-" + subscriptionKeyValue,
            90
        );
        String serverName = normalizeName(
            controlPlanePostgresServerNamePrefix + "-" + subscriptionKeyValue,
            "pg-mappo-" + subscriptionKeyValue,
            63
        );

        CustomResourceOptions withProvider = CustomResourceOptions.builder().provider(provider).build();

        ResourceGroup resourceGroup = new ResourceGroup(
            "control-plane-rg-" + subscriptionKeyValue,
            ResourceGroupArgs.builder()
                .resourceGroupName(resourceGroupName)
                .location(controlPlaneLocation)
                .tags(linkedMapOfString(
                    "managedBy", "pulumi",
                    "system", "mappo",
                    "scope", "control-plane"
                ))
                .build(),
            withProvider
        );

        Server server = new Server(
            "control-plane-postgres-" + subscriptionKeyValue,
            ServerArgs.builder()
                .resourceGroupName(resourceGroup.name())
                .serverName(serverName)
                .location(controlPlaneLocation)
                .createMode("Create")
                .administratorLogin(controlPlanePostgresAdminLogin)
                .administratorLoginPassword(controlPlanePostgresAdminPassword)
                .version(controlPlanePostgresVersion)
                .backup(BackupArgs.builder()
                    .backupRetentionDays(controlPlanePostgresBackupRetentionDays)
                    .geoRedundantBackup("Disabled")
                    .build())
                .highAvailability(HighAvailabilityArgs.builder()
                    .mode("Disabled")
                    .build())
                .network(NetworkArgs.builder()
                    .publicNetworkAccess(controlPlanePostgresPublicNetworkAccess ? "Enabled" : "Disabled")
                    .build())
                .sku(SkuArgs.builder()
                    .name(controlPlanePostgresSkuName)
                    .tier(inferPostgresSkuTier(controlPlanePostgresSkuName))
                    .build())
                .storage(StorageArgs.builder()
                    .storageSizeGB(controlPlanePostgresStorageSizeGb)
                    .autoGrow("Enabled")
                    .build())
                .tags(linkedMapOfString(
                    "managedBy", "pulumi",
                    "system", "mappo",
                    "scope", "control-plane-postgres"
                ))
                .build(),
            CustomResourceOptions.builder()
                .provider(provider)
                .customTimeouts(CustomTimeouts.builder()
                    .create(Duration.ofMinutes(30))
                    .update(Duration.ofMinutes(30))
                    .delete(Duration.ofMinutes(30))
                    .build())
                .build()
        );

        Database database = new Database(
            "control-plane-postgres-db-" + subscriptionKeyValue,
            DatabaseArgs.builder()
                .resourceGroupName(resourceGroup.name())
                .serverName(server.name())
                .databaseName(controlPlanePostgresDatabaseName)
                .charset("UTF8")
                .collation("en_US.utf8")
                .build(),
            withProvider
        );

        new Configuration(
            "control-plane-postgres-ext-" + subscriptionKeyValue,
            ConfigurationArgs.builder()
                .resourceGroupName(resourceGroup.name())
                .serverName(server.name())
                .configurationName("azure.extensions")
                .source("user-override")
                .value("PGCRYPTO")
                .build(),
            CustomResourceOptions.builder()
                .provider(provider)
                .dependsOn(database)
                .customTimeouts(CustomTimeouts.builder()
                    .create(Duration.ofMinutes(10))
                    .update(Duration.ofMinutes(10))
                    .build())
                .build()
        );

        if (controlPlanePostgresPublicNetworkAccess) {
            if (controlPlanePostgresAllowAzureServices) {
                new FirewallRule(
                    "control-plane-postgres-fw-azure-" + subscriptionKeyValue,
                    FirewallRuleArgs.builder()
                        .resourceGroupName(resourceGroup.name())
                        .serverName(server.name())
                        .firewallRuleName("allow-azure-services")
                        .startIpAddress("0.0.0.0")
                        .endIpAddress("0.0.0.0")
                        .build(),
                    withProvider
                );
            }

            for (int i = 0; i < controlPlanePostgresFirewallIpRanges.size(); i++) {
                FirewallIpRange range = controlPlanePostgresFirewallIpRanges.get(i);
                new FirewallRule(
                    "control-plane-postgres-fw-custom-" + subscriptionKeyValue + "-" + (i + 1),
                    FirewallRuleArgs.builder()
                        .resourceGroupName(resourceGroup.name())
                        .serverName(server.name())
                        .firewallRuleName("allow-ip-" + (i + 1))
                        .startIpAddress(range.startIpAddress)
                        .endIpAddress(range.endIpAddress)
                        .build(),
                    withProvider
                );
            }
        }

        Output<String> host = server.fullyQualifiedDomainName();
        Output<String> connectionUsername = Output.of(controlPlanePostgresAdminLogin);
        Output<String> databaseUrl = Output.tuple(connectionUsername, controlPlanePostgresAdminPassword, host, database.name())
            .applyValue(tuple -> "postgresql+psycopg://"
                + urlEncode(tuple.t1)
                + ":"
                + urlEncode(tuple.t2)
                + "@"
                + tuple.t3
                + ":5432/"
                + tuple.t4
                + "?sslmode=require");

        return new ControlPlanePostgresResources(
            controlPlaneSubscriptionId,
            resourceGroup.name(),
            server.name(),
            host,
            5432,
            controlPlanePostgresDatabaseName,
            controlPlanePostgresAdminLogin,
            connectionUsername,
            controlPlanePostgresAdminPassword,
            databaseUrl
        );
    }

    private SubscriptionContext getOrCreateSubscriptionContext(
        Provider provider,
        String subscriptionId,
        String location,
        String authorizationPrincipalObjectId
    ) {
        SubscriptionContext existing = contextBySubscription.get(subscriptionId);
        if (existing != null) {
            if (!Objects.equals(existing.location, location)) {
                ctx.log().warn("Targets in one subscription use multiple regions for " + subscriptionId + ".");
            }
            return existing;
        }

        String key = subscriptionKey(subscriptionId);
        String definitionResourceGroupName = normalizeName(
            definitionResourceGroupPrefix + "-" + key,
            "rg-mappo-ma-def-" + key,
            90
        );
        String applicationResourceGroupName = normalizeName(
            applicationResourceGroupPrefix + "-" + key,
            "rg-mappo-ma-apps-" + key,
            90
        );
        String definitionName = normalizeName(
            definitionNamePrefix + "-" + key,
            "mappo-ma-def-" + key,
            64
        );

        CustomResourceOptions withProvider = CustomResourceOptions.builder().provider(provider).build();

        ResourceGroup definitionResourceGroup = new ResourceGroup(
            "managed-app-definition-rg-" + key,
            ResourceGroupArgs.builder()
                .resourceGroupName(definitionResourceGroupName)
                .location(location)
                .tags(linkedMapOfString(
                    "managedBy", "pulumi",
                    "system", "mappo",
                    "scope", "managed-app-definition"
                ))
                .build(),
            withProvider
        );

        ResourceGroup applicationResourceGroup = new ResourceGroup(
            "managed-app-rg-" + key,
            ResourceGroupArgs.builder()
                .resourceGroupName(applicationResourceGroupName)
                .location(location)
                .tags(linkedMapOfString(
                    "managedBy", "pulumi",
                    "system", "mappo",
                    "scope", "managed-app-instances"
                ))
                .build(),
            withProvider
        );

        ApplicationDefinition definition = new ApplicationDefinition(
            "managed-app-definition-" + key,
            ApplicationDefinitionArgs.builder()
                .resourceGroupName(definitionResourceGroup.name())
                .applicationDefinitionName(definitionName)
                .displayName("MAPPO Managed App Definition (" + key + ")")
                .description("MAPPO service catalog managed application definition for marketplace-accurate demo rollout.")
                .location(location)
                .lockLevel(ApplicationLockLevel.None)
                .deploymentPolicy(ApplicationDeploymentPolicyArgs.builder()
                    .deploymentMode("Incremental")
                    .build())
                .managementPolicy(ApplicationManagementPolicyArgs.builder()
                    .mode("Managed")
                    .build())
                .authorizations(List.of(
                    ApplicationAuthorizationArgs.builder()
                        .principalId(authorizationPrincipalObjectId)
                        .roleDefinitionId(publisherRoleDefinitionId)
                        .build()
                ))
                .createUiDefinition(createUiDefinition)
                .mainTemplate(managedAppMainTemplate)
                .tags(linkedMapOfString(
                    "managedBy", "pulumi",
                    "system", "mappo"
                ))
                .build(),
            withProvider
        );

        SubscriptionContext context = new SubscriptionContext(provider, location, definitionResourceGroup, applicationResourceGroup, definition);
        contextBySubscription.put(subscriptionId, context);
        return context;
    }

    private Provider getProvider(String subscriptionId) {
        Provider existing = providersBySubscription.get(subscriptionId);
        if (existing != null) {
            return existing;
        }

        String key = subscriptionKey(subscriptionId);
        Provider provider = new Provider(
            "provider-sub-" + key,
            ProviderArgs.builder().subscriptionId(subscriptionId).build()
        );
        providersBySubscription.put(subscriptionId, provider);
        return provider;
    }

    private static Map<String, Object> buildCreateUiDefinition() {
        return linkedMapOf(
            "$schema", "https://schema.management.azure.com/schemas/0.1.2-preview/CreateUIDefinition.MultiVm.json#",
            "handler", "Microsoft.Azure.CreateUIDef",
            "version", "0.1.2-preview",
            "parameters", linkedMapOf(
                "basics", List.of(),
                "steps", List.of(),
                "outputs", linkedMapOf()
            )
        );
    }

    private static Map<String, Object> buildManagedAppMainTemplate(double defaultContainerCpu, String defaultContainerMemory) {
        Map<String, Object> parameters = linkedMapOf(
            "location", linkedMapOf("type", "string"),
            "managedEnvironmentName", linkedMapOf("type", "string"),
            "containerAppName", linkedMapOf("type", "string"),
            "containerImage", linkedMapOf("type", "string"),
            "softwareVersion", linkedMapOf("type", "string"),
            "dataModelVersion", linkedMapOf("type", "string"),
            "targetGroup", linkedMapOf("type", "string"),
            "tenantId", linkedMapOf("type", "string"),
            "targetId", linkedMapOf("type", "string"),
            "tier", linkedMapOf("type", "string"),
            "environment", linkedMapOf("type", "string")
        );

        Map<String, Object> managedEnvironment = linkedMapOf(
            "type", "Microsoft.App/managedEnvironments",
            "apiVersion", "2024-03-01",
            "name", "[parameters('managedEnvironmentName')]",
            "location", "[parameters('location')]",
            "properties", linkedMapOf(
                "appLogsConfiguration", linkedMapOf()
            ),
            "tags", linkedMapOf(
                "managedBy", "managedApp",
                "system", "mappo"
            )
        );

        Map<String, Object> containerApp = linkedMapOf(
            "type", "Microsoft.App/containerApps",
            "apiVersion", "2024-03-01",
            "name", "[parameters('containerAppName')]",
            "location", "[parameters('location')]",
            "dependsOn", List.of(
                "[resourceId('Microsoft.App/managedEnvironments', parameters('managedEnvironmentName'))]"
            ),
            "tags", linkedMapOf(
                "managedBy", "managedApp",
                "system", "mappo",
                "ring", "[parameters('targetGroup')]",
                "tenantId", "[parameters('tenantId')]",
                "targetId", "[parameters('targetId')]",
                "tier", "[parameters('tier')]",
                "environment", "[parameters('environment')]"
            ),
            "properties", linkedMapOf(
                "managedEnvironmentId", "[resourceId('Microsoft.App/managedEnvironments', parameters('managedEnvironmentName'))]",
                "configuration", linkedMapOf(
                    "ingress", linkedMapOf(
                        "external", true,
                        "targetPort", 8080,
                        "transport", "Auto"
                    )
                ),
                "template", linkedMapOf(
                    "containers", List.of(linkedMapOf(
                        "name", "app",
                        "image", "[parameters('containerImage')]",
                        "command", List.of("python"),
                        "args", List.of("-c", TARGET_DEMO_SERVER_SCRIPT),
                        "env", List.of(
                            linkedMapOf("name", "MAPPO_SOFTWARE_VERSION", "value", "[parameters('softwareVersion')]") ,
                            linkedMapOf("name", "MAPPO_DATA_MODEL_VERSION", "value", "[parameters('dataModelVersion')]") ,
                            linkedMapOf("name", "MAPPO_FEATURE_FLAG", "value", "[parameters('dataModelVersion')]")
                        ),
                        "resources", linkedMapOf(
                            "cpu", defaultContainerCpu,
                            "memory", defaultContainerMemory
                        )
                    )),
                    "scale", linkedMapOf(
                        "minReplicas", 1,
                        "maxReplicas", 1
                    )
                )
            )
        );

        Map<String, Object> outputs = linkedMapOf(
            "containerAppResourceId", linkedMapOf(
                "type", "string",
                "value", "[resourceId('Microsoft.App/containerApps', parameters('containerAppName'))]"
            )
        );

        return linkedMapOf(
            "$schema", "https://schema.management.azure.com/schemas/2019-04-01/deploymentTemplate.json#",
            "contentVersion", "1.0.0.0",
            "parameters", parameters,
            "resources", List.of(managedEnvironment, containerApp),
            "outputs", outputs
        );
    }

    private static String extractManagedAppOutputValue(Object outputs, String key) {
        if (!(outputs instanceof Map<?, ?> map)) {
            return "";
        }
        Object row = map.get(key);
        if (row instanceof String value) {
            return value;
        }
        if (row instanceof Map<?, ?> rowMap) {
            Object value = rowMap.get("value");
            if (value instanceof String stringValue) {
                return stringValue;
            }
        }
        return "";
    }

    private static Optional<String> optionalConfigWithEnvFallback(Config cfg, String configKey, String envKey) {
        String fromConfig = cfg.get(configKey).orElse("").trim();
        if (!fromConfig.isEmpty()) {
            return Optional.of(fromConfig);
        }
        String fromEnv = Optional.ofNullable(System.getenv(envKey)).orElse("").trim();
        if (!fromEnv.isEmpty()) {
            return Optional.of(fromEnv);
        }
        return Optional.empty();
    }

    private static Optional<Output<String>> optionalSecretConfigWithEnvFallback(Config cfg, String configKey, String envKey) {
        Optional<String> value = optionalConfigWithEnvFallback(cfg, configKey, envKey);
        return value.map(Output::ofSecret);
    }

    private static boolean booleanConfigWithEnvFallback(Config cfg, String configKey, String envKey, boolean defaultValue) {
        Optional<Boolean> fromConfig = cfg.getBoolean(configKey);
        if (fromConfig.isPresent()) {
            return fromConfig.get();
        }
        String fromEnv = Optional.ofNullable(System.getenv(envKey)).orElse("").trim().toLowerCase(Locale.ROOT);
        if (fromEnv.isEmpty()) {
            return defaultValue;
        }
        if (Set.of("1", "true", "yes", "on").contains(fromEnv)) {
            return true;
        }
        if (Set.of("0", "false", "no", "off").contains(fromEnv)) {
            return false;
        }
        throw new IllegalArgumentException(
            "Invalid boolean for " + configKey + "/" + envKey + ". Use one of: 1,true,yes,on,0,false,no,off."
        );
    }

    private static int numberConfigWithEnvFallback(Config cfg, String configKey, String envKey, int defaultValue) {
        Optional<Integer> fromConfig = cfg.getInteger(configKey);
        if (fromConfig.isPresent()) {
            return fromConfig.get();
        }
        String fromEnv = Optional.ofNullable(System.getenv(envKey)).orElse("").trim();
        if (fromEnv.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(fromEnv);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid numeric value for " + configKey + "/" + envKey + ": '" + fromEnv + "'.", ex);
        }
    }

    private static List<FirewallIpRange> parseFirewallIpRanges(Config cfg, String configKey, String envKey) {
        List<String> fromConfig = parseConfigObjectList(cfg, configKey, String.class).orElse(List.of());

        String fromEnvRaw = Optional.ofNullable(System.getenv(envKey)).orElse("").trim();
        List<String> fromEnv = fromEnvRaw.isEmpty()
            ? List.of()
            : List.of(fromEnvRaw.split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();

        List<String> raw = !fromConfig.isEmpty() ? fromConfig : fromEnv;
        List<FirewallIpRange> ranges = new ArrayList<>();
        for (String value : raw) {
            String trimmed = value.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("-", 2);
            String start = parts[0].trim();
            validateIpv4(start, configKey + "/" + envKey);
            String end = parts.length == 1 || parts[1].isBlank() ? start : parts[1].trim();
            validateIpv4(end, configKey + "/" + envKey);
            ranges.add(new FirewallIpRange(start, end));
        }
        return ranges;
    }

    private static void validateIpv4(String value, String source) {
        String[] segments = value.split("\\.");
        if (segments.length != 4) {
            throw new IllegalArgumentException("Invalid IPv4 address in " + source + ": '" + value + "'.");
        }
        for (String segment : segments) {
            if (!IPV4_SEGMENT_PATTERN.matcher(segment).matches()) {
                throw new IllegalArgumentException("Invalid IPv4 address in " + source + ": '" + value + "'.");
            }
            int numeric = Integer.parseInt(segment);
            if (numeric < 0 || numeric > 255) {
                throw new IllegalArgumentException("Invalid IPv4 address in " + source + ": '" + value + "'.");
            }
        }
    }

    private static String inferPostgresSkuTier(String skuName) {
        String normalized = skuName.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("standard_b") || normalized.startsWith("b_") || normalized.startsWith("burstable")) {
            return "Burstable";
        }
        if (normalized.startsWith("standard_d") || normalized.startsWith("standard_ds")
            || normalized.startsWith("gp_") || normalized.startsWith("generalpurpose")) {
            return "GeneralPurpose";
        }
        if (normalized.startsWith("standard_e") || normalized.startsWith("mo_") || normalized.startsWith("memoryoptimized")) {
            return "MemoryOptimized";
        }
        throw new IllegalArgumentException(
            "Unable to infer PostgreSQL sku tier from '" + skuName + "'. "
                + "Set a known Burstable/GeneralPurpose/MemoryOptimized SKU."
        );
    }

    private static String normalizePostgresLogin(String value) {
        String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (normalized.length() < 3) {
            return ("mappoadmin" + normalized).substring(0, Math.min(16, "mappoadmin".length() + normalized.length()));
        }
        return normalized.substring(0, Math.min(63, normalized.length()));
    }

    private static String resolveDemoSubscriptionId(String configuredValue) {
        if (configuredValue != null && !configuredValue.trim().isEmpty()) {
            return configuredValue.trim();
        }

        for (String envKey : List.of("ARM_SUBSCRIPTION_ID", "AZURE_SUBSCRIPTION_ID", "PULUMI_AZURE_NATIVE_SUBSCRIPTION_ID")) {
            String value = Optional.ofNullable(System.getenv(envKey)).orElse("").trim();
            if (!value.isEmpty()) {
                return value;
            }
        }

        Optional<String> detected = detectActiveAzSubscriptionId();
        if (detected.isPresent()) {
            return detected.get();
        }

        throw new IllegalStateException(
            "Unable to determine demo subscription ID. Set mappo:demoSubscriptionId or ARM_SUBSCRIPTION_ID."
        );
    }

    private static Optional<String> detectActiveAzSubscriptionId() {
        try {
            Process process = new ProcessBuilder("az", "account", "show", "--query", "id", "-o", "tsv")
                .redirectErrorStream(true)
                .start();

            String line;
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                line = reader.readLine();
            }
            int exit = process.waitFor();
            if (exit != 0 || line == null || line.trim().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(line.trim());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String resolveAuthorizationPrincipalObjectId(String subscriptionId) {
        String mappedValue = publisherPrincipalObjectIds.get(subscriptionId);
        if (mappedValue != null && !mappedValue.isBlank()) {
            return mappedValue;
        }
        if (publisherPrincipalObjectId != null && !publisherPrincipalObjectId.isBlank()) {
            return publisherPrincipalObjectId;
        }
        throw new IllegalStateException(
            "Missing managed-app authorization principal object ID for subscription " + subscriptionId
                + ". Set mappo:publisherPrincipalObjectIds[\"" + subscriptionId + "\"]"
                + " or mappo:publisherPrincipalObjectId."
        );
    }

    private static Map<String, String> normalizePrincipalMap(Map<String, String> value) {
        if (value == null) {
            return Map.of();
        }
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : value.entrySet()) {
            String subscriptionId = Optional.ofNullable(entry.getKey()).orElse("").trim();
            String principalObjectId = Optional.ofNullable(entry.getValue()).orElse("").trim();
            if (!subscriptionId.isEmpty() && !principalObjectId.isEmpty()) {
                result.put(subscriptionId, principalObjectId);
            }
        }
        return result;
    }

    private static String parseProfileName(String value) {
        if ("empty".equals(value) || "demo10".equals(value)) {
            return value;
        }
        throw new IllegalArgumentException("Unknown mappo:targetProfile '" + value + "'. Expected one of: empty, demo10.");
    }

    private static List<TargetConfig> targetsFromProfile(String profile, String defaultLocation, String defaultSubscriptionId) {
        if ("empty".equals(profile)) {
            return List.of();
        }
        if (!"demo10".equals(profile)) {
            throw new IllegalStateException("Unhandled profile value: " + profile);
        }

        List<DemoTargetDefinition> definitions = List.of(
            new DemoTargetDefinition("target-01", "tenant-001", "canary", "eastus", "gold"),
            new DemoTargetDefinition("target-02", "tenant-002", "canary", "eastus", "gold"),
            new DemoTargetDefinition("target-03", "tenant-003", "prod", "eastus", "gold"),
            new DemoTargetDefinition("target-04", "tenant-004", "prod", "eastus", "gold"),
            new DemoTargetDefinition("target-05", "tenant-005", "prod", "eastus", "silver"),
            new DemoTargetDefinition("target-06", "tenant-006", "prod", "eastus", "silver"),
            new DemoTargetDefinition("target-07", "tenant-007", "prod", "eastus", "silver"),
            new DemoTargetDefinition("target-08", "tenant-008", "prod", "eastus", "silver"),
            new DemoTargetDefinition("target-09", "tenant-009", "prod", "eastus", "bronze"),
            new DemoTargetDefinition("target-10", "tenant-010", "prod", "eastus", "bronze")
        );

        List<TargetConfig> targets = new ArrayList<>();
        for (DemoTargetDefinition definition : definitions) {
            TargetConfig target = new TargetConfig();
            target.id = definition.id;
            target.tenantId = definition.tenantId;
            target.subscriptionId = defaultSubscriptionId;
            target.targetGroup = definition.targetGroup;
            target.region = normalizeNullable(definition.region).orElse(defaultLocation);
            target.tier = definition.tier;
            target.environment = "demo";
            target.tags = linkedMapOfString(
                "tier", definition.tier,
                "environment", "demo"
            );
            targets.add(target);
        }
        return targets;
    }

    private static void assertUniqueTargetIds(List<TargetConfig> targetConfigs) {
        Set<String> seen = new LinkedHashSet<>();
        for (TargetConfig target : targetConfigs) {
            String id = normalizeNullable(target.id).orElseThrow(() ->
                new IllegalArgumentException("Every target config entry must include a non-empty id.")
            );
            if (normalizeNullable(target.subscriptionId).isEmpty()) {
                throw new IllegalArgumentException("Target " + id + " must include a non-empty subscriptionId.");
            }
            if (!seen.add(id)) {
                throw new IllegalArgumentException("Duplicate target id detected in mappo:targets: " + id);
            }
        }
    }

    private static String normalizeName(String value, String fallback, int maxLen) {
        String source = Optional.ofNullable(value).orElse(fallback).toLowerCase(Locale.ROOT);
        String normalized = NON_ALPHANUMERIC_DASH.matcher(source).replaceAll("-");
        normalized = DASH_COLLAPSE.matcher(normalized).replaceAll("-");
        normalized = trimDashes(normalized);

        String candidate = normalized.isEmpty() ? fallback : normalized;
        if (candidate.length() > maxLen) {
            candidate = candidate.substring(0, maxLen);
        }
        candidate = trimTrailingDashes(candidate);
        return candidate.isEmpty() ? fallback.substring(0, Math.min(maxLen, fallback.length())) : candidate;
    }

    private static String trimDashes(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '-') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '-') {
            end--;
        }
        return value.substring(start, end);
    }

    private static String trimTrailingDashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '-') {
            end--;
        }
        return value.substring(0, end);
    }

    private static String subscriptionKey(String subscriptionId) {
        String normalized = subscriptionId.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        return normalized.substring(0, Math.min(8, normalized.length()));
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static Optional<String> normalizeNullable(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(normalized);
    }

    private static <T> Optional<List<T>> parseConfigObjectList(Config cfg, String key, Class<T> itemClass) {
        try {
            Optional<List<T>> parsed = cfg.getObject(key, TypeShape.list(itemClass));
            if (parsed.isPresent()) {
                return parsed;
            }
        } catch (Exception ignored) {
            // Fallback to JSON string parse below.
        }

        Optional<String> raw = cfg.get(key).flatMap(Main::normalizeNullable);
        if (raw.isEmpty()) {
            return Optional.empty();
        }

        Type listType = TypeToken.getParameterized(List.class, itemClass).getType();
        List<T> value = GSON.fromJson(raw.get(), listType);
        return Optional.ofNullable(value);
    }

    private static Map<String, String> parseConfigStringMap(Config cfg, String key) {
        try {
            Optional<Map<String, String>> parsed = cfg.getObject(key, TypeShape.map(String.class, String.class));
            if (parsed.isPresent()) {
                return parsed.get();
            }
        } catch (Exception ignored) {
            // Fallback to JSON string parse below.
        }

        Optional<String> raw = cfg.get(key).flatMap(Main::normalizeNullable);
        if (raw.isEmpty()) {
            return Map.of();
        }

        Type mapType = new TypeToken<Map<String, String>>() {}.getType();
        Map<String, String> value = GSON.fromJson(raw.get(), mapType);
        return value == null ? Map.of() : value;
    }

    private static Map<String, Object> linkedMapOf(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("linkedMapOf expects an even number of arguments.");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            String key = (String) keyValues[i];
            Object value = keyValues[i + 1];
            map.put(key, value);
        }
        return map;
    }

    private static Map<String, String> linkedMapOfString(String... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("linkedMapOfString expects an even number of arguments.");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    public static final class TargetConfig {
        public String id;
        public String tenantId;
        public String subscriptionId;
        public String targetGroup;
        public String region;
        public String tier;
        public String environment;
        public String managedApplicationName;
        public String managedResourceGroupName;
        public String containerAppName;
        public Map<String, String> environmentVariables;
        public Map<String, String> tags;
    }

    private record DemoTargetDefinition(String id, String tenantId, String targetGroup, String region, String tier) {
    }

    private static final class SubscriptionContext {
        private final Provider provider;
        private final String location;
        private final ResourceGroup definitionResourceGroup;
        private final ResourceGroup applicationResourceGroup;
        private final ApplicationDefinition definition;

        private SubscriptionContext(
            Provider provider,
            String location,
            ResourceGroup definitionResourceGroup,
            ResourceGroup applicationResourceGroup,
            ApplicationDefinition definition
        ) {
            this.provider = provider;
            this.location = location;
            this.definitionResourceGroup = definitionResourceGroup;
            this.applicationResourceGroup = applicationResourceGroup;
            this.definition = definition;
        }
    }

    private static final class DeploymentOutput {
        private final String id;
        private final String tenantId;
        private final String subscriptionId;
        private final String targetGroup;
        private final String tier;
        private final String environment;
        private final String region;
        private final String managedApplicationName;
        private final String managedResourceGroupName;
        private final Output<String> managedResourceGroupId;
        private final String containerAppName;
        private final Output<String> containerAppResourceId;
        private final Application managedApplication;

        private DeploymentOutput(
            String id,
            String tenantId,
            String subscriptionId,
            String targetGroup,
            String tier,
            String environment,
            String region,
            String managedApplicationName,
            String managedResourceGroupName,
            Output<String> managedResourceGroupId,
            String containerAppName,
            Output<String> containerAppResourceId,
            Application managedApplication
        ) {
            this.id = id;
            this.tenantId = tenantId;
            this.subscriptionId = subscriptionId;
            this.targetGroup = targetGroup;
            this.tier = tier;
            this.environment = environment;
            this.region = region;
            this.managedApplicationName = managedApplicationName;
            this.managedResourceGroupName = managedResourceGroupName;
            this.managedResourceGroupId = managedResourceGroupId;
            this.containerAppName = containerAppName;
            this.containerAppResourceId = containerAppResourceId;
            this.managedApplication = managedApplication;
        }
    }

    private static final class ControlPlanePostgresResources {
        private final String subscriptionId;
        private final Output<String> resourceGroupName;
        private final Output<String> serverName;
        private final Output<String> host;
        private final int port;
        private final String databaseName;
        private final String adminLogin;
        private final Output<String> connectionUsername;
        private final Output<String> password;
        private final Output<String> databaseUrl;

        private ControlPlanePostgresResources(
            String subscriptionId,
            Output<String> resourceGroupName,
            Output<String> serverName,
            Output<String> host,
            int port,
            String databaseName,
            String adminLogin,
            Output<String> connectionUsername,
            Output<String> password,
            Output<String> databaseUrl
        ) {
            this.subscriptionId = subscriptionId;
            this.resourceGroupName = resourceGroupName;
            this.serverName = serverName;
            this.host = host;
            this.port = port;
            this.databaseName = databaseName;
            this.adminLogin = adminLogin;
            this.connectionUsername = connectionUsername;
            this.password = password;
            this.databaseUrl = databaseUrl;
        }
    }

    private record FirewallIpRange(String startIpAddress, String endIpAddress) {
    }
}
