package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.RELEASES;
import static com.mappo.controlplane.jooq.Tables.RELEASE_PARAMETER_DEFAULTS;
import static com.mappo.controlplane.jooq.Tables.RELEASE_VERIFICATION_HINTS;

import com.mappo.controlplane.jooq.enums.MappoArmDeploymentMode;
import com.mappo.controlplane.jooq.enums.MappoDeploymentScope;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.command.CreateReleaseCommand;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReleaseCommandRepository {

    private final DSLContext dsl;
    private final ReleaseQueryRepository releaseQueryRepository;

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

        Map<String, String> defaults = sanitizeDefaults(request.parameterDefaults());
        for (Map.Entry<String, String> entry : defaults.entrySet()) {
            dsl.insertInto(RELEASE_PARAMETER_DEFAULTS)
                .set(RELEASE_PARAMETER_DEFAULTS.RELEASE_ID, releaseId)
                .set(RELEASE_PARAMETER_DEFAULTS.PARAM_KEY, entry.getKey())
                .set(RELEASE_PARAMETER_DEFAULTS.PARAM_VALUE, entry.getValue())
                .execute();
        }

        List<String> hints = sanitizeHints(request.verificationHints());
        for (int i = 0; i < hints.size(); i++) {
            dsl.insertInto(RELEASE_VERIFICATION_HINTS)
                .set(RELEASE_VERIFICATION_HINTS.RELEASE_ID, releaseId)
                .set(RELEASE_VERIFICATION_HINTS.POSITION, i)
                .set(RELEASE_VERIFICATION_HINTS.HINT, hints.get(i))
                .execute();
        }

        return releaseQueryRepository.getRelease(releaseId).orElseThrow();
    }

    private Map<String, String> sanitizeDefaults(Map<String, String> parameterDefaults) {
        Map<String, String> defaults = new LinkedHashMap<>();
        if (parameterDefaults == null || parameterDefaults.isEmpty()) {
            return defaults;
        }
        for (Map.Entry<String, String> entry : parameterDefaults.entrySet()) {
            String key = normalize(entry.getKey());
            if (!key.isBlank()) {
                defaults.put(key, normalize(entry.getValue()));
            }
        }
        return defaults;
    }

    private List<String> sanitizeHints(List<String> verificationHints) {
        List<String> hints = new ArrayList<>();
        if (verificationHints == null || verificationHints.isEmpty()) {
            return hints;
        }
        for (String item : verificationHints) {
            String hint = normalize(item);
            if (!hint.isBlank()) {
                hints.add(hint);
            }
        }
        return hints;
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
