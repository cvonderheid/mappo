package com.mappo.controlplane.service.run;

import com.azure.core.credential.TokenCredential;
import com.azure.core.exception.HttpResponseException;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobClientBuilder;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mappo.controlplane.azure.AzureExecutorClient;
import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.model.ReleaseRecord;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ReleaseArtifactTemplateLoader {

    private static final TypeReference<Map<String, Object>> TEMPLATE_TYPE = new TypeReference<>() {};
    private static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder().findAndAddModules().build();

    private final AzureExecutorClient azureExecutorClient;
    private final MappoProperties properties;

    public Object loadTemplateDefinition(ReleaseRecord release) {
        URI sourceUri = sourceUri(release);
        String json = sourceUri.getRawQuery() == null || sourceUri.getRawQuery().isBlank()
            ? fetchWithBlobIdentity(sourceUri)
            : fetchWithHttp(sourceUri);
        return parseTemplate(json, sourceUri);
    }

    private URI sourceUri(ReleaseRecord release) {
        String sourceVersionRef = firstNonBlank(release.sourceVersionRef(), release.sourceRef());
        if (sourceVersionRef.isBlank()) {
            throw new IllegalArgumentException("release sourceVersionRef is required for deployment_stack execution");
        }
        URI parsed = URI.create(sourceVersionRef);
        if (parsed.getScheme() == null || parsed.getScheme().isBlank()) {
            throw new IllegalArgumentException("release sourceVersionRef must be an absolute URI for deployment_stack execution");
        }
        return parsed;
    }

    private String fetchWithHttp(URI sourceUri) {
        HttpRequest request = HttpRequest.newBuilder(sourceUri).GET().build();
        try {
            HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalArgumentException(
                    "release artifact fetch failed for " + sourceUri + " with HTTP " + response.statusCode() + "."
                );
            }
            return response.body();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("release artifact fetch failed for " + sourceUri + ".", error);
        } catch (IOException error) {
            throw new IllegalArgumentException("release artifact fetch failed for " + sourceUri + ".", error);
        }
    }

    private String fetchWithBlobIdentity(URI sourceUri) {
        BlobLocation location = BlobLocation.parse(sourceUri);
        TokenCredential credential = azureExecutorClient.createTokenCredential(properties.getAzureTenantId());
        BlobClient client = new BlobClientBuilder()
            .endpoint(location.endpoint())
            .containerName(location.containerName())
            .blobName(location.blobName())
            .credential(credential)
            .buildClient();
        try {
            return client.downloadContent().toString();
        } catch (HttpResponseException error) {
            throw new IllegalArgumentException(
                "release artifact fetch failed for " + sourceUri
                    + ". Ensure MAPPO's Azure principal has Storage Blob Data Reader access.",
                error
            );
        }
    }

    private Object parseTemplate(String json, URI sourceUri) {
        try {
            return OBJECT_MAPPER.readValue(json, TEMPLATE_TYPE);
        } catch (IOException error) {
            throw new IllegalArgumentException("release artifact " + sourceUri + " is not valid JSON.", error);
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return "";
    }

    record BlobLocation(String endpoint, String containerName, String blobName) {
        static BlobLocation parse(URI uri) {
            String rawPath = uri.getPath();
            if (rawPath == null || rawPath.isBlank() || "/".equals(rawPath)) {
                throw new IllegalArgumentException("release sourceVersionRef must include a blob container and path.");
            }
            String trimmed = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
            int firstSlash = trimmed.indexOf('/');
            if (firstSlash <= 0 || firstSlash == trimmed.length() - 1) {
                throw new IllegalArgumentException("release sourceVersionRef must include a blob container and blob path.");
            }
            String endpoint = uri.getScheme() + "://" + uri.getHost();
            if (uri.getPort() != -1) {
                endpoint += ":" + uri.getPort();
            }
            return new BlobLocation(endpoint, trimmed.substring(0, firstSlash), trimmed.substring(firstSlash + 1));
        }
    }
}
