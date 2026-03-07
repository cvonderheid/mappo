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
    private String azureTenantId = "";
    private String azureClientId = "";
    private String azureClientSecret = "";
    private String managedAppReleaseRepo = "cvonderheid/mappo-managed-app";
    private String managedAppReleasePath = "releases/releases.manifest.json";
    private String managedAppReleaseRef = "main";
}
