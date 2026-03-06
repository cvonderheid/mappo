package com.mappo.controlplane.model;

import com.mappo.controlplane.jooq.enums.MappoDeploymentMode;
import com.mappo.controlplane.jooq.enums.MappoDeploymentScope;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record ReleaseRecord(
    String id,
    String templateSpecId,
    String templateSpecVersion,
    MappoDeploymentMode deploymentMode,
    String templateSpecVersionId,
    MappoDeploymentScope deploymentScope,
    Map<String, Object> deploymentModeSettings,
    Map<String, String> parameterDefaults,
    String releaseNotes,
    List<String> verificationHints,
    OffsetDateTime createdAt
) {
}
