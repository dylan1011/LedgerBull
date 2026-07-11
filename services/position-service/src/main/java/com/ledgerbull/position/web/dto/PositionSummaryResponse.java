package com.ledgerbull.position.web.dto;

public record PositionSummaryResponse(String symbol, long net_quantity, long realized_pnl) {
}
