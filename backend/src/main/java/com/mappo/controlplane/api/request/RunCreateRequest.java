package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mappo.controlplane.jooq.enums.MappoStrategyMode;
import com.mappo.controlplane.model.command.CreateRunCommand;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public record RunCreateRequest(
    @NotBlank String releaseId,
    List<String> targetIds,
    Map<String, String> targetTags,
    MappoStrategyMode strategyMode,
    String waveTag,
    List<String> waveOrder,
    Integer concurrency,
    @Valid RunStopPolicyRequest stopPolicy
) {

    public CreateRunCommand toCommand() {
        return new CreateRunCommand(
            null,
            normalize(releaseId),
            sanitizeList(targetIds),
            sanitizeStringMap(targetTags),
            strategyMode == null ? MappoStrategyMode.all_at_once : strategyMode,
            nullable(waveTag),
            sanitizeList(waveOrder),
            concurrency,
            stopPolicy == null ? null : stopPolicy.toCommand()
        );
    }

    private static List<String> sanitizeList(List<String> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (String value : source) {
            String normalized = normalize(value);
            if (!normalized.isBlank()) {
                out.add(normalized);
            }
        }
        return out;
    }

    private static Map<String, String> sanitizeStringMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : source.entrySet()) {
            String key = normalize(entry.getKey());
            String value = normalize(entry.getValue());
            if (!key.isBlank() && !value.isBlank()) {
                out.put(key, value);
            }
        }
        return out;
    }

    private static String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String nullable(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }
}
