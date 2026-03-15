package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.PROJECTS;
import static com.mappo.controlplane.jooq.Tables.RELEASE_INGEST_ENDPOINTS;

import com.mappo.controlplane.domain.releaseingest.ReleaseIngestProviderType;
import com.mappo.controlplane.jooq.enums.MappoReleaseIngestProvider;
import com.mappo.controlplane.model.ReleaseIngestEndpointRecord;
import com.mappo.controlplane.model.ReleaseIngestLinkedProjectRecord;
import com.mappo.controlplane.util.JsonUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReleaseIngestEndpointQueryRepository {

    private final DSLContext dsl;
    private final JsonUtil jsonUtil;

    public List<ReleaseIngestEndpointRecord> listEndpoints() {
        var rows = dsl.select(
                RELEASE_INGEST_ENDPOINTS.ID,
                RELEASE_INGEST_ENDPOINTS.NAME,
                RELEASE_INGEST_ENDPOINTS.PROVIDER,
                RELEASE_INGEST_ENDPOINTS.ENABLED,
                RELEASE_INGEST_ENDPOINTS.SECRET_REF,
                RELEASE_INGEST_ENDPOINTS.REPO_FILTER,
                RELEASE_INGEST_ENDPOINTS.BRANCH_FILTER,
                RELEASE_INGEST_ENDPOINTS.PIPELINE_ID_FILTER,
                RELEASE_INGEST_ENDPOINTS.MANIFEST_PATH,
                RELEASE_INGEST_ENDPOINTS.SOURCE_CONFIG,
                RELEASE_INGEST_ENDPOINTS.CREATED_AT,
                RELEASE_INGEST_ENDPOINTS.UPDATED_AT
            )
            .from(RELEASE_INGEST_ENDPOINTS)
            .orderBy(RELEASE_INGEST_ENDPOINTS.NAME.asc(), RELEASE_INGEST_ENDPOINTS.ID.asc())
            .fetch();
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, List<ReleaseIngestLinkedProjectRecord>> linkedProjectsByEndpointId = loadLinkedProjects(endpointIds(rows));
        List<ReleaseIngestEndpointRecord> records = new ArrayList<>(rows.size());
        for (Record row : rows) {
            String endpointId = row.get(RELEASE_INGEST_ENDPOINTS.ID);
            records.add(toRecord(row, linkedProjectsByEndpointId.getOrDefault(endpointId, List.of())));
        }
        return records;
    }

    public Optional<ReleaseIngestEndpointRecord> getEndpoint(String endpointId) {
        String normalizedEndpointId = normalize(endpointId);
        if (normalizedEndpointId.isBlank()) {
            return Optional.empty();
        }
        Record row = dsl.select(
                RELEASE_INGEST_ENDPOINTS.ID,
                RELEASE_INGEST_ENDPOINTS.NAME,
                RELEASE_INGEST_ENDPOINTS.PROVIDER,
                RELEASE_INGEST_ENDPOINTS.ENABLED,
                RELEASE_INGEST_ENDPOINTS.SECRET_REF,
                RELEASE_INGEST_ENDPOINTS.REPO_FILTER,
                RELEASE_INGEST_ENDPOINTS.BRANCH_FILTER,
                RELEASE_INGEST_ENDPOINTS.PIPELINE_ID_FILTER,
                RELEASE_INGEST_ENDPOINTS.MANIFEST_PATH,
                RELEASE_INGEST_ENDPOINTS.SOURCE_CONFIG,
                RELEASE_INGEST_ENDPOINTS.CREATED_AT,
                RELEASE_INGEST_ENDPOINTS.UPDATED_AT
            )
            .from(RELEASE_INGEST_ENDPOINTS)
            .where(RELEASE_INGEST_ENDPOINTS.ID.eq(normalizedEndpointId))
            .fetchOne();
        if (row == null) {
            return Optional.empty();
        }
        List<ReleaseIngestLinkedProjectRecord> linkedProjects = loadLinkedProjects(List.of(normalizedEndpointId))
            .getOrDefault(normalizedEndpointId, List.of());
        return Optional.of(toRecord(row, linkedProjects));
    }

    public boolean exists(String endpointId) {
        String normalizedEndpointId = normalize(endpointId);
        if (normalizedEndpointId.isBlank()) {
            return false;
        }
        return dsl.fetchExists(
            dsl.selectOne()
                .from(RELEASE_INGEST_ENDPOINTS)
                .where(RELEASE_INGEST_ENDPOINTS.ID.eq(normalizedEndpointId))
        );
    }

    private Map<String, List<ReleaseIngestLinkedProjectRecord>> loadLinkedProjects(List<String> endpointIds) {
        if (endpointIds.isEmpty()) {
            return Map.of();
        }
        Map<String, List<ReleaseIngestLinkedProjectRecord>> index = new LinkedHashMap<>();
        for (String endpointId : endpointIds) {
            index.put(endpointId, new ArrayList<>());
        }
        var rows = dsl.select(
                PROJECTS.RELEASE_INGEST_ENDPOINT_ID,
                PROJECTS.ID,
                PROJECTS.NAME
            )
            .from(PROJECTS)
            .where(PROJECTS.RELEASE_INGEST_ENDPOINT_ID.in(endpointIds))
            .orderBy(PROJECTS.NAME.asc(), PROJECTS.ID.asc())
            .fetch();
        for (Record row : rows) {
            String endpointId = normalize(row.get(PROJECTS.RELEASE_INGEST_ENDPOINT_ID));
            if (endpointId.isBlank()) {
                continue;
            }
            List<ReleaseIngestLinkedProjectRecord> linkedProjects = index.get(endpointId);
            if (linkedProjects == null) {
                continue;
            }
            linkedProjects.add(new ReleaseIngestLinkedProjectRecord(
                row.get(PROJECTS.ID),
                row.get(PROJECTS.NAME)
            ));
        }
        Map<String, List<ReleaseIngestLinkedProjectRecord>> immutable = new LinkedHashMap<>();
        index.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
        return immutable;
    }

    private List<String> endpointIds(Iterable<? extends Record> rows) {
        List<String> ids = new ArrayList<>();
        for (Record row : rows) {
            String endpointId = normalize(row.get(RELEASE_INGEST_ENDPOINTS.ID));
            if (!endpointId.isBlank()) {
                ids.add(endpointId);
            }
        }
        return ids;
    }

    private ReleaseIngestEndpointRecord toRecord(Record row, List<ReleaseIngestLinkedProjectRecord> linkedProjects) {
        MappoReleaseIngestProvider provider = row.get(RELEASE_INGEST_ENDPOINTS.PROVIDER);
        ReleaseIngestProviderType providerType = provider == null
            ? ReleaseIngestProviderType.github
            : ReleaseIngestProviderType.valueOf(provider.getLiteral());
        return new ReleaseIngestEndpointRecord(
            row.get(RELEASE_INGEST_ENDPOINTS.ID),
            row.get(RELEASE_INGEST_ENDPOINTS.NAME),
            providerType,
            Boolean.TRUE.equals(row.get(RELEASE_INGEST_ENDPOINTS.ENABLED)),
            row.get(RELEASE_INGEST_ENDPOINTS.SECRET_REF),
            row.get(RELEASE_INGEST_ENDPOINTS.REPO_FILTER),
            row.get(RELEASE_INGEST_ENDPOINTS.BRANCH_FILTER),
            row.get(RELEASE_INGEST_ENDPOINTS.PIPELINE_ID_FILTER),
            row.get(RELEASE_INGEST_ENDPOINTS.MANIFEST_PATH),
            jsonUtil.toMap(row.get(RELEASE_INGEST_ENDPOINTS.SOURCE_CONFIG).data()),
            linkedProjects,
            row.get(RELEASE_INGEST_ENDPOINTS.CREATED_AT),
            row.get(RELEASE_INGEST_ENDPOINTS.UPDATED_AT)
        );
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
