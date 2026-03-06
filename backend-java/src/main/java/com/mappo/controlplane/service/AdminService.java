package com.mappo.controlplane.service;

import com.mappo.controlplane.api.ApiException;
import com.mappo.controlplane.repository.AdminRepository;
import com.mappo.controlplane.repository.TargetRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AdminService {

    private final AdminRepository adminRepository;
    private final TargetRepository targetRepository;

    public AdminService(AdminRepository adminRepository, TargetRepository targetRepository) {
        this.adminRepository = adminRepository;
        this.targetRepository = targetRepository;
    }

    public Map<String, Object> getOnboardingSnapshot(int eventLimit) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("registrations", adminRepository.listRegistrations());
        snapshot.put("events", adminRepository.listMarketplaceEvents(eventLimit));
        snapshot.put("forwarder_logs", adminRepository.listForwarderLogs(eventLimit));
        return snapshot;
    }

    public Map<String, Object> ingestMarketplaceEvent(Map<String, Object> request) {
        String eventId = stringValue(request.get("event_id"));
        String eventType = stringValue(request.getOrDefault("event_type", "subscription_purchased"));
        String tenantId = stringValue(request.get("tenant_id"));
        String subscriptionId = stringValue(request.get("subscription_id"));

        if (eventId.isBlank() || tenantId.isBlank() || subscriptionId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "event_id, tenant_id, and subscription_id are required");
        }

        if (adminRepository.marketplaceEventExists(eventId)) {
            Map<String, Object> duplicate = new LinkedHashMap<>();
            duplicate.put("event_id", eventId);
            duplicate.put("status", "duplicate");
            duplicate.put("message", "event already processed");
            duplicate.put("target_id", request.get("target_id"));
            return duplicate;
        }

        String targetId = resolveTargetId(request);
        String message;

        if (isDeleteEvent(eventType)) {
            adminRepository.deleteRegistration(targetId);
            targetRepository.deleteTarget(targetId);
            message = "Deleted target registration and target.";
        } else if (isSuspendEvent(eventType)) {
            Map<String, Object> existing = targetRepository.getTarget(targetId);
            if (!existing.isEmpty()) {
                targetRepository.updateTargetHealth(targetId, "degraded");
                message = "Marked target as degraded.";
            } else {
                message = "Target not found; suspension acknowledged.";
            }
        } else {
            String managedAppId = stringValue(request.get("container_app_resource_id"));
            if (managedAppId.isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "container_app_resource_id is required for registration events");
            }

            Map<String, String> tags = buildTags(request);
            Map<String, Object> target = new LinkedHashMap<>();
            target.put("id", targetId);
            target.put("tenant_id", tenantId);
            target.put("subscription_id", subscriptionId);
            target.put("managed_app_id", managedAppId);
            target.put("customer_name", nullableString(request.get("customer_name")));
            target.put("tags", tags);
            target.put("last_deployed_release", stringValue(request.getOrDefault("last_deployed_release", "unknown")));
            target.put("health_status", stringValue(request.getOrDefault("health_status", "registered")));
            target.put("last_check_in_at", OffsetDateTime.now(ZoneOffset.UTC));
            target.put("simulated_failure_mode", "none");
            targetRepository.upsertTarget(target);

            Map<String, Object> metadata = mapValue(request.get("metadata"));
            metadata.putIfAbsent("container_app_name", stringValue(request.get("container_app_name")));
            metadata.putIfAbsent("customer_name", stringValue(request.get("customer_name")));

            String managedResourceGroupId = stringValue(request.get("managed_resource_group_id"));
            if (managedResourceGroupId.isBlank()) {
                managedResourceGroupId = deriveResourceGroupIdFromContainerApp(managedAppId);
            }

            Map<String, Object> registration = new LinkedHashMap<>();
            registration.put("target_id", targetId);
            registration.put("display_name", defaultIfBlank(stringValue(request.get("display_name")), targetId));
            registration.put("managed_application_id", nullableString(request.get("managed_application_id")));
            registration.put("managed_resource_group_id", managedResourceGroupId);
            registration.put("metadata", metadata);
            registration.put("last_event_id", eventId);
            registration.put("created_at", OffsetDateTime.now(ZoneOffset.UTC));
            adminRepository.upsertRegistration(registration);

            message = "Registered target " + targetId + " for subscription " + subscriptionId + ".";
        }

        adminRepository.saveMarketplaceEvent(
            eventId,
            eventType,
            "applied",
            message,
            targetId,
            tenantId,
            subscriptionId,
            request
        );

        return Map.of(
            "event_id", eventId,
            "status", "applied",
            "message", message,
            "target_id", targetId
        );
    }

    public List<Map<String, Object>> listForwarderLogs(int limit) {
        return adminRepository.listForwarderLogs(limit);
    }

    public Map<String, Object> ingestForwarderLog(Map<String, Object> request) {
        String logId = stringValue(request.get("log_id"));
        if (logId.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "log_id is required");
        }

        if (adminRepository.forwarderLogExists(logId)) {
            return Map.of(
                "log_id", logId,
                "status", "duplicate",
                "message", "forwarder log already ingested"
            );
        }

        adminRepository.saveForwarderLog(request);
        return Map.of(
            "log_id", logId,
            "status", "applied",
            "message", "forwarder log ingested"
        );
    }

    public Map<String, Object> updateTargetRegistration(String targetId, Map<String, Object> patch) {
        Map<String, Object> existing = adminRepository.getRegistration(targetId);
        if (existing.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "target registration not found: " + targetId);
        }
        adminRepository.updateRegistrationAndTarget(targetId, patch);
        return adminRepository.getRegistration(targetId);
    }

    public void deleteTargetRegistration(String targetId) {
        adminRepository.deleteRegistration(targetId);
        targetRepository.deleteTarget(targetId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> raw) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                map.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return map;
        }
        return new LinkedHashMap<>();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> buildTags(Map<String, Object> request) {
        Map<String, String> tags = new LinkedHashMap<>();
        Object tagsObj = request.get("tags");
        if (tagsObj instanceof Map<?, ?> raw) {
            for (Map.Entry<?, ?> entry : raw.entrySet()) {
                String key = String.valueOf(entry.getKey()).trim();
                String value = String.valueOf(entry.getValue()).trim();
                if (!key.isBlank() && !value.isBlank()) {
                    tags.put(key, value);
                }
            }
        }

        putDefault(tags, "ring", stringValue(request.getOrDefault("target_group", "prod")));
        putDefault(tags, "region", defaultIfBlank(stringValue(request.get("region")), "eastus"));
        putDefault(tags, "environment", stringValue(request.getOrDefault("environment", "prod")));
        putDefault(tags, "tier", stringValue(request.getOrDefault("tier", "standard")));
        return tags;
    }

    private void putDefault(Map<String, String> tags, String key, String value) {
        if (!tags.containsKey(key) && !value.isBlank()) {
            tags.put(key, value);
        }
    }

    private boolean isSuspendEvent(String eventType) {
        String normalized = eventType.toLowerCase();
        return normalized.contains("suspend") || normalized.contains("subscription_suspended");
    }

    private boolean isDeleteEvent(String eventType) {
        String normalized = eventType.toLowerCase();
        return normalized.contains("delete") || normalized.contains("unsubscribe") || normalized.contains("cancel");
    }

    private String resolveTargetId(Map<String, Object> request) {
        String targetId = stringValue(request.get("target_id"));
        if (!targetId.isBlank()) {
            return targetId;
        }

        String displayName = stringValue(request.get("display_name"));
        if (!displayName.isBlank()) {
            return normalizeId(displayName);
        }

        String managedAppId = stringValue(request.get("managed_application_id"));
        if (!managedAppId.isBlank()) {
            int idx = managedAppId.lastIndexOf("/");
            if (idx >= 0 && idx < managedAppId.length() - 1) {
                return normalizeId(managedAppId.substring(idx + 1));
            }
        }

        String containerAppName = stringValue(request.get("container_app_name"));
        if (!containerAppName.isBlank()) {
            return normalizeId(containerAppName);
        }

        return "target-" + Math.abs((stringValue(request.get("subscription_id")) + "-" + stringValue(request.get("tenant_id"))).hashCode());
    }

    private String normalizeId(String value) {
        String normalized = value.toLowerCase().replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-").replaceAll("^-|-$", "");
        return normalized.isBlank() ? "target-generated" : normalized;
    }

    private String deriveResourceGroupIdFromContainerApp(String containerAppId) {
        int idx = containerAppId.toLowerCase().indexOf("/providers/");
        if (idx > 0) {
            return containerAppId.substring(0, idx);
        }
        return "";
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String defaultIfBlank(String value, String fallback) {
        return value.isBlank() ? fallback : value;
    }

    private String nullableString(Object value) {
        String valueText = stringValue(value);
        return valueText.isBlank() ? null : valueText;
    }
}
