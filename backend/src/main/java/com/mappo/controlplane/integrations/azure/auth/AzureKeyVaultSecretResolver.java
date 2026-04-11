package com.mappo.controlplane.integrations.azure.auth;

import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.SecretClientBuilder;
import com.mappo.controlplane.application.secretreference.SecretBackendResolver;
import com.mappo.controlplane.application.secretreference.SecretReferencePrefixes;
import com.mappo.controlplane.config.MappoProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AzureKeyVaultSecretResolver implements SecretBackendResolver {

    public static final String KEY_VAULT_PREFIX = SecretReferencePrefixes.KEY_VAULT_REFERENCE_PREFIX;

    private final MappoProperties properties;
    private final AzureExecutorClient azureExecutorClient;

    private volatile SecretClient secretClient;

    public boolean isConfigured() {
        return !normalize(properties.getAzure().getKeyVaultUrl()).isBlank() && azureExecutorClient.isConfigured();
    }

    @Override
    public boolean supports(String reference) {
        return !secretName(reference).isBlank();
    }

    @Override
    public String resolve(String secretReference) {
        String secretName = secretName(secretReference);
        if (secretName.isBlank() || !isConfigured()) {
            return "";
        }
        try {
            return normalize(secretClient().getSecret(secretName).getValue());
        } catch (RuntimeException exception) {
            return "";
        }
    }

    public String secretName(String secretReference) {
        String normalizedReference = normalize(secretReference);
        if (!normalizedReference.startsWith(KEY_VAULT_PREFIX)) {
            return "";
        }
        return normalize(normalizedReference.substring(KEY_VAULT_PREFIX.length()));
    }

    private SecretClient secretClient() {
        SecretClient existing = secretClient;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (secretClient == null) {
                secretClient = new SecretClientBuilder()
                    .vaultUrl(normalize(properties.getAzure().getKeyVaultUrl()))
                    .credential(azureExecutorClient.createTokenCredential(properties.getAzure().getTenantId()))
                    .buildClient();
            }
            return secretClient;
        }
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
