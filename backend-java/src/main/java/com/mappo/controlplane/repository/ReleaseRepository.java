package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.RELEASES;
import static com.mappo.controlplane.jooq.Tables.RELEASE_PARAMETER_DEFAULTS;
import static com.mappo.controlplane.jooq.Tables.RELEASE_VERIFICATION_HINTS;

import com.mappo.controlplane.jooq.enums.MappoDeploymentMode;
import com.mappo.controlplane.jooq.enums.MappoDeploymentScope;
import com.mappo.controlplane.util.JsonUtil;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReleaseRepository {

    private final DSLContext dsl;
    private final JsonUtil jsonUtil;

    public List<Map<String, Object>> listReleases() {
        var rows = dsl.select(
                RELEASES.ID,
                RELEASES.TEMPLATE_SPEC_ID,
                RELEASES.TEMPLATE_SPEC_VERSION,
                RELEASES.DEPLOYMENT_MODE,
                RELEASES.TEMPLATE_SPEC_VERSION_ID,
                RELEASES.DEPLOYMENT_SCOPE,
                RELEASES.DEPLOYMENT_MODE_SETTINGS,
                RELEASES.RELEASE_NOTES,
                RELEASES.CREATED_AT
            )
            .from(RELEASES)
            .orderBy(RELEASES.CREATED_AT.desc())
            .fetch();

        List<String> ids = rows.stream().map(row -> row.get(RELEASES.ID)).toList();
        Map<String, Map<String, String>> defaults = loadDefaults(ids);
        Map<String, List<String>> hints = loadHints(ids);

        List<Map<String, Object>> releases = new ArrayList<>(rows.size());
        for (Record row : rows) {
            String id = row.get(RELEASES.ID);
            releases.add(toReleaseMap(row, defaults.getOrDefault(id, Map.of()), hints.getOrDefault(id, List.of())));
        }
        return releases;
    }

    public Map<String, Object> getRelease(String releaseId) {
        Record row = dsl.select(
                RELEASES.ID,
                RELEASES.TEMPLATE_SPEC_ID,
                RELEASES.TEMPLATE_SPEC_VERSION,
                RELEASES.DEPLOYMENT_MODE,
                RELEASES.TEMPLATE_SPEC_VERSION_ID,
                RELEASES.DEPLOYMENT_SCOPE,
                RELEASES.DEPLOYMENT_MODE_SETTINGS,
                RELEASES.RELEASE_NOTES,
                RELEASES.CREATED_AT
            )
            .from(RELEASES)
            .where(RELEASES.ID.eq(releaseId))
            .fetchOne();

        if (row == null) {
            return Map.of();
        }

        Map<String, String> defaults = loadDefaults(List.of(releaseId)).getOrDefault(releaseId, Map.of());
        List<String> hints = loadHints(List.of(releaseId)).getOrDefault(releaseId, List.of());
        return toReleaseMap(row, defaults, hints);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> createRelease(Map<String, Object> request) {
        String releaseId = "rel-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        String deploymentModeLiteral = normalize(request.getOrDefault("deployment_mode", "container_patch"));
        String deploymentScopeLiteral = normalize(request.getOrDefault("deployment_scope", "resource_group"));

        MappoDeploymentMode deploymentMode = enumOrDefault(
            MappoDeploymentMode.lookupLiteral(deploymentModeLiteral),
            MappoDeploymentMode.container_patch
        );
        MappoDeploymentScope deploymentScope = enumOrDefault(
            MappoDeploymentScope.lookupLiteral(deploymentScopeLiteral),
            MappoDeploymentScope.resource_group
        );

        dsl.insertInto(RELEASES)
            .set(RELEASES.ID, releaseId)
            .set(RELEASES.TEMPLATE_SPEC_ID, normalize(request.get("template_spec_id")))
            .set(RELEASES.TEMPLATE_SPEC_VERSION, normalize(request.get("template_spec_version")))
            .set(RELEASES.DEPLOYMENT_MODE, deploymentMode)
            .set(RELEASES.TEMPLATE_SPEC_VERSION_ID, nullableText(request.get("template_spec_version_id")))
            .set(RELEASES.DEPLOYMENT_SCOPE, deploymentScope)
            .set(RELEASES.DEPLOYMENT_MODE_SETTINGS, toJson(request.getOrDefault("deployment_mode_settings", Map.of())))
            .set(RELEASES.RELEASE_NOTES, String.valueOf(request.getOrDefault("release_notes", "")))
            .set(RELEASES.CREATED_AT, now)
            .execute();

        Map<String, String> defaults = new LinkedHashMap<>();
        Object defaultsObj = request.get("parameter_defaults");
        if (defaultsObj instanceof Map<?, ?> rawDefaults) {
            for (Map.Entry<?, ?> entry : rawDefaults.entrySet()) {
                String key = normalize(entry.getKey());
                if (key.isBlank()) {
                    continue;
                }
                defaults.put(key, normalize(entry.getValue()));
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
        Object hintsObj = request.get("verification_hints");
        if (hintsObj instanceof List<?> rawHints) {
            for (Object item : rawHints) {
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

        return getRelease(releaseId);
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

    private Map<String, Object> toReleaseMap(Record row, Map<String, String> defaults, List<String> hints) {
        Map<String, Object> release = new LinkedHashMap<>();
        release.put("id", row.get(RELEASES.ID));
        release.put("template_spec_id", row.get(RELEASES.TEMPLATE_SPEC_ID));
        release.put("template_spec_version", row.get(RELEASES.TEMPLATE_SPEC_VERSION));
        release.put("deployment_mode", row.get(RELEASES.DEPLOYMENT_MODE).getLiteral());
        release.put("template_spec_version_id", row.get(RELEASES.TEMPLATE_SPEC_VERSION_ID));
        release.put("deployment_scope", row.get(RELEASES.DEPLOYMENT_SCOPE).getLiteral());
        release.put("deployment_mode_settings", parseJsonMap(row.get(RELEASES.DEPLOYMENT_MODE_SETTINGS)));
        release.put("parameter_defaults", defaults);
        release.put("release_notes", row.get(RELEASES.RELEASE_NOTES));
        release.put("verification_hints", hints);
        release.put("created_at", row.get(RELEASES.CREATED_AT));
        return release;
    }

    private Map<String, Object> parseJsonMap(JSONB json) {
        if (json == null) {
            return Map.of();
        }
        return jsonUtil.readMap(json.data());
    }

    private JSONB toJson(Object value) {
        return JSONB.valueOf(jsonUtil.write(value));
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
