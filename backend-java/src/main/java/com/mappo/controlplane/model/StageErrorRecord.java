package com.mappo.controlplane.model;

public record StageErrorRecord(
    String code,
    String message,
    StageErrorDetailsRecord details
) {
}
