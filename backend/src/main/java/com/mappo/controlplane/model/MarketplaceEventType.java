package com.mappo.controlplane.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum MarketplaceEventType {
    SUBSCRIPTION_PURCHASED("subscription_purchased"),
    SUBSCRIPTION_SUSPENDED("subscription_suspended"),
    SUBSCRIPTION_DELETED("subscription_deleted"),
    UNKNOWN("unknown");

    private final String literal;

    MarketplaceEventType(String literal) {
        this.literal = literal;
    }

    @JsonValue
    public String literal() {
        return literal;
    }

    @JsonCreator
    public static MarketplaceEventType fromValue(String value) {
        String normalized = normalize(value);
        if (normalized.isBlank()) {
            return UNKNOWN;
        }
        if (
            normalized.contains("subscription_suspended")
                || normalized.equals("suspended")
                || normalized.equals("suspend")
        ) {
            return SUBSCRIPTION_SUSPENDED;
        }
        if (
            normalized.contains("subscription_deleted")
                || normalized.contains("unsubscribe")
                || normalized.contains("cancel")
                || normalized.equals("delete")
                || normalized.equals("deleted")
        ) {
            return SUBSCRIPTION_DELETED;
        }
        if (normalized.contains("subscription_purchased") || normalized.contains("purchased")) {
            return SUBSCRIPTION_PURCHASED;
        }
        return UNKNOWN;
    }

    public boolean isSuspendLike() {
        return this == SUBSCRIPTION_SUSPENDED;
    }

    public boolean isDeleteLike() {
        return this == SUBSCRIPTION_DELETED;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
