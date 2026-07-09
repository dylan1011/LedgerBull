package com.ledgerbull.marketdata.web.dto;

/** Latest price for a symbol; {@code source} is {@code redis} or {@code timescaledb}. */
public record LatestPriceResponse(String symbol, double price, String source) {
}
