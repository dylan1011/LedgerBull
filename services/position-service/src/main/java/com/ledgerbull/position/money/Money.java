package com.ledgerbull.position.money;

/**
 * Scaled-integer money helpers for the position service.
 *
 * <p>All internal money values are {@code long} ticks ({@link #PRICE_SCALE} = 100 → 556.00 = 55600).
 * Human conversion happens at API boundaries only.
 */
public final class Money {

    public static final long PRICE_SCALE = 100L;

    private Money() {
    }

    public static long toTicks(double humanPrice) {
        return Math.round(humanPrice * PRICE_SCALE);
    }

    public static String toHuman(long ticks) {
        long whole = ticks / PRICE_SCALE;
        long fraction = Math.abs(ticks % PRICE_SCALE);
        return whole + "." + String.format("%02d", fraction);
    }

    public static long multiplyQtyPrice(long quantity, long priceTicks) {
        return quantity * priceTicks;
    }
}
