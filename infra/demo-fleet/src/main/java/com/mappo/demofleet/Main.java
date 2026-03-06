package com.mappo.demofleet;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.pulumi.Config;
import com.pulumi.Context;
import com.pulumi.Pulumi;
import com.pulumi.azurenative.Provider;
import com.pulumi.azurenative.ProviderArgs;
import com.pulumi.azurenative.app.ContainerApp;
import com.pulumi.azurenative.app.ContainerAppArgs;
import com.pulumi.azurenative.app.ManagedEnvironment;
import com.pulumi.azurenative.app.ManagedEnvironmentArgs;
import com.pulumi.azurenative.app.inputs.AppLogsConfigurationArgs;
import com.pulumi.azurenative.app.inputs.ConfigurationArgs;
import com.pulumi.azurenative.app.inputs.ContainerArgs;
import com.pulumi.azurenative.app.inputs.ContainerResourcesArgs;
import com.pulumi.azurenative.app.inputs.EnvironmentVarArgs;
import com.pulumi.azurenative.app.inputs.IngressArgs;
import com.pulumi.azurenative.app.inputs.LogAnalyticsConfigurationArgs;
import com.pulumi.azurenative.app.inputs.ScaleArgs;
import com.pulumi.azurenative.app.inputs.TemplateArgs;
import com.pulumi.azurenative.operationalinsights.OperationalinsightsFunctions;
import com.pulumi.azurenative.operationalinsights.Workspace;
import com.pulumi.azurenative.operationalinsights.WorkspaceArgs;
import com.pulumi.azurenative.operationalinsights.inputs.GetWorkspaceSharedKeysArgs;
import com.pulumi.azurenative.operationalinsights.inputs.WorkspaceSkuArgs;
import com.pulumi.azurenative.resources.ResourceGroup;
import com.pulumi.azurenative.resources.ResourceGroupArgs;
import com.pulumi.core.Output;
import com.pulumi.core.TypeShape;
import com.pulumi.deployment.InvokeOutputOptions;
import com.pulumi.resources.CustomResourceOptions;

import java.lang.reflect.Type;
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
    private static final Gson GSON = new Gson();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern NON_ALPHANUMERIC_DASH = Pattern.compile("[^a-z0-9-]");
    private static final Pattern DASH_COLLAPSE = Pattern.compile("-+");

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
        "ThreadingHTTPServer(('0.0.0.0', port), Handler).serve_forever()"
    ));

    private final Context ctx;
    private final Config config;

    private final String stackToken;
    private final String defaultLocation;
    private final String defaultImage;
    private final String defaultSoftwareVersion;
    private final String defaultDataModelVersion;
    private final double defaultCpu;
    private final String defaultMemory;

    private final String environmentResourceGroupPrefix;
    private final String logWorkspaceNamePrefix;
    private final String managedEnvironmentNamePrefix;
    private final String targetResourceGroupPrefix;
    private final String containerAppNamePrefix;

    private final Map<String, Provider> providersBySubscription = new HashMap<>();
    private final Map<String, SubscriptionContext> contextBySubscription = new HashMap<>();

    private Main(Context ctx) {
        this.ctx = ctx;
        this.config = Config.of("demoFleet");
        this.stackToken = normalizeName(ctx.stackName(), "demo", 20);

        this.defaultLocation = config.get("defaultLocation").orElse("eastus");
        this.defaultImage = config.get("defaultImage").orElse("docker.io/library/python:3.11-alpine");
        this.defaultSoftwareVersion = config.get("defaultSoftwareVersion").orElse("2026.02.20.1");
        this.defaultDataModelVersion = config.get("defaultDataModelVersion").orElse("1");
        this.defaultCpu = config.getDouble("defaultCpu").orElse(0.25);
        this.defaultMemory = config.get("defaultMemory").orElse("0.5Gi");

        this.environmentResourceGroupPrefix = config.get("environmentResourceGroupPrefix").orElse("rg-mappo-demo-fleet");
        this.logWorkspaceNamePrefix = config.get("logWorkspaceNamePrefix").orElse("law-mappo-demo-fleet");
        this.managedEnvironmentNamePrefix = config.get("managedEnvironmentNamePrefix").orElse("cae-mappo-demo-fleet");
        this.targetResourceGroupPrefix = config.get("targetResourceGroupPrefix").orElse("rg-mappo-demo-target");
        this.containerAppNamePrefix = config.get("containerAppNamePrefix").orElse("ca-mappo-demo-target");
    }

    public static void main(String[] args) {
        Pulumi.run(ctx -> new Main(ctx).run());
    }

    private void run() {
        List<DemoFleetTargetConfig> targets = parseConfigObjectList(config, "targets", DemoFleetTargetConfig.class).orElse(List.of());
        assertUniqueTargetIds(targets);

        if (targets.isEmpty()) {
            ctx.log().warn("No demo fleet targets configured. Set demoFleet:targets in your stack config.");
        }

        List<DeploymentOutput> deployments = new ArrayList<>();
        for (int i = 0; i < targets.size(); i++) {
            deployments.add(createDeployment(targets.get(i), i));
        }

        List<Output<Map<String, Object>>> rows = deployments.stream().map(this::buildInventoryRow).toList();
        Output<List<Map<String, Object>>> targetInventory = Output.all(rows);

        ctx.export("targetCount", deployments.size());
        ctx.export("mappoTargetInventory", targetInventory);
        ctx.export("mappoTargetInventoryJson", targetInventory.applyValue(value -> PRETTY_GSON.toJson(value)));
        ctx.export("tenantBySubscription", buildTenantBySubscriptionMap(targets));
    }

    private DeploymentOutput createDeployment(DemoFleetTargetConfig target, int index) {
        String region = normalizeTagValue(target.region, defaultLocation);
        String targetGroup = normalizeTagValue(target.targetGroup, "prod");
        String environmentTag = normalizeTagValue(target.environment, "demo");
        String tier = normalizeTagValue(target.tier, "standard");
        String customerName = normalizeNullableValue(target.customerName);

        Provider provider = getProvider(target.subscriptionId);
        SubscriptionContext subscriptionContext = getOrCreateSubscriptionContext(provider, target.subscriptionId, region);

        String targetToken = normalizeName(target.id, "target-" + (index + 1), 28);
        String managedResourceGroupName = normalizeName(
            targetResourceGroupPrefix + "-" + targetToken,
            "rg-mappo-demo-target-" + targetToken,
            90
        );
        String containerAppName = normalizeName(
            containerAppNamePrefix + "-" + targetToken,
            "ca-mappo-demo-target-" + targetToken,
            32
        );
        String managedApplicationId = normalizeNullableResourceId(target.managedApplicationId);

        ResourceGroup managedResourceGroup = new ResourceGroup(
            "demo-fleet-target-rg-" + targetToken,
            ResourceGroupArgs.builder()
                .resourceGroupName(managedResourceGroupName)
                .location(region)
                .tags(linkedMapOfString(
                    "managedBy", "pulumi",
                    "system", "mappo",
                    "scope", "demo-fleet-target",
                    "targetId", target.id,
                    "ring", targetGroup,
                    "environment", environmentTag,
                    "tier", tier
                ))
                .build(),
            CustomResourceOptions.builder().provider(provider).build()
        );

        Map<String, String> mergedTags = new LinkedHashMap<>();
        mergedTags.put("managedBy", "pulumi");
        mergedTags.put("system", "mappo");
        mergedTags.put("scope", "demo-fleet-target");
        mergedTags.put("targetId", target.id);
        mergedTags.put("ring", targetGroup);
        mergedTags.put("region", region);
        mergedTags.put("environment", environmentTag);
        mergedTags.put("tier", tier);
        mergedTags.put("tenantId", target.tenantId);
        if (target.tags != null) {
            mergedTags.putAll(target.tags);
        }
        if (customerName != null) {
            mergedTags.put("customer", customerName);
        }

        ContainerApp containerApp = new ContainerApp(
            "demo-fleet-target-app-" + targetToken,
            ContainerAppArgs.builder()
                .resourceGroupName(managedResourceGroup.name())
                .containerAppName(containerAppName)
                .location(region)
                .managedEnvironmentId(subscriptionContext.environment.id())
                .configuration(ConfigurationArgs.builder()
                    .ingress(IngressArgs.builder()
                        .external(true)
                        .targetPort(8080)
                        .transport("auto")
                        .allowInsecure(false)
                        .build())
                    .build())
                .template(TemplateArgs.builder()
                    .containers(ContainerArgs.builder()
                        .name("demo")
                        .image(defaultImage)
                        .command("python", "-c", TARGET_DEMO_SERVER_SCRIPT)
                        .env(
                            EnvironmentVarArgs.builder().name("PORT").value("8080").build(),
                            EnvironmentVarArgs.builder().name("MAPPO_SOFTWARE_VERSION").value(normalizeTagValue(target.softwareVersion, defaultSoftwareVersion)).build(),
                            EnvironmentVarArgs.builder().name("MAPPO_DATA_MODEL_VERSION").value(normalizeTagValue(target.dataModelVersion, defaultDataModelVersion)).build()
                        )
                        .resources(ContainerResourcesArgs.builder()
                            .cpu(defaultCpu)
                            .memory(defaultMemory)
                            .build())
                        .build())
                    .scale(ScaleArgs.builder()
                        .minReplicas(1)
                        .maxReplicas(1)
                        .build())
                    .build())
                .tags(mergedTags)
                .build(),
            CustomResourceOptions.builder().provider(provider).build()
        );

        Output<String> managedResourceGroupId = Output.format(
            "/subscriptions/%s/resourceGroups/%s",
            target.subscriptionId,
            managedResourceGroup.name()
        );

        return new DeploymentOutput(
            target.id,
            target.tenantId,
            target.subscriptionId,
            region,
            targetGroup,
            tier,
            environmentTag,
            customerName,
            managedApplicationId,
            managedResourceGroupId,
            managedResourceGroup.name(),
            containerAppName,
            containerApp.id()
        );
    }

    private Output<Map<String, Object>> buildInventoryRow(DeploymentOutput deployment) {
        return Output.tuple(deployment.containerAppId, deployment.managedResourceGroupId, deployment.managedResourceGroupName)
            .applyValue(tuple -> {
                String containerAppResourceId = tuple.t1;
                String managedResourceGroupId = tuple.t2;
                String managedResourceGroupName = tuple.t3;

                Map<String, String> tags = linkedMapOfString(
                    "ring", deployment.targetGroup,
                    "region", deployment.region,
                    "environment", deployment.environmentTag,
                    "tier", deployment.tier
                );
                if (deployment.customerName != null) {
                    tags.put("customer", deployment.customerName);
                }

                Map<String, String> metadata = linkedMapOfString(
                    "source", "demo-fleet-pulumi",
                    "managed_resource_group_id", managedResourceGroupId,
                    "managed_resource_group_name", managedResourceGroupName,
                    "container_app_name", deployment.containerAppName
                );
                if (deployment.customerName != null) {
                    metadata.put("customer_name", deployment.customerName);
                }
                if (deployment.managedApplicationId != null) {
                    metadata.put("managed_application_id", deployment.managedApplicationId);
                    String[] segments = deployment.managedApplicationId.split("/");
                    String managedApplicationName = segments.length == 0 ? deployment.targetId : segments[segments.length - 1];
                    metadata.put("managed_application_name", managedApplicationName);
                }

                return linkedMapOf(
                    "id", deployment.targetId,
                    "tenant_id", deployment.tenantId,
                    "subscription_id", deployment.subscriptionId,
                    "managed_app_id", containerAppResourceId,
                    "tags", tags,
                    "metadata", metadata
                );
            });
    }

    private SubscriptionContext getOrCreateSubscriptionContext(Provider provider, String subscriptionId, String location) {
        SubscriptionContext existing = contextBySubscription.get(subscriptionId);
        if (existing != null) {
            if (!Objects.equals(existing.location, location)) {
                ctx.log().warn("demoFleet target regions differ in subscription " + subscriptionId
                    + "; using managed environment in " + existing.location + ".");
            }
            return existing;
        }

        String subscriptionToken = subscriptionKey(subscriptionId);
        String observabilityResourceGroupName = normalizeName(
            environmentResourceGroupPrefix + "-" + stackToken + "-" + subscriptionToken,
            "rg-mappo-demo-fleet-" + stackToken + "-" + subscriptionToken,
            90
        );
        String workspaceName = normalizeName(
            logWorkspaceNamePrefix + "-" + stackToken + "-" + subscriptionToken,
            "law-mappo-demo-fleet-" + stackToken + "-" + subscriptionToken,
            63
        );
        String managedEnvironmentName = normalizeName(
            managedEnvironmentNamePrefix + "-" + stackToken + "-" + subscriptionToken,
            "cae-mappo-demo-fleet-" + stackToken + "-" + subscriptionToken,
            32
        );

        CustomResourceOptions withProvider = CustomResourceOptions.builder().provider(provider).build();

        ResourceGroup observabilityResourceGroup = new ResourceGroup(
            "demo-fleet-observability-rg-" + subscriptionToken,
            ResourceGroupArgs.builder()
                .resourceGroupName(observabilityResourceGroupName)
                .location(location)
                .tags(linkedMapOfString(
                    "managedBy", "pulumi",
                    "system", "mappo",
                    "scope", "demo-fleet-shared"
                ))
                .build(),
            withProvider
        );

        Workspace workspace = new Workspace(
            "demo-fleet-workspace-" + subscriptionToken,
            WorkspaceArgs.builder()
                .workspaceName(workspaceName)
                .resourceGroupName(observabilityResourceGroup.name())
                .location(location)
                .sku(WorkspaceSkuArgs.builder().name("PerGB2018").build())
                .retentionInDays(30)
                .tags(linkedMapOfString(
                    "managedBy", "pulumi",
                    "system", "mappo",
                    "scope", "demo-fleet-shared"
                ))
                .build(),
            withProvider
        );

        Output<String> workspaceSharedKey = OperationalinsightsFunctions.getWorkspaceSharedKeys(
            GetWorkspaceSharedKeysArgs.builder()
                .resourceGroupName(observabilityResourceGroup.name())
                .workspaceName(workspace.name())
                .build(),
            InvokeOutputOptions.builder().provider(provider).build()
        ).applyValue(result -> result.primarySharedKey().orElse(""));

        ManagedEnvironment environment = new ManagedEnvironment(
            "demo-fleet-environment-" + subscriptionToken,
            ManagedEnvironmentArgs.builder()
                .resourceGroupName(observabilityResourceGroup.name())
                .environmentName(managedEnvironmentName)
                .location(location)
                .appLogsConfiguration(AppLogsConfigurationArgs.builder()
                    .destination("log-analytics")
                    .logAnalyticsConfiguration(LogAnalyticsConfigurationArgs.builder()
                        .customerId(workspace.customerId())
                        .sharedKey(workspaceSharedKey)
                        .build())
                    .build())
                .tags(linkedMapOfString(
                    "managedBy", "pulumi",
                    "system", "mappo",
                    "scope", "demo-fleet-shared"
                ))
                .build(),
            withProvider
        );

        SubscriptionContext context = new SubscriptionContext(provider, location, observabilityResourceGroup, workspace, environment);
        contextBySubscription.put(subscriptionId, context);
        return context;
    }

    private Provider getProvider(String subscriptionId) {
        Provider existing = providersBySubscription.get(subscriptionId);
        if (existing != null) {
            return existing;
        }
        Provider provider = new Provider(
            "demo-fleet-provider-" + subscriptionKey(subscriptionId),
            ProviderArgs.builder().subscriptionId(subscriptionId).build()
        );
        providersBySubscription.put(subscriptionId, provider);
        return provider;
    }

    private static void assertUniqueTargetIds(List<DemoFleetTargetConfig> targetsToCheck) {
        Set<String> seen = new LinkedHashSet<>();
        for (DemoFleetTargetConfig target : targetsToCheck) {
            String value = Optional.ofNullable(target.id).orElse("").trim();
            if (value.isEmpty()) {
                throw new IllegalArgumentException("demoFleet target id must not be empty.");
            }
            if (!seen.add(value)) {
                throw new IllegalArgumentException("Duplicate demoFleet target id: '" + value + "'.");
            }
        }
    }

    private static Map<String, String> buildTenantBySubscriptionMap(List<DemoFleetTargetConfig> targets) {
        Map<String, String> rows = new LinkedHashMap<>();
        for (DemoFleetTargetConfig target : targets) {
            String subscriptionId = Optional.ofNullable(target.subscriptionId).orElse("").trim();
            String tenantId = Optional.ofNullable(target.tenantId).orElse("").trim();
            if (!subscriptionId.isEmpty() && !tenantId.isEmpty()) {
                rows.put(subscriptionId, tenantId);
            }
        }
        return rows;
    }

    private static String subscriptionKey(String subscriptionId) {
        String normalized = Optional.ofNullable(subscriptionId)
            .orElse("")
            .trim()
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9]", "");
        if (normalized.isEmpty()) {
            return "nosub";
        }
        return normalized.substring(0, Math.min(8, normalized.length()));
    }

    private static String normalizeTagValue(String value, String fallback) {
        String normalized = normalizeNullableValue(value);
        return normalized == null ? fallback : normalized;
    }

    private static String normalizeNullableValue(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        return normalized;
    }

    private static String normalizeNullableResourceId(String value) {
        String normalized = normalizeNullableValue(value);
        if (normalized == null) {
            return null;
        }
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private static String normalizeName(String value, String fallback, int maxLength) {
        String normalized = NON_ALPHANUMERIC_DASH.matcher(Optional.ofNullable(value).orElse("").toLowerCase(Locale.ROOT)).replaceAll("-");
        normalized = DASH_COLLAPSE.matcher(normalized).replaceAll("-");
        normalized = trimDashes(normalized);

        String candidate = normalized.isEmpty() ? fallback : normalized;
        if (candidate.length() > maxLength) {
            candidate = candidate.substring(0, maxLength);
        }
        candidate = trimTrailingDashes(candidate);
        return candidate.isEmpty() ? fallback.substring(0, Math.min(maxLength, fallback.length())) : candidate;
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

    private static <T> Optional<List<T>> parseConfigObjectList(Config cfg, String key, Class<T> itemClass) {
        try {
            Optional<List<T>> parsed = cfg.getObject(key, TypeShape.list(itemClass));
            if (parsed.isPresent()) {
                return parsed;
            }
        } catch (Exception ignored) {
            // Fallback to JSON parse below.
        }

        Optional<String> raw = cfg.get(key).map(String::trim).filter(v -> !v.isEmpty());
        if (raw.isEmpty()) {
            return Optional.empty();
        }

        Type listType = TypeToken.getParameterized(List.class, itemClass).getType();
        List<T> value = GSON.fromJson(raw.get(), listType);
        return Optional.ofNullable(value);
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

    public static final class DemoFleetTargetConfig {
        public String id;
        public String tenantId;
        public String subscriptionId;
        public String region;
        public String targetGroup;
        public String environment;
        public String tier;
        public String customerName;
        public String managedApplicationId;
        public String softwareVersion;
        public String dataModelVersion;
        public Map<String, String> tags;
    }

    private static final class SubscriptionContext {
        private final Provider provider;
        private final String location;
        private final ResourceGroup observabilityResourceGroup;
        private final Workspace workspace;
        private final ManagedEnvironment environment;

        private SubscriptionContext(
            Provider provider,
            String location,
            ResourceGroup observabilityResourceGroup,
            Workspace workspace,
            ManagedEnvironment environment
        ) {
            this.provider = provider;
            this.location = location;
            this.observabilityResourceGroup = observabilityResourceGroup;
            this.workspace = workspace;
            this.environment = environment;
        }
    }

    private static final class DeploymentOutput {
        private final String targetId;
        private final String tenantId;
        private final String subscriptionId;
        private final String region;
        private final String targetGroup;
        private final String tier;
        private final String environmentTag;
        private final String customerName;
        private final String managedApplicationId;
        private final Output<String> managedResourceGroupId;
        private final Output<String> managedResourceGroupName;
        private final String containerAppName;
        private final Output<String> containerAppId;

        private DeploymentOutput(
            String targetId,
            String tenantId,
            String subscriptionId,
            String region,
            String targetGroup,
            String tier,
            String environmentTag,
            String customerName,
            String managedApplicationId,
            Output<String> managedResourceGroupId,
            Output<String> managedResourceGroupName,
            String containerAppName,
            Output<String> containerAppId
        ) {
            this.targetId = targetId;
            this.tenantId = tenantId;
            this.subscriptionId = subscriptionId;
            this.region = region;
            this.targetGroup = targetGroup;
            this.tier = tier;
            this.environmentTag = environmentTag;
            this.customerName = customerName;
            this.managedApplicationId = managedApplicationId;
            this.managedResourceGroupId = managedResourceGroupId;
            this.managedResourceGroupName = managedResourceGroupName;
            this.containerAppName = containerAppName;
            this.containerAppId = containerAppId;
        }
    }
}
