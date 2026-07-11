-- Scaled-integer convention (LedgerBull):
--   lot.price is the cost basis in BIGINT ticks (same scale as execution/engine prices).
--   Quantities (original_quantity, remaining_quantity) are BIGINT.
--   FIFO: consume lots with smallest sequence_no where remaining_quantity > 0.

CREATE TABLE lots (
    id                  BIGSERIAL PRIMARY KEY,
    symbol              VARCHAR(32) NOT NULL,             -- which symbol this lot is for
    side                VARCHAR(8)  NOT NULL,             -- BUY (lots are opened by buys in a long-only model)
    original_quantity   BIGINT NOT NULL,                 -- quantity when the lot was opened
    remaining_quantity  BIGINT NOT NULL,                 -- quantity still open (decreases as sells consume it)
    price               BIGINT NOT NULL,                 -- price paid, scaled integer ticks (the cost basis of this lot)
    sequence_no         BIGINT NOT NULL,                 -- monotonic open-order for FIFO (oldest = smallest)
    source_fill_id      VARCHAR(64),                     -- optional: the fill/order id that opened this lot (traceability)
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- FIFO consumption needs "oldest open lot for this symbol first":
CREATE INDEX idx_lots_symbol_seq ON lots (symbol, sequence_no);
-- Finding still-open lots quickly:
CREATE INDEX idx_lots_symbol_remaining ON lots (symbol, remaining_quantity);
