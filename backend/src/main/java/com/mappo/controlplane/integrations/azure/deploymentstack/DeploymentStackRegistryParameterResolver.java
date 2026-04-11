package com.mappo.controlplane.integrations.azure.deploymentstack;

import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.jooq.enums.MappoRegistryAuthMode;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class DeploymentStackRegistryParameterResolver {

    private final MappoProperties properties;

    public Map<String, String> registryParameters(Map<String, String> defaults, TargetExecutionContextRecord target) {
        String containerImage = defaults == null ? "" : normalize(defaults.get("containerImage"));
        MappoRegistryAuthMode authMode = target.registryAuthMode() == null
            ? defaultRegistryAuthMode(containerImage)
            : target.registryAuthMode();

        if (authMode == null || authMode == MappoRegistryAuthMode.none) {
            if (imageRequiresRegistryAuth(containerImage)) {
                throw new IllegalArgumentException(
                    "Target " + target.targetId() + " requires registry auth for image " + containerImage + "."
                );
            }
            return Map.of();
        }

        if (authMode == MappoRegistryAuthMode.customer_managed_secret) {
            throw new IllegalArgumentException(
                "registry_auth_mode customer_managed_secret is not implemented yet for deployment_stack execution"
            );
        }

        String registryServer = firstNonBlank(
            target.registryServer(),
            defaults == null ? null : defaults.get("registryServer"),
            deriveRegistryServer(containerImage),
            properties.getPublisherAcr().getServer()
        );
        String registryUsername = firstNonBlank(
            target.registryUsername(),
            defaults == null ? null : defaults.get("registryUsername"),
            properties.getPublisherAcr().getPullClientId()
        );
        String registryPasswordSecretName = firstNonBlank(
            target.registryPasswordSecretName(),
            defaults == null ? null : defaults.get("registryPasswordSecretName"),
            properties.getPublisherAcr().getPullSecretName()
        );
        String registryPassword = normalize(properties.getPublisherAcr().getPullClientSecret());

        if (registryServer.isBlank() || registryUsername.isBlank() || registryPassword.isBlank()) {
            throw new IllegalArgumentException(
                "publisher ACR pull credentials are incomplete for deployment_stack execution"
            );
        }

        Map<String, String> values = new LinkedHashMap<>();
        values.put("registryServer", registryServer);
        values.put("registryUsername", registryUsername);
        values.put("registryPasswordSecretName", registryPasswordSecretName);
        values.put("registryPassword", registryPassword);
        return values;
    }

    private boolean imageRequiresRegistryAuth(String containerImage) {
        String registryServer = deriveRegistryServer(containerImage);
        if (registryServer.isBlank()) {
            return false;
        }
        return registryServer.endsWith(".azurecr.io");
    }

    private MappoRegistryAuthMode defaultRegistryAuthMode(String containerImage) {
        return imageRequiresRegistryAuth(containerImage)
            ? MappoRegistryAuthMode.shared_service_principal_secret
            : MappoRegistryAuthMode.none;
    }

    private String deriveRegistryServer(String containerImage) {
        String normalized = normalize(containerImage);
        int slashIndex = normalized.indexOf('/');
        if (slashIndex <= 0) {
            return "";
        }
        String candidate = normalized.substring(0, slashIndex);
        return candidate.contains(".") ? candidate : "";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
