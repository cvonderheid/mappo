package com.mappo.controlplane.model.command;

import com.mappo.controlplane.jooq.enums.MappoArmDeploymentMode;
import com.mappo.controlplane.jooq.enums.MappoDeploymentMode;
import com.mappo.controlplane.jooq.enums.MappoDeploymentScope;
import java.util.List;
import java.util.Map;

public record CreateReleaseCommand(
    String templateSpecId,
    String templateSpecVersion,
    MappoDeploymentMode deploymentMode,
    String templateSpecVersionId,
    MappoDeploymentScope deploymentScope,
    MappoArmDeploymentMode armDeploymentMode,
    boolean whatIfOnCanary,
    boolean verifyAfterDeploy,
    Map<String, String> parameterDefaults,
    String releaseNotes,
    List<String> verificationHints
) {
    public CreateReleaseCommand {
        parameterDefaults = parameterDefaults == null ? Map.of() : parameterDefaults;
        verificationHints = verificationHints == null ? List.of() : verificationHints;
    }
}
