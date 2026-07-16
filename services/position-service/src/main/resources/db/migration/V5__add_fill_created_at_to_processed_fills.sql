ALTER TABLE processed_fills ADD COLUMN fill_created_at TIMESTAMPTZ;

CREATE INDEX idx_processed_fills_fill_created_at ON processed_fills (fill_created_at, id);
