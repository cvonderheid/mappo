package com.mappo.controlplane.service.release;

import com.mappo.controlplane.api.request.ReleaseCreateRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReleaseManifestParser {

    private final ReleaseManifestDocumentReader documentReader;
    private final ReleaseManifestRowParser rowParser;

    public ParsedReleaseManifest parse(String rawPayload) {
        List<Map<?, ?>> releases = documentReader.readReleaseRows(rawPayload);

        List<ReleaseCreateRequest> normalized = new ArrayList<>();
        int ignoredCount = 0;
        for (int index = 0; index < releases.size(); index += 1) {
            normalized.add(rowParser.parse(releases.get(index), index));
        }

        return new ParsedReleaseManifest(releases.size(), ignoredCount, List.copyOf(normalized));
    }
}
