-- Scaled-integer convention (LedgerBull):
--   Prices and realized PnL are stored as BIGINT ticks (e.g. PRICE_SCALE=100 → 105.00 = 10500).
--   Quantities are BIGINT. No NUMERIC/DECIMAL/double — exact integer math only.
--   Human decimal formatting happens at the API boundary, not in the DB.

CREATE TABLE positions (
    id                  BIGSERIAL PRIMARY KEY,
    symbol              VARCHAR(32) NOT NULL UNIQUE,      -- one row per symbol (e.g. BTC-USD)
    net_quantity        BIGINT NOT NULL DEFAULT 0,        -- signed: positive = long, negative = short
    realized_pnl        BIGINT NOT NULL DEFAULT 0,        -- scaled integer (ticks/money scale), locked-in P/L
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_positions_symbol ON positions (symbol);
