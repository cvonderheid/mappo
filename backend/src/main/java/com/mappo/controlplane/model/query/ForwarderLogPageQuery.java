package com.mappo.controlplane.model.query;

public record ForwarderLogPageQuery(
    Integer page,
    Integer size,
    String logId,
    String level
) {
}
