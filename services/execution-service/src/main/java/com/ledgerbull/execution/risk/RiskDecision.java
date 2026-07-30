package com.ledgerbull.execution.risk;

/**
 * Immutable outcome of a risk evaluation. Empty {@code reason} when allowed; a clear human
 * message when rejected (e.g. "order quantity 5000 exceeds max 1000").
 */
public record RiskDecision(boolean allowed, String reason) {

    public static RiskDecision allow() {
        return new RiskDecision(true, "");
    }

    public static RiskDecision reject(String reason) {
        return new RiskDecision(false, reason == null ? "" : reason);
    }
}
