package com.mappo.controlplane.application.secretreference;

public interface SecretBackendResolver {

    boolean supports(String reference);

    String resolve(String reference);
}
