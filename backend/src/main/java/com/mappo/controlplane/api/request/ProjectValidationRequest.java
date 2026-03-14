package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.mappo.controlplane.model.ProjectValidationScope;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectValidationRequest(
    List<ProjectValidationScope> scopes,
    String targetId
) {
}

