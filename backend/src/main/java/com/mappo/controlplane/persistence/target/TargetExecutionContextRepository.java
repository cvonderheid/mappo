package com.mappo.controlplane.persistence.target;

import static com.mappo.controlplane.jooq.Tables.TARGETS;
import static com.mappo.controlplane.jooq.Tables.TARGET_EXECUTION_CONFIG_ENTRIES;
import static com.mappo.controlplane.jooq.Tables.TARGET_REGISTRATIONS;
import static com.mappo.controlplane.jooq.Tables.TARGET_TAGS;

import com.mappo.controlplane.model.TargetExecutionContextRecord;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TargetExecutionContextRepository {

    private final DSLContext dsl;

    public List<TargetExecutionContextRecord> getExecutionContextsByIds(List<String> targetIds) {
        if (targetIds == null || targetIds.isEmpty()) {
            return List.of();
        }

        var rows = dsl.select(
                TARGETS.ID,
                TARGETS.SUBSCRIPTION_ID,
                TARGETS.TENANT_ID,
                TARGET_REGISTRATIONS.MANAGED_RESOURCE_GROUP_ID,
                TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID,
                TARGET_REGISTRATIONS.DEPLOYMENT_STACK_NAME,
                TARGET_REGISTRATIONS.REGISTRY_AUTH_MODE,
                TARGET_REGISTRATIONS.REGISTRY_SERVER,
                TARGET_REGISTRATIONS.REGISTRY_USERNAME,
                TARGET_REGISTRATIONS.REGISTRY_PASSWORD_SECRET_NAME,
                TARGETS.SIMULATED_FAILURE_MODE
            )
            .from(TARGETS)
            .join(TARGET_REGISTRATIONS)
            .on(TARGET_REGISTRATIONS.TARGET_ID.eq(TARGETS.ID))
            .where(TARGETS.ID.in(targetIds))
            .orderBy(TARGETS.ID.asc())
            .fetch();

        Map<String, Map<String, String>> tagsByTarget = loadTags(targetIds);
        Map<String, Map<String, String>> executionConfigByTarget = loadExecutionConfig(targetIds);
        List<TargetExecutionContextRecord> contexts = new ArrayList<>(rows.size());
        for (Record row : rows) {
            String targetId = row.get(TARGETS.ID);
            contexts.add(new TargetExecutionContextRecord(
                targetId,
                row.get(TARGETS.SUBSCRIPTION_ID),
                row.get(TARGETS.TENANT_ID),
                row.get(TARGET_REGISTRATIONS.MANAGED_RESOURCE_GROUP_ID),
                row.get(TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID),
                row.get(TARGET_REGISTRATIONS.DEPLOYMENT_STACK_NAME),
                row.get(TARGET_REGISTRATIONS.REGISTRY_AUTH_MODE),
                row.get(TARGET_REGISTRATIONS.REGISTRY_SERVER),
                row.get(TARGET_REGISTRATIONS.REGISTRY_USERNAME),
                row.get(TARGET_REGISTRATIONS.REGISTRY_PASSWORD_SECRET_NAME),
                tagsByTarget.getOrDefault(targetId, Map.of()),
                row.get(TARGETS.SIMULATED_FAILURE_MODE),
                executionConfigByTarget.getOrDefault(targetId, Map.of())
            ));
        }
        return contexts;
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
            tags.computeIfAbsent(targetId, key -> new LinkedHashMap<>())
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
            config.computeIfAbsent(targetId, key -> new LinkedHashMap<>())
                .put(
                    row.get(TARGET_EXECUTION_CONFIG_ENTRIES.CONFIG_KEY),
                    row.get(TARGET_EXECUTION_CONFIG_ENTRIES.CONFIG_VALUE)
                );
        }
        return config;
    }
}
