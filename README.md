# brokerage-api

[![CI](https://github.com/SebastianCuccorese/brokerage-api/actions/workflows/ci.yml/badge.svg)](https://github.com/SebastianCuccorese/brokerage-api/actions/workflows/ci.yml)

API REST de un broker: portfolio, búsqueda de instrumentos, órdenes de mercado y límite, y depósitos y retiros de pesos. Java 21 y Spring Boot 4. · [English](README.en.md)

Las decisiones de diseño y el porqué de cada una están en [docs/decisions.md](docs/decisions.md).

## Levantar el proyecto

Requisitos: Java 21 y Docker.

**Con la base local**, cargada con los datos provistos:

```sh
docker compose up -d
./mvnw spring-boot:run
```

**Con la base provista:** copiá `.env.example` a `.env`, completá la conexión y corré `./mvnw spring-boot:run`. El `.env` está en `.gitignore` y nunca se commitea. Si existe, tiene prioridad sobre la base local.

Al arrancar, la aplicación aplica las migraciones pendientes, **también sobre la base provista**: la primera vez le agrega índices y columnas, mueve los movimientos de pesos de `orders` a la tabla `transfers` y crea la tabla `flyway_schema_history`. Usá la conexión directa a la base, no un pooler en modo transacción (por ejemplo, un host con `-pooler`): las migraciones necesitan una sesión estable.

La API queda en `http://localhost:8080`. Los logs salen como texto legible; con `--spring.profiles.active=json` salen como JSON (ECS), para una plataforma de logs.

**Imagen Docker:** el `Dockerfile` arma una imagen lista para producción. La configuración entra solo por variables de entorno.

```sh
docker build -t brokerage-api .
docker run --rm -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://<host>:5432/brokerage \
  -e DB_USERNAME=<usuario> -e DB_PASSWORD=<clave> \
  brokerage-api
```

CI la construye en cada pull request y, en `main`, la publica en `ghcr.io/sebastiancuccorese/brokerage-api` con los tags `latest` y el SHA del commit. Los probes (`/actuator/health/liveness` y `/actuator/health/readiness`) y las métricas (`/actuator/prometheus`) se sirven en el puerto 8081, no en el 8080 de la API; para verlos desde el host agregá `-p 8081:8081`.

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
| POST | `/orders` | Envía una orden de compra o venta, de mercado o límite |
| GET | `/orders/{orderId}` | Devuelve una orden |
| POST | `/orders/{orderId}/cancel` | Cancela una orden abierta (`NEW`) |
| POST | `/users/{userId}/transfers` | Deposita o retira pesos |
| GET | `/users/{userId}/transfers` | Transferencias del usuario, de la más nueva a la más vieja |
| GET | `/users/{userId}/transfers/{transferId}` | Devuelve una transferencia del usuario |

`POST /orders` acepta un header opcional `Idempotency-Key`: un reintento con la misma clave devuelve la misma orden en lugar de enviar otra ([Idempotencia](docs/decisions.md#idempotencia)).

Documentación interactiva de la API: <http://localhost:8080/swagger-ui.html>

Hay ejemplos listos para ejecutar en [`http/`](http/) (REST Client de VS Code o IntelliJ).

## Uso de IA

El proyecto se construyó con agentes de IA bajo las reglas de [AGENTS.md](AGENTS.md): las decisiones son humanas, cada funcionalidad va en su propia branch y PR, y todo cambio llega con sus tests.
