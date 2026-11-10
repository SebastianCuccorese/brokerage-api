# Design decisions

Why the project is built the way it is. How to run it is in the [README](../README.en.md). · [Español](decisions.md)

## Architecture

The system has two halves with different needs, and each uses the style that fits it. Placing and cancelling orders is a problem of rules (states, rejections, balance), so it is modelled with DDD. The portfolio and the search are reads: they are computed from the data, without going through the domain model.

- **Writes (DDD):** the order is an aggregate that controls its own transitions (it can only be cancelled from `NEW`), and the balance is checked with the user locked, so two concurrent orders cannot spend the same money.
- **Reads (projection):** the portfolio is recomputed from the filled orders, with an exact average cost.
- **No isolated domain layer:** the JPA entities are the model. Separating them from Spring would add ports and mappers with no benefit at this size.
- **Two modules:** `market` (instruments and prices) and `trading` (users, orders and portfolio). `trading` uses `market`, never the other way round.

## Database

The provided schema works as it is. The application neither creates nor alters it: Hibernate only validates that the entities match the tables.

- **Proposed indexes** in `db/02-indexes.sql`, for the queries the API runs (orders by user and status, latest price per instrument). They are applied to the local and test databases. On the provided database they are applied by hand, because the application does not alter a database it does not own.
- **No migration tool:** a single index script does not need one. If the schema started changing often, Flyway would be the natural step.

## Money

Every amount is a `Money` (currency + amount), never a bare number. Today there are only pesos (ARS), as the statement asks, but the model accepts other currencies without changing any signature.

- **Precision:** `BigDecimal` in every calculation. It is rounded once, when responding, to the currency's decimals (half-even).
- **No mixing:** combining different currencies is an error, never an implicit conversion.
- **Database:** the price columns do not store the currency and are read as ARS. Supporting another currency would mean adding that column.

## Instrument search

Searches the text in the ticker or the name, case-insensitive, ordered by ticker.

- The peso is left out: it is the account's cash, not an asset you buy.
- Types are exposed in English (`STOCK`, `CURRENCY`). A converter translates the database values (`ACCIONES`, `MONEDA`), so the table's vocabulary does not leak into the model.
- `%` and `_` are searched as literal text, not as wildcards.
- It is a direct projection to the response. There are no rules to protect, so it skips the service layer.
- Possible improvement: ignore accents ("compania" would find "Compañía") with Postgres's `unaccent` extension.

## Portfolio

It is computed by walking the user's orders in chronological order: filled ones move pesos and shares, open ones (`NEW`) only reserve them.

- **Available pesos:** cash minus what open limit buys commit, because that can no longer be used to trade. The total value does include it, because it still belongs to the user.
- **Returns:** the total return is measured against the exact average cost, which a partial sale does not change. The daily return compares `close` with `previousClose`. Both are percentages.
- **The seed sells more than it holds:** order 11 sells 30 BMA with 20 held. The portfolio shows it as a −10 position (a short, which gains when the price falls) to match the orders. Allowing short selling through a margin account was considered and discarded: the statement asks to check that the user holds the shares, and it would also need securities lending and collateral.
- **No price:** if a held instrument has no market data, its value and returns are `null` and do not add to the total.
- **Towards accounts:** today the account is a column of `users`. Co-owners would need an `accounts` table with its holders, orders per account and the lock on the account. It is not built because nothing in the statement asks for it.

## Orders

A market order executes at the latest `close` and is `FILLED`; a limit order stays `NEW` at its own price. An amount in pesos buys whole shares only. There are two kinds of error: a request that makes no sense (broken JSON, both size and amount, a limit order without a price, moving pesos) is a 400 and stores nothing; a valid order the user cannot cover is stored as `REJECTED`, because that is how the statement describes it: the market rejects it and it stays in the history.

- **Rejections answer 201:** the rejected order exists, so the response is 201 with `Location`, `status: REJECTED` and a `rejectionReason`: `INSUFFICIENT_CASH`, `INSUFFICIENT_SHARES` (open sells count), `AMOUNT_BELOW_ONE_SHARE` or `NO_MARKET_DATA`.
- **The reason is not stored:** it only comes in the response that placed the order; reading the order later gives `null`. Storing it needs a new column, and since Hibernate validates the schema, the application would not start against the provided database until someone applies the script. It is left as evolution: a script in `db/` with the column, and the field on the entity.
- **One user at a time:** placing an order locks the user's row before computing their balance, so two concurrent orders are decided one after the other and cannot spend the same pesos. Cancelling locks the order, so two cancels cannot both see it open.
- **Local time:** `datetime` is stored in Buenos Aires time without a zone, like the provided orders (11:13 to 16:11, the local market hours). The clock is injected, so tests fix the time.
- **Limit orders are not executed:** they stay `NEW` and reserve pesos or shares until cancelled. The market is not simulated, so nothing matches them against a price.
