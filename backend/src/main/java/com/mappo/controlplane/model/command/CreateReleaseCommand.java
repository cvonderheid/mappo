package com.mappo.controlplane.model.command;

import com.mappo.controlplane.jooq.enums.MappoArmDeploymentMode;
import com.mappo.controlplane.jooq.enums.MappoDeploymentScope;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import java.util.List;
import java.util.Map;

public record CreateReleaseCommand(
    String projectId,
    String sourceRef,
    String sourceVersion,
    MappoReleaseSourceType sourceType,
    String sourceVersionRef,
    MappoDeploymentScope deploymentScope,
    MappoArmDeploymentMode armDeploymentMode,
    boolean whatIfOnCanary,
    boolean verifyAfterDeploy,
    Map<String, String> parameterDefaults,
    Map<String, String> externalInputs,
    String releaseNotes,
    List<String> verificationHints
) {
    public CreateReleaseCommand {
        parameterDefaults = parameterDefaults == null ? Map.of() : parameterDefaults;
        externalInputs = externalInputs == null ? Map.of() : externalInputs;
        verificationHints = verificationHints == null ? List.of() : verificationHints;
    }
}
