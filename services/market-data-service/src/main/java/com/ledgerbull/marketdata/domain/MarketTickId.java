package com.ledgerbull.marketdata.domain;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Composite identifier for {@link MarketTick}. The {@code market_ticks} hypertable has no
 * surrogate key; a tick is identified by its event time plus symbol.
 */
public class MarketTickId implements Serializable {

    private OffsetDateTime time;
    private String symbol;

    public MarketTickId() {
    }

    public MarketTickId(OffsetDateTime time, String symbol) {
        this.time = time;
        this.symbol = symbol;
    }

    public OffsetDateTime getTime() {
        return time;
    }

    public String getSymbol() {
        return symbol;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof MarketTickId that)) {
            return false;
        }
        return Objects.equals(time, that.time) && Objects.equals(symbol, that.symbol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(time, symbol);
    }
}
