package com.mappo.controlplane.model.query;

import com.mappo.controlplane.jooq.enums.MappoRunStatus;

public record RunPageQuery(
    Integer page,
    Integer size,
    String projectId,
    String runId,
    String releaseId,
    MappoRunStatus status
) {
}
