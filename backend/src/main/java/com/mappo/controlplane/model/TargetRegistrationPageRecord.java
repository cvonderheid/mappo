package com.mappo.controlplane.model;

import java.util.List;

public record TargetRegistrationPageRecord(
    List<TargetRegistrationRecord> items,
    PageMetadataRecord page
) {
}
