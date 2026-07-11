package com.ledgerbull.position.client.dto;

/**
 * A fill enriched with the taker order's side from execution's order detail.
 */
public record ExecutionIngestFill(
        String taker_order_id,
        String maker_order_id,
        String symbol,
        double price,
        long quantity,
        String taker_side,
        String created_at) {
}
