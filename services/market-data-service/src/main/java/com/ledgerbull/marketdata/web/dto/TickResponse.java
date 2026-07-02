package com.ledgerbull.marketdata.web.dto;

import com.ledgerbull.marketdata.domain.MarketTick;
import java.time.OffsetDateTime;

/** A single historical tick returned by the history endpoint. */
public record TickResponse(OffsetDateTime time, String symbol, double price, Double volume, String source) {

    public static TickResponse from(MarketTick tick) {
        return new TickResponse(tick.getTime(), tick.getSymbol(), tick.getPrice(), tick.getVolume(),
                tick.getSource());
    }
}
