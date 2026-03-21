package com.mappo.controlplane.infrastructure.pipeline.ado;

import com.mappo.controlplane.config.MappoProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AzureDevOpsSecretResolver {

    static final String DEFAULT_PAT_SECRET_REF = "mappo.azure-devops.personal-access-token";
    static final String DEFAULT_PAT_ENV_VAR = "MAPPO_AZURE_DEVOPS_PERSONAL_ACCESS_TOKEN";

    private final MappoProperties properties;

    String resolvePersonalAccessToken(String secretReference) {
        String reference = normalize(secretReference);
        if (reference.isBlank() || DEFAULT_PAT_SECRET_REF.equals(reference) || ("config:" + DEFAULT_PAT_SECRET_REF).equals(reference)) {
            return configuredDefaultToken();
        }
        if (reference.startsWith("env:")) {
            return normalize(System.getenv(reference.substring("env:".length())));
        }
        if (reference.startsWith("literal:")) {
            return normalize(reference.substring("literal:".length()));
        }
        if (reference.startsWith("config:")) {
            String configKey = normalize(reference.substring("config:".length()));
            if (DEFAULT_PAT_SECRET_REF.equals(configKey)) {
                return configuredDefaultToken();
            }
            return "";
        }
        String directEnvValue = normalize(System.getenv(reference));
        if (!directEnvValue.isBlank()) {
            return directEnvValue;
        }
        String envAlias = normalize(System.getenv(toEnvironmentStyle(reference)));
        if (!envAlias.isBlank()) {
            return envAlias;
        }
        return "";
    }

    private String configuredDefaultToken() {
        String configured = normalize(properties.getAzureDevOps().getPersonalAccessToken());
        if (!configured.isBlank()) {
            return configured;
        }
        return normalize(System.getenv(DEFAULT_PAT_ENV_VAR));
    }

    private String toEnvironmentStyle(String reference) {
        return normalize(reference).toUpperCase().replaceAll("[^A-Z0-9]", "_");
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
