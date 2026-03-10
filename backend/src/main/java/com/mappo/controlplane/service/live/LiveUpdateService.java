package com.mappo.controlplane.service.live;

import com.mappo.controlplane.config.MappoProperties;
import com.mappo.controlplane.model.LiveUpdateEventRecord;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class LiveUpdateService {

    private static final long SSE_TIMEOUT_MS = 0L;
    private static final Set<String> ALL_TOPICS = Set.of("targets", "releases", "admin", "runs");

    private final Map<String, EmitterRegistration> emitters = new ConcurrentHashMap<>();
    private final Map<String, LiveUpdateEventRecord> pendingEvents = new ConcurrentHashMap<>();
    private final MappoProperties properties;

    public LiveUpdateService(MappoProperties properties) {
        this.properties = properties;
    }

    public SseEmitter subscribe(Set<String> requestedTopics) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        String emitterId = "sse-" + UUID.randomUUID();
        emitters.put(emitterId, new EmitterRegistration(emitter, normalizeTopics(requestedTopics)));
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
        enqueue("targets-updated", null);
    }

    public void emitReleasesUpdated() {
        enqueue("releases-updated", null);
    }

    public void emitAdminUpdated() {
        enqueue("admin-updated", null);
    }

    public void emitRunsUpdated() {
        enqueue("runs-updated", null);
    }

    public void emitRunUpdated(String runId) {
        enqueue("run-updated", runId);
    }

    @Scheduled(
        fixedDelayString = "${mappo.sse.heartbeat-interval-ms:15000}",
        initialDelayString = "${mappo.sse.heartbeat-interval-ms:15000}"
    )
    public void emitHeartbeat() {
        if (!properties.getSse().isEnabled() || emitters.isEmpty()) {
            return;
        }
        publishDirect("heartbeat", null);
    }

    @Scheduled(
        fixedDelayString = "${mappo.sse.coalesce-window-ms:250}",
        initialDelayString = "${mappo.sse.coalesce-window-ms:250}"
    )
    public void flushPending() {
        if (!properties.getSse().isEnabled() || emitters.isEmpty() || pendingEvents.isEmpty()) {
            pendingEvents.clear();
            return;
        }
        Map<String, LiveUpdateEventRecord> snapshot = new LinkedHashMap<>();
        pendingEvents.forEach((key, value) -> {
            if (pendingEvents.remove(key, value)) {
                snapshot.put(key, value);
            }
        });
        snapshot.values().forEach(event -> publishDirect(event.type(), event.subjectId()));
    }

    private void enqueue(String type, String subjectId) {
        if (!properties.getSse().isEnabled() || emitters.isEmpty()) {
            return;
        }
        LiveUpdateEventRecord event = new LiveUpdateEventRecord(type, subjectId, now());
        pendingEvents.put(eventKey(type, subjectId), event);
    }

    private void publishDirect(String type, String subjectId) {
        if (!properties.getSse().isEnabled() || emitters.isEmpty()) {
            return;
        }
        LiveUpdateEventRecord event = new LiveUpdateEventRecord(type, subjectId, now());
        String topic = topicFor(type);
        emitters.forEach((emitterId, registration) -> {
            if (!topic.isBlank() && !registration.topics().contains(topic)) {
                return;
            }
            sendSafely(emitterId, registration.emitter(), type, event);
        });
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

    private Set<String> normalizeTopics(Set<String> requestedTopics) {
        if (requestedTopics == null || requestedTopics.isEmpty()) {
            return ALL_TOPICS;
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String topic : requestedTopics) {
            String value = topic == null ? "" : topic.trim().toLowerCase();
            if (ALL_TOPICS.contains(value)) {
                normalized.add(value);
            }
        }
        return normalized.isEmpty() ? ALL_TOPICS : Set.copyOf(normalized);
    }

    private String eventKey(String type, String subjectId) {
        return type + ":" + (subjectId == null ? "" : subjectId);
    }

    private String topicFor(String type) {
        return switch (type) {
            case "targets-updated" -> "targets";
            case "releases-updated" -> "releases";
            case "admin-updated" -> "admin";
            case "runs-updated", "run-updated" -> "runs";
            default -> "";
        };
    }

    private record EmitterRegistration(
        SseEmitter emitter,
        Set<String> topics
    ) {
    }
}
