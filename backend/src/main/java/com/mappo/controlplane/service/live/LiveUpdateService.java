package com.mappo.controlplane.service.live;

import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.model.LiveUpdateEventRecord;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class LiveUpdateService {

    private static final long SSE_TIMEOUT_MS = 0L;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    private final MappoProperties properties;

    public LiveUpdateService(MappoProperties properties) {
        this.properties = properties;
    }

    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        String emitterId = "sse-" + UUID.randomUUID();
        emitters.put(emitterId, emitter);
        emitter.onCompletion(() -> emitters.remove(emitterId));
        emitter.onTimeout(() -> {
            emitter.complete();
            emitters.remove(emitterId);
        });
        emitter.onError(error -> emitters.remove(emitterId));
        sendSafely(emitterId, emitter, "connected", new LiveUpdateEventRecord("connected", null, now()));
        return emitter;
    }

    public void emitTargetsUpdated() {
        publish("targets-updated", null);
    }

    public void emitReleasesUpdated() {
        publish("releases-updated", null);
    }

    public void emitAdminUpdated() {
        publish("admin-updated", null);
    }

    public void emitRunsUpdated() {
        publish("runs-updated", null);
    }

    public void emitRunUpdated(String runId) {
        publish("run-updated", runId);
    }

    @Scheduled(
        fixedDelayString = "${mappo.sse-heartbeat-interval-ms:15000}",
        initialDelayString = "${mappo.sse-heartbeat-interval-ms:15000}"
    )
    public void emitHeartbeat() {
        if (!properties.isSseEnabled() || emitters.isEmpty()) {
            return;
        }
        publish("heartbeat", null);
    }

    private void publish(String type, String subjectId) {
        if (!properties.isSseEnabled() || emitters.isEmpty()) {
            return;
        }
        LiveUpdateEventRecord event = new LiveUpdateEventRecord(type, subjectId, now());
        emitters.forEach((emitterId, emitter) -> sendSafely(emitterId, emitter, type, event));
    }

    private void sendSafely(String emitterId, SseEmitter emitter, String eventName, LiveUpdateEventRecord event) {
        try {
            emitter.send(
                SseEmitter.event()
                    .name(eventName)
                    .data(event)
            );
        } catch (IOException | IllegalStateException error) {
            emitters.remove(emitterId);
            try {
                emitter.completeWithError(error);
            } catch (IllegalStateException ignored) {
                // emitter already closed
            }
        }
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
