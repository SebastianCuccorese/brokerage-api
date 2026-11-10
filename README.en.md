# brokerage-api

[![CI](https://github.com/SebastianCuccorese/brokerage-api/actions/workflows/ci.yml/badge.svg)](https://github.com/SebastianCuccorese/brokerage-api/actions/workflows/ci.yml)

Brokerage REST API: portfolio, instrument search, market and limit orders, and peso deposits and withdrawals. Java 21 and Spring Boot 4. · [Español](README.md)

The design decisions and the reason for each one are in [docs/decisions.en.md](docs/decisions.en.md).

## Running it

Requirements: Java 21 and Docker.

**With the local database**, loaded with the provided data:

```sh
docker compose up -d
./mvnw spring-boot:run
```

**With the provided database:** copy `.env.example` to `.env`, fill in the connection and run `./mvnw spring-boot:run`. `.env` is in `.gitignore` and never committed. When it exists, it takes precedence over the local database.

On startup the application applies the pending migrations, **including on the provided database**: the first time it adds indexes and columns, moves the cash movements from `orders` to the `transfers` table and creates the `flyway_schema_history` table. Use the direct database connection, not a transaction-mode pooler (for example a host with `-pooler`): migrations need a stable session.

The API listens on `http://localhost:8080`. Logs are readable text; with `--spring.profiles.active=json` they are JSON (ECS), for a log platform.

**Docker image:** the `Dockerfile` builds a production-ready image. Configuration comes in only through environment variables.

```sh
docker build -t brokerage-api .
docker run --rm -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://<host>:5432/brokerage \
  -e DB_USERNAME=<user> -e DB_PASSWORD=<password> \
  brokerage-api
```

CI builds it on every pull request and, on `main`, publishes it to `ghcr.io/sebastiancuccorese/brokerage-api` tagged `latest` and with the commit SHA. The probes (`/actuator/health/liveness` and `/actuator/health/readiness`) and the metrics (`/actuator/prometheus`) are served on port 8081, not on the API's 8080; add `-p 8081:8081` to reach them from the host.

## Tests

```sh
./mvnw verify
```

They use a throwaway Postgres (Testcontainers) with the same data, so they need Docker and never touch a shared database.

## Endpoints

| Method | Path | What it does |
|---|---|---|
| GET | `/instruments?query=banco` | Searches instruments by ticker or name, accent-insensitive; paginated with `page` and `size` |
| GET | `/users/{userId}/portfolio` | Total value, available pesos and positions |
| GET | `/users/{userId}/orders?status=NEW&page=0&size=20` | Lists the user's orders, newest first (`status`, `page` and `size` optional) |
| POST | `/orders` | Places a market or limit buy or sell order |
| GET | `/orders/{orderId}` | Returns one order |
| POST | `/orders/{orderId}/cancel` | Cancels an open (`NEW`) order |
| POST | `/users/{userId}/transfers` | Deposits or withdraws pesos |
| GET | `/users/{userId}/transfers` | The user's transfers, newest first |
| GET | `/users/{userId}/transfers/{transferId}` | Returns one of the user's transfers |

`POST /orders` and `POST /users/{userId}/transfers` take an optional `Idempotency-Key` header: a retry with the same key returns the same order or transfer instead of creating another ([Idempotency](docs/decisions.en.md#idempotency)).

Interactive API docs: <http://localhost:8080/swagger-ui.html>

Ready-to-run examples live in [`http/`](http/) (VS Code REST Client or IntelliJ).

## Use of AI

The project was built with AI agents under the rules in [AGENTS.md](AGENTS.md): decisions are human, each feature goes in its own branch and PR, and every change comes with its tests.
