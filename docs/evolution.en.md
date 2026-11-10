# Evolution

What was left out on purpose, and how it would be done. Each item says why it was not done now. The decisions taken are in [decisions.en.md](decisions.en.md). · [Español](evolution.md)

## Positions table

Today the portfolio walks all of the user's orders on every request. With a long history that stops scaling.

- **How:** a `positions` table (user, instrument, quantity, cost) updated in the same transaction that fills the order, under the same user lock. The portfolio reads it directly, and walking the orders remains as a rebuild or a check.
- **Why not now:** at these volumes walking is instant, and the statement asks to compute from the orders. The change is local: the read side changes its source and the write side adds one step.

## Business metrics and logs

- **How:** counters such as `orders.placed{side,type,status}` and `transfers.placed{type,status}` in Prometheus, and one INFO event per order or transfer (id, user, outcome and reason), without personal data.
- **Why not now:** the technical base (JVM and HTTP metrics, request id) is in place. The business side is not part of the statement.

## Authentication and resource ownership

- **How:** the API as an OAuth2 resource server with JWT. The user comes from the token, not from the body or the path, and cancelling or reading an order or a transfer checks that it is theirs.
- **Why not now:** the statement says authentication is not needed. Asking the client for a `userId` to "check" it would give security that does not exist.

## Accounts with co-owners

- **How:** an `accounts` table, an `account_holders` table (user, account and role), orders and transfers per account, and the lock on the account instead of the user.
- **Why not now:** today the account is a column of `users`, one to one, and nothing in the statement asks to share it.

## Executing limit orders

- **How:** a process that, on each new price, fills the `NEW` orders whose limit is crossed, with support for partial fills (filled quantity apart from the requested one).
- **Why not now:** the statement asks not to simulate the market.

## Idempotency

- **How:** an `Idempotency-Key` header on `POST /orders` and on transfers. A retry with the same key returns the original result instead of creating another order.
- **Why not now:** it is the first thing to add for real clients (a timeout should not duplicate a purchase), but it goes beyond the challenge.

## Search and listings

- **How:** accent-insensitive search with the `unaccent` extension and a `pg_trgm` index; pagination for the search and the histories; a `GET /users/{id}/orders`.
- **Why not now:** the catalogue has 66 instruments and the histories are short.

## Several currencies

- **How:** a currency column on prices, orders and transfers, and conversions with JSR 354 (Moneta) when converting is needed. The rest of the code already works with `Money`.
- **Why not now:** everything is in pesos, as the statement asks.

## Image and deployment

- **How:** a `jlink`-trimmed runtime (the image goes well below today's ~400 MB), Swagger switched off in production with a profile, and short-lived database credentials instead of a fixed user and password.
- **Why not now:** the current image is already reproducible, runs as non-root and is scanned on every PR.
