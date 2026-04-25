package com.mappo.pulumi;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.pulumi.Config;
import com.pulumi.core.Output;
import com.pulumi.core.TypeShape;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

final class PulumiSupport {
    private static final Gson GSON = new Gson();
    private static final Pattern NON_ALPHANUMERIC_DASH = Pattern.compile("[^a-z0-9-]");
    private static final Pattern DASH_COLLAPSE = Pattern.compile("-+");
    private static final Pattern IPV4_SEGMENT_PATTERN = Pattern.compile("^\\d+$");

    private PulumiSupport() {
    }

    static Optional<String> optionalConfigWithEnvFallback(Config cfg, String configKey, String envKey) {
        return cfg.get(configKey)
            .flatMap(PulumiSupport::normalizeNullable)
            .or(() -> normalizeNullable(System.getenv(envKey)));
    }

    static Optional<Output<String>> optionalSecretConfigWithEnvFallback(Config cfg, String configKey, String envKey) {
        if (cfg.get(configKey).isPresent()) {
            return Optional.of(cfg.requireSecret(configKey));
        }
        return normalizeNullable(System.getenv(envKey)).map(Output::ofSecret);
    }

    static boolean booleanConfigWithEnvFallback(Config cfg, String configKey, String envKey, boolean defaultValue) {
        Optional<String> raw = optionalConfigWithEnvFallback(cfg, configKey, envKey);
        if (raw.isEmpty()) {
            return defaultValue;
        }
        String value = raw.get().toLowerCase(Locale.ROOT);
        if (List.of("true", "1", "yes", "on").contains(value)) {
            return true;
        }
        if (List.of("false", "0", "no", "off").contains(value)) {
            return false;
        }
        throw new IllegalArgumentException(
            "Invalid boolean value for " + configKey + "/" + envKey + ": '" + raw.get() + "'."
        );
    }

    static int numberConfigWithEnvFallback(Config cfg, String configKey, String envKey, int defaultValue) {
        Optional<String> raw = optionalConfigWithEnvFallback(cfg, configKey, envKey);
        if (raw.isEmpty()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(raw.get());
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException(
                "Invalid integer value for " + configKey + "/" + envKey + ": '" + raw.get() + "'.",
                error
            );
        }
    }

    static List<FirewallIpRange> parseFirewallIpRanges(Config cfg, String configKey, String envKey) {
        try {
            Optional<List<String>> parsed = cfg.getObject(configKey, TypeShape.list(String.class));
            if (parsed.isPresent()) {
                return parsed.get().stream().map(PulumiSupport::parseFirewallRange).toList();
            }
        } catch (Exception ignored) {
            // Fallback below.
        }

        Optional<String> raw = optionalConfigWithEnvFallback(cfg, configKey, envKey);
        if (raw.isEmpty()) {
            return List.of();
        }

        String trimmed = raw.get().trim();
        if (trimmed.startsWith("[")) {
            Type listType = new TypeToken<List<String>>() {}.getType();
            List<String> parsed = GSON.fromJson(trimmed, listType);
            return parsed == null ? List.of() : parsed.stream().filter(Objects::nonNull).map(PulumiSupport::parseFirewallRange).toList();
        }

        List<FirewallIpRange> ranges = new ArrayList<>();
        for (String token : trimmed.split(",")) {
            normalizeNullable(token).map(PulumiSupport::parseFirewallRange).ifPresent(ranges::add);
        }
        return ranges;
    }

    static String inferPostgresSkuTier(String skuName) {
        String normalized = skuName == null ? "" : skuName.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("standard_d")) {
            return "GeneralPurpose";
        }
        if (normalized.startsWith("standard_e")) {
            return "MemoryOptimized";
        }
        return "Burstable";
    }

    static String normalizePostgresLogin(String value) {
        String normalized = normalizeNullable(value)
            .map(v -> v.replaceAll("[^a-zA-Z0-9]", ""))
            .orElse("mappoadmin");
        if (normalized.length() > 63) {
            normalized = normalized.substring(0, 63);
        }
        return normalized.isBlank() ? "mappoadmin" : normalized.toLowerCase(Locale.ROOT);
    }

    static String resolveDemoSubscriptionId(String configuredValue) {
        return normalizeNullable(configuredValue)
            .or(() -> normalizeNullable(System.getenv("AZURE_SUBSCRIPTION_ID")))
            .or(() -> normalizeNullable(System.getenv("ARM_SUBSCRIPTION_ID")))
            .or(PulumiSupport::detectActiveAzSubscriptionId)
            .orElse("00000000-0000-0000-0000-000000000000");
    }

    static String normalizeName(String value, String fallback, int maxLen) {
        String source = Optional.ofNullable(value).orElse(fallback).toLowerCase(Locale.ROOT);
        String normalized = NON_ALPHANUMERIC_DASH.matcher(source).replaceAll("-");
        normalized = DASH_COLLAPSE.matcher(normalized).replaceAll("-");
        normalized = trimDashes(normalized);

        String candidate = normalized.isEmpty() ? fallback : normalized;
        if (candidate.length() > maxLen) {
            candidate = candidate.substring(0, maxLen);
        }
        candidate = trimTrailingDashes(candidate);
        return candidate.isEmpty() ? fallback.substring(0, Math.min(maxLen, fallback.length())) : candidate;
    }

    static String subscriptionKey(String subscriptionId) {
        String normalized = subscriptionId.replaceAll("[^a-zA-Z0-9]", "").toLowerCase(Locale.ROOT);
        return normalized.substring(0, Math.min(8, normalized.length()));
    }

    static String stackKey(String stackName) {
        String normalized = normalizeName(stackName, "demo", 40);
        return normalized.isBlank() ? "demo" : normalized;
    }

    static String stackScopedResourceSuffix(String stackName, String subscriptionId) {
        String subscriptionKey = subscriptionKey(subscriptionId);
        String stackKey = stackKey(stackName);
        if ("demo".equals(stackKey)) {
            return subscriptionKey;
        }
        return normalizeName(stackKey + "-" + subscriptionKey, subscriptionKey, 52);
    }

    static String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    static Optional<String> normalizeNullable(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(normalized);
    }

    static Map<String, String> linkedMapOfString(String... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("linkedMapOfString expects an even number of arguments.");
        }
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], keyValues[i + 1]);
        }
        return map;
    }

    private static Optional<String> detectActiveAzSubscriptionId() {
        try {
            Process process = new ProcessBuilder("az", "account", "show", "--query", "id", "-o", "tsv")
                .redirectErrorStream(true)
                .start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line = reader.readLine();
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    return normalizeNullable(line);
                }
            }
        } catch (Exception ignored) {
            // best effort only.
        }
        return Optional.empty();
    }

    private static FirewallIpRange parseFirewallRange(String rawValue) {
        String value = normalizeNullable(rawValue)
            .orElseThrow(() -> new IllegalArgumentException("Firewall IP range entries must be non-empty."));
        String[] parts = value.split("-");
        if (parts.length == 1) {
            validateIpv4(parts[0], value);
            return new FirewallIpRange(parts[0], parts[0]);
        }
        if (parts.length == 2) {
            validateIpv4(parts[0], value);
            validateIpv4(parts[1], value);
            return new FirewallIpRange(parts[0], parts[1]);
        }
        throw new IllegalArgumentException(
            "Invalid firewall IP range '" + value + "'. Expected IPv4 or IPv4-IPv4 format."
        );
    }

    private static void validateIpv4(String value, String source) {
        String[] segments = value.split("\\.");
        if (segments.length != 4) {
            throw new IllegalArgumentException("Invalid IPv4 address '" + value + "' in '" + source + "'.");
        }
        for (String segment : segments) {
            if (!IPV4_SEGMENT_PATTERN.matcher(segment).matches()) {
                throw new IllegalArgumentException("Invalid IPv4 address '" + value + "' in '" + source + "'.");
            }
            int parsed = Integer.parseInt(segment);
            if (parsed < 0 || parsed > 255) {
                throw new IllegalArgumentException("Invalid IPv4 address '" + value + "' in '" + source + "'.");
            }
        }
    }

    private static String trimDashes(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && value.charAt(start) == '-') {
            start++;
        }
        while (end > start && value.charAt(end - 1) == '-') {
            end--;
        }
        return value.substring(start, end);
    }

    private static String trimTrailingDashes(String value) {
        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '-') {
            end--;
        }
        return value.substring(0, end);
    }

    record FirewallIpRange(String startIpAddress, String endIpAddress) {
    }
}
