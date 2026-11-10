# Decisiones de diseño

Por qué el proyecto está hecho como está. Cómo levantarlo está en el [README](../README.md). · [English](decisions.en.md)

## Arquitectura

El sistema tiene dos mitades con necesidades distintas, y cada una usa el estilo que le sirve. Enviar y cancelar órdenes es un problema de reglas (estados, rechazos, saldo), así que se modela con DDD. El portfolio y la búsqueda son lecturas: se calculan a partir de los datos, sin pasar por el modelo de dominio.

- **Escritura (DDD):** la orden es un agregado que controla sus propias transiciones (solo se cancela desde `NEW`), y el saldo se valida con el usuario bloqueado, para que dos órdenes simultáneas no gasten el mismo dinero.
- **Lectura (proyección):** el portfolio se recalcula desde las órdenes ejecutadas, con costo promedio exacto.
- **Sin capa de dominio aislada:** las entidades JPA son el modelo. Separarlas de Spring agregaría puertos y mappers sin beneficio a este tamaño.
- **Dos módulos:** `market` (instrumentos y precios) y `trading` (usuarios, órdenes y portfolio). `trading` usa `market`, nunca al revés.

## Base de datos

El esquema provisto alcanza tal como está. La aplicación no lo crea ni lo modifica: Hibernate solo valida que las entidades coincidan con las tablas.

- **Índices propuestos** en `db/02-indexes.sql`, para las consultas que hace la API (órdenes por usuario y estado, último precio por instrumento). Se aplican en la base local y en los tests. En la base provista se aplican a mano, porque la aplicación no modifica una base que no es suya.
- **Sin herramienta de migraciones:** con un solo script de índices no hace falta. Si el esquema empezara a cambiar seguido, el paso natural es Flyway.

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

Una orden de mercado se ejecuta al último `close` y queda `FILLED`; una límite queda `NEW` a su precio. Con un monto en pesos se compran solo acciones enteras. Se distinguen dos tipos de error: un pedido que no tiene sentido (JSON roto, cantidad y monto a la vez, una orden límite sin precio, mover pesos) es un 400 y no guarda nada; una orden válida que el usuario no puede cubrir se guarda como `REJECTED`, porque así la describe el enunciado: el mercado la rechaza y queda en el historial.

- **Rechazos con 201:** la orden rechazada existe, así que la respuesta es 201 con `Location`, `status: REJECTED` y un `rejectionReason`: `INSUFFICIENT_CASH`, `INSUFFICIENT_SHARES` (las ventas abiertas cuentan), `AMOUNT_BELOW_ONE_SHARE` o `NO_MARKET_DATA`.
- **El motivo no se guarda:** solo viene en la respuesta del envío; al leer la orden después es `null`. Guardarlo pide una columna nueva, y como Hibernate valida el esquema, la aplicación no arrancaría contra la base provista hasta que alguien aplique el script. Queda como evolución: un script en `db/` con la columna y el campo en la entidad.
- **Un usuario a la vez:** el envío bloquea la fila del usuario antes de calcular su saldo, así dos órdenes simultáneas se deciden una después de la otra y no gastan los mismos pesos. La cancelación bloquea la orden, para que dos cancelaciones no la vean abierta a la vez.
- **Hora local:** `datetime` se guarda en hora de Buenos Aires y sin zona, como las órdenes provistas (de 11:13 a 16:11, el horario del mercado local). El reloj se inyecta, así los tests fijan la hora.
- **Las órdenes límite no se ejecutan:** quedan `NEW` y reservan pesos o acciones hasta que se cancelan. El mercado no se simula, así que nada las cruza contra un precio.
