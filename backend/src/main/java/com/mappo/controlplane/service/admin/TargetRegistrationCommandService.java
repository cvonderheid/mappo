package com.mappo.controlplane.service.admin;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.TargetRegistrationPatchRequest;
import com.mappo.controlplane.model.TargetRegistrationRecord;
import com.mappo.controlplane.persistence.target.TargetCommandRepository;
import com.mappo.controlplane.persistence.target.TargetExecutionConfigCommandRepository;
import com.mappo.controlplane.persistence.target.TargetRecordQueryRepository;
import com.mappo.controlplane.persistence.target.TargetRegistrationCommandRepository;
import com.mappo.controlplane.persistence.target.TargetRegistrationQueryRepository;
import com.mappo.controlplane.service.TransactionHookService;
import com.mappo.controlplane.service.live.LiveUpdateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TargetRegistrationCommandService {

    private final TargetRegistrationQueryRepository targetRegistrationQueryRepository;
    private final TargetRegistrationCommandRepository targetRegistrationCommandRepository;
    private final TargetCommandRepository targetCommandRepository;
    private final TargetExecutionConfigCommandRepository targetExecutionConfigCommandRepository;
    private final TargetRecordQueryRepository targetRecordQueryRepository;
    private final LiveUpdateService liveUpdateService;
    private final TransactionHookService transactionHookService;

    @Transactional
    public TargetRegistrationRecord update(String targetId, TargetRegistrationPatchRequest patch) {
        TargetRegistrationRecord existing = targetRegistrationQueryRepository.getRegistration(targetId).orElse(null);
        if (existing == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "target registration not found: " + targetId);
        }
        if (patch == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "registration patch is required");
        }

        targetRegistrationCommandRepository.updateRegistrationAndTarget(targetId, patch.toCommand());
        if (patch.metadata() != null && patch.metadata().executionConfig() != null) {
            targetExecutionConfigCommandRepository.replaceConfigEntries(
                targetId,
                patch.metadata().sanitizedExecutionConfig()
            );
        }
        String projectId = targetRecordQueryRepository.getTarget(targetId)
            .map(target -> target.projectId() == null ? "" : target.projectId())
            .orElse("");
        transactionHookService.afterCommitOrNow(() -> {
            liveUpdateService.emitAdminUpdated();
            liveUpdateService.emitTargetsUpdated(projectId);
        });
        return targetRegistrationQueryRepository.getRegistration(targetId)
            .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "target registration not found: " + targetId));
    }

    @Transactional
    public void delete(String targetId) {
        String projectId = targetRecordQueryRepository.getTarget(targetId)
            .map(target -> target.projectId() == null ? "" : target.projectId())
            .orElse("");
        targetRegistrationCommandRepository.deleteRegistration(targetId);
        targetCommandRepository.deleteTarget(targetId);
        transactionHookService.afterCommitOrNow(() -> {
            liveUpdateService.emitAdminUpdated();
            liveUpdateService.emitTargetsUpdated(projectId);
        });
    }
}
