CREATE TABLE orders (
    id                  BIGSERIAL PRIMARY KEY,           -- internal auto-increment PK
    order_id            VARCHAR(64) NOT NULL UNIQUE,     -- the client/engine order id (numeric string, e.g. "1")
    symbol              VARCHAR(32) NOT NULL,            -- e.g. BTC-USD
    side                VARCHAR(4)  NOT NULL,            -- BUY or SELL
    order_type          VARCHAR(8)  NOT NULL,            -- LIMIT or MARKET
    price               BIGINT,                          -- integer ticks (NULL allowed for MARKET orders)
    original_quantity   BIGINT NOT NULL,                 -- requested quantity
    filled_quantity     BIGINT NOT NULL DEFAULT 0,       -- how much has filled
    remaining_quantity  BIGINT NOT NULL,                 -- original - filled
    status              VARCHAR(20) NOT NULL,            -- NEW, PARTIALLY_FILLED, FILLED, CANCELLED, REJECTED
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_orders_order_id ON orders (order_id);
CREATE INDEX idx_orders_symbol_status ON orders (symbol, status);
