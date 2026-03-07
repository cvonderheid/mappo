package com.mappo.controlplane.service.release;

public interface ReleaseManifestSourceClient {

    String fetchGithubManifest(String repo, String path, String ref);
}
