package com.ledgerbull.marketdata.web.dto;

/** Latest cached price for a symbol, served from Redis. */
public record LatestPriceResponse(String symbol, double price, String source) {
}
