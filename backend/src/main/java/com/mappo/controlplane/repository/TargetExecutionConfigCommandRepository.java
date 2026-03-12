package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.TARGET_EXECUTION_CONFIG_ENTRIES;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TargetExecutionConfigCommandRepository {

    private final DSLContext dsl;

    public void replaceConfigEntries(String targetId, Map<String, String> entries) {
        dsl.deleteFrom(TARGET_EXECUTION_CONFIG_ENTRIES)
            .where(TARGET_EXECUTION_CONFIG_ENTRIES.TARGET_ID.eq(targetId))
            .execute();

        if (entries == null || entries.isEmpty()) {
            return;
        }

        entries.forEach((key, value) -> dsl.insertInto(TARGET_EXECUTION_CONFIG_ENTRIES)
            .set(TARGET_EXECUTION_CONFIG_ENTRIES.TARGET_ID, targetId)
            .set(TARGET_EXECUTION_CONFIG_ENTRIES.CONFIG_KEY, key)
            .set(TARGET_EXECUTION_CONFIG_ENTRIES.CONFIG_VALUE, value)
            .execute());
    }
}
