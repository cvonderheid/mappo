package com.mappo.demo.pipelinedelivery;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.pulumi.Config;
import com.pulumi.Context;
import com.pulumi.Pulumi;
import com.pulumi.azurenative.Provider;
import com.pulumi.azurenative.ProviderArgs;
import com.pulumi.azurenative.resources.ResourceGroup;
import com.pulumi.azurenative.resources.ResourceGroupArgs;
import com.pulumi.azurenative.web.AppServicePlan;
import com.pulumi.azurenative.web.AppServicePlanArgs;
import com.pulumi.azurenative.web.WebApp;
import com.pulumi.azurenative.web.WebAppArgs;
import com.pulumi.azurenative.web.inputs.NameValuePairArgs;
import com.pulumi.azurenative.web.inputs.SiteConfigArgs;
import com.pulumi.azurenative.web.inputs.SkuDescriptionArgs;
import com.pulumi.core.Output;
import com.pulumi.resources.CustomResourceOptions;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

public final class Main {
    private static final Gson GSON = new Gson();
    private static final Gson PRETTY_GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Pattern NON_ALPHANUMERIC_DASH = Pattern.compile("[^a-z0-9-]");
    private static final Pattern DASH_COLLAPSE = Pattern.compile("-+");
    private static final String PROJECT_ID = "azure-appservice-ado-pipeline";

    private final Context ctx;
    private final Config config;
    private final String stackToken;
    private final String defaultLocation;
    private final String defaultSoftwareVersion;
    private final String defaultDataModelVersion;
    private final String resourceGroupPrefix;
    private final String planNamePrefix;
    private final String appNamePrefix;
    private final Map<String, Provider> providersBySubscription = new HashMap<>();

    private Main(Context ctx) {
        this.ctx = ctx;
        this.config = Config.of("pipelineDeliveryTargets");
        this.stackToken = normalizeName(ctx.stackName(), "demo", 20);
        this.defaultLocation = config.get("defaultLocation").orElse("eastus");
        this.defaultSoftwareVersion = config.get("defaultSoftwareVersion").orElse("2026.03.01.1");
        this.defaultDataModelVersion = config.get("defaultDataModelVersion").orElse("1");
        this.resourceGroupPrefix = config.get("resourceGroupPrefix").orElse("rg-mappo-pipeline-delivery-target");
        this.planNamePrefix = config.get("planNamePrefix").orElse("asp-mappo-pipeline-delivery");
        this.appNamePrefix = config.get("appNamePrefix").orElse("app-mappo-pipeline");
    }

    public static void main(String[] args) {
        Pulumi.run(ctx -> new Main(ctx).run());
    }

    private void run() {
        List<AppServiceTargetConfig> targets = parseTargets();
        assertUniqueTargetIds(targets);
        if (targets.isEmpty()) {
            ctx.log().warn("No pipeline delivery demo targets configured. Set pipelineDeliveryTargets:targets in your stack config.");
        }

        List<DeploymentOutput> deployments = new ArrayList<>();
        for (int index = 0; index < targets.size(); index++) {
            deployments.add(createDeployment(targets.get(index), index));
        }

        List<Output<Map<String, Object>>> rows = deployments.stream().map(this::buildInventoryRow).toList();
        Output<List<Map<String, Object>>> targetInventory = Output.all(rows);

        ctx.export("targetCount", deployments.size());
        ctx.export("mappoTargetInventory", targetInventory);
        ctx.export("mappoTargetInventoryJson", targetInventory.applyValue(PRETTY_GSON::toJson));
        ctx.export("tenantBySubscription", buildTenantBySubscriptionMap(targets));
    }

    private DeploymentOutput createDeployment(AppServiceTargetConfig target, int index) {
        Provider provider = getProvider(target.subscriptionId());
        String region = defaultIfBlank(normalizeTagValue(target.region(), defaultLocation), defaultLocation);
        String targetGroup = normalizeTagValue(target.targetGroup(), "prod");
        String environment = normalizeTagValue(target.environment(), "prod");
        String tier = normalizeTagValue(target.tier(), "standard");
        String customerName = normalizeNullableValue(target.customerName());
        String targetToken = normalizeName(target.id(), "target-" + (index + 1), 28);
        String resourceGroupName = normalizeName(resourceGroupPrefix + "-" + targetToken, "rg-mappo-pipeline-delivery-target-" + targetToken, 90);
        String planName = normalizeName(planNamePrefix + "-" + targetToken, "asp-mappo-pipeline-delivery-" + targetToken, 40);
        String appName = normalizeName(appNamePrefix + "-" + stackToken + "-" + targetToken + "-" + shortSubscription(target.subscriptionId()),
            "app-mappo-pipeline-" + stackToken + "-" + targetToken, 60);

        ResourceGroup resourceGroup = new ResourceGroup(
            "targets-pipeline-delivery-rg-" + targetToken,
            ResourceGroupArgs.builder()
                .resourceGroupName(resourceGroupName)
                .location(region)
                .tags(linkedMapOfString(
                    "managedBy", "pulumi",
                    "system", "mappo",
                    "scope", "targets-pipeline-delivery-target",
                    "targetId", target.id(),
                    "ring", targetGroup,
                    "environment", environment,
                    "tier", tier,
                    "projectId", PROJECT_ID
                ))
                .build(),
            CustomResourceOptions.builder().provider(provider).build()
        );

        AppServicePlan plan = new AppServicePlan(
            "targets-pipeline-delivery-plan-" + targetToken,
            AppServicePlanArgs.builder()
                .resourceGroupName(resourceGroup.name())
                .name(planName)
                .location(region)
                .kind("linux")
                .reserved(true)
                .sku(SkuDescriptionArgs.builder()
                    .name("B1")
                    .tier("Basic")
                    .size("B1")
                    .capacity(1)
                    .build())
                .build(),
            CustomResourceOptions.builder().provider(provider).build()
        );

        List<NameValuePairArgs> appSettings = List.of(
            NameValuePairArgs.builder().name("APP_VERSION").value(defaultSoftwareVersion).build(),
            NameValuePairArgs.builder().name("DATA_MODEL_VERSION").value(defaultDataModelVersion).build(),
            NameValuePairArgs.builder().name("MAPPO_TARGET_ID").value(target.id()).build(),
            NameValuePairArgs.builder().name("SCM_DO_BUILD_DURING_DEPLOYMENT").value("false").build(),
            NameValuePairArgs.builder().name("WEBSITE_RUN_FROM_PACKAGE").value("1").build()
        );

        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("managedBy", "pulumi");
        tags.put("system", "mappo");
        tags.put("scope", "targets-pipeline-delivery-target");
        tags.put("targetId", target.id());
        tags.put("ring", targetGroup);
        tags.put("region", region);
        tags.put("environment", environment);
        tags.put("tier", tier);
        tags.put("projectId", PROJECT_ID);
        if (customerName != null) {
            tags.put("customer", customerName);
        }
        if (target.tags() != null) {
            target.tags().forEach((key, value) -> {
                if (key != null && !key.isBlank() && value != null && !value.isBlank()) {
                    tags.put(key, value);
                }
            });
        }

        WebApp webApp = new WebApp(
            "targets-pipeline-delivery-app-" + targetToken,
            WebAppArgs.builder()
                .resourceGroupName(resourceGroup.name())
                .name(appName)
                .location(region)
                .serverFarmId(plan.id())
                .kind("app,linux")
                .httpsOnly(true)
                .siteConfig(SiteConfigArgs.builder()
                    .linuxFxVersion("NODE|22-lts")
                    .appCommandLine("node server.js")
                    .healthCheckPath("/health")
                    .alwaysOn(true)
                    .appSettings(appSettings)
                    .build())
                .tags(tags)
                .build(),
            CustomResourceOptions.builder().provider(provider).build()
        );

        Output<String> resourceGroupId = Output.format("/subscriptions/%s/resourceGroups/%s", target.subscriptionId(), resourceGroup.name());
        Output<String> runtimeBaseUrl = Output.format("https://%s", webApp.defaultHostName());

        return new DeploymentOutput(
            target.id(),
            target.tenantId(),
            target.subscriptionId(),
            region,
            targetGroup,
            tier,
            environment,
            customerName,
            resourceGroupId,
            resourceGroup.name(),
            appName,
            webApp.id(),
            runtimeBaseUrl
        );
    }

    private Output<Map<String, Object>> buildInventoryRow(DeploymentOutput deployment) {
        return Output.tuple(deployment.resourceGroupId(), deployment.resourceGroupName(), deployment.webAppId(), deployment.runtimeBaseUrl())
            .applyValue(tuple -> {
                Map<String, String> tags = linkedMapOfString(
                    "ring", deployment.targetGroup(),
                    "region", deployment.region(),
                    "environment", deployment.environment(),
                    "tier", deployment.tier()
                );
                if (deployment.customerName() != null) {
                    tags.put("customer", deployment.customerName());
                }

                Map<String, String> executionConfig = linkedMapOfString(
                    "resourceGroup", tuple.t2,
                    "appServiceName", deployment.appName(),
                    "webAppResourceId", tuple.t3,
                    "runtimeBaseUrl", tuple.t4,
                    "runtimeHealthPath", "/health",
                    "runtimeExpectedStatus", "200"
                );

                Map<String, Object> metadata = linkedMapOf(
                    "source", "targets-pipeline-delivery-pulumi",
                    "display_name", deployment.appName(),
                    "customer_name", deployment.customerName(),
                    "managed_resource_group_id", tuple.t1,
                    "execution_config", executionConfig
                );

                return linkedMapOf(
                    "id", deployment.targetId(),
                    "project_id", PROJECT_ID,
                    "tenant_id", deployment.tenantId(),
                    "subscription_id", deployment.subscriptionId(),
                    "tags", tags,
                    "metadata", metadata
                );
            });
    }

    private Output<Map<String, String>> buildTenantBySubscriptionMap(List<AppServiceTargetConfig> targets) {
        Map<String, String> map = new LinkedHashMap<>();
        for (AppServiceTargetConfig target : targets) {
            map.put(target.subscriptionId(), target.tenantId());
        }
        return Output.of(map);
    }

    private List<AppServiceTargetConfig> parseTargets() {
        Optional<String> rawTargets = config.get("targets");
        if (rawTargets.isEmpty() || rawTargets.get().isBlank()) {
            return List.of();
        }
        Type type = new TypeToken<List<AppServiceTargetConfig>>() {}.getType();
        List<AppServiceTargetConfig> parsed = GSON.fromJson(rawTargets.get(), type);
        return parsed == null ? List.of() : parsed;
    }

    private void assertUniqueTargetIds(List<AppServiceTargetConfig> targets) {
        Set<String> seen = new LinkedHashSet<>();
        for (AppServiceTargetConfig target : targets) {
            if (target == null || normalize(target.id()).isBlank()) {
                throw new IllegalArgumentException("pipelineDeliveryTargets target id is required");
            }
            if (!seen.add(target.id())) {
                throw new IllegalArgumentException("Duplicate pipelineDeliveryTargets target id: " + target.id());
            }
        }
    }

    private Provider getProvider(String subscriptionId) {
        return providersBySubscription.computeIfAbsent(subscriptionId, id -> new Provider(
            "targets-pipeline-delivery-provider-" + shortSubscription(id),
            ProviderArgs.builder().subscriptionId(id).build()
        ));
    }

    private String shortSubscription(String subscriptionId) {
        String normalized = normalize(subscriptionId).replace("-", "");
        return normalized.length() <= 8 ? normalized : normalized.substring(0, 8);
    }

    private String normalizeName(String value, String fallback, int maxLength) {
        String normalized = normalize(value).toLowerCase(Locale.ROOT);
        normalized = NON_ALPHANUMERIC_DASH.matcher(normalized).replaceAll("-");
        normalized = DASH_COLLAPSE.matcher(normalized).replaceAll("-").replaceAll("^-|-$", "");
        if (normalized.isBlank()) {
            normalized = fallback;
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength).replaceAll("-+$", "");
    }

    private String normalizeTagValue(String value, String fallback) {
        String normalized = normalize(value).toLowerCase(Locale.ROOT);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String normalizeNullableValue(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isBlank() ? fallback : normalized;
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    @SafeVarargs
    private static <T> Map<String, T> linkedMapOf(Object... entries) {
        Map<String, T> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            @SuppressWarnings("unchecked")
            T value = (T) entries[i + 1];
            if (value != null) {
                map.put(String.valueOf(entries[i]), value);
            }
        }
        return map;
    }

    private static Map<String, String> linkedMapOfString(Object... entries) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            String value = entries[i + 1] == null ? "" : String.valueOf(entries[i + 1]).trim();
            if (!value.isBlank()) {
                map.put(String.valueOf(entries[i]), value);
            }
        }
        return map;
    }

    private record AppServiceTargetConfig(
        String id,
        String tenantId,
        String subscriptionId,
        String targetGroup,
        String region,
        String environment,
        String tier,
        String customerName,
        Map<String, String> tags
    ) {
    }

    private record DeploymentOutput(
        String targetId,
        String tenantId,
        String subscriptionId,
        String region,
        String targetGroup,
        String tier,
        String environment,
        String customerName,
        Output<String> resourceGroupId,
        Output<String> resourceGroupName,
        String appName,
        Output<String> webAppId,
        Output<String> runtimeBaseUrl
    ) {
    }
}
