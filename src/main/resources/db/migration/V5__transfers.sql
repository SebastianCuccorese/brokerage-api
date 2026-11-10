-- Cash transfers get a table of their own: a deposit or a withdrawal has no instrument, price or market.
-- The cash movements stored as orders move over with their time, and orders keep only trades.
CREATE TABLE transfers (
  id SERIAL PRIMARY KEY,
  userId INT NOT NULL REFERENCES users(id),
  type VARCHAR(10) NOT NULL CHECK (type IN ('DEPOSIT', 'WITHDRAWAL')),
  amount NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
  status VARCHAR(10) NOT NULL CHECK (status IN ('COMPLETED', 'REJECTED')),
  rejectionReason VARCHAR(30),
  datetime timestamptz NOT NULL
);

CREATE INDEX transfers_user_status_idx ON transfers (userId, status);

-- Only filled and rejected cash movements have a transfer equivalent; any other status stops the
-- migration (and its transaction) instead of being guessed.
DO $$
DECLARE
  unexpected TEXT;
BEGIN
  SELECT string_agg(DISTINCT coalesce(status, 'NULL'), ', ') INTO unexpected
  FROM orders
  WHERE side IN ('CASH_IN', 'CASH_OUT') AND (status IS NULL OR status NOT IN ('FILLED', 'REJECTED'));
  IF unexpected IS NOT NULL THEN
    RAISE EXCEPTION 'Cash movements in orders with statuses that have no transfer equivalent: %', unexpected;
  END IF;
END $$;

-- The statement stores the amount of a cash movement in size.
INSERT INTO transfers (userId, type, amount, status, rejectionReason, datetime)
SELECT userId,
       CASE side WHEN 'CASH_IN' THEN 'DEPOSIT' WHEN 'CASH_OUT' THEN 'WITHDRAWAL' END,
       size,
       CASE status WHEN 'FILLED' THEN 'COMPLETED' WHEN 'REJECTED' THEN 'REJECTED' END,
       rejectionReason,
       datetime
FROM orders
WHERE side IN ('CASH_IN', 'CASH_OUT')
ORDER BY datetime, id;

DELETE FROM orders WHERE side IN ('CASH_IN', 'CASH_OUT');

ALTER TABLE orders ADD CONSTRAINT orders_side_check CHECK (side IN ('BUY', 'SELL'));
