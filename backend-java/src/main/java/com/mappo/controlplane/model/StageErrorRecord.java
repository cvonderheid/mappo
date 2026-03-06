package com.mappo.controlplane.model;

import java.util.Map;

public record StageErrorRecord(
    String code,
    String message,
    Map<String, Object> details
) {
}
