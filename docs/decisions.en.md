# Design decisions

Why the project is built the way it is. How to run it is in the [README](../README.en.md). · [Español](decisions.md)

## Architecture

The system has two halves with different needs, and each uses the style that fits it. Placing and cancelling orders is a problem of rules (states, rejections, balance), so it is modelled with DDD. The portfolio and the search are reads: they are computed from the data, without going through the domain model.

- **Writes (DDD):** the order is an aggregate that controls its own transitions (it can only be cancelled from `NEW`), and the balance is checked with the user locked, so two concurrent orders cannot spend the same money.
- **Reads (projection):** the portfolio is recomputed from the filled orders, with an exact average cost.
- **No isolated domain layer:** the JPA entities are the model. Separating them from Spring would add ports and mappers with no benefit at this size.
- **Two modules:** `market` (instruments and prices) and `trading` (users, orders and portfolio). `trading` uses `market`, never the other way round.

## Database

The provided schema is version 1. On startup the application takes any database without history as version 1 and applies the pending migrations (Flyway), so the local database, the tests and the provided database follow the same path. Hibernate only validates that the entities match the tables.

- **Consequence:** the first run against the provided database alters it: it adds indexes and, later, columns, and creates the `flyway_schema_history` table.
- **Migrations** live in `src/main/resources/db/migration`. `db/01-database.sql` stays as the provided state: the local and test databases load it when they are created.
- **Same version as the provided database:** Postgres 18.6 locally and in the tests, so what is tested is what runs.

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
- **The reason is stored** in the `rejectionReason` column (migration V3), so rejections can be audited: it comes back when the order is placed and whenever it is read later. It is `null` for orders that were not rejected.
- **One user at a time:** placing an order locks the user's row before computing their balance, so two concurrent orders are decided one after the other and cannot spend the same pesos. Cancelling locks the order, so two cancels cannot both see it open.
- **Time in UTC:** `datetime` is stored as an instant (`timestamptz`) and the API returns it as ISO-8601 UTC, for example `2026-11-10T14:30:00Z`. The provided orders held Buenos Aires time without a zone (11:13 to 16:11, the local market hours); migration V4 converted them once, and the portfolio's chronological order does not change. The time comes from a single UTC `Clock`, which tests replace to fix it.
- **Limit orders are not executed:** they stay `NEW` and reserve pesos or shares until cancelled. The market is not simulated, so nothing matches them against a price.

## Logs

Each request carries an id (`X-Request-Id`) that appears on all its log lines and is returned in the response, to follow a call end to end. Business events are logged at INFO (they come with orders); never emails or secrets.

- **Request id:** the client's value is used only when it is 1 to 64 characters of `[A-Za-z0-9._-]`; otherwise a UUID is generated. It is validated so a client cannot inject line breaks or fake text into the logs.
- **Locally:** readable text, with the id in brackets on every line.
- **Log platform:** the `json` profile emits ECS-format JSON (Spring Boot's built-in support), with the id as the `requestId` field.
- **No distributed tracing:** with a single service it is not worth its cost.

## Deployment

The application is deployed as a container image. It is layered so a code change only rebuilds the last layer and the rest comes from cache.

- **Layers:** dependencies, loader, snapshots and code, from least to most frequently changing.
- **Runtime:** JRE only, with an unprivileged user and a heap sized from the container's memory limit.
- **Configuration:** only through environment variables (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`); the image carries no secrets.
- **Probes:** `/actuator/health/liveness` and `/actuator/health/readiness` for the orchestrator; it is the only Actuator endpoint exposed.
- **CI:** the image is built on every PR and published from `main`. It is scanned with Trivy and fails on critical vulnerabilities that have a fix.
