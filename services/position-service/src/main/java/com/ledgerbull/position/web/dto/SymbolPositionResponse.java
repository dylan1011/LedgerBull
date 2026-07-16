package com.ledgerbull.position.web.dto;

public record SymbolPositionResponse(
        String symbol, long net_quantity, long realized_pnl, String realized_pnl_human) {
}
