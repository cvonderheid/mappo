package com.mappo.controlplane.repository;

import static com.mappo.controlplane.jooq.Tables.PROJECT_CONFIGURATION_AUDIT_EVENTS;

import com.mappo.controlplane.model.PageMetadataRecord;
import com.mappo.controlplane.model.ProjectConfigurationAuditAction;
import com.mappo.controlplane.model.ProjectConfigurationAuditPageRecord;
import com.mappo.controlplane.model.ProjectConfigurationAuditRecord;
import com.mappo.controlplane.model.command.ProjectConfigurationAuditCommand;
import com.mappo.controlplane.model.query.ProjectConfigurationAuditPageQuery;
import com.mappo.controlplane.util.JsonUtil;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Record;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ProjectConfigurationAuditRepository {

    private final DSLContext dsl;
    private final JsonUtil jsonUtil;

    public void saveAuditEvent(ProjectConfigurationAuditCommand command) {
        OffsetDateTime createdAt = command.createdAt() == null
            ? OffsetDateTime.now(ZoneOffset.UTC)
            : command.createdAt();

        dsl.insertInto(PROJECT_CONFIGURATION_AUDIT_EVENTS)
            .set(PROJECT_CONFIGURATION_AUDIT_EVENTS.ID, normalize(command.id()))
            .set(PROJECT_CONFIGURATION_AUDIT_EVENTS.PROJECT_ID, normalize(command.projectId()))
            .set(PROJECT_CONFIGURATION_AUDIT_EVENTS.ACTION, normalize(command.action()))
            .set(PROJECT_CONFIGURATION_AUDIT_EVENTS.ACTOR, nullable(command.actor()))
            .set(PROJECT_CONFIGURATION_AUDIT_EVENTS.CHANGE_SUMMARY, defaultIfBlank(command.changeSummary(), "Project configuration updated."))
            .set(PROJECT_CONFIGURATION_AUDIT_EVENTS.BEFORE_SNAPSHOT, jsonbOrNull(command.beforeSnapshot()))
            .set(PROJECT_CONFIGURATION_AUDIT_EVENTS.AFTER_SNAPSHOT, jsonb(command.afterSnapshot()))
            .set(PROJECT_CONFIGURATION_AUDIT_EVENTS.CREATED_AT, createdAt)
            .execute();
    }

    public ProjectConfigurationAuditPageRecord listProjectAuditPage(ProjectConfigurationAuditPageQuery query) {
        int page = normalizePage(query == null ? null : query.page());
        int size = normalizeSize(query == null ? null : query.size());
        Condition condition = buildCondition(query);
        if (condition == null) {
            return new ProjectConfigurationAuditPageRecord(List.of(), new PageMetadataRecord(page, size, 0L, 0));
        }

        long totalItems = dsl.fetchCount(
            dsl.select(PROJECT_CONFIGURATION_AUDIT_EVENTS.ID)
                .from(PROJECT_CONFIGURATION_AUDIT_EVENTS)
                .where(condition)
        );
        int totalPages = totalItems == 0 ? 0 : (int) Math.ceil((double) totalItems / size);

        List<ProjectConfigurationAuditRecord> items = dsl.select(
                PROJECT_CONFIGURATION_AUDIT_EVENTS.ID,
                PROJECT_CONFIGURATION_AUDIT_EVENTS.PROJECT_ID,
                PROJECT_CONFIGURATION_AUDIT_EVENTS.ACTION,
                PROJECT_CONFIGURATION_AUDIT_EVENTS.ACTOR,
                PROJECT_CONFIGURATION_AUDIT_EVENTS.CHANGE_SUMMARY,
                PROJECT_CONFIGURATION_AUDIT_EVENTS.BEFORE_SNAPSHOT,
                PROJECT_CONFIGURATION_AUDIT_EVENTS.AFTER_SNAPSHOT,
                PROJECT_CONFIGURATION_AUDIT_EVENTS.CREATED_AT
            )
            .from(PROJECT_CONFIGURATION_AUDIT_EVENTS)
            .where(condition)
            .orderBy(PROJECT_CONFIGURATION_AUDIT_EVENTS.CREATED_AT.desc())
            .limit(size)
            .offset(page * size)
            .fetch(this::toRecord);

        return new ProjectConfigurationAuditPageRecord(
            items,
            new PageMetadataRecord(page, size, totalItems, totalPages)
        );
    }

    private ProjectConfigurationAuditRecord toRecord(Record row) {
        return new ProjectConfigurationAuditRecord(
            row.get(PROJECT_CONFIGURATION_AUDIT_EVENTS.ID),
            row.get(PROJECT_CONFIGURATION_AUDIT_EVENTS.PROJECT_ID),
            parseAction(row.get(PROJECT_CONFIGURATION_AUDIT_EVENTS.ACTION)),
            row.get(PROJECT_CONFIGURATION_AUDIT_EVENTS.ACTOR),
            row.get(PROJECT_CONFIGURATION_AUDIT_EVENTS.CHANGE_SUMMARY),
            readMap(row.get(PROJECT_CONFIGURATION_AUDIT_EVENTS.BEFORE_SNAPSHOT)),
            readMap(row.get(PROJECT_CONFIGURATION_AUDIT_EVENTS.AFTER_SNAPSHOT)),
            row.get(PROJECT_CONFIGURATION_AUDIT_EVENTS.CREATED_AT)
        );
    }

    private Condition buildCondition(ProjectConfigurationAuditPageQuery query) {
        if (query == null) {
            return DSL.trueCondition();
        }
        String projectId = normalize(query.projectId());
        if (projectId.isBlank()) {
            return null;
        }

        Condition condition = PROJECT_CONFIGURATION_AUDIT_EVENTS.PROJECT_ID.eq(projectId);
        if (query.action() != null) {
            condition = condition.and(PROJECT_CONFIGURATION_AUDIT_EVENTS.ACTION.eq(normalize(query.action())));
        }
        return condition;
    }

    private Map<String, Object> readMap(JSONB value) {
        if (value == null || value.data() == null || value.data().isBlank()) {
            return Map.of();
        }
        return jsonUtil.readMap(value.data());
    }

    private ProjectConfigurationAuditAction parseAction(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return ProjectConfigurationAuditAction.updated;
        }
        try {
            return ProjectConfigurationAuditAction.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return ProjectConfigurationAuditAction.updated;
        }
    }

    private JSONB jsonb(Map<String, Object> value) {
        return JSONB.valueOf(jsonUtil.write(value == null ? Map.of() : value));
    }

    private JSONB jsonbOrNull(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        return jsonb(value);
    }

    private int normalizePage(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    private int normalizeSize(Integer value) {
        if (value == null || value <= 0) {
            return 25;
        }
        return Math.min(value, 100);
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String nullable(Object value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    private String defaultIfBlank(String value, String fallback) {
        String normalized = normalize(value);
        return normalized.isBlank() ? fallback : normalized;
    }
}

