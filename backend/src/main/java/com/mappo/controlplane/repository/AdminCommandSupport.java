package com.mappo.controlplane.repository;

import com.mappo.controlplane.model.MarketplaceEventType;
import java.time.OffsetDateTime;
import java.util.UUID;

final class AdminCommandSupport {

    private AdminCommandSupport() {
    }

    static com.mappo.controlplane.jooq.enums.MappoMarketplaceEventType toMarketplaceEventEnum(
        MarketplaceEventType type
    ) {
        return switch (enumOrDefault(type, MarketplaceEventType.SUBSCRIPTION_PURCHASED)) {
            case SUBSCRIPTION_PURCHASED -> com.mappo.controlplane.jooq.enums.MappoMarketplaceEventType.subscription_purchased;
            case SUBSCRIPTION_SUSPENDED -> com.mappo.controlplane.jooq.enums.MappoMarketplaceEventType.subscription_suspended;
            case SUBSCRIPTION_DELETED -> com.mappo.controlplane.jooq.enums.MappoMarketplaceEventType.subscription_deleted;
            case UNKNOWN -> com.mappo.controlplane.jooq.enums.MappoMarketplaceEventType.subscription_purchased;
        };
    }

    static String firstNullableText(String candidate, String fallback) {
        String normalizedCandidate = normalize(candidate);
        return normalizedCandidate.isBlank() ? nullableText(fallback) : normalizedCandidate;
    }

    static String firstNonBlank(String candidate, String fallback) {
        String normalizedCandidate = normalize(candidate);
        return normalizedCandidate.isBlank() ? normalize(fallback) : normalizedCandidate;
    }

    static String defaultIfBlank(String candidate, String fallback) {
        String normalizedCandidate = normalize(candidate);
        return normalizedCandidate.isBlank() ? normalize(fallback) : normalizedCandidate;
    }

    static OffsetDateTime toTimestamp(OffsetDateTime value, OffsetDateTime fallback) {
        return value == null ? fallback : value;
    }

    static String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    static String nullableText(String value) {
        String normalized = normalize(value);
        return normalized.isBlank() ? null : normalized;
    }

    static UUID requiredUuid(UUID value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
        return value;
    }

    static <E extends Enum<E>> E enumOrDefault(E value, E fallback) {
        return value == null ? fallback : value;
    }
}
