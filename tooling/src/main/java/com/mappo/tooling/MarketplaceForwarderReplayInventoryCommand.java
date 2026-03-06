package com.mappo.tooling;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class MarketplaceForwarderReplayInventoryCommand {

    private static final TypeReference<List<LinkedHashMap<String, Object>>> LIST_OF_MAPS = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpSupport httpSupport = new HttpSupport(objectMapper);

    int run(List<String> rawArgs) {
        for (String arg : rawArgs) {
            if (Arguments.isHelpFlag(arg)) {
                printUsage();
                return 0;
            }
        }
        Arguments args = new Arguments(rawArgs);
        Path inventoryFile = FileSupport.repoRoot().resolve(".data/mappo-target-inventory.json");
        String forwarderUrl = System.getenv().getOrDefault("MAPPO_MARKETPLACE_FORWARDER_URL", "");
        String eventType = "subscription_purchased";
        String eventIdPrefix = "evt-marketplace-webhook";
        boolean dryRun = false;

        while (args.hasNext()) {
            String arg = args.next();
            switch (arg) {
                case "--forwarder-url" -> forwarderUrl = args.nextRequired("--forwarder-url");
                case "--inventory-file" -> inventoryFile = resolvePath(args.nextRequired("--inventory-file"));
                case "--event-type" -> eventType = args.nextRequired("--event-type");
                case "--event-id-prefix" -> eventIdPrefix = args.nextRequired("--event-id-prefix");
                case "--dry-run" -> dryRun = true;
                case "-h", "--help" -> {
                    printUsage();
                    return 0;
                }
                default -> throw new ToolingException("marketplace-forwarder-replay: unknown argument: " + arg, 2);
            }
        }

        if (forwarderUrl.isBlank()) {
            throw new ToolingException("marketplace-forwarder-replay: --forwarder-url is required.", 1);
        }
        if (!java.nio.file.Files.exists(inventoryFile)) {
            throw new ToolingException("marketplace-forwarder-replay: inventory file not found: " + inventoryFile, 1);
        }

        List<LinkedHashMap<String, Object>> rows;
        try {
            rows = objectMapper.readValue(FileSupport.readText(inventoryFile), LIST_OF_MAPS);
        } catch (Exception exception) {
            throw new ToolingException("marketplace-forwarder-replay: inventory JSON must be an array", 1);
        }

        int applied = 0;
        int failed = 0;

        for (int index = 0; index < rows.size(); index++) {
            Map<String, Object> row = rows.get(index);
            String tenantId = stringValue(row.get("tenant_id"));
            String subscriptionId = stringValue(row.get("subscription_id"));
            String containerAppId = stringValue(row.get("managed_app_id"));
            if (tenantId.isBlank() || subscriptionId.isBlank() || containerAppId.isBlank()) {
                continue;
            }

            String targetId = firstNonBlank(stringValue(row.get("id")), "target-%03d".formatted(index + 1));
            Map<String, String> tags = stringMap(mapValue(row.get("tags")));
            Map<String, Object> metadata = mapValue(row.get("metadata"));

            Map<String, Object> target = new LinkedHashMap<>();
            target.put("tenantId", tenantId);
            target.put("subscriptionId", subscriptionId);
            target.put("containerAppResourceId", containerAppId);
            putIfPresent(target, "managedApplicationId", metadata.get("managed_application_id"));
            putIfPresent(target, "managedResourceGroupId", metadata.get("managed_resource_group_id"));
            putIfPresent(target, "containerAppName", metadata.get("container_app_name"));
            target.put("targetGroup", firstNonBlank(tags.get("ring"), "prod"));
            target.put("region", firstNonBlank(tags.get("region"), "eastus"));
            target.put("environment", firstNonBlank(tags.get("environment"), "prod"));
            target.put("tier", firstNonBlank(tags.get("tier"), "standard"));
            target.put("displayName", targetId);
            target.put("tags", tags);
            target.put(
                "metadata",
                Map.of(
                    "source", "marketplace-forwarder-replay",
                    "inventoryTargetId", targetId
                )
            );

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", "%s-%03d-%d".formatted(eventIdPrefix, index + 1, Instant.now().getEpochSecond()));
            payload.put("eventType", eventType);
            payload.put("action", eventType);
            payload.put("mappoTarget", target);

            if (dryRun) {
                System.out.println(writeJson(payload));
                applied += 1;
                continue;
            }

            HttpSupport.HttpResult result = httpSupport.postJson(
                forwarderUrl,
                payload,
                Map.of("Content-Type", "application/json"),
                20
            );
            if (result.isSuccess()) {
                System.out.printf("%s: HTTP %d :: %s%n", targetId, result.statusCode(), result.body());
                applied += 1;
            } else {
                System.err.printf("%s: HTTP %d :: %s%n", targetId, result.statusCode(), result.body());
                failed += 1;
            }
        }

        System.out.printf(
            "marketplace-forwarder-replay: applied=%d failed=%d dry_run=%s%n",
            applied,
            failed,
            dryRun ? "true" : "false"
        );
        return failed > 0 ? 1 : 0;
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

    private String writeJson(Map<String, Object> payload) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload);
        } catch (Exception exception) {
            throw new ToolingException("marketplace-forwarder-replay: failed serializing payload: " + exception.getMessage(), 1);
        }
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

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        String normalized = stringValue(value);
        if (!normalized.isBlank()) {
            target.put(key, normalized);
        }
    }

    private void printUsage() {
        System.out.println("""
            usage: marketplace-forwarder-replay [options]
              --forwarder-url <url>
              --inventory-file <path>
              --event-type <value>
              --event-id-prefix <value>
              --dry-run
            """);
    }
}
