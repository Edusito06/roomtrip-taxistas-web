# Esquema de base de datos

Entregable "DDL, DML y esquema de la base de datos" del plan de proyecto. La persistencia es
**MongoDB** (NoSQL, RES-04 y Unidad 3 del curso), así que no hay DDL en sentido SQL: las colecciones
se crean al primer uso y los índices los crea Spring Data al arrancar
(`spring.data.mongodb.auto-index-creation: true`). Aquí se documentan las colecciones, sus campos,
los índices y los datos iniciales (el equivalente del DML).

Dos bases lógicas dentro del mismo clúster (*database per service*, ver `ARQUITECTURA.md` sección 2).
Ningún servicio lee la base del otro; se relacionan solo por identificadores.

```
taxi_registry  (taxista-service)          taxi_ratings  (calificacion-service)
├─ taxistas                               └─ calificaciones
├─ administradores
├─ servicios
└─ eventos
```

Las referencias entre colecciones son por id (sin claves foráneas):

```
servicios.taxistaId ───────────▶ taxistas._id          (misma base)
calificaciones.taxistaId ──────▶ taxistas._id          (otra base: se valida por HTTP, no por Mongo)
calificaciones.servicioTaxiId ─▶ servicios._id         (otra base: ídem)
eventos.sujetoId ──────────────▶ taxistas._id o servicios._id, según el tipo de evento
```

---

## Base `taxi_registry`

### `taxistas`

Documento raíz del subsistema (RF-WTX-002, 003, 004, 007).

| Campo | Tipo | Notas |
|---|---|---|
| `_id` | ObjectId | |
| `nombres`, `apellidos` | string | |
| `tipoDocumento` | string | `DNI`, `CE` o `PASAPORTE` |
| `numeroDocumento` | string | **único** |
| `fechaNacimiento` | fecha (`yyyy-MM-dd`) | Obligatoria en el registro |
| `correo` | string | **único**, en minúsculas |
| `telefono`, `domicilio` | string | |
| `passwordHash` | string | **BCrypt**; nunca en claro |
| `fotoUrl`, `fotoVehiculoUrl` | string | URL de Cloudinary; no se guardan binarios en Mongo |
| `vehiculoMarca`, `vehiculoModelo`, `vehiculoAnio`, `vehiculoColor` | string | |
| `placa` | string | **única**, en mayúsculas |
| `estadoHabilitacion` | enum | `PENDIENTE`, `APROBADO`, `RECHAZADO`, `INHABILITADO` |
| `estadoDisponibilidad` | enum | `DISPONIBLE`, `EN_SERVICIO`, `NO_DISPONIBLE` |
| `motivoRechazo`, `motivoInhabilitacion` | string | Opcionales |
| `valoracionPromedio`, `totalCalificaciones` | double, long | **Copia de solo lectura**; la fuente es `taxi_ratings` |
| `ubicacionLatitud`, `ubicacionLongitud` | double | Última posición; `null` si nunca reportó |
| `ubicacionFecha` | instante UTC | |
| `fechaRegistro`, `fechaActualizacion` | instante UTC | |
| `decididoPor` | string | id del Superadmin que decidió la solicitud |

Índices: `numeroDocumento` (único), `correo` (único), `placa` (única).

### `administradores`

Usuarios administrativos (RF-WTX-009): el Superadmin y la credencial de servicio de la app móvil.

| Campo | Tipo | Notas |
|---|---|---|
| `_id` | ObjectId | |
| `correo` | string | **único** |
| `passwordHash` | string | BCrypt |
| `rol` | enum | `SUPERADMIN` o `APP_MOVIL` |

Índices: `correo` (único).

### `servicios`

Pedido de taxi hotel → aeropuerto y su ciclo de vida (ver `ARQUITECTURA.md` 3.2).

| Campo | Tipo | Notas |
|---|---|---|
| `_id` | ObjectId | |
| `version` | long | `@Version`: bloqueo optimista para que solo un taxista acepte el pedido |
| `estado` | enum | `SOLICITADO`, `ASIGNADO`, `EN_CAMINO`, `EN_TRASLADO`, `FINALIZADO` |
| `hotelId`, `hotelNombre` | string | |
| `clienteId` | string | id del cliente en la app móvil |
| `aeropuertoDestino` | string | |
| `taxistaId` | string | `null` hasta que se acepta el pedido |
| `qrCodigo` | string | UUID aleatorio; el taxista lo valida para finalizar; no se expone al taxista |
| `fechaSolicitud`, `fechaAsignacion`, `fechaFinalizacion`, `fechaActualizacion` | instante UTC | |

Índices: `taxistaId`.

### `eventos`

Log de eventos relevantes (auditoría). Solo se inserta y se lee; nunca se edita.

| Campo | Tipo | Notas |
|---|---|---|
| `_id` | ObjectId | |
| `fecha` | instante UTC | |
| `tipo` | string | `TAXISTA_REGISTRADO`, `TAXISTA_APROBADO`, `TAXISTA_RECHAZADO`, `TAXISTA_INHABILITADO`, `TAXISTA_REACTIVADO`, `DISPONIBILIDAD_CAMBIADA`, `LOGIN_OK`, `LOGIN_FALLIDO`, `LOGIN_BLOQUEADO`, `SERVICIO_SOLICITADO`, `SERVICIO_ASIGNADO`, `SERVICIO_EN_CAMINO`, `SERVICIO_EN_TRASLADO`, `SERVICIO_FINALIZADO` |
| `actor` | string | Quién lo hizo (id del usuario o correo en eventos de login) |
| `sujetoId` | string | Sobre qué recurso (taxista o servicio) |
| `detalle` | string | |

Índices: `fecha`.

---

## Base `taxi_ratings`

### `calificaciones`

Una calificación por servicio prestado (RF-WTX-006, RN-013).

| Campo | Tipo | Notas |
|---|---|---|
| `_id` | ObjectId | |
| `taxistaId` | string | Referencia lógica a `taxi_registry.taxistas` |
| `servicioTaxiId` | string | **único**: un servicio se califica una sola vez (hace idempotente el reintento de la app) |
| `puntuacion` | int | De 1 a 5 |
| `observacion` | string | Opcional |
| `fecha` | instante UTC | |

Índices: `taxistaId`, `servicioTaxiId` (único).

La valoración promedio no se guarda aquí: se calcula como media de `puntuacion` por `taxistaId`,
redondeada a dos decimales, y se empuja a `taxistas.valoracionPromedio` tras cada calificación.

---

## Equivalente en `mongosh` (para quien prefiera ver el DDL explícito)

Spring Data crea esto solo; se incluye como referencia y por si hubiera que recrear las bases a mano.

```javascript
use taxi_registry
db.taxistas.createIndex({ numeroDocumento: 1 }, { unique: true })
db.taxistas.createIndex({ correo: 1 }, { unique: true })
db.taxistas.createIndex({ placa: 1 }, { unique: true })
db.administradores.createIndex({ correo: 1 }, { unique: true })
db.servicios.createIndex({ taxistaId: 1 })
db.eventos.createIndex({ fecha: 1 })

use taxi_ratings
db.calificaciones.createIndex({ taxistaId: 1 })
db.calificaciones.createIndex({ servicioTaxiId: 1 }, { unique: true })
```

## Datos iniciales (DML)

El sistema necesita dos cuentas para poder usarse por primera vez. **No se insertan a mano**: las crea
`CargaInicial` al arrancar `taxista-service`, con la contraseña hasheada en BCrypt, a partir de estas
variables de entorno (en la nube, secretos de Secret Manager):

| Cuenta | Correo (variable) | Contraseña (variable) | Rol |
|---|---|---|---|
| Superadmin | `SUPERADMIN_CORREO` | `SUPERADMIN_PASSWORD` | `SUPERADMIN` |
| App móvil | `APP_MOVIL_CORREO` | `APP_MOVIL_PASSWORD` | `APP_MOVIL` |

El resultado equivale a este documento (el hash es un ejemplo, no una contraseña real):

```javascript
db.administradores.insertMany([
  { correo: "superadmin@roomtrip.pe", passwordHash: "$2a$10$...", rol: "SUPERADMIN" },
  { correo: "app-movil@roomtrip.pe",  passwordHash: "$2a$10$...", rol: "APP_MOVIL" }
])
```

Si la contraseña de la variable cambia, al reiniciar el servicio se actualiza el hash de la cuenta
existente. Con `EXIGIR_CLAVES_FUERTES=true` el servicio no arranca con contraseñas por defecto o de
menos de 12 caracteres.

Los taxistas, servicios y calificaciones **no tienen datos iniciales**: los crea el uso del sistema (el
autoregistro desde el portal y las llamadas de la app).
