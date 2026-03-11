package com.mappo.controlplane.model;

public record TargetVerificationResultRecord(
    boolean succeeded,
    String message,
    StageErrorRecord error
) {
    public static TargetVerificationResultRecord success(String message) {
        return new TargetVerificationResultRecord(true, message, null);
    }

    public static TargetVerificationResultRecord failure(String message, StageErrorRecord error) {
        return new TargetVerificationResultRecord(false, message, error);
    }
}
