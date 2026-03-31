package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.PROVIDER_CONNECTIONS;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.jooq.enums.MappoReleaseIngestProvider;
import com.mappo.controlplane.service.providerconnection.ProviderConnectionMutationRecord;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProviderConnectionCommandRepository {

    private final DSLContext dsl;

    public void createConnection(ProviderConnectionMutationRecord mutation) {
        try {
            dsl.insertInto(PROVIDER_CONNECTIONS)
                .set(PROVIDER_CONNECTIONS.ID, normalize(mutation.id()))
                .set(PROVIDER_CONNECTIONS.NAME, normalize(mutation.name()))
                .set(PROVIDER_CONNECTIONS.PROVIDER, requiredProvider(mutation))
                .set(PROVIDER_CONNECTIONS.ENABLED, mutation.enabled())
                .set(PROVIDER_CONNECTIONS.ORGANIZATION_FILTER, optional(mutation.organizationUrl()))
                .set(PROVIDER_CONNECTIONS.PERSONAL_ACCESS_TOKEN_REF, optional(mutation.personalAccessTokenRef()))
                .set(PROVIDER_CONNECTIONS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .execute();
        } catch (DataAccessException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "provider connection already exists: " + normalize(mutation.id()));
        }
    }

    public void updateConnection(ProviderConnectionMutationRecord mutation) {
        int updated = dsl.update(PROVIDER_CONNECTIONS)
            .set(PROVIDER_CONNECTIONS.NAME, normalize(mutation.name()))
            .set(PROVIDER_CONNECTIONS.PROVIDER, requiredProvider(mutation))
            .set(PROVIDER_CONNECTIONS.ENABLED, mutation.enabled())
            .set(PROVIDER_CONNECTIONS.ORGANIZATION_FILTER, optional(mutation.organizationUrl()))
            .set(PROVIDER_CONNECTIONS.PERSONAL_ACCESS_TOKEN_REF, optional(mutation.personalAccessTokenRef()))
            .set(PROVIDER_CONNECTIONS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(PROVIDER_CONNECTIONS.ID.eq(normalize(mutation.id())))
            .execute();
        if (updated <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "provider connection not found: " + normalize(mutation.id()));
        }
    }

    public void deleteConnection(String connectionId) {
        String normalizedConnectionId = normalize(connectionId);
        int deleted = dsl.deleteFrom(PROVIDER_CONNECTIONS)
            .where(PROVIDER_CONNECTIONS.ID.eq(normalizedConnectionId))
            .execute();
        if (deleted <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "provider connection not found: " + normalizedConnectionId);
        }
    }

    private MappoReleaseIngestProvider requiredProvider(ProviderConnectionMutationRecord mutation) {
        String literal = mutation.provider() == null ? "" : mutation.provider().name();
        MappoReleaseIngestProvider provider = MappoReleaseIngestProvider.lookupLiteral(literal);
        if (provider == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid provider connection provider");
        }
        return provider;
    }

    private String optional(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
