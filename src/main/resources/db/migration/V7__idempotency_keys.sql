-- What each idempotency key created, so a retried request returns that result instead of acting again.
-- resourceId is an order or a transfer depending on the operation, so it has no foreign key.
CREATE TABLE idempotency_keys (
  id BIGSERIAL PRIMARY KEY,
  userId INT NOT NULL REFERENCES users(id),
  operation VARCHAR(20) NOT NULL,
  idempotencyKey VARCHAR(64) NOT NULL,
  requestHash VARCHAR(64) NOT NULL,
  resourceId INT NOT NULL,
  createdAt TIMESTAMPTZ NOT NULL,
  CONSTRAINT idempotency_keys_user_operation_key_unique UNIQUE (userId, operation, idempotencyKey)
);

-- The hourly purge deletes by age.
CREATE INDEX idempotency_keys_created_at_idx ON idempotency_keys (createdAt);
