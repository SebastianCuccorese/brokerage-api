# Evolución

Cómo crecería el sistema a medida que aumentan los usuarios, el volumen y las exigencias. Lo que ya está construido, y por qué, está en las [decisiones de negocio](decisions.md) y las [técnicas](technical.md). · [English](evolution.en.md)

## Tabla de posiciones

Hoy el portfolio recorre todas las órdenes del usuario en cada consulta, y con un historial largo eso deja de escalar. El paso es materializar las posiciones en una tabla `positions` (usuario, instrumento, cantidad, costo), y hay dos formas de mantenerla:

- **En la misma transacción** que ejecuta la orden, bajo el bloqueo del usuario. El portfolio siempre está al día, a cambio de un paso más en cada escritura.
- **De forma asíncrona:** la orden ejecutada publica un evento (`OrderFilled`) que un handler de Spring (`@TransactionalEventListener` después del commit) o un consumidor de una cola aplica sobre la tabla. Para no perder eventos si el proceso cae, se usa el patrón outbox: el evento se guarda en la misma transacción que la orden y se publica después. La escritura queda liviana y el portfolio pasa a ser *eventualmente consistente*.
- En los dos casos, **la validación de saldo no lee la proyección**: sigue decidiendo sobre la fuente de verdad, con el usuario bloqueado, para que una proyección atrasada nunca habilite a gastar dos veces.

## Métricas y logs de negocio

- Contadores como `orders.placed{side,type,status}` y `transfers.placed{type,status}` en Prometheus, que muestran el volumen y la tasa de rechazos por motivo.
- Un evento en INFO por cada orden o transferencia (id, usuario, resultado y motivo), sin datos personales, que se correlaciona con el request id que ya viaja en cada línea.

## Autenticación y dueño de los recursos

- La API como resource server OAuth2 con JWT. El usuario sale del token, no del cuerpo ni de la ruta.
- Cancelar o leer una orden o una transferencia verifica que sea de quien la pide.

## Cuentas con cotitulares

- Una tabla `accounts` y otra `account_holders` (usuario, cuenta y rol). Las órdenes y las transferencias pasan a ser de la cuenta, y el bloqueo de consistencia pasa del usuario a la cuenta.

## Ejecución de órdenes límite

- Un proceso que, con cada precio nuevo, ejecuta las órdenes `NEW` cuyo límite se cruza, con ejecuciones parciales (cantidad ejecutada aparte de la pedida). Encaja con el flujo de eventos de la tabla de posiciones: cada ejecución es un `OrderFilled` más.

## Varias monedas

- Una columna de moneda en precios, órdenes y transferencias, y conversiones con JSR 354 (Moneta). El resto del código ya trabaja con `Money` y rechaza mezclar monedas.

## Imagen y despliegue

- Un runtime recortado con `jlink`, para bajar los ~400 MB actuales de la imagen.
- Swagger apagado en producción con un perfil.
- Credenciales temporales para la base en lugar de un usuario y una contraseña fijos.
