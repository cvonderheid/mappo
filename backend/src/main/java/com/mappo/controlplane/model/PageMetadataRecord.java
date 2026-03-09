package com.mappo.controlplane.model;

public record PageMetadataRecord(
    Integer page,
    Integer size,
    Long totalItems,
    Integer totalPages
) {
}
