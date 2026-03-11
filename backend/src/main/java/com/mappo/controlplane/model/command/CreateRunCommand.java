package com.mappo.controlplane.model.command;

import com.mappo.controlplane.jooq.enums.MappoStrategyMode;
import java.util.List;
import java.util.Map;

public record CreateRunCommand(
    String projectId,
    String releaseId,
    List<String> targetIds,
    Map<String, String> targetTags,
    MappoStrategyMode strategyMode,
    String waveTag,
    List<String> waveOrder,
    Integer concurrency,
    RunStopPolicyCommand stopPolicy
) {
    public CreateRunCommand {
        targetIds = targetIds == null ? List.of() : targetIds;
        targetTags = targetTags == null ? Map.of() : targetTags;
        waveOrder = waveOrder == null ? List.of() : waveOrder;
    }
}
