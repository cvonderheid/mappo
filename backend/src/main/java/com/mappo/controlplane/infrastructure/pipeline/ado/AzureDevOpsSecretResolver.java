package com.mappo.controlplane.infrastructure.pipeline.ado;

import com.mappo.controlplane.config.MappoProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class AzureDevOpsSecretResolver {

    static final String DEFAULT_PAT_SECRET_REF = "mappo.azure-devops.personal-access-token";

    private final MappoProperties properties;

    String resolvePersonalAccessToken(String secretReference) {
        String reference = normalize(secretReference);
        if (reference.isBlank() || DEFAULT_PAT_SECRET_REF.equals(reference)) {
            return normalize(properties.getAzureDevOps().getPersonalAccessToken());
        }
        if (reference.startsWith("env:")) {
            return normalize(System.getenv(reference.substring("env:".length())));
        }
        if (reference.startsWith("literal:")) {
            return normalize(reference.substring("literal:".length()));
        }
        return "";
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
