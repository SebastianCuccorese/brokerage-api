# Evolution

How the system would grow as users, volume and demands increase. What is already built, and why, is in the [business](decisions.en.md) and [technical](technical.en.md) decisions. · [Español](evolution.md)

## Positions table

Today the portfolio walks all of the user's orders on every request, and with a long history that stops scaling. The step is to materialise positions in a `positions` table (user, instrument, quantity, cost), and there are two ways to keep it:

- **In the same transaction** that fills the order, under the user lock. The portfolio is always up to date, at the cost of one more step on every write.
- **Asynchronously:** a filled order publishes an event (`OrderFilled`) that a Spring handler (`@TransactionalEventListener` after commit) or a queue consumer applies to the table. To avoid losing events if the process dies, the outbox pattern applies: the event is stored in the same transaction as the order and published afterwards. Writes stay light and the portfolio becomes *eventually consistent*.
- Either way, **the balance check does not read the projection**: it keeps deciding on the source of truth, with the user locked, so a lagging projection can never allow spending twice.

## Business metrics and logs

- Counters such as `orders.placed{side,type,status}` and `transfers.placed{type,status}` in Prometheus, showing volume and the rejection rate per reason.
- One INFO event per order or transfer (id, user, outcome and reason), without personal data, correlated with the request id every line already carries.

## Authentication and resource ownership

- The API as an OAuth2 resource server with JWT. The user comes from the token, not from the body or the path.
- Cancelling or reading an order or a transfer checks that it belongs to the caller.

## Accounts with co-owners

- An `accounts` table and an `account_holders` table (user, account and role). Orders and transfers belong to the account, and the consistency lock moves from the user to the account.

## Executing limit orders

- A process that, on each new price, fills the `NEW` orders whose limit is crossed, with partial fills (filled quantity apart from the requested one). It fits the event flow of the positions table: each execution is one more `OrderFilled`.

## Several currencies

- A currency column on prices, orders and transfers, and conversions with JSR 354 (Moneta). The rest of the code already works with `Money` and refuses to mix currencies.

## Image and deployment

- A `jlink`-trimmed runtime, to bring the image below today's ~400 MB.
- Swagger switched off in production with a profile.
- Short-lived database credentials instead of a fixed user and password.
