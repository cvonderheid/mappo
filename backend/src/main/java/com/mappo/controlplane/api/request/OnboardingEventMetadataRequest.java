package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record OnboardingEventMetadataRequest(
    String source,
    String marketplacePayloadId,
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
