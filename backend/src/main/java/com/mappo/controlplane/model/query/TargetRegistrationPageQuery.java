package com.mappo.controlplane.model.query;

public record TargetRegistrationPageQuery(
    Integer page,
    Integer size,
    String targetId,
    String ring,
    String region,
    String tier
) {
}
