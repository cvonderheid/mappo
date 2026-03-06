package com.mappo.controlplane.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "mappo")
public class MappoProperties {

    private String appVersion = "0.1.0-java";
    private String apiPrefix = "/api/v1";
    private String marketplaceIngestToken = "";
    private List<String> corsOrigins = new ArrayList<>();
    private String azureTenantId = "";
    private String azureClientId = "";
    private String azureClientSecret = "";

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public String getApiPrefix() {
        return apiPrefix;
    }

    public void setApiPrefix(String apiPrefix) {
        this.apiPrefix = apiPrefix;
    }

    public String getMarketplaceIngestToken() {
        return marketplaceIngestToken;
    }

    public void setMarketplaceIngestToken(String marketplaceIngestToken) {
        this.marketplaceIngestToken = marketplaceIngestToken;
    }

    public List<String> getCorsOrigins() {
        return corsOrigins;
    }

    public void setCorsOrigins(List<String> corsOrigins) {
        this.corsOrigins = corsOrigins;
    }

    public String getAzureTenantId() {
        return azureTenantId;
    }

    public void setAzureTenantId(String azureTenantId) {
        this.azureTenantId = azureTenantId;
    }

    public String getAzureClientId() {
        return azureClientId;
    }

    public void setAzureClientId(String azureClientId) {
        this.azureClientId = azureClientId;
    }

    public String getAzureClientSecret() {
        return azureClientSecret;
    }

    public void setAzureClientSecret(String azureClientSecret) {
        this.azureClientSecret = azureClientSecret;
    }
}
