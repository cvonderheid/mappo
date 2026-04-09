package com.mappo.controlplane.application.project.config;

public interface ProjectConfigDescriptor<K, C> {

    K key();

    Class<? extends C> configType();

    C defaults();
}
