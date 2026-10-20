# API REST para la app Android

Guía para quien escribe el cliente HTTP de la app (Retrofit). Todo pasa por el **gateway**; la app
nunca toca MongoDB (RES-04). Los cuerpos son JSON y los nombres de campo van en español, tal cual
aparecen aquí.

- **URL base en local:** `http://localhost:8080`. Desde el emulador de Android es `http://10.0.2.2:8080`.
- **URL base en producción:** la del `gateway-service` en Cloud Run (la imprime `scripts/deploy-cloudrun.sh`).
- **Fechas:** ISO 8601 (`2026-10-20T15:52:20Z`; `fechaNacimiento` es solo fecha, `1992-03-14`).

---

## 1. Autenticación

Todos los endpoints, salvo `POST /api/auth/login` y `POST /api/taxistas/registro`, exigen el
encabezado `Authorization: Bearer <token>`.

### `POST /api/auth/login`

```json
{ "correo": "app-movil@roomtrip.pe", "password": "..." }
```

Respuesta `200`:

```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "tipo": "Bearer",
  "rol": "APP_MOVIL",
  "subject": "66f1c0...",
  "expiraEnSegundos": 7200
}
```

- `rol` es `SUPERADMIN`, `APP_MOVIL` o `TAXISTA`. `subject` es el id del usuario; para un taxista es
  el mismo `id` que se usa en `/api/taxistas/{id}`.
- El token dura 2 horas por defecto. Al recibir `401` hay que volver a iniciar sesión.
- Credencial incorrecta o taxista rechazado: `401` con mensaje. El mensaje no distingue entre "correo
  inexistente" y "contraseña errónea" a propósito.

### Qué rol usar para cada cosa

| Quién actúa en la app | Se autentica como | Para qué |
|---|---|---|
| Cliente / hotel (pedir taxi, ver estado, calificar) | `APP_MOVIL`, credencial de servicio de la app | Crear el pedido, consultar estado, ubicación y valoración, calificar |
| Taxista (aceptar pedidos, avanzar, finalizar) | `TAXISTA`, con **su propio** correo y contraseña del portal web | Aceptar, cambiar estado, reportar ubicación, validar el QR |
| Superadmin | `SUPERADMIN` | Aprobar taxistas, ver eventos |

> **Decisión que el equipo debe tomar:** las acciones del taxista exigen un token `TAXISTA`, es decir,
> que el taxista inicie sesión contra esta API con la contraseña con la que se registró en la web.
> Cuando entre Firebase Auth (Lab 6) habrá que decidir cómo se enlazan las dos identidades.
> La mejora prevista está descrita en `docs/ARQUITECTURA.md`.

---

## 2. Errores

Todos los errores de negocio tienen el mismo cuerpo:

```json
{
  "timestamp": "2026-10-20T15:52:20.509Z",
  "status": 409,
  "error": "REGLA_NEGOCIO",
  "mensaje": "El pedido ya fue tomado por otro taxista"
}
```

| Código | `error` | Significa |
|---|---|---|
| 400 | `DATOS_INVALIDOS` | Faltan campos o tienen formato inválido. Trae `campos`: `{ "domicilio": "El domicilio es obligatorio" }` |
| 401 | `NO_AUTORIZADO` | Sin token, token vencido o credenciales incorrectas |
| 403 | — | Token válido pero el rol (o el taxista) no puede hacer eso |
| 404 | `NO_ENCONTRADO` | No existe el recurso |
| 409 | `REGLA_NEGOCIO` | La operación viola una regla (estado inválido, ya calificado, etc.). El `mensaje` es apto para mostrar al usuario |
| 503 | `SERVICIO_NO_DISPONIBLE` | El gateway no logra hablar con el servicio (RF-TAX-018) |

### Cuando la API de taxistas no está disponible (RF-TAX-018, RNF-FIA-001)

Si el servicio de atrás falla, el gateway abre un circuit breaker y responde **`503`** al instante, con
cualquier método (GET, POST, PATCH, PUT):

```json
{
  "timestamp": "2026-10-20T15:52:20.509Z",
  "status": 503,
  "error": "SERVICIO_NO_DISPONIBLE",
  "servicio": "taxistas",
  "mensaje": "El servicio de gestion de taxistas no esta disponible en este momento. Intentalo nuevamente en unos minutos."
}
```

La app debe mostrar el `mensaje` **sin romper el resto de las reservas**. Un solo `try/catch` sobre este
código basta. Con Cloud Run, la primera llamada tras un rato inactivo puede tardar unos segundos
(arranque en frío); no es un error.

---

## 3. Taxistas

Objeto `Taxista` que devuelven estos endpoints (nunca incluye la contraseña):

```json
{
  "id": "66f1c0...",
  "nombres": "Juan", "apellidos": "Perez",
  "tipoDocumento": "DNI", "numeroDocumento": "45678912",
  "fechaNacimiento": "1990-05-20",
  "correo": "juan.taxi@mail.com", "telefono": "987654321",
  "domicilio": "Av. Larco 123, Miraflores",
  "fotoUrl": "https://res.cloudinary.com/.../foto.jpg",
  "vehiculoMarca": "Toyota", "vehiculoModelo": "Yaris", "vehiculoAnio": "2020", "vehiculoColor": "Blanco",
  "placa": "ABC-123",
  "fotoVehiculoUrl": "https://res.cloudinary.com/.../auto.jpg",
  "estadoHabilitacion": "APROBADO",
  "estadoDisponibilidad": "DISPONIBLE",
  "valoracionPromedio": 4.8,
  "totalCalificaciones": 14
}
```

- `estadoHabilitacion`: `PENDIENTE`, `APROBADO` o `RECHAZADO`. Solo un `APROBADO` presta servicios.
- `estadoDisponibilidad`: `DISPONIBLE`, `EN_SERVICIO` o `NO_DISPONIBLE`.

| Método y ruta | Rol | Para qué |
|---|---|---|
| `POST /api/taxistas/registro` | público | Autoregistro (RF-WTX-001/002). Lo usa el portal web. Queda `PENDIENTE` |
| `GET /api/taxistas/disponibles` | `APP_MOVIL`, `SUPERADMIN` | Taxistas `APROBADO` y `DISPONIBLE` (RF-API-001) |
| `GET /api/taxistas/{id}` | `APP_MOVIL`, `SUPERADMIN`, el propio taxista | Datos del taxista asignado: foto, vehículo, valoración |
| `PATCH /api/taxistas/{id}/disponibilidad` | `APP_MOVIL`, `SUPERADMIN`, el propio taxista | Cuerpo `{ "estado": "DISPONIBLE" }`. Un taxista no aprobado no puede ponerse disponible (409) |
| `PUT /api/taxistas/{id}/ubicacion` | `APP_MOVIL`, el propio taxista | Cuerpo `{ "latitud": -12.04, "longitud": -77.03 }`. **Solo si el taxista está `EN_SERVICIO`**, si no responde 409 |
| `GET /api/taxistas/{id}/ubicacion` | `APP_MOVIL`, `SUPERADMIN`, el propio taxista | Última posición para el mapa del cliente |

Respuesta de ubicación (`latitud` y `longitud` vienen `null` si aún no se reportó ninguna):

```json
{ "taxistaId": "66f1c0...", "latitud": -12.04, "longitud": -77.03, "actualizadaEn": "2026-10-20T15:52:20Z" }
```

La ubicación debe reportarse **periódicamente durante el servicio** (cada pocos segundos); el cliente la
consulta con `GET` en un intervalo similar para mover el marcador.

---

## 4. Servicio de taxi (hotel → aeropuerto)

### Estados

```
SOLICITADO ──aceptar──▶ ASIGNADO ──▶ EN_CAMINO ──▶ EN_TRASLADO ──(QR)──▶ FINALIZADO
```

El flujo es lineal: no se puede saltar un estado. `FINALIZADO` **solo** se alcanza validando el QR.

Objeto `Servicio`:

```json
{
  "id": "6712ab...",
  "estado": "ASIGNADO",
  "hotelId": "h1", "hotelNombre": "Hotel Miraflores",
  "clienteId": "c1",
  "aeropuertoDestino": "Aeropuerto Jorge Chavez",
  "taxistaId": "66f1c0...",
  "qrCodigo": "0a1b2c3d-...",
  "fechaSolicitud": "2026-10-20T15:50:00Z",
  "fechaAsignacion": "2026-10-20T15:51:00Z",
  "fechaFinalizacion": null
}
```

`qrCodigo` solo lo ven `APP_MOVIL` y `SUPERADMIN`. **Al taxista le llega `null`**: es lo que el cliente le
muestra al llegar, y el taxista lo lee con la cámara.

| Método y ruta | Rol | Qué hace |
|---|---|---|
| `POST /api/servicios` | `APP_MOVIL`, `SUPERADMIN` | Crea el pedido (`SOLICITADO`). Cuerpo: `{ "hotelId", "hotelNombre", "clienteId", "aeropuertoDestino" }`. Devuelve `201` con el `qrCodigo`; el hotel se lo envía al cliente |
| `GET /api/servicios/solicitados` | `APP_MOVIL`, `SUPERADMIN`, `TAXISTA` | Pedidos que se pueden aceptar |
| `GET /api/servicios/{id}` | `APP_MOVIL`, `SUPERADMIN`, el taxista asignado | Estado actual (el hotel y el cliente lo consultan para ver el avance) |
| `POST /api/servicios/{id}/aceptar` | `TAXISTA` | `SOLICITADO` → `ASIGNADO`. El taxista debe estar aprobado y `DISPONIBLE`; pasa a `EN_SERVICIO`. Si otro lo tomó antes: 409 |
| `PATCH /api/servicios/{id}/estado` | `TAXISTA` asignado | Cuerpo `{ "estado": "EN_CAMINO" }` o `{ "estado": "EN_TRASLADO" }`, en ese orden |
| `POST /api/servicios/{id}/finalizar` | `TAXISTA` asignado | Cuerpo `{ "qrCodigo": "<lo leído del QR>" }`. Solo desde `EN_TRASLADO` y con el QR correcto (409 si no). Pasa a `FINALIZADO` y libera al taxista (`DISPONIBLE`) |

Aceptar es exclusivo: si dos taxistas aceptan a la vez, uno gana y el otro recibe `409` con el mensaje
"El pedido ya fue tomado por otro taxista". La app debe refrescar la lista en ese caso.

### Flujo típico

1. Checkout con taxi: `POST /api/servicios` → se guarda el `id` y el `qrCodigo` (el hotel lo envía al cliente).
2. El taxista ve `GET /api/servicios/solicitados` y hace `POST .../aceptar`.
3. Cliente y hotel consultan `GET /api/servicios/{id}` y, con el `taxistaId`, `GET /api/taxistas/{id}` (datos y
   vehículo) y `GET /api/taxistas/{id}/ubicacion` (mapa).
4. El taxista avanza `EN_CAMINO` → `EN_TRASLADO` y reporta su ubicación con `PUT`.
5. Al llegar, el cliente muestra el QR, el taxista lo lee y llama a `POST .../finalizar`.
6. El cliente califica (sección 5).

---

## 5. Calificaciones y valoración

| Método y ruta | Rol | Qué hace |
|---|---|---|
| `POST /api/calificaciones` | `APP_MOVIL` | Registra la calificación del cliente (RF-TAX-017) |
| `GET /api/calificaciones/taxista/{id}/valoracion` | `APP_MOVIL`, `SUPERADMIN`, el propio taxista | Promedio y total (RN-013) |

Cuerpo del `POST`:

```json
{ "taxistaId": "66f1c0...", "servicioTaxiId": "6712ab...", "puntuacion": 5, "observacion": "Muy amable" }
```

- `puntuacion` de 1 a 5. Un servicio solo se puede calificar **una vez**; el segundo intento da `409`
  ("Ese servicio de taxi ya fue calificado"), así que reintentar es seguro.
- Respuesta de la valoración: `{ "taxistaId": "...", "promedio": 4.33, "totalCalificaciones": 3 }`. Un taxista
  sin calificaciones devuelve `0.0` y `0`, no un error.
- El promedio se refleja también en `valoracionPromedio` del objeto `Taxista`.

---

## 6. Eventos (solo Superadmin)

`GET /api/eventos` (rol `SUPERADMIN`) devuelve los últimos 200 eventos relevantes: registro, aprobación,
rechazo, cambios de disponibilidad, inicios de sesión (también los fallidos) y cada cambio de estado de un
servicio. La ubicación no se registra, porque es periódica.

```json
[ { "id": "...", "fecha": "2026-10-20T15:52:20Z", "tipo": "SERVICIO_FINALIZADO",
    "actor": "66f1c0...", "sujetoId": "6712ab...", "detalle": "QR validado" } ]
```

---

## 7. Esqueleto para Retrofit

```java
public interface RoomTripApi {
    @POST("api/auth/login")
    Call<TokenResponse> login(@Body LoginRequest req);

    @GET("api/taxistas/disponibles")
    Call<List<Taxista>> disponibles();

    @GET("api/taxistas/{id}")
    Call<Taxista> taxista(@Path("id") String id);

    @GET("api/taxistas/{id}/ubicacion")
    Call<Ubicacion> ubicacion(@Path("id") String id);

    @PUT("api/taxistas/{id}/ubicacion")
    Call<Ubicacion> reportarUbicacion(@Path("id") String id, @Body UbicacionRequest req);

    @POST("api/servicios")
    Call<Servicio> solicitarServicio(@Body SolicitarServicioRequest req);

    @GET("api/servicios/{id}")
    Call<Servicio> servicio(@Path("id") String id);

    @POST("api/servicios/{id}/aceptar")
    Call<Servicio> aceptar(@Path("id") String id);

    @PATCH("api/servicios/{id}/estado")
    Call<Servicio> avanzar(@Path("id") String id, @Body AvanzarRequest req);

    @POST("api/servicios/{id}/finalizar")
    Call<Servicio> finalizar(@Path("id") String id, @Body FinalizarRequest req);

    @POST("api/calificaciones")
    Call<Calificacion> calificar(@Body CalificacionRequest req);
}
```

Un `Interceptor` de OkHttp que agregue `Authorization: Bearer <token>` y otro punto central que traduzca
un `503` a un aviso (sin cerrar la pantalla de reservas) resuelven los dos requisitos transversales
de la app.

Al probar contra HTTP local desde Android 9+ hay que permitir tráfico no cifrado
(`android:usesCleartextTraffic="true"` o un `network_security_config`); en producción es HTTPS.
