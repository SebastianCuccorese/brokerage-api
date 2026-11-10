# Decisiones de diseño

Por qué el proyecto está hecho como está. Cómo levantarlo está en el [README](../README.md). · [English](decisions.en.md)

## Arquitectura

El sistema tiene dos mitades con necesidades distintas, y cada una usa el estilo que le sirve. Enviar y cancelar órdenes es un problema de reglas (estados, rechazos, saldo), así que se modela con DDD. El portfolio y la búsqueda son lecturas: se calculan a partir de los datos, sin pasar por el modelo de dominio.

- **Escritura (DDD):** la orden es un agregado que controla sus propias transiciones (solo se cancela desde `NEW`), y el saldo se valida con el usuario bloqueado, para que dos órdenes simultáneas no gasten el mismo dinero.
- **Lectura (proyección):** el portfolio se recalcula desde las órdenes ejecutadas, con costo promedio exacto.
- **Sin capa de dominio aislada:** las entidades JPA son el modelo. Separarlas de Spring agregaría puertos y mappers sin beneficio a este tamaño.
- **Dos módulos:** `market` (instrumentos y precios) y `trading` (usuarios, órdenes y portfolio). `trading` usa `market`, nunca al revés.

## Base de datos

El esquema provisto es la versión 1. Al arrancar, la aplicación toma cualquier base sin historial como versión 1 y aplica las migraciones pendientes (Flyway), así que la base local, los tests y la base provista siguen el mismo camino. Hibernate solo valida que las entidades coincidan con las tablas.

- **Consecuencia:** el primer arranque contra la base provista la modifica: agrega índices y, más adelante, columnas, y crea la tabla `flyway_schema_history`.
- **Migraciones** en `src/main/resources/db/migration`. `db/01-database.sql` queda como el estado provisto: la base local y la de tests lo cargan al crearse.
- **Misma versión que la base provista:** Postgres 18.6 en la base local y en los tests, para que lo que se prueba sea lo que corre.

## Dinero

Todo importe es un `Money` (moneda + monto), nunca un número suelto. Hoy solo hay pesos (ARS), como pide el enunciado, pero el modelo admite otras monedas sin cambiar ninguna firma.

- **Precisión:** `BigDecimal` en todos los cálculos. Se redondea una sola vez, al responder, a los decimales de la moneda (half-even).
- **Sin mezclas:** combinar monedas distintas es un error, nunca una conversión implícita.
- **Base de datos:** las columnas de precio no guardan la moneda y se leen como ARS. Para soportar otra moneda habría que agregar esa columna.

## Búsqueda de instrumentos

Busca el texto en el ticker o en el nombre, sin distinguir mayúsculas, y ordena por ticker.

- El peso no aparece: es la caja de la cuenta, no un activo que se compre.
- Los tipos se exponen en inglés (`STOCK`, `CURRENCY`). Un converter traduce los valores de la base (`ACCIONES`, `MONEDA`), así el vocabulario de la tabla no se filtra al modelo.
- `%` y `_` se buscan como texto literal, no como comodines.
- Es una proyección directa a la respuesta. No hay reglas que proteger, así que no pasa por un servicio.
- Mejora posible: ignorar acentos ("compania" encontraría "Compañía") con la extensión `unaccent` de Postgres.

## Portfolio

Se calcula recorriendo las órdenes del usuario en orden cronológico: las ejecutadas mueven pesos y acciones, y las abiertas (`NEW`) solo los reservan.

- **Pesos disponibles:** el cash menos lo comprometido en compras límite abiertas, porque eso ya no se puede usar para operar. El valor total sí lo incluye, porque sigue siendo del usuario.
- **Rendimiento:** el total se mide contra el costo promedio exacto, que no cambia con una venta parcial. El diario compara `close` con `previousClose`. Ambos van en porcentaje.
- **El seed vende más de lo que tiene:** la orden 11 vende 30 BMA con 20 en cartera. El portfolio lo muestra como una posición de −10 (un corto, que gana si el precio baja) para coincidir con las órdenes. Se evaluó permitir ventas en corto con una cuenta de margen y se descartó: el enunciado pide validar que el usuario tenga las acciones, y además requeriría préstamo de títulos y garantías.
- **Sin precio:** si un instrumento en cartera no tiene market data, su valor y sus rendimientos vienen en `null` y no suman al total.
- **Evolución hacia cuentas:** hoy la cuenta es una columna de `users`. Para cotitulares haría falta una tabla `accounts` con sus titulares, las órdenes por cuenta y el bloqueo sobre la cuenta. No se implementa porque nada del enunciado lo pide.

## Órdenes

Una orden de mercado se ejecuta al último `close` y queda `FILLED`. Una límite queda `NEW` a su precio y reserva pesos o acciones hasta que se cancela: el mercado no se simula, así que nada la cruza contra un precio. Con un monto en pesos se compran solo acciones enteras.

- **Dos tipos de error:** un pedido que no tiene sentido (JSON roto, cantidad y monto a la vez, una orden límite sin precio, mover pesos) es un 400 y no guarda nada. Una orden válida que el usuario no puede cubrir se guarda como `REJECTED` y responde 201 con su `rejectionReason` (`INSUFFICIENT_CASH`, `INSUFFICIENT_SHARES` contando las ventas abiertas, `AMOUNT_BELOW_ONE_SHARE` o `NO_MARKET_DATA`), porque así la describe el enunciado: el mercado la rechaza y queda en el historial. Un monto que no alcanza para una acción es un rechazo y no un 400, porque depende del precio de ese momento.
- **El motivo se guarda** en la columna `rejectionReason` (migración V3), para poder auditar los rechazos; es `null` en las demás órdenes.
- **Un usuario a la vez:** el envío bloquea la fila del usuario antes de calcular su saldo, así dos órdenes simultáneas no gastan los mismos pesos. La cancelación bloquea la orden, para que dos cancelaciones no la vean abierta a la vez.
- **Hora en UTC:** `datetime` es un instante (`timestamptz`) y la API lo devuelve en ISO-8601 UTC (`2026-11-10T14:30:00Z`). Las órdenes provistas tenían la hora de Buenos Aires sin zona (de 11:13 a 16:11, el horario del mercado local); la migración V4 las convirtió una sola vez, sin cambiar su orden. La hora sale de un único `Clock`, que los tests fijan.
- **Cancelar no verifica al dueño:** no se comprueba que la orden sea de quien lo pide, porque no hay autenticación, y un `userId` enviado por el cliente solo parecería seguridad.

## Errores

Toda respuesta de error es un problem detail (RFC 9457) con un `code` estable y el `requestId` del pedido. El dominio lanza excepciones que llevan su `code` pero no saben qué es HTTP; un único `@RestControllerAdvice` convierte cada categoría en un estado. Así el cliente decide por el `code`, y soporte encuentra los logs con el `requestId`.

- **Categorías:** `NotFoundException` (404), `InvalidRequestException` (400) y `ConflictException` (409), todas con un `ErrorCode` (`ORDER_NOT_FOUND`, `ORDER_NOT_CANCELLABLE`, `SIZE_OR_AMOUNT_REQUIRED`...). Un código nunca se renombra ni cambia de significado; el texto sí puede cambiar.
- **Validación:** un 400 `VALIDATION_FAILED` con una lista `errors` de `{field, message}`, para que el cliente marque el campo. Un JSON roto es `MALFORMED_REQUEST` y no repite el mensaje de Jackson.
- **Errores del framework** (método no permitido, ruta inexistente) conservan su estado y suman el `requestId`, sin `code`.
- **Un 500 nunca filtra detalles:** responde `INTERNAL_ERROR` con un texto genérico que pide citar el `requestId`; el mensaje y el stack quedan solo en el log, en nivel ERROR.

## Logs

Cada request lleva un id (`X-Request-Id`) que aparece en todas sus líneas de log y se devuelve en la respuesta, para seguir un pedido de punta a punta. Se registran los eventos de negocio a nivel INFO (llegan con las órdenes); nunca emails ni secretos.

- **Id de request:** se toma el que manda el cliente solo si son 1 a 64 caracteres de `[A-Za-z0-9._-]`; si no, se genera un UUID. Se valida para que un cliente no pueda inyectar saltos de línea o texto falso en los logs.
- **Local:** texto legible, con el id entre corchetes en cada línea.
- **Plataforma de logs:** el perfil `json` emite JSON en formato ECS (el soporte de Spring Boot), con el id como campo `requestId`.
- **Sin tracing distribuido:** con un solo servicio no aporta lo que cuesta.

## Despliegue

La aplicación se despliega como una imagen de contenedor. Está en capas para que un cambio de código solo reconstruya la última y el resto salga de la caché.

- **Capas:** dependencias, loader, snapshots y código, de la que menos cambia a la que más.
- **Runtime:** solo JRE, con un usuario sin privilegios y heap según el límite de memoria del contenedor.
- **Configuración:** solo por variables de entorno (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`); la imagen no lleva secretos.
- **Probes:** `/actuator/health/liveness` y `/actuator/health/readiness` para el orquestador, en el puerto de gestión (ver [Observabilidad](#observabilidad)).
- **CI:** la imagen se construye en cada PR y se publica desde `main`. Se escanea con Trivy y falla ante vulnerabilidades críticas con arreglo disponible.
- **Posible mejora:** un runtime recortado con jlink para reducir el tamaño de la imagen.

## Observabilidad

La aplicación publica sus métricas en formato Prometheus (`/actuator/prometheus`), el estándar de facto para scraping, sin código propio de métricas.

- **Qué se mide:** JVM, peticiones HTTP (`http.server.requests`), pool de conexiones Hikari y Tomcat, todos con el tag `application=brokerage-api`.
- **Puerto de gestión:** Actuator se sirve en el 8081 y la API en el 8080. Así las métricas y los probes no quedan expuestos a internet por el ingress de la API; solo los alcanza quien está dentro de la red.
- **Liveness y readiness:** liveness dice si el proceso debe reiniciarse y readiness si puede recibir tráfico (incluye la base). Liveness no depende de la base: una caída de la base no debe provocar el reinicio del pod.
- **Negocio:** las métricas de órdenes llegan con el paso de logging de órdenes.
