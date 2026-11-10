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

## Despliegue

La aplicación se despliega como una imagen de contenedor. Está en capas para que un cambio de código solo reconstruya la última y el resto salga de la caché.

- **Capas:** dependencias, loader, snapshots y código, de la que menos cambia a la que más.
- **Runtime:** solo JRE, con un usuario sin privilegios y heap según el límite de memoria del contenedor.
- **Configuración:** solo por variables de entorno (`DB_URL`, `DB_USERNAME`, `DB_PASSWORD`); la imagen no lleva secretos.
- **Probes:** `/actuator/health/liveness` y `/actuator/health/readiness` para el orquestador; es lo único que se expone de Actuator.
- **CI:** la imagen se construye en cada PR y se publica desde `main`. Se escanea con Trivy y falla ante vulnerabilidades críticas con arreglo disponible.
