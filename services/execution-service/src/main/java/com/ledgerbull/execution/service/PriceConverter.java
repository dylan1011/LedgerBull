package com.ledgerbull.execution.service;

/**
 * Converts human-readable prices to engine integer ticks and back.
 *
 * <p>Matches the C++ engine {@code PRICE_SCALE} (100): one tick = 0.01 quote units,
 * e.g. human {@code 101.23} → {@code 10123} ticks.
 */
public final class PriceConverter {

    public static final long PRICE_SCALE = 100L;

    private PriceConverter() {
    }

    public static long toTicks(double humanPrice) {
        return Math.round(humanPrice * PRICE_SCALE);
    }

    public static double fromTicks(long ticks) {
        return ticks / (double) PRICE_SCALE;
    }
}
