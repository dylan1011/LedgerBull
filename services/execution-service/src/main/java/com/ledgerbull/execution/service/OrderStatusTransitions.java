package com.ledgerbull.execution.service;

import java.util.Map;
import java.util.Set;

final class OrderStatusTransitions {

    static final String NEW = "NEW";
    static final String PARTIALLY_FILLED = "PARTIALLY_FILLED";
    static final String FILLED = "FILLED";
    static final String CANCELLED = "CANCELLED";
    static final String REJECTED = "REJECTED";

    private static final Map<String, Set<String>> ALLOWED = Map.of(
            NEW, Set.of(NEW, PARTIALLY_FILLED, FILLED, REJECTED, CANCELLED),
            PARTIALLY_FILLED, Set.of(PARTIALLY_FILLED, FILLED, CANCELLED),
            FILLED, Set.of(FILLED),
            CANCELLED, Set.of(CANCELLED),
            REJECTED, Set.of(REJECTED));

    private OrderStatusTransitions() {
    }

    static boolean canTransition(String from, String to) {
        return ALLOWED.getOrDefault(from, Set.of()).contains(to);
    }
}
