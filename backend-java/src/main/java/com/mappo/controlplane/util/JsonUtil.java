package com.mappo.controlplane.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JsonUtil {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public JsonUtil(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> readMap(String value) {
        if (value == null || value.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(value, MAP_TYPE);
        } catch (Exception ignored) {
            return new HashMap<>();
        }
    }

    public List<String> readStringList(String value) {
        if (value == null || value.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return objectMapper.readValue(value, STRING_LIST_TYPE);
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value == null ? Collections.emptyMap() : value);
        } catch (Exception ignored) {
            return "{}";
        }
    }
}
