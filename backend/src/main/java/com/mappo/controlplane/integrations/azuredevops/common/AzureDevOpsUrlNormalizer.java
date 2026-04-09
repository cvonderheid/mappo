package com.mappo.controlplane.integrations.azuredevops.common;

import java.net.URI;
import java.util.Locale;

public final class AzureDevOpsUrlNormalizer {

    private AzureDevOpsUrlNormalizer() {
    }

    public static String normalizeOrganizationUrl(String value, String defaultBaseUrl) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.startsWith("https://") || normalized.startsWith("http://")) {
            return normalizeAbsoluteUrl(normalized);
        }
        if (normalized.startsWith("dev.azure.com/") || normalized.startsWith("www.dev.azure.com/")) {
            return normalizeAbsoluteUrl("https://" + normalized);
        }
        if (normalized.contains(".visualstudio.com")) {
            return normalizeAbsoluteUrl("https://" + normalized);
        }
        String orgSegment = firstPathSegment(normalized);
        if (orgSegment.isBlank()) {
            return "";
        }
        String resolvedBase = normalize(defaultBaseUrl).isBlank()
            ? "https://dev.azure.com"
            : trimTrailingSlash(defaultBaseUrl);
        return trimTrailingSlash(resolvedBase) + "/" + orgSegment;
    }

    private static String normalizeAbsoluteUrl(String value) {
        try {
            URI uri = URI.create(value);
            String scheme = normalize(uri.getScheme()).isBlank() ? "https" : normalize(uri.getScheme()).toLowerCase(Locale.ROOT);
            String host = normalize(uri.getHost()).toLowerCase(Locale.ROOT);
            if (host.isBlank()) {
                return trimTrailingSlash(value);
            }
            String authority = host;
            if (uri.getPort() > 0) {
                authority = authority + ":" + uri.getPort();
            }
            if ("dev.azure.com".equals(host) || "www.dev.azure.com".equals(host)) {
                String organization = firstPathSegment(uri.getPath());
                if (organization.isBlank()) {
                    return scheme + "://dev.azure.com";
                }
                return scheme + "://dev.azure.com/" + organization;
            }
            if (host.endsWith(".visualstudio.com")) {
                return scheme + "://" + authority;
            }
            return trimTrailingSlash(scheme + "://" + authority + normalize(uri.getPath()));
        } catch (RuntimeException ignored) {
            return trimTrailingSlash(value);
        }
    }

    private static String firstPathSegment(String value) {
        String normalized = normalize(value);
        String withoutLeadingSlash = normalized.startsWith("/") ? normalized.substring(1) : normalized;
        int slashIndex = withoutLeadingSlash.indexOf('/');
        return slashIndex >= 0 ? withoutLeadingSlash.substring(0, slashIndex).trim() : withoutLeadingSlash.trim();
    }

    private static String trimTrailingSlash(String value) {
        String normalized = normalize(value);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}
