CREATE TABLE fills (
    id               BIGSERIAL PRIMARY KEY,           -- internal auto-increment PK
    order_ref_id     BIGINT NOT NULL,                 -- FK to orders.id (the internal PK of the owning order)
    taker_order_id   VARCHAR(64) NOT NULL,            -- the aggressing order's business id
    maker_order_id   VARCHAR(64) NOT NULL,            -- the resting order's business id
    symbol           VARCHAR(32) NOT NULL,            -- e.g. BTC-USD
    fill_price       BIGINT NOT NULL,                 -- integer ticks (the maker/resting price)
    fill_quantity    BIGINT NOT NULL,                 -- quantity matched in this fill
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT fk_fills_order FOREIGN KEY (order_ref_id) REFERENCES orders (id)
);

CREATE INDEX idx_fills_order_ref_id ON fills (order_ref_id);
