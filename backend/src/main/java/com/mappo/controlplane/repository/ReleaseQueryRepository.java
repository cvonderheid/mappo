package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.RELEASES;
import static com.mappo.controlplane.jooq.Tables.RELEASE_EXTERNAL_INPUT_ENTRIES;
import static com.mappo.controlplane.jooq.Tables.RELEASE_PARAMETER_DEFAULTS;
import static com.mappo.controlplane.jooq.Tables.RELEASE_VERIFICATION_HINTS;

import com.mappo.controlplane.jooq.enums.MappoArmDeploymentMode;
import com.mappo.controlplane.model.ReleaseExecutionSettingsRecord;
import com.mappo.controlplane.model.ReleaseRecord;
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
public class ReleaseQueryRepository {

    private final DSLContext dsl;

    public List<ReleaseRecord> listReleases() {
        return listReleases(null);
    }

    public List<ReleaseRecord> listReleases(String projectId) {
        var select = dsl.select(
                RELEASES.ID,
                RELEASES.PROJECT_ID,
                RELEASES.SOURCE_REF,
                RELEASES.SOURCE_VERSION,
                RELEASES.SOURCE_TYPE,
                RELEASES.SOURCE_VERSION_REF,
                RELEASES.DEPLOYMENT_SCOPE,
                RELEASES.ARM_DEPLOYMENT_MODE,
                RELEASES.WHAT_IF_ON_CANARY,
                RELEASES.VERIFY_AFTER_DEPLOY,
                RELEASES.RELEASE_NOTES,
                RELEASES.CREATED_AT
            )
            .from(RELEASES);

        var rows = (projectId != null && !projectId.isBlank()
            ? select.where(RELEASES.PROJECT_ID.eq(projectId))
            : select)
            .orderBy(RELEASES.CREATED_AT.desc())
            .fetch();

        List<String> ids = rows.stream().map(row -> row.get(RELEASES.ID)).toList();
        Map<String, Map<String, String>> defaults = loadDefaults(ids);
        Map<String, Map<String, String>> externalInputs = loadExternalInputs(ids);
        Map<String, List<String>> hints = loadHints(ids);

        List<ReleaseRecord> releases = new ArrayList<>(rows.size());
        for (Record row : rows) {
            String id = row.get(RELEASES.ID);
            releases.add(
                toReleaseRecord(
                    row,
                    defaults.getOrDefault(id, Map.of()),
                    externalInputs.getOrDefault(id, Map.of()),
                    hints.getOrDefault(id, List.of())
                )
            );
        }
        return releases;
    }

    public Optional<ReleaseRecord> getRelease(String releaseId) {
        Record row = dsl.select(
                RELEASES.ID,
                RELEASES.PROJECT_ID,
                RELEASES.SOURCE_REF,
                RELEASES.SOURCE_VERSION,
                RELEASES.SOURCE_TYPE,
                RELEASES.SOURCE_VERSION_REF,
                RELEASES.DEPLOYMENT_SCOPE,
                RELEASES.ARM_DEPLOYMENT_MODE,
                RELEASES.WHAT_IF_ON_CANARY,
                RELEASES.VERIFY_AFTER_DEPLOY,
                RELEASES.RELEASE_NOTES,
                RELEASES.CREATED_AT
            )
            .from(RELEASES)
            .where(RELEASES.ID.eq(releaseId))
            .fetchOne();

        if (row == null) {
            return Optional.empty();
        }

        Map<String, String> defaults = loadDefaults(List.of(releaseId)).getOrDefault(releaseId, Map.of());
        Map<String, String> externalInputs = loadExternalInputs(List.of(releaseId)).getOrDefault(releaseId, Map.of());
        List<String> hints = loadHints(List.of(releaseId)).getOrDefault(releaseId, List.of());
        return Optional.of(toReleaseRecord(row, defaults, externalInputs, hints));
    }

    private Map<String, Map<String, String>> loadDefaults(List<String> releaseIds) {
        if (releaseIds == null || releaseIds.isEmpty()) {
            return Map.of();
        }

        var rows = dsl.select(
                RELEASE_PARAMETER_DEFAULTS.RELEASE_ID,
                RELEASE_PARAMETER_DEFAULTS.PARAM_KEY,
                RELEASE_PARAMETER_DEFAULTS.PARAM_VALUE
            )
            .from(RELEASE_PARAMETER_DEFAULTS)
            .where(RELEASE_PARAMETER_DEFAULTS.RELEASE_ID.in(releaseIds))
            .fetch();

        Map<String, Map<String, String>> defaults = new LinkedHashMap<>();
        for (Record row : rows) {
            String releaseId = row.get(RELEASE_PARAMETER_DEFAULTS.RELEASE_ID);
            defaults.computeIfAbsent(releaseId, ignored -> new LinkedHashMap<>())
                .put(row.get(RELEASE_PARAMETER_DEFAULTS.PARAM_KEY), row.get(RELEASE_PARAMETER_DEFAULTS.PARAM_VALUE));
        }
        return defaults;
    }

    private Map<String, Map<String, String>> loadExternalInputs(List<String> releaseIds) {
        if (releaseIds == null || releaseIds.isEmpty()) {
            return Map.of();
        }

        var rows = dsl.select(
                RELEASE_EXTERNAL_INPUT_ENTRIES.RELEASE_ID,
                RELEASE_EXTERNAL_INPUT_ENTRIES.INPUT_KEY,
                RELEASE_EXTERNAL_INPUT_ENTRIES.INPUT_VALUE
            )
            .from(RELEASE_EXTERNAL_INPUT_ENTRIES)
            .where(RELEASE_EXTERNAL_INPUT_ENTRIES.RELEASE_ID.in(releaseIds))
            .fetch();

        Map<String, Map<String, String>> inputs = new LinkedHashMap<>();
        for (Record row : rows) {
            String releaseId = row.get(RELEASE_EXTERNAL_INPUT_ENTRIES.RELEASE_ID);
            inputs.computeIfAbsent(releaseId, ignored -> new LinkedHashMap<>())
                .put(
                    row.get(RELEASE_EXTERNAL_INPUT_ENTRIES.INPUT_KEY),
                    row.get(RELEASE_EXTERNAL_INPUT_ENTRIES.INPUT_VALUE)
                );
        }
        return inputs;
    }

    private Map<String, List<String>> loadHints(List<String> releaseIds) {
        if (releaseIds == null || releaseIds.isEmpty()) {
            return Map.of();
        }

        var rows = dsl.select(
                RELEASE_VERIFICATION_HINTS.RELEASE_ID,
                RELEASE_VERIFICATION_HINTS.HINT
            )
            .from(RELEASE_VERIFICATION_HINTS)
            .where(RELEASE_VERIFICATION_HINTS.RELEASE_ID.in(releaseIds))
            .orderBy(RELEASE_VERIFICATION_HINTS.POSITION.asc())
            .fetch();

        Map<String, List<String>> hints = new LinkedHashMap<>();
        for (Record row : rows) {
            String releaseId = row.get(RELEASE_VERIFICATION_HINTS.RELEASE_ID);
            hints.computeIfAbsent(releaseId, ignored -> new ArrayList<>())
                .add(row.get(RELEASE_VERIFICATION_HINTS.HINT));
        }
        return hints;
    }

    private ReleaseRecord toReleaseRecord(
        Record row,
        Map<String, String> defaults,
        Map<String, String> externalInputs,
        List<String> hints
    ) {
        return new ReleaseRecord(
            row.get(RELEASES.ID),
            row.get(RELEASES.PROJECT_ID),
            row.get(RELEASES.SOURCE_REF),
            row.get(RELEASES.SOURCE_VERSION),
            row.get(RELEASES.SOURCE_TYPE),
            row.get(RELEASES.SOURCE_VERSION_REF),
            row.get(RELEASES.DEPLOYMENT_SCOPE),
            executionSettings(row),
            defaults,
            externalInputs,
            row.get(RELEASES.RELEASE_NOTES),
            hints,
            row.get(RELEASES.CREATED_AT)
        );
    }

    private ReleaseExecutionSettingsRecord executionSettings(Record row) {
        return new ReleaseExecutionSettingsRecord(
            enumOrDefault(row.get(RELEASES.ARM_DEPLOYMENT_MODE), MappoArmDeploymentMode.incremental),
            Boolean.TRUE.equals(row.get(RELEASES.WHAT_IF_ON_CANARY)),
            !Boolean.FALSE.equals(row.get(RELEASES.VERIFY_AFTER_DEPLOY))
        );
    }

    private <T> T enumOrDefault(T value, T fallback) {
        return value == null ? fallback : value;
    }
}
