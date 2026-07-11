-- Local copy of fills ingested from execution-service (Option B: REST API).
-- source_fill_id is the idempotency key (UNIQUE) so re-ingestion never double-counts.

CREATE TABLE processed_fills (
    id                    BIGSERIAL PRIMARY KEY,
    source_fill_id        VARCHAR(64) NOT NULL,      -- idempotency key: execution fill id or documented composite
    taker_order_id        VARCHAR(64) NOT NULL,
    maker_order_id        VARCHAR(64) NOT NULL,
    symbol                VARCHAR(32) NOT NULL,
    fill_price            BIGINT NOT NULL,           -- integer ticks (scaled), stored as-is from execution
    fill_quantity         BIGINT NOT NULL,
    ingested_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_processed_fills_source UNIQUE (source_fill_id)
);

CREATE INDEX idx_processed_fills_symbol ON processed_fills (symbol);
