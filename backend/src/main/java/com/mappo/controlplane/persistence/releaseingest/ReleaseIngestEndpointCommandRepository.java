package com.mappo.controlplane.persistence.releaseingest;

import static com.mappo.controlplane.jooq.Tables.RELEASE_INGEST_ENDPOINTS;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.jooq.enums.MappoReleaseIngestProvider;
import com.mappo.controlplane.service.releaseingest.ReleaseIngestEndpointMutationRecord;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReleaseIngestEndpointCommandRepository {

    private final DSLContext dsl;

    public void createEndpoint(ReleaseIngestEndpointMutationRecord mutation) {
        try {
            dsl.insertInto(RELEASE_INGEST_ENDPOINTS)
                .set(RELEASE_INGEST_ENDPOINTS.ID, normalize(mutation.id()))
                .set(RELEASE_INGEST_ENDPOINTS.NAME, normalize(mutation.name()))
                .set(RELEASE_INGEST_ENDPOINTS.PROVIDER, requiredProvider(mutation))
                .set(RELEASE_INGEST_ENDPOINTS.ENABLED, mutation.enabled())
                .set(RELEASE_INGEST_ENDPOINTS.SECRET_REF, optional(mutation.secretRef()))
                .set(RELEASE_INGEST_ENDPOINTS.REPO_FILTER, optional(mutation.repoFilter()))
                .set(RELEASE_INGEST_ENDPOINTS.BRANCH_FILTER, optional(mutation.branchFilter()))
                .set(RELEASE_INGEST_ENDPOINTS.PIPELINE_ID_FILTER, optional(mutation.pipelineIdFilter()))
                .set(RELEASE_INGEST_ENDPOINTS.MANIFEST_PATH, optional(mutation.manifestPath()))
                .set(RELEASE_INGEST_ENDPOINTS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .execute();
        } catch (DataAccessException exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release source already exists: " + normalize(mutation.id()));
        }
    }

    public void updateEndpoint(ReleaseIngestEndpointMutationRecord mutation) {
        int updated = dsl.update(RELEASE_INGEST_ENDPOINTS)
            .set(RELEASE_INGEST_ENDPOINTS.NAME, normalize(mutation.name()))
            .set(RELEASE_INGEST_ENDPOINTS.PROVIDER, requiredProvider(mutation))
            .set(RELEASE_INGEST_ENDPOINTS.ENABLED, mutation.enabled())
            .set(RELEASE_INGEST_ENDPOINTS.SECRET_REF, optional(mutation.secretRef()))
            .set(RELEASE_INGEST_ENDPOINTS.REPO_FILTER, optional(mutation.repoFilter()))
            .set(RELEASE_INGEST_ENDPOINTS.BRANCH_FILTER, optional(mutation.branchFilter()))
            .set(RELEASE_INGEST_ENDPOINTS.PIPELINE_ID_FILTER, optional(mutation.pipelineIdFilter()))
            .set(RELEASE_INGEST_ENDPOINTS.MANIFEST_PATH, optional(mutation.manifestPath()))
            .set(RELEASE_INGEST_ENDPOINTS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(RELEASE_INGEST_ENDPOINTS.ID.eq(normalize(mutation.id())))
            .execute();
        if (updated <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release source not found: " + normalize(mutation.id()));
        }
    }

    public void deleteEndpoint(String endpointId) {
        String normalizedEndpointId = normalize(endpointId);
        int deleted = dsl.deleteFrom(RELEASE_INGEST_ENDPOINTS)
            .where(RELEASE_INGEST_ENDPOINTS.ID.eq(normalizedEndpointId))
            .execute();
        if (deleted <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "release source not found: " + normalizedEndpointId);
        }
    }

    private MappoReleaseIngestProvider requiredProvider(ReleaseIngestEndpointMutationRecord mutation) {
        String literal = mutation.provider() == null ? "" : mutation.provider().name();
        MappoReleaseIngestProvider provider = MappoReleaseIngestProvider.lookupLiteral(literal);
        if (provider == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "invalid release source provider");
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
