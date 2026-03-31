package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.PROJECTS;
import static com.mappo.controlplane.jooq.Tables.PROVIDER_CONNECTIONS;

import com.mappo.controlplane.domain.providerconnection.ProviderConnectionProviderType;
import com.mappo.controlplane.infrastructure.pipeline.ado.AzureDevOpsUrlNormalizer;
import com.mappo.controlplane.jooq.enums.MappoReleaseIngestProvider;
import com.mappo.controlplane.model.ProviderConnectionLinkedProjectRecord;
import com.mappo.controlplane.model.ProviderConnectionRecord;
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
public class ProviderConnectionQueryRepository {

    private final DSLContext dsl;

    public List<ProviderConnectionRecord> listConnections() {
        var rows = dsl.select(
                PROVIDER_CONNECTIONS.ID,
                PROVIDER_CONNECTIONS.NAME,
                PROVIDER_CONNECTIONS.PROVIDER,
                PROVIDER_CONNECTIONS.ENABLED,
                PROVIDER_CONNECTIONS.ORGANIZATION_FILTER,
                PROVIDER_CONNECTIONS.PERSONAL_ACCESS_TOKEN_REF,
                PROVIDER_CONNECTIONS.CREATED_AT,
                PROVIDER_CONNECTIONS.UPDATED_AT
            )
            .from(PROVIDER_CONNECTIONS)
            .orderBy(PROVIDER_CONNECTIONS.NAME.asc(), PROVIDER_CONNECTIONS.ID.asc())
            .fetch();
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, List<ProviderConnectionLinkedProjectRecord>> linkedProjectsByConnectionId = loadLinkedProjects(connectionIds(rows));
        List<ProviderConnectionRecord> records = new ArrayList<>(rows.size());
        for (Record row : rows) {
            String connectionId = row.get(PROVIDER_CONNECTIONS.ID);
            records.add(toRecord(row, linkedProjectsByConnectionId.getOrDefault(connectionId, List.of())));
        }
        return records;
    }

    public Optional<ProviderConnectionRecord> getConnection(String connectionId) {
        String normalizedConnectionId = normalize(connectionId);
        if (normalizedConnectionId.isBlank()) {
            return Optional.empty();
        }
        Record row = dsl.select(
                PROVIDER_CONNECTIONS.ID,
                PROVIDER_CONNECTIONS.NAME,
                PROVIDER_CONNECTIONS.PROVIDER,
                PROVIDER_CONNECTIONS.ENABLED,
                PROVIDER_CONNECTIONS.ORGANIZATION_FILTER,
                PROVIDER_CONNECTIONS.PERSONAL_ACCESS_TOKEN_REF,
                PROVIDER_CONNECTIONS.CREATED_AT,
                PROVIDER_CONNECTIONS.UPDATED_AT
            )
            .from(PROVIDER_CONNECTIONS)
            .where(PROVIDER_CONNECTIONS.ID.eq(normalizedConnectionId))
            .fetchOne();
        if (row == null) {
            return Optional.empty();
        }
        List<ProviderConnectionLinkedProjectRecord> linkedProjects = loadLinkedProjects(List.of(normalizedConnectionId))
            .getOrDefault(normalizedConnectionId, List.of());
        return Optional.of(toRecord(row, linkedProjects));
    }

    public boolean exists(String connectionId) {
        String normalizedConnectionId = normalize(connectionId);
        if (normalizedConnectionId.isBlank()) {
            return false;
        }
        return dsl.fetchExists(
            dsl.selectOne()
                .from(PROVIDER_CONNECTIONS)
                .where(PROVIDER_CONNECTIONS.ID.eq(normalizedConnectionId))
        );
    }

    private Map<String, List<ProviderConnectionLinkedProjectRecord>> loadLinkedProjects(List<String> connectionIds) {
        if (connectionIds.isEmpty()) {
            return Map.of();
        }
        Map<String, List<ProviderConnectionLinkedProjectRecord>> index = new LinkedHashMap<>();
        for (String connectionId : connectionIds) {
            index.put(connectionId, new ArrayList<>());
        }
        var rows = dsl.select(
                PROJECTS.PROVIDER_CONNECTION_ID,
                PROJECTS.ID,
                PROJECTS.NAME
            )
            .from(PROJECTS)
            .where(PROJECTS.PROVIDER_CONNECTION_ID.in(connectionIds))
            .orderBy(PROJECTS.NAME.asc(), PROJECTS.ID.asc())
            .fetch();
        for (Record row : rows) {
            String connectionId = normalize(row.get(PROJECTS.PROVIDER_CONNECTION_ID));
            if (connectionId.isBlank()) {
                continue;
            }
            List<ProviderConnectionLinkedProjectRecord> linkedProjects = index.get(connectionId);
            if (linkedProjects == null) {
                continue;
            }
            linkedProjects.add(new ProviderConnectionLinkedProjectRecord(
                row.get(PROJECTS.ID),
                row.get(PROJECTS.NAME)
            ));
        }
        Map<String, List<ProviderConnectionLinkedProjectRecord>> immutable = new LinkedHashMap<>();
        index.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
        return immutable;
    }

    private List<String> connectionIds(Iterable<? extends Record> rows) {
        List<String> ids = new ArrayList<>();
        for (Record row : rows) {
            String connectionId = normalize(row.get(PROVIDER_CONNECTIONS.ID));
            if (!connectionId.isBlank()) {
                ids.add(connectionId);
            }
        }
        return ids;
    }

    private ProviderConnectionRecord toRecord(
        Record row,
        List<ProviderConnectionLinkedProjectRecord> linkedProjects
    ) {
        MappoReleaseIngestProvider provider = row.get(PROVIDER_CONNECTIONS.PROVIDER);
        ProviderConnectionProviderType providerType = provider == null
            ? ProviderConnectionProviderType.azure_devops
            : ProviderConnectionProviderType.valueOf(provider.getLiteral());
        return new ProviderConnectionRecord(
            row.get(PROVIDER_CONNECTIONS.ID),
            row.get(PROVIDER_CONNECTIONS.NAME),
            providerType,
            Boolean.TRUE.equals(row.get(PROVIDER_CONNECTIONS.ENABLED)),
            providerType == ProviderConnectionProviderType.azure_devops
                ? AzureDevOpsUrlNormalizer.normalizeOrganizationUrl(row.get(PROVIDER_CONNECTIONS.ORGANIZATION_FILTER), "https://dev.azure.com")
                : "",
            row.get(PROVIDER_CONNECTIONS.PERSONAL_ACCESS_TOKEN_REF),
            linkedProjects,
            row.get(PROVIDER_CONNECTIONS.CREATED_AT),
            row.get(PROVIDER_CONNECTIONS.UPDATED_AT)
        );
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
