package com.mappo.controlplane.model;

import com.mappo.controlplane.jooq.enums.MappoDeploymentScope;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ReleaseRecord(
    String id,
    String sourceRef,
    String sourceVersion,
    MappoReleaseSourceType sourceType,
    String sourceVersionRef,
    MappoDeploymentScope deploymentScope,
    ReleaseExecutionSettingsRecord executionSettings,
    Map<String, String> parameterDefaults,
    String releaseNotes,
    List<String> verificationHints,
    OffsetDateTime createdAt
) {
}
