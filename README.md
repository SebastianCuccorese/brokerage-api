# brokerage-api

[![CI](https://github.com/SebastianCuccorese/brokerage-api/actions/workflows/ci.yml/badge.svg)](https://github.com/SebastianCuccorese/brokerage-api/actions/workflows/ci.yml)

API REST de un broker: portfolio, búsqueda de instrumentos y órdenes de mercado y límite. Java 21 y Spring Boot 4. · [English](README.en.md)

Las decisiones de diseño y el porqué de cada una están en [docs/decisions.md](docs/decisions.md).

## Levantar el proyecto

Requisitos: Java 21 y Docker.

**Con la base local**, cargada con los datos provistos:

```sh
docker compose up -d
./mvnw spring-boot:run
```

**Con la base provista:** copiá `.env.example` a `.env`, completá la conexión y corré `./mvnw spring-boot:run`. El `.env` está en `.gitignore` y nunca se commitea. Si existe, tiene prioridad sobre la base local.

La API queda en `http://localhost:8080`.

**Imagen Docker:** el `Dockerfile` arma una imagen lista para producción. La configuración entra solo por variables de entorno.

```sh
docker build -t brokerage-api .
docker run --rm -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://<host>:5432/brokerage \
  -e DB_USERNAME=<usuario> -e DB_PASSWORD=<clave> \
  brokerage-api
```

CI la construye en cada pull request y, en `main`, la publica en `ghcr.io/sebastiancuccorese/brokerage-api` con los tags `latest` y el SHA del commit. Los probes están en `/actuator/health/liveness` y `/actuator/health/readiness`.

## Tests

```sh
./mvnw verify
```

Usan un Postgres descartable (Testcontainers) con los mismos datos, así que necesitan Docker y nunca tocan una base compartida.

## Endpoints

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/instruments?query=banco` | Busca instrumentos por ticker o nombre |

Hay ejemplos listos para ejecutar en [`http/`](http/) (REST Client de VS Code o IntelliJ).

## Uso de IA

El proyecto se construyó con agentes de IA bajo las reglas de [AGENTS.md](AGENTS.md): las decisiones son humanas, cada funcionalidad va en su propia branch y PR, y todo cambio llega con sus tests.
