package com.mappo.controlplane.service.release;

import com.mappo.controlplane.api.request.ReleaseCreateRequest;
import java.util.List;

public record ParsedReleaseManifest(
    int manifestReleaseCount,
    int ignoredCount,
    List<ReleaseCreateRequest> requests
) {
}
