package com.mappo.tooling;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AzureScriptSupportCommand {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final Pattern GUID_PATTERN = Pattern.compile(
        "^[0-9a-fA-F]{8}-"
            + "[0-9a-fA-F]{4}-"
            + "[0-9a-fA-F]{4}-"
            + "[0-9a-fA-F]{4}-"
            + "[0-9a-fA-F]{12}$"
    );
    private static final Pattern CONTAINER_APP_RESOURCE_ID_PATTERN = Pattern.compile(
        "^/subscriptions/[^/]+/resourceGroups/[^/]+/providers/Microsoft\\.App/containerApps/[^/]+$",
        Pattern.CASE_INSENSITIVE
    );
    private static final Pattern RESOURCE_GROUP_FROM_RESOURCE_ID_PATTERN = Pattern.compile(
        "^/subscriptions/[^/]+/resourceGroups/([^/]+)/providers/",
        Pattern.CASE_INSENSITIVE
    );
    private static final String RANDOM_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    int run(List<String> args) {
        if (args.isEmpty()) {
            throw new ToolingException("missing azure-script-support subcommand", 2);
        }

        String subcommand = args.getFirst();
        Arguments commandArgs = new Arguments(args.subList(1, args.size()));
        return switch (subcommand) {
            case "sp-credentials" -> runSpCredentials(commandArgs);
            case "partner-token" -> runPartnerToken(commandArgs);
            case "tenant-map-json" -> runTenantMapJson(commandArgs);
            case "upsert-export-line" -> runUpsertExportLine(commandArgs);
            case "export-db-env" -> runExportDbEnv(commandArgs);
            case "subscription-tenant-id" -> runSubscriptionTenantId(commandArgs);
            case "inventory-rg-scopes" -> runInventoryResourceGroupScopes(commandArgs);
            case "csv-to-json-array" -> runCsvToJsonArray(commandArgs);
            case "account-stats" -> runAccountStats(commandArgs);
            case "validate-tenant-map" -> runValidateTenantMap(commandArgs);
            case "inventory-stats" -> runInventoryStats(commandArgs);
            case "tenant-resolution-stats" -> runTenantResolutionStats(commandArgs);
            case "aca-env-row" -> runAcaEnvRow(commandArgs);
            case "random-suffix" -> runRandomSuffix(commandArgs);
            case "job-execution-name" -> runJobExecutionName(commandArgs);
            case "easyauth-redirect-uris" -> runEasyAuthRedirectUris(commandArgs);
            default -> throw new ToolingException("unknown azure-script-support subcommand: " + subcommand, 2);
        };
    }

    private int runSpCredentials(Arguments args) {
        Map<String, String> options = parseOptions(args);
        JsonNode root = parseJsonNode(requiredOption(options, "--json"));
        System.out.println(String.join("\t",
            text(root, "clientId"),
            text(root, "clientSecret"),
            text(root, "tenantId")
        ));
        return 0;
    }

    private int runPartnerToken(Arguments args) {
        Map<String, String> options = parseOptions(args);
        JsonNode root = parseJsonNode(requiredOption(options, "--json"));
        System.out.println(String.join("\t",
            text(root, "token"),
            text(root, "expiresOn")
        ));
        return 0;
    }

    private int runTenantMapJson(Arguments args) {
        Map<String, String> options = parseOptions(args);
        List<String> requestedSubscriptions = splitCsv(requiredOption(options, "--subscriptions"));
        JsonNode rows = parseJsonNode(requiredOption(options, "--account-list-json"));
        if (requestedSubscriptions.isEmpty()) {
            throw new ToolingException("azure-tenant-map: no valid subscription IDs provided", 1);
        }

        Map<String, String> tenantBySubscription = new HashMap<>();
        if (rows.isArray()) {
            for (JsonNode row : rows) {
                String subscriptionId = text(row, "id").trim();
                String tenantId = text(row, "tenantId").trim();
                if (!subscriptionId.isEmpty() && !tenantId.isEmpty()) {
                    tenantBySubscription.put(subscriptionId, tenantId);
                }
            }
        }

        List<String> missing = requestedSubscriptions.stream()
            .filter(subscriptionId -> !tenantBySubscription.containsKey(subscriptionId))
            .toList();
        if (!missing.isEmpty()) {
            throw new ToolingException(
                "azure-tenant-map: subscription(s) not present in az context: " + String.join(", ", missing),
                1
            );
        }

        Map<String, String> ordered = new LinkedHashMap<>();
        for (String subscriptionId : requestedSubscriptions) {
            ordered.put(subscriptionId, tenantBySubscription.get(subscriptionId));
        }
        printJson(ordered);
        return 0;
    }

    private int runUpsertExportLine(Arguments args) {
        Map<String, String> options = parseOptions(args);
        Path envFile = Path.of(requiredOption(options, "--env-file")).toAbsolutePath().normalize();
        String key = requiredOption(options, "--key");
        String value = requiredOption(options, "--value");
        String line = "export " + key + "=" + shQuote(value);

        List<String> rawLines = Files.exists(envFile) ? FileSupport.readLines(envFile) : List.of();
        List<String> updated = new ArrayList<>();
        boolean replaced = false;
        for (String rawLine : rawLines) {
            String stripped = rawLine.trim();
            if (stripped.startsWith(key + "=") || stripped.startsWith("export " + key + "=")) {
                if (!replaced) {
                    updated.add(line);
                    replaced = true;
                }
                continue;
            }
            updated.add(rawLine);
        }
        if (!replaced) {
            if (!updated.isEmpty() && !updated.getLast().trim().isEmpty()) {
                updated.add("");
            }
            updated.add(line);
        }
        FileSupport.writeText(envFile, String.join("\n", updated) + "\n");
        return 0;
    }

    private int runExportDbEnv(Arguments args) {
        Map<String, String> options = parseOptions(args);
        JsonNode outputs = parseJsonNode(requiredOption(options, "--outputs-json"));
        Path envFile = Path.of(requiredOption(options, "--env-file")).toAbsolutePath().normalize();

        if (!outputs.path("controlPlanePostgresEnabled").asBoolean(false)) {
            throw new ToolingException(
                "iac-export-db-env: managed Postgres is not enabled in this stack. "
                    + "Set mappo:controlPlanePostgresEnabled=true and run pulumi up first.",
                2
            );
        }

        Map<String, String> requiredKeys = Map.of(
            "controlPlanePostgresHost", "host",
            "controlPlanePostgresPort", "port",
            "controlPlanePostgresDatabase", "database",
            "controlPlanePostgresConnectionUsername", "username",
            "controlPlanePostgresPassword", "password",
            "controlPlaneDatabaseUrl", "database_url"
        );

        Map<String, String> resolved = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : requiredKeys.entrySet()) {
            String value = outputs.path(entry.getKey()).asText("").trim();
            if (value.isEmpty()) {
                throw new ToolingException(
                    "iac-export-db-env: missing Pulumi output '" + entry.getKey() + "' (" + entry.getValue() + ").",
                    3
                );
            }
            resolved.put(entry.getValue(), value);
        }

        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        String content = String.join("\n",
            "# Generated by scripts/iac_export_db_env.sh on " + timestamp,
            "export MAPPO_DATABASE_URL=" + shQuote(resolved.get("database_url")),
            "export DATABASE_URL=" + shQuote(resolved.get("database_url")),
            "export MAPPO_DB_HOST=" + shQuote(resolved.get("host")),
            "export MAPPO_DB_PORT=" + shQuote(resolved.get("port")),
            "export MAPPO_DB_NAME=" + shQuote(resolved.get("database")),
            "export MAPPO_DB_USER=" + shQuote(resolved.get("username")),
            "export MAPPO_DB_PASSWORD=" + shQuote(resolved.get("password")),
            "export MAPPO_DB_SSLMODE='require'",
            ""
        );
        FileSupport.writeText(envFile, content);
        set0600IfPossible(envFile);

        System.out.println("iac-export-db-env: wrote " + envFile);
        System.out.println(
            "iac-export-db-env: exported keys: MAPPO_DATABASE_URL, MAPPO_DB_HOST, MAPPO_DB_PORT, "
                + "MAPPO_DB_NAME, MAPPO_DB_USER, MAPPO_DB_PASSWORD"
        );
        return 0;
    }

    private int runSubscriptionTenantId(Arguments args) {
        Map<String, String> options = parseOptions(args);
        JsonNode rows = parseJsonNode(requiredOption(options, "--account-list-json"));
        String subscriptionId = requiredOption(options, "--subscription-id");
        if (rows.isArray()) {
            for (JsonNode row : rows) {
                if (subscriptionId.equals(text(row, "id").trim())) {
                    System.out.println(text(row, "tenantId").trim());
                    return 0;
                }
            }
        }
        System.out.println();
        return 0;
    }

    private int runInventoryResourceGroupScopes(Arguments args) {
        Map<String, String> options = parseOptions(args);
        Path inventoryFile = Path.of(requiredOption(options, "--inventory-file")).toAbsolutePath().normalize();
        String subscriptionId = requiredOption(options, "--subscription-id");
        JsonNode payload = parseJsonNode(FileSupport.readText(inventoryFile));
        Set<String> scopes = new LinkedHashSet<>();

        if (payload.isArray()) {
            for (JsonNode row : payload) {
                if (!subscriptionId.equals(text(row, "subscription_id").trim())) {
                    continue;
                }
                JsonNode metadata = row.path("metadata");
                if (metadata.isObject()) {
                    String managedResourceGroupId = text(metadata, "managed_resource_group_id").trim();
                    String managedResourceGroupName = text(metadata, "managed_resource_group_name").trim();
                    if (!managedResourceGroupId.isEmpty()) {
                        scopes.add(managedResourceGroupId);
                        continue;
                    }
                    if (!managedResourceGroupName.isEmpty()) {
                        scopes.add("/subscriptions/" + subscriptionId + "/resourceGroups/" + managedResourceGroupName);
                        continue;
                    }
                }

                Matcher matcher = RESOURCE_GROUP_FROM_RESOURCE_ID_PATTERN.matcher(text(row, "managed_app_id").trim());
                if (matcher.find()) {
                    scopes.add("/subscriptions/" + subscriptionId + "/resourceGroups/" + matcher.group(1));
                }
            }
        }

        for (String scope : scopes) {
            System.out.println(scope);
        }
        return 0;
    }

    private int runCsvToJsonArray(Arguments args) {
        Map<String, String> options = parseOptions(args);
        printJson(splitCsv(requiredOption(options, "--csv")));
        return 0;
    }

    private int runAccountStats(Arguments args) {
        Map<String, String> options = parseOptions(args);
        JsonNode rows = parseJsonNode(requiredOption(options, "--account-list-json"));
        int subscriptionCount = rows.isArray() ? rows.size() : 0;
        Set<String> tenantIds = new HashSet<>();
        if (rows.isArray()) {
            for (JsonNode row : rows) {
                String tenantId = text(row, "tenantId").trim();
                if (!tenantId.isEmpty()) {
                    tenantIds.add(tenantId);
                }
            }
        }
        System.out.println(tenantIds.size() + "|" + subscriptionCount);
        return 0;
    }

    private int runValidateTenantMap(Arguments args) {
        Map<String, String> options = parseOptions(args);
        String raw = options.getOrDefault("--raw", "").trim();
        if (raw.isEmpty()) {
            System.out.println();
            return 0;
        }
        try {
            parseTenantMap(raw);
            System.out.println();
            return 0;
        } catch (ToolingException exception) {
            System.out.println(exception.getMessage());
            return 0;
        }
    }

    private int runInventoryStats(Arguments args) {
        Map<String, String> options = parseOptions(args);
        Path inventoryPath = Path.of(requiredOption(options, "--inventory-path")).toAbsolutePath().normalize();
        JsonNode payload = parseJsonNode(FileSupport.readText(inventoryPath));
        if (!payload.isArray()) {
            throw new ToolingException("inventory payload must be a JSON array", 1);
        }

        int targets = payload.size();
        Set<String> tenantIds = new HashSet<>();
        Set<String> subscriptionIds = new HashSet<>();
        int managedMetaCount = 0;
        int invalidTargetResourceIds = 0;

        for (JsonNode row : payload) {
            String tenantId = text(row, "tenant_id").trim();
            if (!tenantId.isEmpty()) {
                tenantIds.add(tenantId);
            }
            String subscriptionId = text(row, "subscription_id").trim();
            if (!subscriptionId.isEmpty()) {
                subscriptionIds.add(subscriptionId);
            }
            JsonNode metadata = row.path("metadata");
            if (metadata.isObject()) {
                String managedApplicationId = text(metadata, "managed_application_id").trim();
                String managedResourceGroupId = text(metadata, "managed_resource_group_id").trim();
                if (!managedApplicationId.isEmpty() && !managedResourceGroupId.isEmpty()) {
                    managedMetaCount += 1;
                }
            }
            String targetResourceId = text(row, "managed_app_id").trim();
            if (!CONTAINER_APP_RESOURCE_ID_PATTERN.matcher(targetResourceId).matches()) {
                invalidTargetResourceIds += 1;
            }
        }

        System.out.println(targets + "|" + tenantIds.size() + "|" + subscriptionIds.size() + "|"
            + managedMetaCount + "|" + invalidTargetResourceIds);
        return 0;
    }

    private int runTenantResolutionStats(Arguments args) {
        Map<String, String> options = parseOptions(args);
        Path inventoryPath = Path.of(requiredOption(options, "--inventory-path")).toAbsolutePath().normalize();
        String rawTenantMap = options.getOrDefault("--raw-map", "");
        JsonNode payload = parseJsonNode(FileSupport.readText(inventoryPath));
        if (!payload.isArray()) {
            throw new ToolingException("inventory payload must be a JSON array", 1);
        }

        Map<String, Set<String>> subscriptions = new LinkedHashMap<>();
        for (JsonNode row : payload) {
            String subscriptionId = text(row, "subscription_id").trim();
            if (subscriptionId.isEmpty()) {
                continue;
            }
            subscriptions.computeIfAbsent(subscriptionId, ignored -> new LinkedHashSet<>());
            String tenantId = text(row, "tenant_id").trim();
            if (!tenantId.isEmpty()) {
                subscriptions.get(subscriptionId).add(tenantId);
            }
        }

        List<String> unresolvedSubscriptions = subscriptions.entrySet().stream()
            .filter(entry -> entry.getValue().stream().noneMatch(value -> GUID_PATTERN.matcher(value).matches()))
            .map(Map.Entry::getKey)
            .toList();

        Map<String, String> tenantMap = Map.of();
        String parseError = "";
        if (!rawTenantMap.trim().isEmpty()) {
            try {
                tenantMap = parseTenantMap(rawTenantMap);
            } catch (ToolingException exception) {
                parseError = exception.getMessage();
            }
        }
        Map<String, String> resolvedTenantMap = tenantMap;

        List<String> missing = unresolvedSubscriptions.stream()
            .filter(subscriptionId -> !resolvedTenantMap.containsKey(subscriptionId))
            .toList();

        System.out.println(
            unresolvedSubscriptions.size() + "|" + missing.size() + "|"
                + String.join(",", unresolvedSubscriptions.stream().limit(5).toList()) + "|"
                + String.join(",", missing.stream().limit(5).toList()) + "|"
                + parseError
        );
        return 0;
    }

    private int runAcaEnvRow(Arguments args) {
        Map<String, String> options = parseOptions(args);
        String raw = requiredOption(options, "--json").trim();
        JsonNode payload = raw.isEmpty() || "null".equals(raw) ? null : parseJsonNode(raw);
        if (payload == null || payload.isNull()) {
            System.out.println();
            return 0;
        }
        System.out.println(String.join("\t",
            text(payload, "name"),
            text(payload, "resourceGroup"),
            text(payload, "location"),
            text(payload, "id")
        ));
        return 0;
    }

    private int runRandomSuffix(Arguments args) {
        Map<String, String> options = parseOptions(args);
        int length = Integer.parseInt(options.getOrDefault("--length", "6"));
        if (length < 1) {
            throw new ToolingException("random suffix length must be >= 1", 2);
        }
        StringBuilder value = new StringBuilder(length);
        for (int index = 0; index < length; index += 1) {
            value.append(RANDOM_ALPHABET.charAt(RANDOM.nextInt(RANDOM_ALPHABET.length())));
        }
        System.out.println(value);
        return 0;
    }

    private int runJobExecutionName(Arguments args) {
        Map<String, String> options = parseOptions(args);
        JsonNode payload = parseJsonNode(options.getOrDefault("--json", "{}"));
        String name = text(payload, "name").trim();
        if (!name.isEmpty()) {
            System.out.println(name);
            return 0;
        }
        String rawId = text(payload, "id").trim();
        if (!rawId.isEmpty()) {
            String normalized = rawId.endsWith("/") ? rawId.substring(0, rawId.length() - 1) : rawId;
            int separatorIndex = normalized.lastIndexOf('/');
            if (separatorIndex >= 0 && separatorIndex + 1 < normalized.length()) {
                System.out.println(normalized.substring(separatorIndex + 1));
                return 0;
            }
        }
        System.out.println();
        return 0;
    }

    private int runEasyAuthRedirectUris(Arguments args) {
        Map<String, String> options = parseOptions(args);
        JsonNode existing = parseJsonNode(options.getOrDefault("--existing-json", "[]"));
        String callbackUrl = options.getOrDefault("--callback-url", "");
        String extraRedirectUris = options.getOrDefault("--extra-redirect-uris", "");
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        if (existing.isArray()) {
            for (JsonNode value : existing) {
                String uri = value.asText("").trim();
                if (!uri.isEmpty()) {
                    ordered.add(uri);
                }
            }
        }
        if (!callbackUrl.trim().isEmpty()) {
            ordered.add(callbackUrl.trim());
        }
        for (String uri : splitCsv(extraRedirectUris)) {
            ordered.add(uri);
        }
        for (String uri : ordered) {
            System.out.println(uri);
        }
        return 0;
    }

    private static Map<String, String> parseOptions(Arguments args) {
        Map<String, String> options = new LinkedHashMap<>();
        while (args.hasNext()) {
            String optionName = args.next();
            if (Arguments.isHelpFlag(optionName)) {
                throw new ToolingException("help requested", 2);
            }
            if (!optionName.startsWith("--")) {
                throw new ToolingException("unknown argument: " + optionName, 2);
            }
            options.put(optionName, args.nextRequired(optionName));
        }
        return options;
    }

    private static String requiredOption(Map<String, String> options, String optionName) {
        String value = options.get(optionName);
        if (value == null) {
            throw new ToolingException("missing required option: " + optionName, 2);
        }
        return value;
    }

    private static JsonNode parseJsonNode(String raw) {
        try {
            return OBJECT_MAPPER.readTree(raw);
        } catch (JsonProcessingException exception) {
            throw new ToolingException("invalid JSON payload: " + exception.getOriginalMessage(), 1);
        }
    }

    private static String text(JsonNode node, String fieldName) {
        return node == null ? "" : node.path(fieldName).asText("");
    }

    private static Map<String, String> parseTenantMap(String raw) {
        String trimmed = raw == null ? "" : raw.trim();
        if (trimmed.isEmpty()) {
            return Map.of();
        }
        Map<String, String> tenantMap = new LinkedHashMap<>();
        if (trimmed.startsWith("{")) {
            JsonNode parsed = parseJsonNode(trimmed);
            if (!parsed.isObject()) {
                throw new ToolingException("JSON map must be an object", 1);
            }
            parsed.fields().forEachRemaining(entry -> {
                String subscriptionId = entry.getKey().trim();
                String tenantId = entry.getValue().asText("").trim();
                if (!subscriptionId.isEmpty() && !tenantId.isEmpty()) {
                    tenantMap.put(subscriptionId, tenantId);
                }
            });
            return tenantMap;
        }

        for (String chunk : trimmed.replace(';', ',').split(",")) {
            String pair = chunk.trim();
            if (pair.isEmpty()) {
                continue;
            }
            int separatorIndex = pair.indexOf('=');
            if (separatorIndex < 0) {
                separatorIndex = pair.indexOf(':');
            }
            if (separatorIndex < 0) {
                throw new ToolingException("entries must use subscription=tenant format", 1);
            }
            String subscriptionId = pair.substring(0, separatorIndex).trim();
            String tenantId = pair.substring(separatorIndex + 1).trim();
            if (!subscriptionId.isEmpty() && !tenantId.isEmpty()) {
                tenantMap.put(subscriptionId, tenantId);
            }
        }
        return tenantMap;
    }

    private static List<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.replace(';', ',').split(","))
            .map(String::trim)
            .filter(value -> !value.isEmpty())
            .toList();
    }

    private static void printJson(Object value) {
        try {
            System.out.println(OBJECT_MAPPER.writeValueAsString(value));
        } catch (JsonProcessingException exception) {
            throw new ToolingException("failed to render JSON: " + exception.getOriginalMessage(), 1);
        }
    }

    private static String shQuote(String value) {
        return "'" + value.replace("'", "'\"'\"'") + "'";
    }

    private static void set0600IfPossible(Path path) {
        try {
            Files.setPosixFilePermissions(path, Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE
            ));
        } catch (UnsupportedOperationException | IOException ignored) {
            // Best effort only.
        }
    }
}
