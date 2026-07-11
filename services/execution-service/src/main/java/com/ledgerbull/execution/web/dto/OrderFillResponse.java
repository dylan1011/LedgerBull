package com.ledgerbull.execution.web.dto;

public record OrderFillResponse(
        String taker_order_id,
        String maker_order_id,
        double price,
        long quantity,
        String symbol,
        String created_at) {
}
