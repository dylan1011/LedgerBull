package com.ledgerbull.position.service;

/**
 * Builds idempotency keys for fills ingested from execution's REST API.
 *
 * <p>Execution's {@code OrderFillResponse} does not expose the internal fill {@code id}, so we use a
 * composite key: taker + maker + symbol + price ticks + quantity. Two legitimately identical fills
 * could collide; exposing fill {@code id} from execution would be the stronger fix.
 */
public final class FillIdempotencyKey {

    private static final String COMPOSITE_PREFIX = "composite:";

    private FillIdempotencyKey() {
    }

    public static String fromComposite(
            String takerOrderId,
            String makerOrderId,
            String symbol,
            long fillPriceTicks,
            long fillQuantity) {
        return COMPOSITE_PREFIX
                + takerOrderId
                + "|"
                + makerOrderId
                + "|"
                + symbol
                + "|"
                + fillPriceTicks
                + "|"
                + fillQuantity;
    }
}
