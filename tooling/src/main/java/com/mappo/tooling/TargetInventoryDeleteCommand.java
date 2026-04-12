package com.mappo.tooling;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class TargetInventoryDeleteCommand {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpSupport httpSupport = new HttpSupport(objectMapper);
    private final TargetInventoryPayloadFactory payloadFactory = new TargetInventoryPayloadFactory(objectMapper, "target-delete-inventory");

    int run(List<String> rawArgs) {
        Arguments args = new Arguments(rawArgs);
        Path inventoryFile = FileSupport.repoRoot().resolve(".data/mappo-target-inventory.json");
        String apiBaseUrl = System.getenv().getOrDefault("MAPPO_API_BASE_URL", "http://localhost:8010");
        boolean dryRun = false;

        while (args.hasNext()) {
            String arg = args.next();
            switch (arg) {
                case "--inventory-file" -> inventoryFile = resolvePath(args.nextRequired("--inventory-file"));
                case "--api-base-url" -> apiBaseUrl = args.nextRequired("--api-base-url");
                case "--dry-run" -> dryRun = true;
                case "-h", "--help" -> {
                    printUsage();
                    return 0;
                }
                default -> throw new ToolingException("target-delete-inventory: unknown argument: " + arg, 2);
            }
        }

        if (!java.nio.file.Files.exists(inventoryFile)) {
            throw new ToolingException("target-delete-inventory: inventory file not found: " + inventoryFile, 2);
        }

        List<LinkedHashMap<String, Object>> rows = payloadFactory.readInventory(inventoryFile);
        String endpointBase = apiBaseUrl.replaceAll("/+$", "") + "/api/v1/admin/onboarding/registrations";

        int deleted = 0;
        int requestFailed = 0;
        int validationFailed = 0;

        for (int index = 0; index < rows.size(); index++) {
            Map<String, Object> row = rows.get(index);
            String targetId = payloadFactory.targetId(row);
            if (targetId.isBlank()) {
                System.err.printf("target-delete-inventory: skipping target row #%d due to missing field: id%n", index + 1);
                validationFailed += 1;
                continue;
            }
            String endpoint = endpointBase + "/" + URLEncoder.encode(targetId, StandardCharsets.UTF_8);

            if (dryRun) {
                System.out.printf("DELETE %s%n", endpoint);
                continue;
            }

            Map<String, String> headers = Map.of("Accept", "application/json");
            HttpSupport.HttpResult result = httpSupport.request("DELETE", endpoint, headers, null, 20);
            if (!result.isSuccess()) {
                requestFailed += 1;
                System.err.printf(
                    "target-delete-inventory: HTTP %d for target %s: %s%n",
                    result.statusCode(),
                    targetId,
                    result.body()
                );
                continue;
            }
            deleted += 1;
            System.out.printf("%s: deleted%n", targetId);
        }

        System.out.printf(
            "target-delete-inventory: rows=%d deleted=%d validation_failed=%d request_failed=%d dry_run=%s%n",
            rows.size(),
            deleted,
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

    private void printUsage() {
        System.out.println("""
            usage: target-delete-inventory [options]
              --inventory-file <path>
              --api-base-url <url>
              --dry-run
            """);
    }
}
