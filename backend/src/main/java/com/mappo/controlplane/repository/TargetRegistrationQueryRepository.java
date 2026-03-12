package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.TARGETS;
import static com.mappo.controlplane.jooq.Tables.TARGET_EXECUTION_CONFIG_ENTRIES;
import static com.mappo.controlplane.jooq.Tables.TARGET_REGISTRATIONS;
import static com.mappo.controlplane.jooq.Tables.TARGET_TAGS;

import com.mappo.controlplane.model.TargetRegistrationMetadataRecord;
import com.mappo.controlplane.model.TargetRegistrationRecord;
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
public class TargetRegistrationQueryRepository {

    private final DSLContext dsl;

    public List<TargetRegistrationRecord> listRegistrations() {
        var rows = dsl.select(
                TARGET_REGISTRATIONS.TARGET_ID,
                TARGETS.TENANT_ID,
                TARGETS.SUBSCRIPTION_ID,
                TARGET_REGISTRATIONS.MANAGED_APPLICATION_ID,
                TARGET_REGISTRATIONS.MANAGED_RESOURCE_GROUP_ID,
                TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID,
                TARGET_REGISTRATIONS.DISPLAY_NAME,
                TARGET_REGISTRATIONS.CUSTOMER_NAME,
                TARGET_REGISTRATIONS.CONTAINER_APP_NAME,
                TARGET_REGISTRATIONS.DEPLOYMENT_STACK_NAME,
                TARGET_REGISTRATIONS.REGISTRY_AUTH_MODE,
                TARGET_REGISTRATIONS.REGISTRY_SERVER,
                TARGET_REGISTRATIONS.REGISTRY_USERNAME,
                TARGET_REGISTRATIONS.REGISTRY_PASSWORD_SECRET_NAME,
                TARGET_REGISTRATIONS.REGISTRATION_SOURCE,
                TARGET_REGISTRATIONS.LAST_EVENT_ID,
                TARGETS.LAST_DEPLOYED_RELEASE,
                TARGETS.HEALTH_STATUS,
                TARGET_REGISTRATIONS.CREATED_AT,
                TARGET_REGISTRATIONS.UPDATED_AT
            )
            .from(TARGET_REGISTRATIONS)
            .join(TARGETS)
            .on(TARGETS.ID.eq(TARGET_REGISTRATIONS.TARGET_ID))
            .orderBy(TARGET_REGISTRATIONS.UPDATED_AT.desc())
            .fetch();

        List<String> targetIds = rows.stream().map(row -> row.get(TARGET_REGISTRATIONS.TARGET_ID)).toList();
        Map<String, Map<String, String>> tagsByTarget = loadTags(targetIds);
        Map<String, Map<String, String>> executionConfigByTarget = loadExecutionConfig(targetIds);

        List<TargetRegistrationRecord> registrations = new ArrayList<>(rows.size());
        for (Record row : rows) {
            String targetId = row.get(TARGET_REGISTRATIONS.TARGET_ID);
            registrations.add(
                toRegistrationRecord(
                    row,
                    tagsByTarget.getOrDefault(targetId, Map.of()),
                    executionConfigByTarget.getOrDefault(targetId, Map.of())
                )
            );
        }
        return registrations;
    }

    public Optional<TargetRegistrationRecord> getRegistration(String targetId) {
        Record row = dsl.select(
                TARGET_REGISTRATIONS.TARGET_ID,
                TARGETS.TENANT_ID,
                TARGETS.SUBSCRIPTION_ID,
                TARGET_REGISTRATIONS.MANAGED_APPLICATION_ID,
                TARGET_REGISTRATIONS.MANAGED_RESOURCE_GROUP_ID,
                TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID,
                TARGET_REGISTRATIONS.DISPLAY_NAME,
                TARGET_REGISTRATIONS.CUSTOMER_NAME,
                TARGET_REGISTRATIONS.CONTAINER_APP_NAME,
                TARGET_REGISTRATIONS.DEPLOYMENT_STACK_NAME,
                TARGET_REGISTRATIONS.REGISTRY_AUTH_MODE,
                TARGET_REGISTRATIONS.REGISTRY_SERVER,
                TARGET_REGISTRATIONS.REGISTRY_USERNAME,
                TARGET_REGISTRATIONS.REGISTRY_PASSWORD_SECRET_NAME,
                TARGET_REGISTRATIONS.REGISTRATION_SOURCE,
                TARGET_REGISTRATIONS.LAST_EVENT_ID,
                TARGETS.LAST_DEPLOYED_RELEASE,
                TARGETS.HEALTH_STATUS,
                TARGET_REGISTRATIONS.CREATED_AT,
                TARGET_REGISTRATIONS.UPDATED_AT
            )
            .from(TARGET_REGISTRATIONS)
            .join(TARGETS)
            .on(TARGETS.ID.eq(TARGET_REGISTRATIONS.TARGET_ID))
            .where(TARGET_REGISTRATIONS.TARGET_ID.eq(targetId))
            .fetchOne();

        if (row == null) {
            return Optional.empty();
        }

        Map<String, String> tags = loadTags(List.of(targetId)).getOrDefault(targetId, Map.of());
        Map<String, String> executionConfig = loadExecutionConfig(List.of(targetId)).getOrDefault(targetId, Map.of());
        return Optional.of(toRegistrationRecord(row, tags, executionConfig));
    }

    private Map<String, Map<String, String>> loadTags(List<String> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return Map.of();
        }

        var rows = dsl.select(TARGET_TAGS.TARGET_ID, TARGET_TAGS.TAG_KEY, TARGET_TAGS.TAG_VALUE)
            .from(TARGET_TAGS)
            .where(TARGET_TAGS.TARGET_ID.in(targetIds))
            .fetch();

        Map<String, Map<String, String>> tags = new LinkedHashMap<>();
        for (Record row : rows) {
            String targetId = row.get(TARGET_TAGS.TARGET_ID);
            tags.computeIfAbsent(targetId, ignored -> new LinkedHashMap<>())
                .put(row.get(TARGET_TAGS.TAG_KEY), row.get(TARGET_TAGS.TAG_VALUE));
        }
        return tags;
    }

    private Map<String, Map<String, String>> loadExecutionConfig(List<String> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return Map.of();
        }

        var rows = dsl.select(
                TARGET_EXECUTION_CONFIG_ENTRIES.TARGET_ID,
                TARGET_EXECUTION_CONFIG_ENTRIES.CONFIG_KEY,
                TARGET_EXECUTION_CONFIG_ENTRIES.CONFIG_VALUE
            )
            .from(TARGET_EXECUTION_CONFIG_ENTRIES)
            .where(TARGET_EXECUTION_CONFIG_ENTRIES.TARGET_ID.in(targetIds))
            .fetch();

        Map<String, Map<String, String>> config = new LinkedHashMap<>();
        for (Record row : rows) {
            String targetId = row.get(TARGET_EXECUTION_CONFIG_ENTRIES.TARGET_ID);
            config.computeIfAbsent(targetId, ignored -> new LinkedHashMap<>())
                .put(
                    row.get(TARGET_EXECUTION_CONFIG_ENTRIES.CONFIG_KEY),
                    row.get(TARGET_EXECUTION_CONFIG_ENTRIES.CONFIG_VALUE)
                );
        }
        return config;
    }

    private TargetRegistrationRecord toRegistrationRecord(
        Record row,
        Map<String, String> tags,
        Map<String, String> executionConfig
    ) {
        return new TargetRegistrationRecord(
            row.get(TARGET_REGISTRATIONS.TARGET_ID),
            row.get(TARGETS.TENANT_ID),
            row.get(TARGETS.SUBSCRIPTION_ID),
            row.get(TARGET_REGISTRATIONS.MANAGED_APPLICATION_ID),
            row.get(TARGET_REGISTRATIONS.MANAGED_RESOURCE_GROUP_ID),
            row.get(TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID),
            row.get(TARGET_REGISTRATIONS.DISPLAY_NAME),
            row.get(TARGET_REGISTRATIONS.CUSTOMER_NAME),
            tags,
            registrationMetadata(row, executionConfig),
            row.get(TARGET_REGISTRATIONS.LAST_EVENT_ID),
            row.get(TARGETS.LAST_DEPLOYED_RELEASE),
            row.get(TARGETS.HEALTH_STATUS),
            row.get(TARGET_REGISTRATIONS.CREATED_AT),
            row.get(TARGET_REGISTRATIONS.UPDATED_AT)
        );
    }

    private TargetRegistrationMetadataRecord registrationMetadata(Record row, Map<String, String> executionConfig) {
        return new TargetRegistrationMetadataRecord(
            nullableText(row.get(TARGET_REGISTRATIONS.CONTAINER_APP_NAME)),
            nullableText(row.get(TARGET_REGISTRATIONS.REGISTRATION_SOURCE)),
            nullableText(row.get(TARGET_REGISTRATIONS.DEPLOYMENT_STACK_NAME)),
            row.get(TARGET_REGISTRATIONS.REGISTRY_AUTH_MODE),
            nullableText(row.get(TARGET_REGISTRATIONS.REGISTRY_SERVER)),
            nullableText(row.get(TARGET_REGISTRATIONS.REGISTRY_USERNAME)),
            nullableText(row.get(TARGET_REGISTRATIONS.REGISTRY_PASSWORD_SECRET_NAME)),
            executionConfig == null || executionConfig.isEmpty() ? null : Map.copyOf(executionConfig)
        );
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String nullableText(Object value) {
        String text = normalize(value);
        return text.isBlank() ? null : text;
    }
}
