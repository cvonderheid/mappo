package com.mappo.controlplane.service.runtime;

import com.mappo.controlplane.domain.health.RuntimeHealthProvider;
import com.mappo.controlplane.jooq.enums.MappoRuntimeProbeStatus;
import com.mappo.controlplane.model.TargetRuntimeProbeContextRecord;
import com.mappo.controlplane.model.TargetRuntimeProbeRecord;
import com.mappo.controlplane.persistence.target.TargetCommandRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TargetRuntimeProbeExecutionService {

    private final RuntimeHealthProviderRegistry runtimeHealthProviderRegistry;
    private final TargetCommandRepository targetCommandRepository;

    public Optional<TargetRuntimeProbeRecord> probeAndPersist(TargetRuntimeProbeContextRecord target) {
        return probe(target).map(record -> {
            targetCommandRepository.upsertRuntimeProbe(record);
            return record;
        });
    }

    public Optional<TargetRuntimeProbeRecord> probe(TargetRuntimeProbeContextRecord target) {
        try {
            return runtimeHealthProviderRegistry.findConfigured(target)
                .map(provider -> provider.probe(target));
        } catch (RuntimeException error) {
            return Optional.of(new TargetRuntimeProbeRecord(
                target.targetId(),
                MappoRuntimeProbeStatus.unknown,
                OffsetDateTime.now(ZoneOffset.UTC),
                null,
                null,
                "Runtime probe failed: " + summarizeError(error)
            ));
        }
    }

    private String summarizeError(Throwable error) {
        String message = error == null ? "" : String.valueOf(error.getMessage()).trim();
        if (!message.isBlank()) {
            return message;
        }
        return error == null ? "unknown error" : error.getClass().getSimpleName();
    }
}
