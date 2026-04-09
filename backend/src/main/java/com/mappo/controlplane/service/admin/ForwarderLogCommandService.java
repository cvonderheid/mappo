package com.mappo.controlplane.service.admin;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.api.request.ForwarderLogIngestRequest;
import com.mappo.controlplane.jooq.enums.MappoMarketplaceEventStatus;
import com.mappo.controlplane.model.ForwarderLogIngestResultRecord;
import com.mappo.controlplane.persistence.admin.ForwarderLogCommandRepository;
import com.mappo.controlplane.service.TransactionHookService;
import com.mappo.controlplane.service.live.LiveUpdateService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ForwarderLogCommandService {

    private final ForwarderLogCommandRepository forwarderLogCommandRepository;
    private final LiveUpdateService liveUpdateService;
    private final TransactionHookService transactionHookService;

    @Transactional
    public ForwarderLogIngestResultRecord ingest(ForwarderLogIngestRequest request) {
        if (request == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "forwarder log request is required");
        }

        var command = request.toCommand();
        String logId = normalize(command.logId());
        if (logId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "log_id is required");
        }

        if (forwarderLogCommandRepository.forwarderLogExists(logId)) {
            return new ForwarderLogIngestResultRecord(
                logId,
                MappoMarketplaceEventStatus.duplicate,
                "forwarder log already ingested"
            );
        }

        forwarderLogCommandRepository.saveForwarderLog(command);
        transactionHookService.afterCommitOrNow(liveUpdateService::emitAdminUpdated);
        return new ForwarderLogIngestResultRecord(
            logId,
            MappoMarketplaceEventStatus.applied,
            "forwarder log ingested"
        );
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
