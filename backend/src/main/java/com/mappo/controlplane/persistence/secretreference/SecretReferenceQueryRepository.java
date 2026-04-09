package com.mappo.controlplane.persistence.secretreference;

import static com.mappo.controlplane.jooq.Tables.PROVIDER_CONNECTIONS;
import static com.mappo.controlplane.jooq.Tables.RELEASE_INGEST_ENDPOINTS;
import static com.mappo.controlplane.jooq.Tables.SECRET_REFERENCES;

import com.mappo.controlplane.domain.secretreference.SecretReferenceModeType;
import com.mappo.controlplane.domain.secretreference.SecretReferenceProviderType;
import com.mappo.controlplane.domain.secretreference.SecretReferenceUsageType;
import com.mappo.controlplane.model.SecretReferenceLinkedDeploymentConnectionRecord;
import com.mappo.controlplane.model.SecretReferenceLinkedReleaseSourceRecord;
import com.mappo.controlplane.model.SecretReferenceRecord;
import com.mappo.controlplane.service.secretreference.SecretReferenceResolver;
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
public class SecretReferenceQueryRepository {

    private final DSLContext dsl;

    public List<SecretReferenceRecord> listSecretReferences() {
        var rows = dsl.select(
                SECRET_REFERENCES.ID,
                SECRET_REFERENCES.NAME,
                SECRET_REFERENCES.PROVIDER,
                SECRET_REFERENCES.USAGE,
                SECRET_REFERENCES.MODE,
                SECRET_REFERENCES.BACKEND_REF,
                SECRET_REFERENCES.CREATED_AT,
                SECRET_REFERENCES.UPDATED_AT
            )
            .from(SECRET_REFERENCES)
            .orderBy(SECRET_REFERENCES.NAME.asc(), SECRET_REFERENCES.ID.asc())
            .fetch();
        if (rows.isEmpty()) {
            return List.of();
        }
        List<String> secretReferenceIds = secretReferenceIds(rows);
        Map<String, List<SecretReferenceLinkedDeploymentConnectionRecord>> linkedDeploymentConnections =
            loadLinkedDeploymentConnections(secretReferenceIds);
        Map<String, List<SecretReferenceLinkedReleaseSourceRecord>> linkedReleaseSources =
            loadLinkedReleaseSources(secretReferenceIds);
        List<SecretReferenceRecord> records = new ArrayList<>(rows.size());
        for (Record row : rows) {
            String secretReferenceId = normalize(row.get(SECRET_REFERENCES.ID));
            records.add(toRecord(
                row,
                linkedDeploymentConnections.getOrDefault(secretReferenceId, List.of()),
                linkedReleaseSources.getOrDefault(secretReferenceId, List.of())
            ));
        }
        return records;
    }

    public Optional<SecretReferenceRecord> getSecretReference(String secretReferenceId) {
        String normalizedSecretReferenceId = normalize(secretReferenceId);
        if (normalizedSecretReferenceId.isBlank()) {
            return Optional.empty();
        }
        Record row = dsl.select(
                SECRET_REFERENCES.ID,
                SECRET_REFERENCES.NAME,
                SECRET_REFERENCES.PROVIDER,
                SECRET_REFERENCES.USAGE,
                SECRET_REFERENCES.MODE,
                SECRET_REFERENCES.BACKEND_REF,
                SECRET_REFERENCES.CREATED_AT,
                SECRET_REFERENCES.UPDATED_AT
            )
            .from(SECRET_REFERENCES)
            .where(SECRET_REFERENCES.ID.eq(normalizedSecretReferenceId))
            .fetchOne();
        if (row == null) {
            return Optional.empty();
        }
        return Optional.of(
            toRecord(
                row,
                loadLinkedDeploymentConnections(List.of(normalizedSecretReferenceId)).getOrDefault(normalizedSecretReferenceId, List.of()),
                loadLinkedReleaseSources(List.of(normalizedSecretReferenceId)).getOrDefault(normalizedSecretReferenceId, List.of())
            )
        );
    }

    public boolean exists(String secretReferenceId) {
        String normalizedSecretReferenceId = normalize(secretReferenceId);
        if (normalizedSecretReferenceId.isBlank()) {
            return false;
        }
        return dsl.fetchExists(
            dsl.selectOne()
                .from(SECRET_REFERENCES)
                .where(SECRET_REFERENCES.ID.eq(normalizedSecretReferenceId))
        );
    }

    private Map<String, List<SecretReferenceLinkedDeploymentConnectionRecord>> loadLinkedDeploymentConnections(
        List<String> secretReferenceIds
    ) {
        if (secretReferenceIds.isEmpty()) {
            return Map.of();
        }
        List<String> tokens = secretReferenceIds.stream().map((id) -> SecretReferenceResolver.SECRET_REFERENCE_PREFIX + id).toList();
        Map<String, List<SecretReferenceLinkedDeploymentConnectionRecord>> index = new LinkedHashMap<>();
        for (String id : secretReferenceIds) {
            index.put(id, new ArrayList<>());
        }
        var rows = dsl.select(
                PROVIDER_CONNECTIONS.PERSONAL_ACCESS_TOKEN_REF,
                PROVIDER_CONNECTIONS.ID,
                PROVIDER_CONNECTIONS.NAME
            )
            .from(PROVIDER_CONNECTIONS)
            .where(PROVIDER_CONNECTIONS.PERSONAL_ACCESS_TOKEN_REF.in(tokens))
            .orderBy(PROVIDER_CONNECTIONS.NAME.asc(), PROVIDER_CONNECTIONS.ID.asc())
            .fetch();
        for (Record row : rows) {
            String secretReferenceId = extractSecretReferenceId(row.get(PROVIDER_CONNECTIONS.PERSONAL_ACCESS_TOKEN_REF));
            if (secretReferenceId.isBlank()) {
                continue;
            }
            index.computeIfAbsent(secretReferenceId, ignored -> new ArrayList<>()).add(
                new SecretReferenceLinkedDeploymentConnectionRecord(
                    normalize(row.get(PROVIDER_CONNECTIONS.ID)),
                    normalize(row.get(PROVIDER_CONNECTIONS.NAME))
                )
            );
        }
        return immutableIndex(index);
    }

    private Map<String, List<SecretReferenceLinkedReleaseSourceRecord>> loadLinkedReleaseSources(List<String> secretReferenceIds) {
        if (secretReferenceIds.isEmpty()) {
            return Map.of();
        }
        List<String> tokens = secretReferenceIds.stream().map((id) -> SecretReferenceResolver.SECRET_REFERENCE_PREFIX + id).toList();
        Map<String, List<SecretReferenceLinkedReleaseSourceRecord>> index = new LinkedHashMap<>();
        for (String id : secretReferenceIds) {
            index.put(id, new ArrayList<>());
        }
        var rows = dsl.select(
                RELEASE_INGEST_ENDPOINTS.SECRET_REF,
                RELEASE_INGEST_ENDPOINTS.ID,
                RELEASE_INGEST_ENDPOINTS.NAME
            )
            .from(RELEASE_INGEST_ENDPOINTS)
            .where(RELEASE_INGEST_ENDPOINTS.SECRET_REF.in(tokens))
            .orderBy(RELEASE_INGEST_ENDPOINTS.NAME.asc(), RELEASE_INGEST_ENDPOINTS.ID.asc())
            .fetch();
        for (Record row : rows) {
            String secretReferenceId = extractSecretReferenceId(row.get(RELEASE_INGEST_ENDPOINTS.SECRET_REF));
            if (secretReferenceId.isBlank()) {
                continue;
            }
            index.computeIfAbsent(secretReferenceId, ignored -> new ArrayList<>()).add(
                new SecretReferenceLinkedReleaseSourceRecord(
                    normalize(row.get(RELEASE_INGEST_ENDPOINTS.ID)),
                    normalize(row.get(RELEASE_INGEST_ENDPOINTS.NAME))
                )
            );
        }
        return immutableIndex(index);
    }

    private <T> Map<String, List<T>> immutableIndex(Map<String, List<T>> index) {
        Map<String, List<T>> immutable = new LinkedHashMap<>();
        index.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
        return immutable;
    }

    private List<String> secretReferenceIds(Iterable<? extends Record> rows) {
        List<String> ids = new ArrayList<>();
        for (Record row : rows) {
            String secretReferenceId = normalize(row.get(SECRET_REFERENCES.ID));
            if (!secretReferenceId.isBlank()) {
                ids.add(secretReferenceId);
            }
        }
        return ids;
    }

    private SecretReferenceRecord toRecord(
        Record row,
        List<SecretReferenceLinkedDeploymentConnectionRecord> linkedDeploymentConnections,
        List<SecretReferenceLinkedReleaseSourceRecord> linkedReleaseSources
    ) {
        return new SecretReferenceRecord(
            normalize(row.get(SECRET_REFERENCES.ID)),
            normalize(row.get(SECRET_REFERENCES.NAME)),
            SecretReferenceProviderType.valueOf(normalize(row.get(SECRET_REFERENCES.PROVIDER))),
            SecretReferenceUsageType.valueOf(normalize(row.get(SECRET_REFERENCES.USAGE))),
            SecretReferenceModeType.valueOf(normalize(row.get(SECRET_REFERENCES.MODE))),
            normalize(row.get(SECRET_REFERENCES.BACKEND_REF)),
            linkedDeploymentConnections,
            linkedReleaseSources,
            row.get(SECRET_REFERENCES.CREATED_AT),
            row.get(SECRET_REFERENCES.UPDATED_AT)
        );
    }

    private String extractSecretReferenceId(String reference) {
        String normalized = normalize(reference);
        if (!normalized.startsWith(SecretReferenceResolver.SECRET_REFERENCE_PREFIX)) {
            return "";
        }
        return normalize(normalized.substring(SecretReferenceResolver.SECRET_REFERENCE_PREFIX.length()));
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
