package com.ledgerbull.position.service;

/**
 * Converts human-readable prices from execution's REST API back to integer ticks.
 *
 * <p>Matches execution/engine {@code PRICE_SCALE} (100): human {@code 525.0} → {@code 52500} ticks.
 */
public final class PriceConverter {

    public static final long PRICE_SCALE = 100L;

    private PriceConverter() {
    }

    public static long toTicks(double humanPrice) {
        return Math.round(humanPrice * PRICE_SCALE);
    }
}
