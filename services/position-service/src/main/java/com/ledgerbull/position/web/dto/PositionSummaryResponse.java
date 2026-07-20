package com.ledgerbull.position.web.dto;

public record PositionSummaryResponse(
        String symbol,
        long net_quantity,
        long realized_pnl,
        String realized_pnl_human,
        Long unrealized_pnl,
        String unrealized_pnl_human,
        Long current_price,
        String current_price_human) {
}
