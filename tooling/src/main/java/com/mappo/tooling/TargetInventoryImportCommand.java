package com.mappo.tooling;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class TargetInventoryImportCommand {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpSupport httpSupport = new HttpSupport(objectMapper);
    private final TargetInventoryPayloadFactory payloadFactory = new TargetInventoryPayloadFactory(objectMapper, "target-import-inventory");

    int run(List<String> rawArgs) {
        Arguments args = new Arguments(rawArgs);
        Path inventoryFile = FileSupport.repoRoot().resolve(".data/mappo-target-inventory.json");
        String apiBaseUrl = System.getenv().getOrDefault("MAPPO_API_BASE_URL", "http://localhost:8010");
        String projectIdOverride = "";
        String eventType = "subscription_purchased";
        String ingestToken = System.getenv().getOrDefault("MAPPO_MARKETPLACE_INGEST_TOKEN", "");
        String eventIdPrefix = "evt-target-import";
        String sourceLabel = "iac-target-inventory";
        boolean dryRun = false;

        while (args.hasNext()) {
            String arg = args.next();
            switch (arg) {
                case "--inventory-file" -> inventoryFile = resolvePath(args.nextRequired("--inventory-file"));
                case "--api-base-url" -> apiBaseUrl = args.nextRequired("--api-base-url");
                case "--project-id" -> projectIdOverride = args.nextRequired("--project-id");
                case "--event-type" -> eventType = args.nextRequired("--event-type");
                case "--ingest-token" -> ingestToken = args.nextRequired("--ingest-token");
                case "--event-id-prefix" -> eventIdPrefix = args.nextRequired("--event-id-prefix");
                case "--source-label" -> sourceLabel = args.nextRequired("--source-label");
                case "--dry-run" -> dryRun = true;
                case "-h", "--help" -> {
                    printUsage();
                    return 0;
                }
                default -> throw new ToolingException("target-import-inventory: unknown argument: " + arg, 2);
            }
        }

        if (!java.nio.file.Files.exists(inventoryFile)) {
            throw new ToolingException("target-import-inventory: inventory file not found: " + inventoryFile, 2);
        }

        List<LinkedHashMap<String, Object>> rows = payloadFactory.readInventory(inventoryFile);
        String endpoint = apiBaseUrl.replaceAll("/+$", "") + "/api/v1/admin/onboarding/events";
        String batchId = UUID.randomUUID().toString().replace("-", "").substring(0, 10);

        int applied = 0;
        int duplicate = 0;
        int rejected = 0;
        int requestFailed = 0;
        int validationFailed = 0;

        for (int index = 0; index < rows.size(); index++) {
            Map<String, Object> row = rows.get(index);
            List<String> missingFields = payloadFactory.missingInventoryFields(row);
            if (!missingFields.isEmpty()) {
                System.err.printf(
                    "target-import-inventory: skipping target row #%d due to missing fields: %s%n",
                    index + 1,
                    String.join(", ", missingFields)
                );
                validationFailed += 1;
                continue;
            }

            String targetId = payloadFactory.targetId(row);
            String payloadEventId = "%s-%s-%03d".formatted(eventIdPrefix, batchId, index + 1);
            Map<String, Object> payload = payloadFactory.buildOnboardingPayload(
                row,
                new TargetInventoryPayloadFactory.PayloadOptions(eventType, payloadEventId, sourceLabel, projectIdOverride)
            );

            if (dryRun) {
                System.out.println(payloadFactory.writeJson(payload));
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
                    "target-import-inventory: HTTP %d for target %s: %s%n",
                    result.statusCode(),
                    targetId,
                    result.body()
                );
                continue;
            }

            Map<String, Object> response = payloadFactory.readMap(result.body());
            String statusValue = stringValue(response.get("status")).toLowerCase();
            String message = firstNonBlank(stringValue(response.get("message")), "(no message)");
            switch (statusValue) {
                case "applied" -> applied += 1;
                case "duplicate" -> duplicate += 1;
                case "rejected" -> rejected += 1;
                default -> {
                    requestFailed += 1;
                    System.err.printf(
                        "target-import-inventory: unexpected response status for %s: %s%n",
                        targetId,
                        statusValue.isBlank() ? "unknown" : statusValue
                    );
                }
            }
            System.out.printf("%s: %s :: %s%n", targetId, statusValue.isBlank() ? "unknown" : statusValue, message);
        }

        System.out.printf(
            "target-import-inventory: rows=%d applied=%d duplicate=%d rejected=%d validation_failed=%d request_failed=%d dry_run=%s%n",
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

    private Path resolvePath(String value) {
        Path path = Path.of(value);
        return path.isAbsolute() ? path : FileSupport.repoRoot().resolve(path).normalize();
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
            usage: target-import-inventory [options]
              --inventory-file <path>
              --api-base-url <url>
              --project-id <id>
              --event-type <name>
              --ingest-token <token>
              --event-id-prefix <prefix>
              --source-label <label>
              --dry-run
            """);
    }
}
