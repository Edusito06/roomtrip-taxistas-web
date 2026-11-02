# Decisiones de arquitectura

Documento vivo. Cada sección explica una decisión, la alternativa que se descartó y el motivo.
Sirve de insumo para el entregable "Archivo de arquitectura" del plan de proyecto.

## 1. Por qué Spring Boot y no MERN

El sílabo del curso deja las dos puertas abiertas: la bibliografía obligatoria incluye
*Beginning Spring Boot 2* y *Pro MERN Stack*, y el Anexo D del ERS lo registra como el asunto
abierto A2. La decisión se tomó por tres razones concretas:

1. El equipo ya escribe Java para la app Android nativa. Un solo lenguaje significa que cualquiera
   puede entrar a cualquier parte del proyecto, que es lo que más pesa en un equipo de cinco
   personas con un solo ciclo de plazo.
2. IntelliJ IDEA aparece como herramienta del curso en el sílabo, junto a Android Studio. IntelliJ
   para un proyecto Node sería una elección rara; para Spring Boot es la elección natural.
3. La Unidad 5 del curso es "Introducción a los microservicios / Gateway y resiliencia /
   Despliegue de microservicios". Spring Cloud Gateway y Resilience4j son, literalmente, la
   implementación de referencia de esos tres temas. Sustentar el proyecto ante el jurado es más
   directo si el código usa los mismos nombres que las diapositivas.

Contraargumento honesto: MERN arranca más rápido y tiene más tutoriales en video. Si el equipo
tuviera experiencia previa en Node y ninguna en Spring, la decisión debería invertirse.

## 2. Por qué MongoDB Atlas y no Firestore

El RF-WTX-008 exige que este subsistema tenga base de datos propia e independiente de la que usa
la app móvil. Como la app móvil usa Firebase, usar Firestore aquí también haría confusa esa
separación ante el jefe de práctica, aunque técnicamente fueran proyectos distintos.

MongoDB además cierra la Unidad 3 del curso, que cubre NoSQL "con dos proveedores, MongoDB y
Firebase". Usar los dos en el mismo proyecto demuestra las dos rutas.

El tier M0 de Atlas da 512 MB y es gratis para siempre sin tarjeta, de sobra para un proyecto de
curso: el padrón de taxistas son decenas de documentos.

**Database per service.** `taxista-service` escribe en `taxi_registry` y `calificacion-service`
en `taxi_ratings`. Son dos bases lógicas dentro del mismo clúster M0 gratuito. Ninguno de los dos
servicios lee la base del otro; si necesitan un dato, se lo piden por HTTP. Es el patrón correcto
y no cuesta nada más.

## 3. Por qué este corte de microservicios

El subsistema tiene solo nueve requisitos funcionales. Partirlo en seis servicios sería
sobreingeniería evidente y el jurado lo notaría. Partirlo en cero servicios incumpliría la
restricción RES-10. El corte elegido son dos servicios de dominio más un gateway, y se justifica
por cómo crecen los datos:

- El **padrón de taxistas** es pequeño, estable y muy leído (cada solicitud de taxi lo consulta).
- Las **calificaciones** crecen sin límite, una por servicio prestado, y casi solo se escriben.

Son dos perfiles de carga distintos, que es la razón de libro para separar servicios. Separarlos
permite, por ejemplo, subir las instancias de `taxista-service` sin tocar el otro.

El `portal-web` es un cuarto deployable pero no es un microservicio de dominio: no tiene base de
datos y consume la misma API pública que la app Android. Eso es deliberado: si el portal funciona,
el contrato de la API está probado por dos clientes distintos y cualquier hueco aparece temprano.

## 3.1 Sincronizacion de valoracionPromedio

`Taxista.valoracionPromedio` es una copia de solo lectura, no la fuente de verdad (esa vive en
`taxi_ratings`, en `calificacion-service`). Cada `POST /api/calificaciones` recalcula el promedio
del taxista y lo empuja de inmediato con un `PATCH /api/taxistas/{id}/valoracion`, autenticado con
el rol `SERVICIO_INTERNO` de la seccion 5.

Es una llamada best-effort a proposito (`TaxistaClient.actualizarValoracion` atrapa cualquier error
y solo lo registra en el log): si taxista-service estuviera caido en ese instante, la calificacion
ya quedo guardada en su propia base y no hay razon para fallar esa operacion por un problema de
sincronizacion de una copia. El padron del Superadmin puede mostrar un promedio desactualizado por
unos segundos hasta el siguiente evento; es el mismo trade-off de consistencia eventual que ya
describe la seccion 2 para database-per-service.

## 3.2 El servicio de taxi vive en `taxista-service`

El plan de proyecto pide que la app consulte, mediante esta API, los taxistas disponibles, los datos
del taxista asignado, el estado del servicio y su ubicación. Para cumplirlo, el pedido de taxi
(`ServicioTaxi`, colección `servicios`) se guarda aquí y no en Firebase.

Por qué en este servicio y no en un tercero:

- **Dueño del dato que cambia a la vez.** Aceptar un pedido cambia el servicio (`ASIGNADO`) y la
  disponibilidad del taxista (`EN_SERVICIO`); finalizarlo los revierte. Si vivieran en servicios
  distintos habría que coordinar dos escrituras por HTTP. Aquí son dos documentos de la misma base.
- **RES-04.** La app Android nunca toca esta base: crea, consulta y avanza los servicios por REST.
  Que los pedidos vivan en el subsistema web es lo que hace verificable esa restricción.

```
SOLICITADO ──aceptar──▶ ASIGNADO ──▶ EN_CAMINO ──▶ EN_TRASLADO ──(QR)──▶ FINALIZADO
```

- **Flujo lineal.** No se puede saltar un estado; solo el taxista asignado avanza el servicio.
- **Aceptación exclusiva.** `ServicioTaxi` lleva un campo `@Version` (bloqueo optimista). Si dos
  taxistas aceptan el mismo pedido a la vez, el segundo guardado falla y recibe un 409 "el pedido ya
  fue tomado". No hace falta un bloqueo distribuido: es una sola base y una sola escritura.
- **El QR cierra el servicio.** Al crear el pedido se genera un `qrCodigo` aleatorio que el hotel
  envía al cliente. `FINALIZADO` solo se registra si el taxista presenta ese código desde
  `EN_TRASLADO`. El taxista nunca recibe el código en las respuestas: lo lee del QR del cliente.
- **Ubicación.** `PUT /api/taxistas/{id}/ubicacion` solo se acepta con el taxista `EN_SERVICIO`
  ("durante un servicio activo"). Se guarda solo la última posición dentro del documento del taxista;
  no es un historial. El cliente la consulta por polling con `GET`.

Decisión que el equipo de la app debe conocer: como los pedidos viven aquí, la app debe crear y
consultar el servicio por esta API y no duplicarlo en Firestore. El detalle del contrato está en
[`API.md`](API.md).

## 3.3 Habilitación del taxista

`EstadoHabilitacion` tiene cuatro valores:

| Estado | Cómo se llega | Puede prestar servicios |
|---|---|---|
| `PENDIENTE` | Autoregistro | No |
| `APROBADO` | El Superadmin aprueba la solicitud | Sí |
| `RECHAZADO` | El Superadmin rechaza la solicitud | No, y no puede iniciar sesión |
| `INHABILITADO` | El Superadmin desactiva a un aprobado | No, pero conserva sesión y perfil |

El plan de proyecto dice que el Superadmin "puede activar y desactivar" taxistas; `INHABILITADO` es
ese estado. Dos reglas, decididas con el equipo:

1. **No se desactiva a un taxista con un traslado en curso** (`ASIGNADO`, `EN_CAMINO` o
   `EN_TRASLADO`): responde 409 hasta que termine, para no dejar a un cliente sin taxista.
2. **Un inhabilitado deja de ser asignable** (no aparece en `/disponibles`, no acepta pedidos, no
   puede ponerse `DISPONIBLE`) pero **puede seguir entrando** a ver su perfil y el motivo. Quedó
   así a propósito: bloquearle el acceso entero le impediría enterarse de por qué.

Desactivar exige un motivo y reactivar lo devuelve a `APROBADO` con disponibilidad `NO_DISPONIBLE`.

## 3.4 Registro de eventos

El requerimiento de calidad "el sistema deberá registrar eventos relevantes" y la función del
Superadmin "ver los logs de los eventos sucedidos" se resuelven con una colección `eventos` de solo
escritura y lectura (nunca se edita) y `GET /api/eventos` para el Superadmin.

Se registran: `TAXISTA_REGISTRADO`, `TAXISTA_APROBADO`, `TAXISTA_RECHAZADO`, `TAXISTA_INHABILITADO`,
`TAXISTA_REACTIVADO`, `DISPONIBILIDAD_CAMBIADA`, `LOGIN_OK`, `LOGIN_FALLIDO`, `LOGIN_BLOQUEADO` y los
cambios de servicio (`SERVICIO_SOLICITADO`, `SERVICIO_ASIGNADO`, `SERVICIO_EN_CAMINO`,
`SERVICIO_EN_TRASLADO`, `SERVICIO_FINALIZADO`).

Dos decisiones: el evento se registra **en el controlador**, donde se conoce al actor (el sujeto del
JWT), y no en la capa de negocio; y **la ubicación no se registra**, porque es periódica y llenaría el
log. El portal muestra los últimos 200 eventos.

## 4. Por qué el frontend es server-side

Una SPA en React se vería más moderna, pero agrega un segundo despliegue, configuración de CORS,
manejo de tokens en el navegador y un build de JS que mantener. Para nueve requisitos que son
básicamente dos formularios y una tabla, Thymeleaf entrega lo mismo con una fracción del riesgo,
y el token vive en la sesión del servidor en lugar de quedar expuesto a scripts en la página.

El RNF-POR-004 solo pide que funcione en Chrome, Edge y Firefox. Bootstrap 5 lo resuelve.

**Sesión del portal.** El token JWT vive en la sesión del servidor, no en el navegador. La sesión
expira tras **30 minutos de inactividad** (`SESSION_TIMEOUT`); al expirar, la siguiente página privada
redirige a `/login?expirada`, que muestra el aviso. La cookie es `HttpOnly` y `SameSite=Lax`. Las
páginas dinámicas se envían con `Cache-Control: no-store`: sin eso, tras cerrar sesión la flecha
"atrás" mostraba el panel guardado en la caché del navegador. Los recursos estáticos (`/css`, `/js`)
sí se cachean, y van versionados por hash para que un despliegue nunca deje una hoja de estilos vieja.

**Diseño.** La web reutiliza la paleta de la app Android (`colors.xml`: turquesa `#18C0C1`, fondo
`#F2F5FA`, Poppins) para que los dos subsistemas se vean como un solo producto.

## 5. Autenticación

`taxista-service` emite JWT firmados en HS256 con un secreto compartido. El gateway los valida en
el borde y cada microservicio los vuelve a validar: defensa en profundidad, para que exponer un
servicio por error no abra un hueco.

Cuatro roles viajan en el claim `roles`:

- `SUPERADMIN`: aprueba, rechaza, desactiva y reactiva taxistas, y ve servicios y eventos.
- `TAXISTA`: gestiona su perfil y presta servicios: acepta pedidos, avanza el estado, reporta su
  ubicación y finaliza con el QR. Solo opera sobre sus propios datos y servicios.
- `APP_MOVIL`: credencial de servicio que usa la app Android para las operaciones del cliente y del
  hotel (crear el pedido, consultar estado, ubicación y valoración, calificar). Así la app no
  consume la API sin autenticar, que sería el hueco más obvio del sistema.
- `SERVICIO_INTERNO`: lo usa exclusivamente `calificacion-service` para llamar a
  `PATCH /api/taxistas/{id}/valoracion` en `taxista-service` (RN-013, ver sección 3). No lo emite
  `taxista-service` para nadie más: `calificacion-service` firma su propio token de un solo uso con
  el mismo secreto compartido, vía `InternalTokenService`, cada vez que necesita empujar el
  promedio recalculado. Es la misma idea de "database per service, se piden los datos por HTTP"
  aplicada a una escritura en lugar de una lectura.

Las contraseñas se guardan con BCrypt, nunca en texto plano (requisito de calidad del plan de
proyecto).

### 5.1 Control de acceso verificable

El requerimiento de calidad pide "restringir las operaciones según el rol autenticado" y que "un
cliente solo acceda a sus propios ... servicios de taxi". Cada endpoint declara su regla con
`@PreAuthorize`, y esas reglas **se prueban**: `PermisosPorRolTest` recorre una matriz de llamadas
(rol, ruta, resultado esperado) contra la cadena de seguridad real, con los servicios de negocio
simulados. Cubre 63 casos en `taxista-service` y 13 en `calificacion-service`: por ejemplo, que un
taxista no pueda ver el servicio de otro, ni aprobar solicitudes, ni leer los eventos. Las pruebas se
verificaron rompiendo permisos a propósito para confirmar que fallan cuando deben.

### 5.2 Credenciales iniciales

`CargaInicial` crea al arrancar el Superadmin y la cuenta `APP_MOVIL`. El secreto manda: si la
contraseña configurada cambia, al reiniciar se actualiza la de la cuenta existente, de modo que rotar
una clave es cambiar el secreto. Con `EXIGIR_CLAVES_FUERTES=true` (lo activa el script de despliegue)
el servicio **se niega a arrancar** si `JWT_SECRET`, `SUPERADMIN_PASSWORD` o `APP_MOVIL_PASSWORD` son
`cambiame` o tienen menos de 12 caracteres. En desarrollo solo avisa.

### 5.3 Límite de intentos de login

Tras 5 fallos seguidos para un mismo correo, el login responde **429** con `Retry-After` durante 15
minutos; un login correcto reinicia el contador. Se cuenta por correo, exista o no, para no revelar
qué correos están registrados. El bloqueo dispara un solo evento `LOGIN_BLOQUEADO`.

Limitaciones aceptadas y documentadas: los contadores viven en la memoria de cada instancia, así que
con varias instancias de Cloud Run el límite efectivo puede ser hasta 3 veces mayor y se reinicia al
reciclarse la instancia (frena la fuerza bruta, no es un control exacto; si hiciera falta, se mueve a
Mongo). Y alguien puede bloquear temporalmente la cuenta de otra persona fallando a propósito; por eso
el bloqueo es corto.

### 5.4 Subida de fotos

El formulario público de registro sube fotos a Cloudinary. El portal valida que el archivo sea
realmente una imagen **por sus primeros bytes** (JPG, PNG o WebP) y no por el `Content-Type` ni la
extensión, que el cliente controla. SVG, HTML y ejecutables se rechazan aunque se llamen `.jpg`. Un
archivo de más de 5 MB vuelve al formulario con un mensaje claro.

**Mejora pendiente.** Lo más elegante sería que la app Android mande su token de Firebase Auth y
el gateway lo valide contra el JWKS público de Google, federando los dos sistemas de identidad.
Se dejó para después porque HS256 con credencial de servicio ya cumple el requisito y se entiende
en cinco minutos; la federación se puede agregar sin tocar los microservicios de dominio.

## 6. Resiliencia

El RF-TAX-018 y el RNF-FIA-001 piden que la caída de esta API no rompa las reservas de la app
móvil. Se resuelve en dos lugares:

- **Aquí**: Resilience4j en el gateway, con timeout de 4 s y un circuito que se abre cuando falla más
  del 50 % de las últimas 10 llamadas (mínimo 5) y permanece abierto 10 s antes de probar de nuevo.
  Con el circuito abierto, el gateway responde un 503 con mensaje legible al instante, sin seguir
  golpeando el servicio caído. El fallback acepta **cualquier método HTTP**: la app usa también PATCH
  y PUT (estado, disponibilidad, ubicación), y antes esas llamadas recibían un 405 en lugar del 503.
  Lo cubre `GatewayIntegrationTest`, que levanta el gateway contra un backend lento simulado.
- **En la app Android**: timeout en OkHttp y un mensaje al usuario cuando llega el 503. Sin esta
  mitad, el requisito no está cumplido aunque el gateway se comporte bien.

## 7. Riesgos abiertos

| Riesgo | Mitigación |
|---|---|
| Arranque en frío de Cloud Run: la JVM tarda unos segundos si el servicio escaló a cero | Poner `--min-instances=1` en el gateway el día de la demo y devolverlo a 0 después |
| El tier M0 de Atlas tiene 512 MB y 100 operaciones por segundo | Suficiente para el curso; queda documentado en el OPEX qué costaría el siguiente tier |
| El secreto JWT compartido entre cuatro servicios | Vive en Secret Manager, nunca en el repositorio; rotarlo obliga a redesplegar los cuatro |
| Los pedidos de taxi viven en este subsistema y no en Firebase | El equipo de la app debe crearlos y consultarlos por la API; duplicarlos en Firestore generaría dos fuentes de verdad (ver 3.2) |
| La app necesita el token `TAXISTA` para las acciones del taxista, y ese login usa la contraseña del portal | Decidir en el Lab 6 cómo se enlaza con Firebase Auth; la federación descrita en la sección 5 lo resuelve |
| Los contadores de intentos de login viven en memoria de cada instancia | Documentado en 5.3; migrar a Mongo si hiciera falta un límite exacto |
| La ubicación se consulta por polling, no por push | Suficiente para el mapa del cliente; un canal en tiempo real sería una mejora, no un requisito |
| A2 del ERS sin confirmar por el coordinador | Esta decisión se toma y se documenta; si el profesor exige MERN, el corte de servicios y los contratos REST se mantienen y solo cambia la implementación |
