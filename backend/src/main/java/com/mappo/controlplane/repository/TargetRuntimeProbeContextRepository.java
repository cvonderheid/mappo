package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.PROJECTS;
import static com.mappo.controlplane.jooq.Tables.TARGET_EXECUTION_CONFIG_ENTRIES;
import static com.mappo.controlplane.jooq.Tables.TARGETS;
import static com.mappo.controlplane.jooq.Tables.TARGET_REGISTRATIONS;

import com.mappo.controlplane.domain.project.AzureContainerAppHttpRuntimeHealthProviderConfig;
import com.mappo.controlplane.domain.project.HttpEndpointRuntimeHealthProviderConfig;
import com.mappo.controlplane.domain.project.ProjectRuntimeHealthProviderType;
import com.mappo.controlplane.model.TargetRuntimeProbeContextRecord;
import com.mappo.controlplane.util.JsonUtil;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TargetRuntimeProbeContextRepository {

    private final DSLContext dsl;
    private final JsonUtil jsonUtil;

    public List<TargetRuntimeProbeContextRecord> listRuntimeProbeContexts() {
        var rows = dsl.select(
                TARGETS.ID,
                TARGETS.PROJECT_ID,
                TARGETS.TENANT_ID,
                TARGETS.SUBSCRIPTION_ID,
                TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID,
                PROJECTS.RUNTIME_HEALTH_PROVIDER,
                PROJECTS.RUNTIME_HEALTH_PROVIDER_CONFIG
            )
            .from(TARGETS)
            .join(TARGET_REGISTRATIONS)
            .on(TARGET_REGISTRATIONS.TARGET_ID.eq(TARGETS.ID))
            .join(PROJECTS)
            .on(PROJECTS.ID.eq(TARGETS.PROJECT_ID))
            .orderBy(TARGETS.ID.asc())
            .fetch();
        Map<String, Map<String, String>> executionConfigByTargetId = loadExecutionConfig(rows.stream()
            .map(row -> row.get(TARGETS.ID))
            .toList());
        return rows.stream()
            .map(row -> toRuntimeProbeContext(row, executionConfigByTargetId.getOrDefault(row.get(TARGETS.ID), Map.of())))
            .toList();
    }

    private TargetRuntimeProbeContextRecord toRuntimeProbeContext(Record row, Map<String, String> executionConfig) {
        ProjectRuntimeHealthProviderType providerType = ProjectRuntimeHealthProviderType.valueOf(
            row.get(PROJECTS.RUNTIME_HEALTH_PROVIDER).getLiteral()
        );
        RuntimeProbeConfig probeConfig = parseRuntimeProbeConfig(providerType, row.get(PROJECTS.RUNTIME_HEALTH_PROVIDER_CONFIG).data());
        return new TargetRuntimeProbeContextRecord(
            row.get(TARGETS.ID),
            row.get(TARGETS.PROJECT_ID),
            row.get(TARGETS.TENANT_ID),
            row.get(TARGETS.SUBSCRIPTION_ID),
            row.get(TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID),
            providerType,
            probeConfig.path(),
            probeConfig.expectedStatus(),
            probeConfig.timeoutMs(),
            executionConfig
        );
    }

    private Map<String, Map<String, String>> loadExecutionConfig(List<String> targetIds) {
        if (targetIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, String>> executionConfigByTargetId = new HashMap<>();
        dsl.select(
                TARGET_EXECUTION_CONFIG_ENTRIES.TARGET_ID,
                TARGET_EXECUTION_CONFIG_ENTRIES.CONFIG_KEY,
                TARGET_EXECUTION_CONFIG_ENTRIES.CONFIG_VALUE
            )
            .from(TARGET_EXECUTION_CONFIG_ENTRIES)
            .where(TARGET_EXECUTION_CONFIG_ENTRIES.TARGET_ID.in(targetIds))
            .fetch()
            .forEach(row -> executionConfigByTargetId
                .computeIfAbsent(row.get(TARGET_EXECUTION_CONFIG_ENTRIES.TARGET_ID), ignored -> new LinkedHashMap<>())
                .put(
                    row.get(TARGET_EXECUTION_CONFIG_ENTRIES.CONFIG_KEY),
                    row.get(TARGET_EXECUTION_CONFIG_ENTRIES.CONFIG_VALUE)
                ));
        return executionConfigByTargetId;
    }

    private RuntimeProbeConfig parseRuntimeProbeConfig(ProjectRuntimeHealthProviderType providerType, String value) {
        return switch (providerType) {
            case azure_container_app_http -> {
                AzureContainerAppHttpRuntimeHealthProviderConfig config = jsonUtil.read(
                    value,
                    AzureContainerAppHttpRuntimeHealthProviderConfig.class,
                    AzureContainerAppHttpRuntimeHealthProviderConfig.defaults()
                );
                yield new RuntimeProbeConfig(config.path(), config.expectedStatus(), config.timeoutMs());
            }
            case http_endpoint -> {
                HttpEndpointRuntimeHealthProviderConfig config = jsonUtil.read(
                    value,
                    HttpEndpointRuntimeHealthProviderConfig.class,
                    HttpEndpointRuntimeHealthProviderConfig.defaults()
                );
                yield new RuntimeProbeConfig(config.path(), config.expectedStatus(), config.timeoutMs());
            }
        };
    }

    private record RuntimeProbeConfig(
        String path,
        int expectedStatus,
        long timeoutMs
    ) {
    }
}
