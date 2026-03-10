package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.TARGETS;
import static com.mappo.controlplane.jooq.Tables.TARGET_RUNTIME_PROBES;
import static com.mappo.controlplane.jooq.Tables.TARGET_TAGS;

import com.mappo.controlplane.jooq.enums.MappoHealthStatus;
import com.mappo.controlplane.jooq.enums.MappoRuntimeProbeStatus;
import com.mappo.controlplane.jooq.enums.MappoSimulatedFailureMode;
import com.mappo.controlplane.model.TargetRuntimeProbeRecord;
import com.mappo.controlplane.model.command.TargetUpsertCommand;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TargetCommandRepository {

    private final DSLContext dsl;

    public void upsertTarget(TargetUpsertCommand target) {
        String targetId = requiredText(target.id(), "id");
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        String release = normalize(defaultIfBlank(target.lastDeployedRelease(), "unknown"));
        if (release.isBlank()) {
            release = "unknown";
        }

        dsl.insertInto(TARGETS)
            .set(TARGETS.ID, targetId)
            .set(TARGETS.TENANT_ID, requiredUuid(target.tenantId(), "tenant_id"))
            .set(TARGETS.SUBSCRIPTION_ID, requiredUuid(target.subscriptionId(), "subscription_id"))
            .set(TARGETS.LAST_DEPLOYED_RELEASE, release)
            .set(TARGETS.HEALTH_STATUS, enumOrDefault(target.healthStatus(), MappoHealthStatus.registered))
            .set(TARGETS.LAST_CHECK_IN_AT, toTimestamp(target.lastCheckInAt(), now))
            .set(TARGETS.SIMULATED_FAILURE_MODE, enumOrDefault(target.simulatedFailureMode(), MappoSimulatedFailureMode.none))
            .set(TARGETS.UPDATED_AT, now)
            .onConflict(TARGETS.ID)
            .doUpdate()
            .set(TARGETS.TENANT_ID, requiredUuid(target.tenantId(), "tenant_id"))
            .set(TARGETS.SUBSCRIPTION_ID, requiredUuid(target.subscriptionId(), "subscription_id"))
            .set(TARGETS.LAST_DEPLOYED_RELEASE, release)
            .set(TARGETS.HEALTH_STATUS, enumOrDefault(target.healthStatus(), MappoHealthStatus.registered))
            .set(TARGETS.LAST_CHECK_IN_AT, toTimestamp(target.lastCheckInAt(), now))
            .set(TARGETS.SIMULATED_FAILURE_MODE, enumOrDefault(target.simulatedFailureMode(), MappoSimulatedFailureMode.none))
            .set(TARGETS.UPDATED_AT, now)
            .execute();

        replaceTags(targetId, target.tags());
    }

    public void updateTargetHealth(String targetId, MappoHealthStatus healthStatus) {
        dsl.update(TARGETS)
            .set(TARGETS.HEALTH_STATUS, enumOrDefault(healthStatus, MappoHealthStatus.registered))
            .set(TARGETS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
            .where(TARGETS.ID.eq(targetId))
            .execute();
    }

    public void upsertRuntimeProbe(TargetRuntimeProbeRecord probe) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.insertInto(TARGET_RUNTIME_PROBES)
            .set(TARGET_RUNTIME_PROBES.TARGET_ID, requiredText(probe.targetId(), "target_id"))
            .set(
                TARGET_RUNTIME_PROBES.RUNTIME_STATUS,
                enumOrDefault(probe.runtimeStatus(), MappoRuntimeProbeStatus.unknown)
            )
            .set(TARGET_RUNTIME_PROBES.CHECKED_AT, toTimestamp(probe.checkedAt(), now))
            .set(TARGET_RUNTIME_PROBES.ENDPOINT_URL, nullableText(probe.endpointUrl()))
            .set(TARGET_RUNTIME_PROBES.HTTP_STATUS_CODE, probe.httpStatusCode())
            .set(TARGET_RUNTIME_PROBES.SUMMARY, defaultIfBlank(probe.summary(), ""))
            .set(TARGET_RUNTIME_PROBES.UPDATED_AT, now)
            .onConflict(TARGET_RUNTIME_PROBES.TARGET_ID)
            .doUpdate()
            .set(
                TARGET_RUNTIME_PROBES.RUNTIME_STATUS,
                enumOrDefault(probe.runtimeStatus(), MappoRuntimeProbeStatus.unknown)
            )
            .set(TARGET_RUNTIME_PROBES.CHECKED_AT, toTimestamp(probe.checkedAt(), now))
            .set(TARGET_RUNTIME_PROBES.ENDPOINT_URL, nullableText(probe.endpointUrl()))
            .set(TARGET_RUNTIME_PROBES.HTTP_STATUS_CODE, probe.httpStatusCode())
            .set(TARGET_RUNTIME_PROBES.SUMMARY, defaultIfBlank(probe.summary(), ""))
            .set(TARGET_RUNTIME_PROBES.UPDATED_AT, now)
            .execute();
    }

    public void updateLastDeployedRelease(String targetId, String releaseVersion) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        dsl.update(TARGETS)
            .set(TARGETS.LAST_DEPLOYED_RELEASE, normalize(releaseVersion))
            .set(TARGETS.UPDATED_AT, now)
            .where(TARGETS.ID.eq(targetId))
            .execute();
    }

    public void deleteTarget(String targetId) {
        dsl.deleteFrom(TARGETS)
            .where(TARGETS.ID.eq(targetId))
            .execute();
    }

    private void replaceTags(String targetId, Map<String, String> tags) {
        dsl.deleteFrom(TARGET_TAGS)
            .where(TARGET_TAGS.TARGET_ID.eq(targetId))
            .execute();

        if (tags == null || tags.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : tags.entrySet()) {
            dsl.insertInto(TARGET_TAGS)
                .set(TARGET_TAGS.TARGET_ID, targetId)
                .set(TARGET_TAGS.TAG_KEY, entry.getKey())
                .set(TARGET_TAGS.TAG_VALUE, entry.getValue())
                .execute();
        }
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String defaultIfBlank(String candidate, String fallback) {
        String normalizedCandidate = normalize(candidate);
        return normalizedCandidate.isBlank() ? normalize(fallback) : normalizedCandidate;
    }

    private String requiredText(String value, String fieldName) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return normalized;
    }

    private String nullableText(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    private OffsetDateTime toTimestamp(OffsetDateTime value, OffsetDateTime fallback) {
        return value == null ? fallback : value;
    }

    private UUID requiredUuid(UUID value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    private <E extends Enum<E>> E enumOrDefault(E value, E fallback) {
        return value == null ? fallback : value;
    }
}
