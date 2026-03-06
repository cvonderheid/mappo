package com.mappo.controlplane.repository;

import com.mappo.controlplane.util.JsonUtil;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Result;
import org.springframework.stereotype.Repository;

@Repository
public class ReleaseRepository {

    private final DSLContext dsl;
    private final JsonUtil jsonUtil;

    public ReleaseRepository(DSLContext dsl, JsonUtil jsonUtil) {
        this.dsl = dsl;
        this.jsonUtil = jsonUtil;
    }

    public List<Map<String, Object>> listReleases() {
        Result<Record> rows = dsl.fetch(
            "select r.id, r.template_spec_id, r.template_spec_version, r.deployment_mode::text as deployment_mode, "
                + "r.template_spec_version_id, r.deployment_scope::text as deployment_scope, "
                + "r.deployment_mode_settings::text as deployment_mode_settings, r.release_notes, r.created_at "
                + "from releases r order by r.created_at desc"
        );

        List<String> ids = rows.stream().map(row -> row.get("id", String.class)).toList();
        Map<String, Map<String, String>> defaults = loadDefaults(ids);
        Map<String, List<String>> hints = loadHints(ids);

        List<Map<String, Object>> releases = new ArrayList<>();
        for (Record row : rows) {
            String id = row.get("id", String.class);
            Map<String, Object> release = new LinkedHashMap<>();
            release.put("id", id);
            release.put("template_spec_id", row.get("template_spec_id", String.class));
            release.put("template_spec_version", row.get("template_spec_version", String.class));
            release.put("deployment_mode", row.get("deployment_mode", String.class));
            release.put("template_spec_version_id", row.get("template_spec_version_id", String.class));
            release.put("deployment_scope", row.get("deployment_scope", String.class));
            release.put("deployment_mode_settings", jsonUtil.readMap(row.get("deployment_mode_settings", String.class)));
            release.put("parameter_defaults", defaults.getOrDefault(id, Map.of()));
            release.put("release_notes", row.get("release_notes", String.class));
            release.put("verification_hints", hints.getOrDefault(id, List.of()));
            release.put("created_at", row.get("created_at", OffsetDateTime.class));
            releases.add(release);
        }
        return releases;
    }

    public Map<String, Object> getRelease(String releaseId) {
        Result<Record> rows = dsl.fetch(
            "select r.id, r.template_spec_id, r.template_spec_version, r.deployment_mode::text as deployment_mode, "
                + "r.template_spec_version_id, r.deployment_scope::text as deployment_scope, "
                + "r.deployment_mode_settings::text as deployment_mode_settings, r.release_notes, r.created_at "
                + "from releases r where r.id = ?",
            releaseId
        );
        if (rows.isEmpty()) {
            return Map.of();
        }
        Record row = rows.getFirst();
        Map<String, Object> release = new LinkedHashMap<>();
        release.put("id", row.get("id", String.class));
        release.put("template_spec_id", row.get("template_spec_id", String.class));
        release.put("template_spec_version", row.get("template_spec_version", String.class));
        release.put("deployment_mode", row.get("deployment_mode", String.class));
        release.put("template_spec_version_id", row.get("template_spec_version_id", String.class));
        release.put("deployment_scope", row.get("deployment_scope", String.class));
        release.put("deployment_mode_settings", jsonUtil.readMap(row.get("deployment_mode_settings", String.class)));
        release.put("parameter_defaults", loadDefaults(List.of(releaseId)).getOrDefault(releaseId, Map.of()));
        release.put("release_notes", row.get("release_notes", String.class));
        release.put("verification_hints", loadHints(List.of(releaseId)).getOrDefault(releaseId, List.of()));
        release.put("created_at", row.get("created_at", OffsetDateTime.class));
        return release;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> createRelease(Map<String, Object> request) {
        String releaseId = "rel-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        String deploymentMode = String.valueOf(request.getOrDefault("deployment_mode", "container_patch"));
        String deploymentScope = String.valueOf(request.getOrDefault("deployment_scope", "resource_group"));
        String templateSpecVersionId = request.get("template_spec_version_id") == null
            ? null
            : String.valueOf(request.get("template_spec_version_id"));
        String releaseNotes = String.valueOf(request.getOrDefault("release_notes", ""));

        dsl.query(
            "insert into releases (id, template_spec_id, template_spec_version, deployment_mode, template_spec_version_id, "
                + "deployment_scope, deployment_mode_settings, release_notes, created_at) "
                + "values (?, ?, ?, ?::mappo_deployment_mode, ?, ?::mappo_deployment_scope, ?::jsonb, ?, ?)",
            releaseId,
            String.valueOf(request.get("template_spec_id")),
            String.valueOf(request.get("template_spec_version")),
            deploymentMode,
            templateSpecVersionId,
            deploymentScope,
            jsonUtil.write(request.getOrDefault("deployment_mode_settings", Map.of())),
            releaseNotes,
            now
        ).execute();

        Map<String, String> defaults = new LinkedHashMap<>();
        Object defaultsObj = request.get("parameter_defaults");
        if (defaultsObj instanceof Map<?, ?> raw) {
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                String key = String.valueOf(entry.getKey()).trim();
                if (key.isEmpty()) {
                    continue;
                }
                defaults.put(key, String.valueOf(entry.getValue()));
            }
        }

        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            dsl.query(
                "insert into release_parameter_defaults (release_id, param_key, param_value) values (?, ?, ?)",
                releaseId,
                entry.getKey(),
                entry.getValue()
            ).execute();
        }

        List<String> hints = new ArrayList<>();
        Object hintsObj = request.get("verification_hints");
        if (hintsObj instanceof List<?> rawList) {
            for (Object item : rawList) {
                String hint = String.valueOf(item).trim();
                if (!hint.isEmpty()) {
                    hints.add(hint);
                }
            }
        }

        for (int i = 0; i < hints.size(); i++) {
            dsl.query(
                "insert into release_verification_hints (release_id, position, hint) values (?, ?, ?)",
                releaseId,
                i,
                hints.get(i)
            ).execute();
        }

        return getRelease(releaseId);
    }

    private Map<String, Map<String, String>> loadDefaults(List<String> releaseIds) {
        if (releaseIds == null || releaseIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = releaseIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        Result<Record> rows = dsl.fetch(
            "select release_id, param_key, param_value from release_parameter_defaults where release_id in ("
                + placeholders + ")",
            releaseIds.toArray()
        );
        Map<String, Map<String, String>> defaults = new LinkedHashMap<>();
        for (Record row : rows) {
            String releaseId = row.get("release_id", String.class);
            defaults.computeIfAbsent(releaseId, key -> new LinkedHashMap<>())
                .put(row.get("param_key", String.class), row.get("param_value", String.class));
        }
        return defaults;
    }

    private Map<String, List<String>> loadHints(List<String> releaseIds) {
        if (releaseIds == null || releaseIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = releaseIds.stream().map(id -> "?").collect(Collectors.joining(", "));
        Result<Record> rows = dsl.fetch(
            "select release_id, hint from release_verification_hints where release_id in ("
                + placeholders + ") order by position asc",
            releaseIds.toArray()
        );
        Map<String, List<String>> hints = new LinkedHashMap<>();
        for (Record row : rows) {
            String releaseId = row.get("release_id", String.class);
            hints.computeIfAbsent(releaseId, key -> new ArrayList<>())
                .add(row.get("hint", String.class));
        }
        return hints;
    }
}
