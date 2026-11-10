# brokerage-api

Brokerage REST API: portfolio, instrument search and market and limit orders. · [Español](README.md)

## Architecture

The system has two halves with different needs, and each uses the style that fits it. Placing and cancelling orders is a problem of rules (states, rejections, balance), so it is modelled with DDD. The portfolio and the search are reads: they are computed from the data, without going through the domain model.

- **Writes (DDD):** the order is an aggregate that controls its own transitions (it can only be cancelled from `NEW`), and the balance is checked with the account locked, so two concurrent orders cannot spend the same money.
- **Reads (projection):** the portfolio is recomputed from the filled orders, with an exact average cost.
- **No isolated domain layer:** the JPA entities are the model. Separating them from Spring would add ports and mappers with no benefit at this size.
- **Two modules:** `market` (instruments and prices) and `trading` (accounts, orders and portfolio). `trading` uses `market`, never the other way round.
