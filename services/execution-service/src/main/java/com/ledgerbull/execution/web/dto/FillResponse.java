package com.ledgerbull.execution.web.dto;

public record FillResponse(
        String taker_order_id,
        String maker_order_id,
        double price,
        long quantity,
        String symbol) {
}
