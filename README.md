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

Al arrancar, la aplicación aplica las migraciones pendientes, **también sobre la base provista**: la primera vez le agrega índices y columnas y crea la tabla `flyway_schema_history`. Usá la conexión directa a la base, no un pooler en modo transacción (por ejemplo, un host con `-pooler`): las migraciones necesitan una sesión estable.

La API queda en `http://localhost:8080`. Los logs salen como texto legible; con `--spring.profiles.active=json` salen como JSON (ECS), para una plataforma de logs.

## Tests

```sh
./mvnw verify
```

Usan un Postgres descartable (Testcontainers) con los mismos datos, así que necesitan Docker y nunca tocan una base compartida.

## Endpoints

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/instruments?query=banco` | Busca instrumentos por ticker o nombre |
| GET | `/users/{userId}/portfolio` | Valor total, pesos disponibles y posiciones |

Documentación interactiva de la API: <http://localhost:8080/swagger-ui.html>

Hay ejemplos listos para ejecutar en [`http/`](http/) (REST Client de VS Code o IntelliJ).

## Uso de IA

El proyecto se construyó con agentes de IA bajo las reglas de [AGENTS.md](AGENTS.md): las decisiones son humanas, cada funcionalidad va en su propia branch y PR, y todo cambio llega con sus tests.
