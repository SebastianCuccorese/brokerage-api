# Evolución

Lo que se dejó afuera a propósito, y cómo se haría. Cada punto dice por qué no se hizo ahora. Las decisiones tomadas están en [decisions.md](decisions.md). · [English](evolution.en.md)

## Tabla de posiciones

Hoy el portfolio recorre todas las órdenes del usuario en cada consulta. Con un historial largo eso deja de escalar.

- **Cómo:** una tabla `positions` (usuario, instrumento, cantidad, costo) que se actualiza en la misma transacción que ejecuta la orden, bajo el mismo bloqueo del usuario. El portfolio la lee directo, y recorrer las órdenes queda como reconstrucción o verificación.
- **Por qué no ahora:** con estos volúmenes recorrer es instantáneo, y el enunciado pide calcular desde las órdenes. El cambio es local: la lectura cambia de fuente y la escritura suma un paso.

## Métricas y logs de negocio

- **Cómo:** contadores como `orders.placed{side,type,status}` y `transfers.placed{type,status}` en Prometheus, y un evento en INFO por cada orden o transferencia (id, usuario, resultado y motivo), sin datos personales.
- **Por qué no ahora:** la base técnica (métricas de la JVM y HTTP, request id) ya está. Lo de negocio no lo pide el enunciado.

## Autenticación y dueño de los recursos

- **Cómo:** la API como resource server OAuth2 con JWT. El usuario sale del token, no del cuerpo ni de la ruta, y cancelar o leer una orden o una transferencia verifica que sea suya.
- **Por qué no ahora:** el enunciado dice que no hace falta autenticación. Pedir un `userId` al cliente para "verificar" daría una seguridad que no existe.

## Cuentas con cotitulares

- **Cómo:** una tabla `accounts`, otra `account_holders` (usuario, cuenta y rol), órdenes y transferencias por cuenta, y el bloqueo sobre la cuenta en lugar del usuario.
- **Por qué no ahora:** hoy la cuenta es una columna de `users`, en relación 1:1, y nada del enunciado pide compartirla.

## Ejecución de órdenes límite

- **Cómo:** un proceso que, con cada precio nuevo, ejecuta las órdenes `NEW` cuyo límite se cruza, con soporte para ejecuciones parciales (cantidad ejecutada aparte de la pedida).
- **Por qué no ahora:** el enunciado pide no simular el mercado.

## Idempotencia

- **Cómo:** un header `Idempotency-Key` en `POST /orders` y en las transferencias. Un reintento con la misma clave devuelve el resultado original en lugar de crear otra orden.
- **Por qué no ahora:** es lo primero que se agregaría para clientes reales (un timeout no debería duplicar una compra), pero excede el challenge.

## Búsqueda y listados

- **Cómo:** búsqueda sin acentos con la extensión `unaccent` y un índice `pg_trgm`; paginación en la búsqueda y en los historiales; un `GET /users/{id}/orders`.
- **Por qué no ahora:** el catálogo tiene 66 instrumentos y los historiales son cortos.

## Varias monedas

- **Cómo:** una columna de moneda en precios, órdenes y transferencias, y conversiones con JSR 354 (Moneta) cuando haga falta convertir. El resto del código ya trabaja con `Money`.
- **Por qué no ahora:** todo está en pesos, como pide el enunciado.

## Imagen y despliegue

- **Cómo:** un runtime recortado con `jlink` (la imagen pasa de ~400 MB a bastante menos), Swagger apagado en producción con un perfil, y la base con credenciales temporales en lugar de usuario y contraseña fijos.
- **Por qué no ahora:** la imagen actual ya es reproducible, corre sin root y se escanea en cada PR.
