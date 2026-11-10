# brokerage-api

[![CI](https://github.com/SebastianCuccorese/brokerage-api/actions/workflows/ci.yml/badge.svg)](https://github.com/SebastianCuccorese/brokerage-api/actions/workflows/ci.yml)

Brokerage REST API: portfolio, instrument search and market and limit orders. Java 21 and Spring Boot 4. · [Español](README.md)

The design decisions and the reason for each one are in [docs/decisions.en.md](docs/decisions.en.md).

## Running it

Requirements: Java 21 and Docker.

**With the local database**, loaded with the provided data:

```sh
docker compose up -d
./mvnw spring-boot:run
```

**With the provided database:** copy `.env.example` to `.env`, fill in the connection and run `./mvnw spring-boot:run`. `.env` is in `.gitignore` and never committed. When it exists, it takes precedence over the local database.

The API listens on `http://localhost:8080`. Logs are readable text; with `--spring.profiles.active=json` they are JSON (ECS), for a log platform.

## Tests

```sh
./mvnw verify
```

They use a throwaway Postgres (Testcontainers) with the same data, so they need Docker and never touch a shared database.

## Endpoints

| Method | Path | What it does |
|---|---|---|
| GET | `/instruments?query=banco` | Searches instruments by ticker or name |
| GET | `/users/{userId}/portfolio` | Total value, available pesos and positions |
| POST | `/orders` | Places a market or limit buy or sell order |
| GET | `/orders/{orderId}` | Returns one order |
| POST | `/orders/{orderId}/cancel` | Cancels an open (`NEW`) order |

Interactive API docs: <http://localhost:8080/swagger-ui.html>

Ready-to-run examples live in [`http/`](http/) (VS Code REST Client or IntelliJ).

## Use of AI

The project was built with AI agents under the rules in [AGENTS.md](AGENTS.md): decisions are human, each feature goes in its own branch and PR, and every change comes with its tests.
