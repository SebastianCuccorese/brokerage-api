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
