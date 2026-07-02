package com.ledgerbull.marketdata.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * A normalized market tick, mapped to the TimescaleDB {@code market_ticks} hypertable.
 *
 * <p>Rows are written via {@code JdbcTemplate} (see the persistence service) to avoid JPA
 * merge semantics on the composite key; this entity + repository are used for read queries.
 */
@Entity
@Table(name = "market_ticks")
@IdClass(MarketTickId.class)
public class MarketTick {

    @Id
    @Column(name = "time", nullable = false)
    private OffsetDateTime time;

    @Id
    @Column(name = "symbol", nullable = false)
    private String symbol;

    @Column(name = "price", nullable = false)
    private double price;

    @Column(name = "volume")
    private Double volume;

    @Column(name = "source")
    private String source;

    protected MarketTick() {
    }

    public MarketTick(OffsetDateTime time, String symbol, double price, Double volume, String source) {
        this.time = time;
        this.symbol = symbol;
        this.price = price;
        this.volume = volume;
        this.source = source;
    }

    public OffsetDateTime getTime() {
        return time;
    }

    public String getSymbol() {
        return symbol;
    }

    public double getPrice() {
        return price;
    }

    public Double getVolume() {
        return volume;
    }

    public String getSource() {
        return source;
    }
}
