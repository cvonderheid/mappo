package com.mappo.controlplane.persistence.target;
import com.mappo.controlplane.persistence.support.AdminCommandSupport;

import static com.mappo.controlplane.jooq.Tables.TARGET_REGISTRATIONS;
import static com.mappo.controlplane.jooq.Tables.TARGETS;

import com.mappo.controlplane.jooq.enums.MappoHealthStatus;
import com.mappo.controlplane.model.TargetRegistrationRecord;
import com.mappo.controlplane.model.command.TargetRegistrationPatchCommand;
import com.mappo.controlplane.model.command.TargetRegistrationUpsertCommand;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TargetRegistrationCommandRepository {

    private final DSLContext dsl;
    private final TargetRegistrationQueryRepository targetRegistrationQueryRepository;

    public void upsertRegistration(TargetRegistrationUpsertCommand registration) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime createdAt = AdminCommandSupport.toTimestamp(registration.createdAt(), now);

        dsl.insertInto(TARGET_REGISTRATIONS)
            .set(TARGET_REGISTRATIONS.TARGET_ID, AdminCommandSupport.normalize(registration.targetId()))
            .set(TARGET_REGISTRATIONS.DISPLAY_NAME, AdminCommandSupport.normalize(registration.displayName()))
            .set(TARGET_REGISTRATIONS.CUSTOMER_NAME, AdminCommandSupport.nullableText(registration.customerName()))
            .set(TARGET_REGISTRATIONS.MANAGED_APPLICATION_ID, AdminCommandSupport.nullableText(registration.managedApplicationId()))
            .set(TARGET_REGISTRATIONS.MANAGED_RESOURCE_GROUP_ID, AdminCommandSupport.nullableText(registration.managedResourceGroupId()))
            .set(TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID, AdminCommandSupport.nullableText(registration.containerAppResourceId()))
            .set(TARGET_REGISTRATIONS.CONTAINER_APP_NAME, AdminCommandSupport.nullableText(registration.containerAppName()))
            .set(TARGET_REGISTRATIONS.DEPLOYMENT_STACK_NAME, AdminCommandSupport.nullableText(registration.deploymentStackName()))
            .set(TARGET_REGISTRATIONS.REGISTRY_AUTH_MODE, registration.registryAuthMode())
            .set(TARGET_REGISTRATIONS.REGISTRY_SERVER, AdminCommandSupport.nullableText(registration.registryServer()))
            .set(TARGET_REGISTRATIONS.REGISTRY_USERNAME, AdminCommandSupport.nullableText(registration.registryUsername()))
            .set(
                TARGET_REGISTRATIONS.REGISTRY_PASSWORD_SECRET_NAME,
                AdminCommandSupport.nullableText(registration.registryPasswordSecretName())
            )
            .set(TARGET_REGISTRATIONS.REGISTRATION_SOURCE, AdminCommandSupport.nullableText(registration.registrationSource()))
            .set(TARGET_REGISTRATIONS.LAST_EVENT_ID, AdminCommandSupport.nullableText(registration.lastEventId()))
            .set(TARGET_REGISTRATIONS.CREATED_AT, createdAt)
            .set(TARGET_REGISTRATIONS.UPDATED_AT, now)
            .onConflict(TARGET_REGISTRATIONS.TARGET_ID)
            .doUpdate()
            .set(TARGET_REGISTRATIONS.DISPLAY_NAME, AdminCommandSupport.normalize(registration.displayName()))
            .set(TARGET_REGISTRATIONS.CUSTOMER_NAME, AdminCommandSupport.nullableText(registration.customerName()))
            .set(TARGET_REGISTRATIONS.MANAGED_APPLICATION_ID, AdminCommandSupport.nullableText(registration.managedApplicationId()))
            .set(TARGET_REGISTRATIONS.MANAGED_RESOURCE_GROUP_ID, AdminCommandSupport.nullableText(registration.managedResourceGroupId()))
            .set(TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID, AdminCommandSupport.nullableText(registration.containerAppResourceId()))
            .set(TARGET_REGISTRATIONS.CONTAINER_APP_NAME, AdminCommandSupport.nullableText(registration.containerAppName()))
            .set(TARGET_REGISTRATIONS.DEPLOYMENT_STACK_NAME, AdminCommandSupport.nullableText(registration.deploymentStackName()))
            .set(TARGET_REGISTRATIONS.REGISTRY_AUTH_MODE, registration.registryAuthMode())
            .set(TARGET_REGISTRATIONS.REGISTRY_SERVER, AdminCommandSupport.nullableText(registration.registryServer()))
            .set(TARGET_REGISTRATIONS.REGISTRY_USERNAME, AdminCommandSupport.nullableText(registration.registryUsername()))
            .set(
                TARGET_REGISTRATIONS.REGISTRY_PASSWORD_SECRET_NAME,
                AdminCommandSupport.nullableText(registration.registryPasswordSecretName())
            )
            .set(TARGET_REGISTRATIONS.REGISTRATION_SOURCE, AdminCommandSupport.nullableText(registration.registrationSource()))
            .set(TARGET_REGISTRATIONS.LAST_EVENT_ID, AdminCommandSupport.nullableText(registration.lastEventId()))
            .set(TARGET_REGISTRATIONS.UPDATED_AT, now)
            .execute();
    }

    public void deleteRegistration(String targetId) {
        dsl.deleteFrom(TARGET_REGISTRATIONS)
            .where(TARGET_REGISTRATIONS.TARGET_ID.eq(targetId))
            .execute();
    }

    public void updateRegistrationAndTarget(String targetId, TargetRegistrationPatchCommand patch) {
        Optional<TargetRegistrationRecord> currentOptional = targetRegistrationQueryRepository.getRegistration(targetId);
        if (currentOptional.isEmpty()) {
            return;
        }
        TargetRegistrationRecord current = currentOptional.get();

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        String displayName = AdminCommandSupport.firstNonBlank(patch.displayName(), current.displayName());
        String customerName = AdminCommandSupport.firstNullableText(patch.customerName(), current.customerName());
        String managedApplicationId = AdminCommandSupport.firstNullableText(
            patch.managedApplicationId(),
            current.managedApplicationId()
        );
        String managedResourceGroupId = AdminCommandSupport.firstNullableText(
            patch.managedResourceGroupId(),
            current.managedResourceGroupId()
        );
        String containerAppResourceId = AdminCommandSupport.firstNullableText(
            patch.containerAppResourceId(),
            current.containerAppResourceId()
        );
        String containerAppName = AdminCommandSupport.firstNullableText(
            patch.containerAppName(),
            current.metadata() == null ? null : current.metadata().containerAppName()
        );
        String deploymentStackName = AdminCommandSupport.firstNullableText(
            patch.deploymentStackName(),
            current.metadata() == null ? null : current.metadata().deploymentStackName()
        );
        var registryAuthMode = patch.registryAuthMode() == null
            ? current.metadata() == null ? null : current.metadata().registryAuthMode()
            : patch.registryAuthMode();
        String registryServer = AdminCommandSupport.firstNullableText(
            patch.registryServer(),
            current.metadata() == null ? null : current.metadata().registryServer()
        );
        String registryUsername = AdminCommandSupport.firstNullableText(
            patch.registryUsername(),
            current.metadata() == null ? null : current.metadata().registryUsername()
        );
        String registryPasswordSecretName = AdminCommandSupport.firstNullableText(
            patch.registryPasswordSecretName(),
            current.metadata() == null ? null : current.metadata().registryPasswordSecretName()
        );
        String registrationSource = AdminCommandSupport.firstNullableText(
            patch.registrationSource(),
            current.metadata() == null ? null : current.metadata().source()
        );

        dsl.update(TARGET_REGISTRATIONS)
            .set(TARGET_REGISTRATIONS.DISPLAY_NAME, displayName)
            .set(TARGET_REGISTRATIONS.CUSTOMER_NAME, customerName)
            .set(TARGET_REGISTRATIONS.MANAGED_APPLICATION_ID, managedApplicationId)
            .set(TARGET_REGISTRATIONS.MANAGED_RESOURCE_GROUP_ID, managedResourceGroupId)
            .set(TARGET_REGISTRATIONS.CONTAINER_APP_RESOURCE_ID, containerAppResourceId)
            .set(TARGET_REGISTRATIONS.CONTAINER_APP_NAME, containerAppName)
            .set(TARGET_REGISTRATIONS.DEPLOYMENT_STACK_NAME, deploymentStackName)
            .set(TARGET_REGISTRATIONS.REGISTRY_AUTH_MODE, registryAuthMode)
            .set(TARGET_REGISTRATIONS.REGISTRY_SERVER, registryServer)
            .set(TARGET_REGISTRATIONS.REGISTRY_USERNAME, registryUsername)
            .set(TARGET_REGISTRATIONS.REGISTRY_PASSWORD_SECRET_NAME, registryPasswordSecretName)
            .set(TARGET_REGISTRATIONS.REGISTRATION_SOURCE, registrationSource)
            .set(TARGET_REGISTRATIONS.UPDATED_AT, now)
            .where(TARGET_REGISTRATIONS.TARGET_ID.eq(targetId))
            .execute();

        if (patch.lastDeployedRelease() != null || patch.healthStatus() != null) {
            String lastDeployedRelease = AdminCommandSupport.firstNonBlank(
                patch.lastDeployedRelease(),
                current.lastDeployedRelease()
            );
            dsl.update(TARGETS)
                .set(TARGETS.LAST_DEPLOYED_RELEASE, AdminCommandSupport.defaultIfBlank(lastDeployedRelease, "unknown"))
                .set(
                    TARGETS.HEALTH_STATUS,
                    patch.healthStatus() == null
                        ? AdminCommandSupport.enumOrDefault(current.healthStatus(), MappoHealthStatus.registered)
                        : AdminCommandSupport.enumOrDefault(patch.healthStatus(), MappoHealthStatus.registered)
                )
                .set(TARGETS.UPDATED_AT, now)
                .where(TARGETS.ID.eq(targetId))
                .execute();
        }
    }
}
