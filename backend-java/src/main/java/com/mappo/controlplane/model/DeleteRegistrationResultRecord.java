package com.mappo.controlplane.model;

public record DeleteRegistrationResultRecord(
    String targetId,
    boolean deleted
) {
}
