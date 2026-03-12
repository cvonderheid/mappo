package com.mappo.controlplane.domain.access;

import com.mappo.controlplane.model.StageErrorRecord;

public record TargetAccessValidation(
    boolean valid,
    String message,
    StageErrorRecord error,
    ResolvedTargetAccessContext accessContext
) {
    public static TargetAccessValidation success(String message) {
        return new TargetAccessValidation(true, normalize(message), null, null);
    }

    public static TargetAccessValidation success(String message, ResolvedTargetAccessContext accessContext) {
        return new TargetAccessValidation(true, normalize(message), null, accessContext);
    }

    public static TargetAccessValidation failure(String message, StageErrorRecord error) {
        return new TargetAccessValidation(false, normalize(message), error, null);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
