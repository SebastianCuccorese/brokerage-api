-- Indexes for the queries this API runs. Applied to the local and test databases;
-- see the README before applying them to a shared database.
CREATE INDEX IF NOT EXISTS orders_user_status_idx ON orders (userid, status);
CREATE INDEX IF NOT EXISTS marketdata_instrument_date_idx ON marketdata (instrumentid, date DESC);
