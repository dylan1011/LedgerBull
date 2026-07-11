package com.ledgerbull.position.web.dto;

public record ProcessedFillResponse(
        String source_fill_id,
        String taker_order_id,
        String maker_order_id,
        String symbol,
        long fill_price,
        long fill_quantity,
        String taker_side,
        String ingested_at) {
}
