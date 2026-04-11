package com.mappo.controlplane.service.project;

import static com.mappo.controlplane.application.project.validation.ProjectValidationFindingFactory.fail;
import static com.mappo.controlplane.application.project.validation.ProjectValidationFindingFactory.pass;
import static com.mappo.controlplane.application.project.validation.ProjectValidationFindingFactory.warning;

import com.mappo.controlplane.api.request.ProjectValidationRequest;
import com.mappo.controlplane.application.project.validation.ProjectCredentialsValidator;
import com.mappo.controlplane.application.project.validation.ProjectTargetContractValidator;
import com.mappo.controlplane.application.project.validation.ProjectWebhookValidator;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.model.ProjectValidationFindingRecord;
import com.mappo.controlplane.model.ProjectValidationFindingStatus;
import com.mappo.controlplane.model.ProjectValidationResultRecord;
import com.mappo.controlplane.model.ProjectValidationScope;
import com.mappo.controlplane.model.TargetExecutionContextRecord;
import com.mappo.controlplane.model.TargetRecord;
import com.mappo.controlplane.persistence.target.TargetExecutionContextRepository;
import com.mappo.controlplane.persistence.target.TargetRecordQueryRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ProjectValidationService {

    private final ProjectCatalogService projectCatalogService;
    private final TargetRecordQueryRepository targetRecordQueryRepository;
    private final TargetExecutionContextRepository targetExecutionContextRepository;
    private final List<ProjectCredentialsValidator> credentialsValidators;
    private final List<ProjectWebhookValidator> webhookValidators;
    private final List<ProjectTargetContractValidator> targetContractValidators;

    public ProjectValidationResultRecord validateProject(String projectId, ProjectValidationRequest request) {
        ProjectDefinition project = projectCatalogService.getRequired(projectId);
        Set<ProjectValidationScope> scopes = resolveScopes(request);
        List<ProjectValidationFindingRecord> findings = new ArrayList<>();

        if (scopes.contains(ProjectValidationScope.credentials)) {
            findings.addAll(validateCredentials(project));
        }
        if (scopes.contains(ProjectValidationScope.webhook)) {
            findings.addAll(validateWebhook(project));
        }
        if (scopes.contains(ProjectValidationScope.target_contract)) {
            findings.addAll(validateTargetContract(project, request == null ? null : request.targetId()));
        }

        boolean valid = findings.stream().noneMatch(finding -> finding.status() == ProjectValidationFindingStatus.fail);
        return new ProjectValidationResultRecord(project.id(), valid, OffsetDateTime.now(ZoneOffset.UTC), findings);
    }

    private List<ProjectValidationFindingRecord> validateCredentials(ProjectDefinition project) {
        List<ProjectValidationFindingRecord> findings = new ArrayList<>();
        credentialsValidators.stream()
            .filter(validator -> validator.supports(project))
            .flatMap(validator -> validator.validate(project).stream())
            .forEach(findings::add);
        if (findings.isEmpty()) {
            findings.add(warning(
                ProjectValidationScope.credentials,
                "ACCESS_STRATEGY_UNCHECKED",
                "Credential validation has no strategy-specific checks for this access strategy."
            ));
        }
        return findings;
    }

    private List<ProjectValidationFindingRecord> validateWebhook(ProjectDefinition project) {
        List<ProjectValidationFindingRecord> findings = new ArrayList<>();
        webhookValidators.stream()
            .filter(validator -> validator.supports(project))
            .flatMap(validator -> validator.validate(project).stream())
            .forEach(findings::add);
        if (findings.isEmpty()) {
            findings.add(warning(
                ProjectValidationScope.webhook,
                "WEBHOOK_UNCHECKED",
                "Webhook validation has no source-specific checks for this release source."
            ));
        }
        return findings;
    }

    private List<ProjectValidationFindingRecord> validateTargetContract(ProjectDefinition project, String requestedTargetId) {
        List<ProjectValidationFindingRecord> findings = new ArrayList<>();
        Optional<TargetRecord> maybeTarget = resolveTarget(project.id(), requestedTargetId);
        if (maybeTarget.isEmpty()) {
            findings.add(warning(
                ProjectValidationScope.target_contract,
                "NO_TARGETS_AVAILABLE",
                "No targets are registered for this project; target contract validation skipped."
            ));
            return findings;
        }

        TargetRecord target = maybeTarget.get();
        Optional<TargetExecutionContextRecord> context = targetExecutionContextRepository
            .getExecutionContextsByIds(List.of(target.id()))
            .stream()
            .findFirst();
        if (context.isEmpty()) {
            findings.add(fail(
                ProjectValidationScope.target_contract,
                "TARGET_CONTEXT_MISSING",
                "Target " + target.id() + " is missing execution context metadata."
            ));
            return findings;
        }

        TargetExecutionContextRecord record = context.get();
        if (record.tenantId() == null || record.subscriptionId() == null) {
            findings.add(fail(
                ProjectValidationScope.target_contract,
                "TARGET_TENANT_SUBSCRIPTION_MISSING",
                "Target " + target.id() + " is missing tenantId/subscriptionId metadata."
            ));
        }

        int providerFindingStart = findings.size();
        targetContractValidators.stream()
            .filter(validator -> validator.supports(project))
            .flatMap(validator -> validator.validate(project, target, record).stream())
            .forEach(findings::add);
        if (findings.size() == providerFindingStart) {
            findings.add(warning(
                ProjectValidationScope.target_contract,
                "TARGET_CONTRACT_UNCHECKED",
                "Target contract validation has no driver-specific checks for " + project.deploymentDriver() + "."
            ));
        }

        if (findings.stream().noneMatch(item -> item.status() == ProjectValidationFindingStatus.fail)) {
            findings.add(pass(
                ProjectValidationScope.target_contract,
                "TARGET_CONTRACT_VALID",
                "Target " + target.id() + " satisfies required execution metadata for " + project.deploymentDriver() + "."
            ));
        }
        return findings;
    }

    private Optional<TargetRecord> resolveTarget(String projectId, String requestedTargetId) {
        String targetId = normalize(requestedTargetId);
        if (hasText(targetId)) {
            return targetRecordQueryRepository.getTarget(targetId)
                .filter(target -> projectId.equals(target.projectId()));
        }
        List<TargetRecord> targets = targetRecordQueryRepository.getTargetsByTagFiltersForProject(Map.of(), projectId);
        if (targets.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(targets.get(0));
    }

    private Set<ProjectValidationScope> resolveScopes(ProjectValidationRequest request) {
        if (request == null || request.scopes() == null || request.scopes().isEmpty()) {
            return Set.of(ProjectValidationScope.credentials, ProjectValidationScope.webhook, ProjectValidationScope.target_contract);
        }
        Set<ProjectValidationScope> scopes = new LinkedHashSet<>();
        for (ProjectValidationScope scope : request.scopes()) {
            if (scope != null) {
                scopes.add(scope);
            }
        }
        if (scopes.isEmpty()) {
            return Set.of(ProjectValidationScope.credentials, ProjectValidationScope.webhook, ProjectValidationScope.target_contract);
        }
        return scopes;
    }

    private boolean hasText(String value) {
        return !normalize(value).isBlank();
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

}
