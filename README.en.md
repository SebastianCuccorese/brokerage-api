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

The API listens on `http://localhost:8080`.

**Docker image:** the `Dockerfile` builds a production-ready image. Configuration comes in only through environment variables.

```sh
docker build -t brokerage-api .
docker run --rm -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://<host>:5432/brokerage \
  -e DB_USERNAME=<user> -e DB_PASSWORD=<password> \
  brokerage-api
```

CI builds it on every pull request and, on `main`, publishes it to `ghcr.io/sebastiancuccorese/brokerage-api` tagged `latest` and with the commit SHA. The probes are at `/actuator/health/liveness` and `/actuator/health/readiness`.

## Tests

```sh
./mvnw verify
```

They use a throwaway Postgres (Testcontainers) with the same data, so they need Docker and never touch a shared database.

## Endpoints

| Method | Path | What it does |
|---|---|---|
| GET | `/instruments?query=banco` | Searches instruments by ticker or name |

Ready-to-run examples live in [`http/`](http/) (VS Code REST Client or IntelliJ).

## Use of AI

The project was built with AI agents under the rules in [AGENTS.md](AGENTS.md): decisions are human, each feature goes in its own branch and PR, and every change comes with its tests.
