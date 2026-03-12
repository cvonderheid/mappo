package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mappo.controlplane.jooq.enums.MappoRegistryAuthMode;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TargetRegistrationMetadataRequest(
    String containerAppName,
    String source,
    String deploymentStackName,
    MappoRegistryAuthMode registryAuthMode,
    String registryServer,
    String registryUsername,
    String registryPasswordSecretName,
    Map<String, String> executionConfig
) {
    public Map<String, String> sanitizedExecutionConfig() {
        return sanitizeMap(executionConfig);
    }

    private static Map<String, String> sanitizeMap(Map<String, String> source) {
        if (source == null) {
            return null;
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = normalize(entry.getKey());
            String value = normalize(entry.getValue());
            if (!key.isBlank()) {
                out.put(key, value);
            }
        }
        return out;
    }

    private static String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
