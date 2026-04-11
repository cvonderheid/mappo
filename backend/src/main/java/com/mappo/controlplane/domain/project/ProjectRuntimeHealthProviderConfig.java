package com.mappo.controlplane.domain.project;

public interface ProjectRuntimeHealthProviderConfig {

    String path();

    int expectedStatus();

    long timeoutMs();
}
