package com.mappo.controlplane.model;

public record RunPreviewPropertyChangeRecord(
    String path,
    String changeType
) {
}
