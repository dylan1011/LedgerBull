package com.ledgerbull.execution.client;

/**
 * Outcome of a net-position lookup. Distinguishes a known zero from "could not decide"
 * so callers can fail closed without treating fetch failures as flat.
 */
public sealed interface NetPositionResult {

    record Available(long netQuantity) implements NetPositionResult {}

    record Unavailable(String detail) implements NetPositionResult {}

    static NetPositionResult available(long netQuantity) {
        return new Available(netQuantity);
    }

    static NetPositionResult unavailable(String detail) {
        return new Unavailable(detail == null ? "unavailable" : detail);
    }

    default boolean isAvailable() {
        return this instanceof Available;
    }
}
