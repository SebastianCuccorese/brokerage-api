-- Indexes for the queries this API runs: orders by user and status, latest price per instrument.
CREATE INDEX IF NOT EXISTS orders_user_status_idx ON orders (userid, status);
CREATE INDEX IF NOT EXISTS marketdata_instrument_date_idx ON marketdata (instrumentid, date DESC);
