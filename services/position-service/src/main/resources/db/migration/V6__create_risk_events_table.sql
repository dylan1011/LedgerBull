-- Durable post-trade risk events (Phase 5E). Queryable breach records.
-- observed_value / limit_value: quantity units for POSITION_BREACH;
-- money ticks (PRICE_SCALE=100) for LOSS_BREACH (same convention as realized_pnl).

CREATE TABLE risk_events (
    id              BIGSERIAL PRIMARY KEY,
    symbol          VARCHAR(32) NOT NULL,
    event_type      VARCHAR(32) NOT NULL,   -- POSITION_BREACH | LOSS_BREACH
    severity        VARCHAR(16) NOT NULL,   -- WARN | BREACH
    observed_value  BIGINT NOT NULL,
    limit_value     BIGINT NOT NULL,
    detail          VARCHAR(512) NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_risk_events_symbol_created ON risk_events (symbol, created_at DESC);
CREATE INDEX idx_risk_events_type_created ON risk_events (event_type, created_at DESC);
