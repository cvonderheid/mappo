package com.mappo.controlplane.application.project.config;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.util.JsonUtil;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.ObjectMapper;

abstract class AbstractProjectConfigDescriptorRegistry<K, C, D extends ProjectConfigDescriptor<K, C>> {

    private final Map<K, D> descriptors;
    private final JsonUtil jsonUtil;
    private final ObjectMapper objectMapper;

    protected AbstractProjectConfigDescriptorRegistry(List<D> descriptors, JsonUtil jsonUtil, ObjectMapper objectMapper) {
        this.descriptors = indexDescriptors(descriptors);
        this.jsonUtil = jsonUtil;
        this.objectMapper = objectMapper;
    }

    public C parse(K key, String value) {
        D descriptor = descriptor(key);
        @SuppressWarnings("unchecked")
        Class<C> configType = (Class<C>) descriptor.configType();
        return jsonUtil.read(value, configType, descriptor.defaults());
    }

    public Map<String, Object> defaultsAsMap(K key) {
        return jsonUtil.toMap(descriptor(key).defaults());
    }

    public void validate(K key, Map<String, Object> config, String fieldName) {
        convert(key, config, fieldName);
    }

    public C convert(K key, Map<String, Object> config, String fieldName) {
        D descriptor = descriptor(key);
        try {
            @SuppressWarnings("unchecked")
            C converted = (C) objectMapper.convertValue(
                config == null ? Map.of() : config,
                descriptor.configType()
            );
            return converted;
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "invalid " + fieldName + " for " + descriptor.configType().getSimpleName() + ": " + normalize(exception.getMessage())
            );
        }
    }

    protected D descriptor(K key) {
        D descriptor = descriptors.get(key);
        if (descriptor != null) {
            return descriptor;
        }
        throw new IllegalArgumentException("project config descriptor not found for key " + key);
    }

    private Map<K, D> indexDescriptors(List<D> descriptorList) {
        Map<K, D> indexed = new LinkedHashMap<>();
        for (D descriptor : descriptorList) {
            D existing = indexed.putIfAbsent(descriptor.key(), descriptor);
            if (existing != null) {
                throw new IllegalStateException("duplicate project config descriptor for key " + descriptor.key());
            }
        }
        return Map.copyOf(indexed);
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
