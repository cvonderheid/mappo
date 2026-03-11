package com.mappo.controlplane.service.release;

import com.mappo.controlplane.api.ApiException;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class ReleaseManifestDocumentReader {

    private final ObjectMapper objectMapper;

    public ReleaseManifestDocumentReader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @SuppressWarnings("unchecked")
    public List<Map<?, ?>> readReleaseRows(String rawPayload) {
        Object parsed = readJsonValue(rawPayload, "release manifest is not valid JSON");

        Object releasesPayload;
        if (parsed instanceof List<?> list) {
            releasesPayload = list;
        } else if (parsed instanceof Map<?, ?> map) {
            releasesPayload = map.get("releases");
            if (releasesPayload == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "release manifest object must include a 'releases' array");
            }
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release manifest must be an array or an object with a 'releases' array");
        }

        if (!(releasesPayload instanceof List<?> releases)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release manifest 'releases' must be an array");
        }

        for (int index = 0; index < releases.size(); index += 1) {
            if (!(releases.get(index) instanceof Map<?, ?>)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "release manifest row #%d is not an object".formatted(index + 1));
            }
        }

        return (List<Map<?, ?>>) (List<?>) releases;
    }

    public Map<?, ?> readJsonObject(String rawPayload, String errorPrefix) {
        Object parsed = readJsonValue(rawPayload, errorPrefix);
        if (!(parsed instanceof Map<?, ?> map)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorPrefix + ": expected a JSON object");
        }
        return map;
    }

    private Object readJsonValue(String rawPayload, String errorPrefix) {
        try {
            return objectMapper.readValue(rawPayload, Object.class);
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, errorPrefix + ": " + exception.getMessage());
        }
    }
}
