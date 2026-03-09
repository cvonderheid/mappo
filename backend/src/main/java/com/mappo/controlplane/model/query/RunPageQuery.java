package com.mappo.controlplane.model.query;

public record RunPageQuery(
    Integer page,
    Integer size,
    String runId,
    String releaseId,
    String status
) {
}
