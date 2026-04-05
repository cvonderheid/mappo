package com.mappo.controlplane.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mappo")
@Getter
@Setter
public class MappoProperties {

    private String appVersion = "0.1.0-java";
    private String apiPrefix = "/api/v1";
    private String marketplaceIngestToken = "";
    private List<String> corsOrigins = new ArrayList<>();
    private AzureProperties azure = new AzureProperties();
    private ManagedAppReleaseProperties managedAppRelease = new ManagedAppReleaseProperties();
    private PublisherAcrProperties publisherAcr = new PublisherAcrProperties();
    private AzureDevOpsProperties azureDevOps = new AzureDevOpsProperties();
    private RuntimeProbeProperties runtimeProbe = new RuntimeProbeProperties();
    private SseProperties sse = new SseProperties();
    private RetentionProperties retention = new RetentionProperties();
    private RedisProperties redis = new RedisProperties();

    @Getter
    @Setter
    public static class AzureProperties {
        private String tenantId = "";
        private String clientId = "";
        private String clientSecret = "";
        private String keyVaultUrl = "";
        private long deploymentStackAttachTimeoutMs = 120_000L;
        private long deploymentStackAttachPollIntervalMs = 2_000L;
    }

    @Getter
    @Setter
    public static class ManagedAppReleaseProperties {
        private String repo = "cvonderheid/mappo-managed-app";
        private String path = "releases/releases.manifest.json";
        private String ref = "main";
        private String webhookSecret = "";
        private String githubToken = "";
    }

    @Getter
    @Setter
    public static class PublisherAcrProperties {
        private String server = "";
        private String pullClientId = "";
        private String pullClientSecret = "";
        private String pullSecretName = "publisher-acr-pull";
    }

    @Getter
    @Setter
    public static class AzureDevOpsProperties {
        private String baseUrl = "https://dev.azure.com";
        private String personalAccessToken = "";
        private String webhookSecret = "";
        private String apiVersion = "7.1";
        private long connectTimeoutMs = 10_000L;
        private long readTimeoutMs = 30_000L;
        private long runPollIntervalMs = 5_000L;
        private long runPollTimeoutMs = 900_000L;
    }

    @Getter
    @Setter
    public static class RuntimeProbeProperties {
        private boolean enabled = true;
        private long intervalMs = 60_000L;
        private long initialDelayMs = 15_000L;
        private long timeoutMs = 5_000L;
    }

    @Getter
    @Setter
    public static class SseProperties {
        private boolean enabled = true;
        private long heartbeatIntervalMs = 15_000L;
        private long coalesceWindowMs = 250L;
    }

    @Getter
    @Setter
    public static class RetentionProperties {
        private boolean enabled = true;
        private long intervalMs = 86_400_000L;
        private int runRetentionDays = 30;
        private int auditRetentionDays = 30;
    }

    @Getter
    @Setter
    public static class RedisProperties {
        private boolean enabled = false;
        private String queueKey = "mappo:run-queue";
        private String queueLockPrefix = "mappo:run-lock:";
        private String queueDedupPrefix = "mappo:run-dedup:";
        private long lockLeaseMs = 3_600_000L;
        private long queueDedupTtlMs = 120_000L;
        private long workerPollTimeoutMs = 1_000L;
        private long heartbeatIntervalMs = 10_000L;
        private long recoveryIntervalMs = 30_000L;
        private long recoveryStaleAfterMs = 120_000L;
    }
}
