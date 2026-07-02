CREATE TABLE IF NOT EXISTS market_ticks (
    time        TIMESTAMPTZ      NOT NULL,
    symbol      TEXT             NOT NULL,
    price       DOUBLE PRECISION NOT NULL,
    volume      DOUBLE PRECISION,
    source      TEXT
);

-- Convert to a TimescaleDB hypertable, partitioned by time
SELECT create_hypertable('market_ticks', 'time', if_not_exists => TRUE);

-- Index for fast per-symbol time-range queries
CREATE INDEX IF NOT EXISTS idx_market_ticks_symbol_time
    ON market_ticks (symbol, time DESC);
