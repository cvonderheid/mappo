package com.mappo.controlplane.model.query;

import com.mappo.controlplane.jooq.enums.MappoForwarderLogLevel;

public record ForwarderLogPageQuery(
    Integer page,
    Integer size,
    String logId,
    MappoForwarderLogLevel level
) {
}
