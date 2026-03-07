package com.mappo.controlplane.api.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ReleaseManifestIngestRequest(
    String repo,
    String path,
    String ref,
    Boolean allowDuplicates
) {
}
