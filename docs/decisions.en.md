# Design decisions

Why the project is built the way it is. How to run it is in the [README](../README.en.md). · [Español](decisions.md)

## Architecture

The system has two halves with different needs, and each uses the style that fits it. Placing and cancelling orders, or moving pesos, is a problem of rules (states, rejections, balance), so it is modelled with DDD. The portfolio and the search are reads: they are computed from the data, without going through the domain model.

- **Writes (DDD):** the order and the transfer are aggregates that decide their own status (an order can only be cancelled from `NEW`, a withdrawal without funds is `REJECTED`), and the balance is checked with the user locked, so two concurrent operations cannot spend the same money.
- **Reads (projection):** the portfolio is recomputed from the transfers and the filled orders, with an exact average cost.
- **No isolated domain layer:** the JPA entities are the model. Separating them from Spring would add ports and mappers with no benefit at this size.
- **Two modules:** `market` (instruments and prices) and `trading` (users, transfers, orders and portfolio). `trading` uses `market`, never the other way round.

## Database

The provided schema is version 1. On startup the application takes any database without history as version 1 and applies the pending migrations (Flyway), so the local database, the tests and the provided database follow the same path. Hibernate only validates that the entities match the tables.

- **Consequence:** the first run against the provided database alters it: it adds indexes and columns, moves the cash movements from `orders` to the new `transfers` table (V5, see [Transfers](#transfers)) and creates the `flyway_schema_history` table.
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

It is computed from the completed transfers, which move pesos, and from the user's orders walked in chronological order: filled ones move pesos and shares, open ones (`NEW`) only reserve them. Transfers need no such order because they only add or take away pesos.

- **Available pesos:** cash minus what open limit buys commit, because that can no longer be used to trade. The total value does include it, because it still belongs to the user.
- **Returns:** the total return is measured against the exact average cost, which a partial sale does not change. The daily return compares `close` with `previousClose`. Both are percentages.
- **The seed sells more than it holds:** order 11 sells 30 BMA with 20 held. The portfolio shows it as a −10 position (a short, which gains when the price falls) to match the orders. Allowing short selling through a margin account was considered and discarded: the statement asks to check that the user holds the shares, and it would also need securities lending and collateral.
- **No price:** if a held instrument has no market data, its value and returns are `null` and do not add to the total.
- **Towards accounts:** today the account is a column of `users`. Co-owners would need an `accounts` table with its holders, orders per account and the lock on the account. It is not built because nothing in the statement asks for it.

## Orders

A market order executes at the latest `close` and is `FILLED`. A limit order stays `NEW` at its own price and reserves pesos or shares until it is cancelled: the market is not simulated, so nothing matches it against a price. An amount in pesos buys whole shares only. The `side` is `BUY` or `SELL`: moving pesos is a [transfer](#transfers).

- **Two kinds of error:** a request that makes no sense (broken JSON, both size and amount, a limit order without a price, a `side` such as `CASH_IN`) is a 400 and stores nothing. A valid order the user cannot cover is stored as `REJECTED` and answered with 201 and its `rejectionReason` (`INSUFFICIENT_CASH`, `INSUFFICIENT_SHARES` counting open sells, `AMOUNT_BELOW_ONE_SHARE` or `NO_MARKET_DATA`), because that is how the statement describes it: the market rejects it and it stays in the history. An amount too small for one share is a rejection, not a 400, because it depends on the price at that moment.
- **The reason is stored** in the `rejectionReason` column (migration V3), so rejections can be audited; it is `null` for every other order.
- **One user at a time:** placing an order locks the user's row before computing their balance, so two concurrent orders cannot spend the same pesos. Cancelling locks the order, so two cancels cannot both see it open.
- **Time in UTC:** `datetime` is an instant (`timestamptz`) and the API returns it as ISO-8601 UTC (`2026-11-10T14:30:00Z`). The provided orders held Buenos Aires time without a zone (11:13 to 16:11, the local market hours); migration V4 converted them once, keeping their order. The time comes from a single `Clock`, which tests fix.
- **Cancel does not check the owner:** nothing verifies that the order belongs to the caller, because there is no authentication, and a `userId` sent by the client would only look like security.

## Transfers

A deposit or a withdrawal of pesos is a transfer, with its own table (`transfers`), aggregate and endpoint (`/users/{userId}/transfers`). The statement allows modelling them as `CASH_IN`/`CASH_OUT` orders and computing cash from the `orders` table; this project departs from that on purpose, because a transfer has no instrument, price or market. As an order it had to fill those columns with placeholders (the ARS instrument, price 1, the amount in `size`), and every order rule had to exclude those sides.

- **Migration V5:** creates `transfers`, moves the cash movements from `orders` into it with their time (`FILLED` becomes `COMPLETED`, `REJECTED` stays `REJECTED`, and any other status stops the migration instead of guessing), deletes them from `orders` and restricts `side` to `BUY` and `SELL`. It runs in one transaction, and the seed portfolio does not change.
- **Rejected withdrawals:** a withdrawal above the available pesos (open limit buys keep what they reserve) is stored `REJECTED` with `rejectionReason` `INSUFFICIENT_CASH` and answered with 201, like a rejected order. An amount that is not positive or has more than 2 decimals is a 400 and stores nothing.
- **The same lock:** a withdrawal locks the user's row as an order does, so a withdrawal and a buy at the same time cannot spend the same pesos.
- **History:** `GET /users/{userId}/transfers` returns them newest first. Another user's transfer answers 404, like one that does not exist.

## Idempotency

A client that retries after a timeout does not know whether its order arrived, and in a brokerage a retry must not buy twice. `POST /orders` takes an optional `Idempotency-Key` header, with the semantics of the IETF draft "The Idempotency-Key HTTP Header Field": the first request with a key is processed and the key is stored (table `idempotency_keys`, migration V7); a retry with the same key returns the same order, marked `Idempotent-Replayed: true`, instead of placing another. Without the header nothing changes.

- **On the user lock:** the key is looked up and stored in the same transaction that already locks the user's row, so a concurrent retry waits for the first request and finds its key. That is why the 409 the draft foresees for a request still in flight is not needed; the unique constraint on (user, operation, key) is the safety net.
- **A retry returns the order as it is now** (`CANCELLED` if it was cancelled since), not a copy of the first response: the client cares about which order exists and in what state, and storing responses would duplicate what `orders` already holds. A `REJECTED` order is a result too: the retry returns it rejected and does not decide again. A request that fails (400, 404) stores nothing and its key stays free.
- **The same key with a different request** is a 422 `IDEMPOTENCY_KEY_REUSED` and stores nothing. What is compared is a SHA-256 of the parsed request, not of its bytes, so field order, spacing or `1000` against `1000.00` do not change it.
- **Retention:** a key holds for at least 24 hours; an hourly task deletes the expired ones. It is per user and per operation, 1 to 64 characters of `[A-Za-z0-9._:-]`, quoted or not (`"abc"` is `abc`), because the draft defines it as a structured-field string.

## Errors

Every error response is a problem detail (RFC 9457) with a stable `code` and the request's `requestId`. The domain throws exceptions that carry their `code` but do not know what HTTP is; one `@RestControllerAdvice` turns each category into a status. That way a client switches on the `code`, and support finds the logs with the `requestId`.

- **Categories:** `NotFoundException` (404), `InvalidRequestException` (400), `ConflictException` (409) and `UnprocessableContentException` (422: the request contradicts an earlier one, such as a reused idempotency key; not a 409 because retrying cannot fix it, only changing the request can), each with an `ErrorCode` (`ORDER_NOT_FOUND`, `ORDER_NOT_CANCELLABLE`, `SIZE_OR_AMOUNT_REQUIRED`...). A code is never renamed or given a new meaning; the text can change.
- **Validation:** a 400 `VALIDATION_FAILED` with an `errors` list of `{field, message}`, so a client can mark the field. Broken JSON is `MALFORMED_REQUEST` and does not repeat Jackson's message.
- **Framework errors** (method not allowed, unknown route) keep their status and gain the `requestId`, without a `code`.
- **A 500 never leaks internals:** it answers `INTERNAL_ERROR` with a generic text asking to quote the `requestId`; the message and stack stay in the log, at ERROR level.

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
- **Probes:** `/actuator/health/liveness` and `/actuator/health/readiness` for the orchestrator, on the management port (see [Observability](#observability)).
- **CI:** the image is built on every PR and published from `main`. It is scanned with Trivy and fails on critical vulnerabilities that have a fix.
- **Possible improvement:** a jlink-trimmed runtime to shrink the image.

## Observability

The application publishes its metrics in Prometheus format (`/actuator/prometheus`), the de facto standard for scraping, with no custom metrics code.

- **What is measured:** the JVM, HTTP requests (`http.server.requests`), the Hikari connection pool and Tomcat, all tagged `application=brokerage-api`.
- **Management port:** Actuator is served on 8081 and the API on 8080. This way metrics and probes are not exposed to the internet through the API's ingress; only whoever is inside the network reaches them.
- **Liveness and readiness:** liveness says whether the process should be restarted and readiness whether it can take traffic (it includes the database). Liveness does not depend on the database, so a database outage does not get the pod restarted.
- **Business:** order metrics come with the order logging step.
