package com.mappo.controlplane.model;

import java.util.List;

public record ProviderConnectionAdoProjectDiscoveryResultRecord(
    String connectionId,
    String organizationUrl,
    List<ProviderConnectionAdoProjectRecord> projects
) {
}
