package com.mappo.controlplane.service.release;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.domain.project.ProjectDefinition;
import com.mappo.controlplane.domain.project.ProjectDeploymentDriverType;
import com.mappo.controlplane.domain.project.ProjectReleaseArtifactSourceType;
import com.mappo.controlplane.jooq.enums.MappoReleaseSourceType;
import com.mappo.controlplane.model.ReleaseManifestIngestResultRecord;
import com.mappo.controlplane.model.ReleaseRecord;
import com.mappo.controlplane.model.command.CreateReleaseCommand;
import com.mappo.controlplane.repository.ReleaseCommandRepository;
import com.mappo.controlplane.repository.ReleaseQueryRepository;
import com.mappo.controlplane.service.live.LiveUpdateService;
import com.mappo.controlplane.service.TransactionHookService;
import com.mappo.controlplane.service.project.ProjectCatalogService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReleaseManifestApplyService {

    private final ReleaseQueryRepository releaseQueryRepository;
    private final ReleaseCommandRepository releaseCommandRepository;
    private final LiveUpdateService liveUpdateService;
    private final TransactionHookService transactionHookService;
    private final ProjectCatalogService projectCatalogService;

    @Transactional
    public ReleaseManifestIngestResultRecord apply(
        String repo,
        String path,
        String ref,
        boolean allowDuplicates,
        ParsedReleaseManifest parsedManifest
    ) {
        return apply(repo, path, ref, allowDuplicates, parsedManifest, List.of());
    }

    @Transactional
    public ReleaseManifestIngestResultRecord apply(
        String repo,
        String path,
        String ref,
        boolean allowDuplicates,
        ParsedReleaseManifest parsedManifest,
        List<String> fallbackProjectIds
    ) {
        List<String> resolvedFallbackProjectIds = fallbackProjectIds.stream()
            .map(projectCatalogService::resolveRequiredProjectId)
            .distinct()
            .toList();
        Set<String> existingKeys = new LinkedHashSet<>();
        if (!allowDuplicates) {
            for (ReleaseRecord row : releaseQueryRepository.listReleases()) {
                existingKeys.add(releaseKey(row.projectId(), row.sourceRef(), row.sourceVersion()));
            }
        }

        int created = 0;
        int skipped = 0;
        List<String> createdReleaseIds = new ArrayList<>();
        Set<String> createdProjectIds = new LinkedHashSet<>();
        for (var candidate : parsedManifest.requests()) {
            List<String> candidateProjectIds = resolveCandidateProjectIds(candidate, resolvedFallbackProjectIds);
            if (candidateProjectIds.isEmpty()) {
                skipped += 1;
                continue;
            }
            for (String resolvedProjectId : candidateProjectIds) {
                String key = releaseKey(resolvedProjectId, candidate.sourceRef(), candidate.sourceVersion());
                if (!allowDuplicates && existingKeys.contains(key)) {
                    skipped += 1;
                    continue;
                }
                var command = candidate.toCommand();
                ReleaseRecord createdRelease = releaseCommandRepository.createRelease(new CreateReleaseCommand(
                    resolvedProjectId,
                    command.sourceRef(),
                    command.sourceVersion(),
                    command.sourceType(),
                    command.sourceVersionRef(),
                    command.deploymentScope(),
                    command.armDeploymentMode(),
                    command.whatIfOnCanary(),
                    command.verifyAfterDeploy(),
                    command.parameterDefaults(),
                    command.externalInputs(),
                    command.releaseNotes(),
                    command.verificationHints()
                ));
                created += 1;
                createdReleaseIds.add(createdRelease.id());
                createdProjectIds.add(resolvedProjectId);
                existingKeys.add(key);
            }
        }

        if (created > 0) {
            transactionHookService.afterCommitOrNow(() -> createdProjectIds.forEach(liveUpdateService::emitReleasesUpdated));
        }

        return new ReleaseManifestIngestResultRecord(
            repo,
            path,
            ref,
            parsedManifest.manifestReleaseCount(),
            created,
            skipped,
            parsedManifest.ignoredCount(),
            List.copyOf(createdReleaseIds)
        );
    }

    private String releaseKey(String projectId, String sourceRef, String sourceVersion) {
        return normalize(projectId) + "::" + normalize(sourceRef) + "::" + normalize(sourceVersion);
    }

    private List<String> resolveCandidateProjectIds(
        com.mappo.controlplane.api.request.ReleaseCreateRequest candidate,
        List<String> fallbackProjectIds
    ) {
        String explicitProjectId = normalize(candidate.projectId());
        if (!explicitProjectId.isBlank()) {
            return List.of(projectCatalogService.resolveRequiredProjectId(explicitProjectId));
        }
        if (fallbackProjectIds.isEmpty()) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "release %s is missing project_id and no linked project fallback is available".formatted(
                    normalize(candidate.sourceVersion())
                )
            );
        }
        return fallbackProjectIds.stream()
            .map(projectCatalogService::getRequired)
            .filter(project -> projectAcceptsSourceType(project, candidate.sourceType()))
            .map(ProjectDefinition::id)
            .toList();
    }

    private boolean projectAcceptsSourceType(ProjectDefinition project, MappoReleaseSourceType sourceType) {
        String normalizedSourceType = normalize(sourceType);
        return switch (normalizedSourceType) {
            case "deployment_stack", "bicep" ->
                project.releaseArtifactSource() == ProjectReleaseArtifactSourceType.blob_arm_template
                    || project.deploymentDriver() == ProjectDeploymentDriverType.azure_deployment_stack;
            case "template_spec" ->
                project.releaseArtifactSource() == ProjectReleaseArtifactSourceType.template_spec_resource
                    || project.deploymentDriver() == ProjectDeploymentDriverType.azure_template_spec;
            case "external_deployment_inputs" ->
                project.releaseArtifactSource() == ProjectReleaseArtifactSourceType.external_deployment_inputs
                    || project.deploymentDriver() == ProjectDeploymentDriverType.pipeline_trigger;
            default -> false;
        };
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
