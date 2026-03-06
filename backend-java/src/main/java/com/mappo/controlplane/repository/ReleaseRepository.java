package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.RELEASES;
import static com.mappo.controlplane.jooq.Tables.RELEASE_PARAMETER_DEFAULTS;
import static com.mappo.controlplane.jooq.Tables.RELEASE_VERIFICATION_HINTS;

import com.mappo.controlplane.jooq.enums.MappoArmDeploymentMode;
import com.mappo.controlplane.jooq.enums.MappoDeploymentScope;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.model.ReleaseExecutionSettingsRecord;
import com.mappo.controlplane.model.command.CreateReleaseCommand;
import com.mappo.controlplane.model.ReleaseRecord;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReleaseRepository {

    private final DSLContext dsl;

    public List<ReleaseRecord> listReleases() {
        var rows = dsl.select(
                RELEASES.ID,
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
            .orderBy(RELEASES.CREATED_AT.desc())
            .fetch();

        List<String> ids = rows.stream().map(row -> row.get(RELEASES.ID)).toList();
        Map<String, Map<String, String>> defaults = loadDefaults(ids);
        Map<String, List<String>> hints = loadHints(ids);

        List<ReleaseRecord> releases = new ArrayList<>(rows.size());
        for (Record row : rows) {
            String id = row.get(RELEASES.ID);
            releases.add(toReleaseRecord(row, defaults.getOrDefault(id, Map.of()), hints.getOrDefault(id, List.of())));
        }
        return releases;
    }

    public Optional<ReleaseRecord> getRelease(String releaseId) {
        Record row = dsl.select(
                RELEASES.ID,
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
        List<String> hints = loadHints(List.of(releaseId)).getOrDefault(releaseId, List.of());
        return Optional.of(toReleaseRecord(row, defaults, hints));
    }

    public ReleaseRecord createRelease(CreateReleaseCommand request) {
        String releaseId = "rel-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        MappoReleaseSourceType sourceType = enumOrDefault(
            request.sourceType(),
            MappoReleaseSourceType.template_spec
        );
        MappoDeploymentScope deploymentScope = enumOrDefault(
            request.deploymentScope(),
            MappoDeploymentScope.resource_group
        );

        dsl.insertInto(RELEASES)
            .set(RELEASES.ID, releaseId)
            .set(RELEASES.SOURCE_REF, normalize(request.sourceRef()))
            .set(RELEASES.SOURCE_VERSION, normalize(request.sourceVersion()))
            .set(RELEASES.SOURCE_TYPE, sourceType)
            .set(RELEASES.SOURCE_VERSION_REF, nullableText(request.sourceVersionRef()))
            .set(RELEASES.DEPLOYMENT_SCOPE, deploymentScope)
            .set(
                RELEASES.ARM_DEPLOYMENT_MODE,
                enumOrDefault(request.armDeploymentMode(), MappoArmDeploymentMode.incremental)
            )
            .set(RELEASES.WHAT_IF_ON_CANARY, request.whatIfOnCanary())
            .set(RELEASES.VERIFY_AFTER_DEPLOY, request.verifyAfterDeploy())
            .set(RELEASES.RELEASE_NOTES, normalize(request.releaseNotes()))
            .set(RELEASES.CREATED_AT, now)
            .execute();

        Map<String, String> defaults = new LinkedHashMap<>();
        if (request.parameterDefaults() != null && !request.parameterDefaults().isEmpty()) {
            for (Map.Entry<String, String> entry : request.parameterDefaults().entrySet()) {
                String key = normalize(entry.getKey());
                if (!key.isBlank()) {
                    defaults.put(key, normalize(entry.getValue()));
                }
            }
        }

        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            dsl.insertInto(RELEASE_PARAMETER_DEFAULTS)
                .set(RELEASE_PARAMETER_DEFAULTS.RELEASE_ID, releaseId)
                .set(RELEASE_PARAMETER_DEFAULTS.PARAM_KEY, entry.getKey())
                .set(RELEASE_PARAMETER_DEFAULTS.PARAM_VALUE, entry.getValue())
                .execute();
        }

        List<String> hints = new ArrayList<>();
        if (request.verificationHints() != null && !request.verificationHints().isEmpty()) {
            for (String item : request.verificationHints()) {
                String hint = normalize(item);
                if (!hint.isBlank()) {
                    hints.add(hint);
                }
            }
        }

        for (int i = 0; i < hints.size(); i++) {
            dsl.insertInto(RELEASE_VERIFICATION_HINTS)
                .set(RELEASE_VERIFICATION_HINTS.RELEASE_ID, releaseId)
                .set(RELEASE_VERIFICATION_HINTS.POSITION, i)
                .set(RELEASE_VERIFICATION_HINTS.HINT, hints.get(i))
                .execute();
        }

        return getRelease(releaseId).orElseThrow();
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

    private ReleaseRecord toReleaseRecord(Record row, Map<String, String> defaults, List<String> hints) {
        return new ReleaseRecord(
            row.get(RELEASES.ID),
            row.get(RELEASES.SOURCE_REF),
            row.get(RELEASES.SOURCE_VERSION),
            row.get(RELEASES.SOURCE_TYPE),
            row.get(RELEASES.SOURCE_VERSION_REF),
            row.get(RELEASES.DEPLOYMENT_SCOPE),
            executionSettings(row),
            defaults,
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

    private String nullableText(Object value) {
        String text = normalize(value);
        return text.isBlank() ? null : text;
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private <T> T enumOrDefault(T value, T fallback) {
        return value == null ? fallback : value;
    }
}
