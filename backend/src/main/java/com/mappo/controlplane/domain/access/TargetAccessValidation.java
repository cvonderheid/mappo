package com.mappo.controlplane.domain.access;

import com.mappo.controlplane.model.StageErrorRecord;

public record TargetAccessValidation(
    boolean valid,
    String message,
    StageErrorRecord error
) {
    public static TargetAccessValidation success(String message) {
        return new TargetAccessValidation(true, normalize(message), null);
    }

    public static TargetAccessValidation failure(String message, StageErrorRecord error) {
        return new TargetAccessValidation(false, normalize(message), error);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
