package com.mappo.tooling;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class MarketplaceIngestEventsCommand {

    private static final TypeReference<List<LinkedHashMap<String, Object>>> LIST_OF_MAPS = new TypeReference<>() {
    };
    private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpSupport httpSupport = new HttpSupport(objectMapper);

    int run(List<String> rawArgs) {
        Arguments args = new Arguments(rawArgs);
        Path inventoryFile = FileSupport.repoRoot().resolve(".data/mappo-target-inventory.json");
        String apiBaseUrl = System.getenv().getOrDefault("MAPPO_API_BASE_URL", "http://localhost:8010");
        String eventType = "subscription_purchased";
        String ingestToken = System.getenv().getOrDefault("MAPPO_MARKETPLACE_INGEST_TOKEN", "");
        String eventIdPrefix = "evt-marketplace-demo";
        String sourceLabel = "marketplace-webhook-simulator";
        boolean dryRun = false;

        while (args.hasNext()) {
            String arg = args.next();
            switch (arg) {
                case "--inventory-file" -> inventoryFile = resolvePath(args.nextRequired("--inventory-file"));
                case "--api-base-url" -> apiBaseUrl = args.nextRequired("--api-base-url");
                case "--event-type" -> eventType = args.nextRequired("--event-type");
                case "--ingest-token" -> ingestToken = args.nextRequired("--ingest-token");
                case "--event-id-prefix" -> eventIdPrefix = args.nextRequired("--event-id-prefix");
                case "--source-label" -> sourceLabel = args.nextRequired("--source-label");
                case "--dry-run" -> dryRun = true;
                case "-h", "--help" -> {
                    printUsage();
                    return 0;
                }
                default -> throw new ToolingException("marketplace-ingest-events: unknown argument: " + arg, 2);
            }
        }

        if (!java.nio.file.Files.exists(inventoryFile)) {
            throw new ToolingException("marketplace-ingest-events: inventory file not found: " + inventoryFile, 2);
        }

        List<LinkedHashMap<String, Object>> rows = readInventory(inventoryFile);
        String endpoint = apiBaseUrl.replaceAll("/+$", "") + "/api/v1/admin/onboarding/events";
        String batchId = UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        int applied = 0;
        int duplicate = 0;
        int rejected = 0;
        int requestFailed = 0;
        int validationFailed = 0;

        for (int index = 0; index < rows.size(); index++) {
            Map<String, Object> row = rows.get(index);
            List<String> missingFields = missingInventoryFields(row);
            if (!missingFields.isEmpty()) {
                System.err.printf(
                    "marketplace-ingest-events: skipping target row #%d due to missing fields: %s%n",
                    index + 1,
                    String.join(", ", missingFields)
                );
                validationFailed += 1;
                continue;
            }

            String targetId = stringValue(row.get("id"));
            Map<String, String> tags = stringMap(mapValue(row.get("tags")));
            Map<String, Object> metadata = mapValue(row.get("metadata"));
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("eventId", "%s-%s-%03d".formatted(eventIdPrefix, batchId, index + 1));
            payload.put("eventType", eventType);
            payload.put("tenantId", stringValue(row.get("tenant_id")));
            payload.put("subscriptionId", stringValue(row.get("subscription_id")));
            payload.put("containerAppResourceId", stringValue(row.get("managed_app_id")));
            payload.put("targetId", targetId);
            payload.put("displayName", firstNonBlank(stringValue(metadata.get("managed_application_name")), targetId));
            payload.put("targetGroup", firstNonBlank(tags.get("ring"), "prod"));
            payload.put("environment", firstNonBlank(tags.get("environment"), "prod"));
            payload.put("tier", firstNonBlank(tags.get("tier"), "standard"));
            payload.put("healthStatus", "registered");
            payload.put("lastDeployedRelease", "unknown");
            if (!stringValue(metadata.get("managed_application_id")).isBlank()) {
                payload.put("managedApplicationId", stringValue(metadata.get("managed_application_id")));
            }
            if (!stringValue(metadata.get("managed_resource_group_id")).isBlank()) {
                payload.put("managedResourceGroupId", stringValue(metadata.get("managed_resource_group_id")));
            }
            if (!stringValue(metadata.get("container_app_name")).isBlank()) {
                payload.put("containerAppName", stringValue(metadata.get("container_app_name")));
            }
            String customerName = firstNonBlank(tags.get("customer"), stringValue(metadata.get("customer_name")));
            if (!customerName.isBlank()) {
                payload.put("customerName", customerName);
            }
            if (!stringValue(tags.get("region")).isBlank()) {
                payload.put("region", tags.get("region"));
            }
            payload.put("tags", tags);
            payload.put(
                "metadata",
                Map.of(
                    "source", sourceLabel,
                    "inventoryTargetId", targetId,
                    "inventoryManagedApplicationId", stringValue(metadata.get("managed_application_id")),
                    "inventoryManagedResourceGroupId", stringValue(metadata.get("managed_resource_group_id"))
                )
            );

            if (dryRun) {
                System.out.println(writeJson(payload));
                continue;
            }

            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Accept", "application/json");
            if (!ingestToken.isBlank()) {
                headers.put("x-mappo-ingest-token", ingestToken);
            }
            HttpSupport.HttpResult result = httpSupport.postJson(endpoint, payload, headers, 20);
            if (!result.isSuccess()) {
                requestFailed += 1;
                System.err.printf(
                    "marketplace-ingest-events: HTTP %d for target %s: %s%n",
                    result.statusCode(),
                    targetId,
                    result.body()
                );
                continue;
            }

            Map<String, Object> response = readMap(result.body());
            String statusValue = stringValue(response.get("status")).toLowerCase();
            String message = firstNonBlank(stringValue(response.get("message")), "(no message)");
            switch (statusValue) {
                case "applied" -> applied += 1;
                case "duplicate" -> duplicate += 1;
                case "rejected" -> rejected += 1;
                default -> {
                    requestFailed += 1;
                    System.err.printf(
                        "marketplace-ingest-events: unexpected response status for %s: %s%n",
                        targetId,
                        statusValue.isBlank() ? "unknown" : statusValue
                    );
                }
            }
            System.out.printf("%s: %s :: %s%n", targetId, statusValue.isBlank() ? "unknown" : statusValue, message);
        }

        System.out.printf(
            "marketplace-ingest-events: rows=%d applied=%d duplicate=%d rejected=%d validation_failed=%d request_failed=%d dry_run=%s%n",
            rows.size(),
            applied,
            duplicate,
            rejected,
            validationFailed,
            requestFailed,
            dryRun ? "true" : "false"
        );
        return requestFailed > 0 ? 1 : 0;
    }

    private List<LinkedHashMap<String, Object>> readInventory(Path inventoryFile) {
        try {
            return objectMapper.readValue(FileSupport.readText(inventoryFile), LIST_OF_MAPS);
        } catch (Exception exception) {
            throw new ToolingException("marketplace-ingest-events: inventory JSON must be an array", 2);
        }
    }

    private Map<String, Object> readMap(String body) {
        try {
            return objectMapper.readValue(body, MAP_TYPE);
        } catch (Exception exception) {
            throw new ToolingException("marketplace-ingest-events: API returned invalid JSON: " + exception.getMessage(), 1);
        }
    }

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            throw new ToolingException("marketplace-ingest-events: failed serializing payload: " + exception.getMessage(), 1);
        }
    }

    private List<String> missingInventoryFields(Map<String, Object> row) {
        List<String> missing = new ArrayList<>();
        if (stringValue(row.get("id")).isBlank()) {
            missing.add("id");
        }
        if (stringValue(row.get("tenant_id")).isBlank()) {
            missing.add("tenant_id");
        }
        if (stringValue(row.get("subscription_id")).isBlank()) {
            missing.add("subscription_id");
        }
        if (stringValue(row.get("managed_app_id")).isBlank()) {
            missing.add("managed_app_id");
        }
        return missing;
    }

    private Path resolvePath(String value) {
        Path path = Path.of(value);
        return path.isAbsolute() ? path : FileSupport.repoRoot().resolve(path).normalize();
    }

    private Map<String, Object> mapValue(Object value) {
        if (value instanceof Map<?, ?> source) {
            Map<String, Object> out = new LinkedHashMap<>();
            source.forEach((key, item) -> {
                String normalizedKey = stringValue(key);
                if (!normalizedKey.isBlank()) {
                    out.put(normalizedKey, item);
                }
            });
            return out;
        }
        return Map.of();
    }

    private Map<String, String> stringMap(Map<String, Object> source) {
        Map<String, String> out = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            String normalizedKey = stringValue(key);
            String normalizedValue = stringValue(value);
            if (!normalizedKey.isBlank() && !normalizedValue.isBlank()) {
                out.put(normalizedKey, normalizedValue);
            }
        });
        return out;
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private void printUsage() {
        System.out.println("""
            usage: marketplace-ingest-events [options]
              --inventory-file <path>
              --api-base-url <url>
              --event-type <name>
              --ingest-token <token>
              --event-id-prefix <prefix>
              --source-label <label>
              --dry-run
            """);
    }
}
