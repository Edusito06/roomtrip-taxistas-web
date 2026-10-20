# Instrucciones para Claude Code

Este archivo es el encargo completo. Ábrelo desde la raíz del repositorio y sigue las fases en
orden. No saltes a la fase siguiente si la anterior no terminó en verde.

---

## 1. Prompt inicial

Copia esto en Claude Code al empezar:

> Lee el archivo `INSTRUCCIONES_CLAUDE_CODE.md` completo antes de tocar nada. Es el encargo.
> Después lee `README.md` y `docs/ARQUITECTURA.md` para entender las decisiones que ya están
> tomadas. Tu trabajo es dejar el proyecto compilando, con las pruebas en verde y con el flujo
> funcional verificado de punta a punta en local. No cambies la arquitectura ni el stack: si crees
> que algo está mal diseñado, dímelo y espera mi respuesta antes de modificarlo.

---

## 2. Qué es este proyecto

Subsistema web de gestión de taxistas del curso 1TEL05 (Servicios y Aplicaciones para IoT, PUCP
2026-2). Es uno de dos sistemas: el otro es una app Android nativa en Java que vive en otro
repositorio y que consume la API REST de este.

Son cuatro módulos Maven bajo un POM padre:

`gateway-service` es el único punto de entrada público. Valida el JWT, enruta hacia los dos
servicios de dominio y aplica circuit breaker con Resilience4j.

`taxista-service` es dueño del ciclo de vida del taxista: autoregistro, aprobación por el
Superadmin, datos, estado de disponibilidad. También emite los JWT. Su base es `taxi_registry`.

`calificacion-service` guarda las calificaciones y calcula la valoración promedio. Su base es
`taxi_ratings`.

`portal-web` es la interfaz Thymeleaf. No toca Mongo: consume el gateway igual que la app Android.

Stack: Java 21, Spring Boot 3.5.x, Spring Cloud 2025.0.x, MongoDB, Thymeleaf con Bootstrap 5,
Docker Compose para desarrollo local.

---

## 3. Reglas que no se deben romper

Estas no son preferencias de estilo, son restricciones del curso y del documento de requisitos
(ERS v1.0). Romperlas invalida el entregable.

El código Java es el único lenguaje del backend. Nada de Kotlin, nada de Node, nada de frameworks
multiplataforma.

El frontend es server-side con Thymeleaf. No introduzcas React, Vue ni ningún build de JavaScript.

`taxista-service` y `calificacion-service` no comparten base de datos y no se leen entre sí por
Mongo. Si uno necesita un dato del otro, se lo pide por HTTP.

La app móvil no accede nunca a MongoDB. Todo pasa por la API REST del gateway. Esta es la
restricción RES-04 y es la que más mira el jefe de práctica.

Las contraseñas se guardan con BCrypt. Nunca en texto plano, en ningún lado, ni siquiera en
pruebas o datos de ejemplo.

Los comentarios que citan IDs de requisito (`RF-WTX-004`, `RN-013`, `RES-04` y similares) se
conservan. Son la trazabilidad con el ERS y sirven para el documento final del curso. Si mueves
código, el comentario se va con él.

No borres pruebas para que el build pase. Si una prueba falla, o el código está mal o la prueba
está mal, y hay que decidir cuál con criterio y explicarlo.

No commitees secretos. El archivo `.env` está en `.gitignore` y ahí se queda.

---

## 4. Fase 1: que compile

El proyecto fue escrito sin poder compilarse (el entorno donde se generó no tenía acceso a Maven
Central), así que es esperable que la primera compilación falle. Los errores son de dependencias
y de nombres de API, no de lógica. La sintaxis de los 37 archivos Java ya fue validada.

Empieza por:

```bash
mvn -B clean compile
```

Arregla lo que salga, módulo por módulo, y recién después corre las pruebas.

### 4.1 Puntos donde es probable que falle

Revisa estos primero, en este orden. Son los que ya identifiqué como riesgosos.

**Versiones fijadas en el POM padre.** Están puestas `spring-boot-starter-parent` en `3.5.6` y
`spring-cloud-dependencies` en `2025.0.0`. Si alguna no resuelve, busca la última versión
publicada de la misma línea (`3.5.x` y `2025.0.x`) y ajústala. No subas a Spring Boot 4.x ni a
Spring Cloud 2025.1.x: rompen compatibilidad y casi toda la documentación que el equipo va a
consultar está escrita para la línea 3.x.

**Nombre del artefacto del gateway.** En `gateway-service/pom.xml` está
`spring-cloud-starter-gateway`. A partir de Gateway 4.3 el artefacto se renombró a
`spring-cloud-starter-gateway-server-webflux` y el viejo quedó deprecado. Si no resuelve, cámbialo.
Ya hay un comentario en el POM avisando de esto.

**Setters del circuit breaker.** En `gateway-service/.../config/RouteConfig.java` se encadena
`cb.setName(...).setFallbackUri(...)`. Si esos setters devuelven `void` en la versión que resuelva,
el encadenamiento no compila y hay que separarlo en dos líneas dentro del `Consumer`. Es un arreglo
de dos minutos, pero es el error más probable de todo el proyecto.

**Cloudinary.** La dependencia `cloudinary-http44` está fijada en `1.39.0` en el POM padre. Si esa
versión no existe, ajusta a la última estable. Ojo: hoy la dependencia está declarada pero no se
usa en el código, porque el formulario recibe la URL ya subida. Si no logras resolverla, coméntala
y avísame en lugar de pelear con ella.

**nimbus-jose-jwt sin versión.** En `taxista-service/pom.xml` se declara sin `<version>` porque la
gestiona el BOM de Spring Boot. Si Maven se queja de versión faltante, fíjala explícitamente.

**Configuración de seguridad reactiva vs servlet.** El gateway usa WebFlux
(`ServerHttpSecurity`, `ReactiveJwtDecoder`) y los otros dos usan MVC (`HttpSecurity`,
`JwtDecoder`). Si ves errores de tipos en las clases `SecurityConfig`, revisa que no se hayan
cruzado los imports entre los dos mundos. Son APIs parecidas y es fácil confundirlas.

**Dockerfiles.** Cada uno hace `mvn dependency:go-offline` con solo los POMs copiados, antes de
copiar el código. Si ese paso falla, quítalo: es solo una optimización de caché de Docker, no es
necesario para que la imagen se construya.

### 4.2 Criterio de salida de la fase 1

```bash
mvn -B clean verify
```

Termina en `BUILD SUCCESS`, con las pruebas de `TaxistaServiceTest` pasando. Son seis y cubren el
estado inicial de la solicitud, el hasheo de la contraseña, el correo duplicado, la restricción de
que un pendiente no puede estar disponible, la doble decisión sobre una solicitud y el filtro de
disponibles.

---

## 5. Fase 2: que levante

```bash
cp .env.example .env
```

Edita `.env` y pon un `JWT_SECRET` de al menos 32 caracteres. Genera uno con
`openssl rand -base64 48`. Cambia también las contraseñas iniciales.

```bash
docker compose up --build
```

Los cuatro servicios tienen que quedar arriba. Verifica cada uno:

```bash
curl -s localhost:8080/actuator/health   # gateway
curl -s localhost:8081/actuator/health   # taxista-service
curl -s localhost:8082/actuator/health   # calificacion-service
curl -s localhost:8083/actuator/health   # portal-web
```

Los cuatro devuelven `{"status":"UP"}`.

Si algún contenedor se reinicia en bucle, lee el log con
`docker compose logs -f <servicio>` antes de cambiar nada. Las causas típicas son el `JWT_SECRET`
demasiado corto (el `JwtService` lanza excepción a propósito si tiene menos de 32 bytes) o Mongo
que todavía no terminó de arrancar.

---

## 6. Fase 3: que cumpla los requisitos

Esta es la parte que decide la nota. Cada fila de la tabla es un requisito del ERS y una forma
concreta de comprobarlo. Ejecuta las pruebas de humo de abajo y marca la tabla.

| ID | Requisito | Cómo se comprueba |
|---|---|---|
| RF-WTX-001 | El sistema web permite el autoregistro de taxistas | `POST /api/taxistas/registro` sin token devuelve 201 |
| RF-WTX-002 | Se exigen datos personales, vehículo, placa y las dos fotos | Enviar el registro sin `placa` devuelve 400 con el campo señalado |
| RF-WTX-003 | La solicitud queda en estado PENDIENTE | La respuesta del registro trae `estadoHabilitacion: PENDIENTE` |
| RF-WTX-004 | El Superadmin lista pendientes y aprueba o rechaza | `GET /api/taxistas/pendientes` y `PATCH /{id}/aprobacion` funcionan solo con rol SUPERADMIN |
| RF-WTX-005 | Se puede consultar y actualizar la información del taxista | `GET /api/taxistas` devuelve el padrón completo |
| RF-WTX-006 | Se registran calificaciones y se calcula el promedio | `POST /api/calificaciones` y luego `GET /api/calificaciones/taxista/{id}/valoracion` |
| RF-WTX-007 | Se mantiene DISPONIBLE, EN_SERVICIO o NO_DISPONIBLE | `PATCH /{id}/disponibilidad` cambia el estado y se refleja en la consulta |
| RF-WTX-008 | Base de datos propia, la app móvil no se conecta directo | No existe ninguna credencial de Mongo fuera de los dos servicios de dominio |
| RF-WTX-009 | Autenticación de administradores y taxistas | `POST /api/auth/login` devuelve token para ambos tipos de usuario |
| RF-API-001 | La API expone los taxistas disponibles | `GET /api/taxistas/disponibles` devuelve solo APROBADO y DISPONIBLE |
| RF-TAX-018 | Aviso claro si la API no está disponible | Apagar `taxista-service` y comprobar que el gateway responde 503 con mensaje |
| RNF-POR-004 | El portal funciona en Chrome, Edge y Firefox | Abrir `localhost:8083` en los tres |
| Calidad | Contraseñas hasheadas | En Mongo, el campo `passwordHash` empieza con `$2a$` |
| Calidad | Operaciones restringidas por rol | Un token de TAXISTA recibe 403 en `/api/taxistas/pendientes` |

### 6.1 Pruebas de humo

Ejecuta esto en orden y verifica cada respuesta.

**Login del Superadmin.** Usa el correo y contraseña que pusiste en `.env`.

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"correo":"superadmin@roomtrip.pe","password":"TU_PASSWORD"}' \
  | python -c "import sys,json; print(json.load(sys.stdin)['token'])")
echo $TOKEN
```

**Autoregistro de un taxista.** Sin token, tiene que devolver 201.

```bash
curl -s -X POST localhost:8080/api/taxistas/registro \
  -H "Content-Type: application/json" \
  -d '{
    "nombres":"Juan","apellidos":"Perez","tipoDocumento":"DNI",
    "numeroDocumento":"70123456","correo":"juan@example.com",
    "telefono":"987654321","password":"secreto123",
    "fotoUrl":"https://res.cloudinary.com/demo/foto.jpg",
    "vehiculoMarca":"Toyota","vehiculoModelo":"Yaris","vehiculoAnio":"2020",
    "vehiculoColor":"Blanco","placa":"ABC-123",
    "fotoVehiculoUrl":"https://res.cloudinary.com/demo/auto.jpg"
  }'
```

Guarda el `id` que devuelve.

**Validación.** El mismo comando quitando `"placa"` tiene que devolver 400 indicando el campo
faltante. Esto es lo que hace verificable el RF-WTX-002.

**Bandeja del Superadmin.**

```bash
curl -s localhost:8080/api/taxistas/pendientes -H "Authorization: Bearer $TOKEN"
```

**Aprobación.**

```bash
curl -s -X PATCH localhost:8080/api/taxistas/EL_ID/aprobacion \
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
  -d '{"aprobado":true}'
```

**Regla de negocio.** Repite el comando anterior. Tiene que devolver 409, porque una solicitud ya
decidida no se vuelve a procesar.

**Disponibilidad y consulta de la app móvil.** Marca al taxista como DISPONIBLE y luego consulta
la lista con el token de `app-movil@roomtrip.pe`. Solo debe aparecer si está APROBADO y
DISPONIBLE al mismo tiempo.

**Degradación con gracia.** Este es el que más peso tiene en la sustentación:

```bash
docker compose stop taxista-service
curl -i localhost:8080/api/taxistas/disponibles -H "Authorization: Bearer $TOKEN"
```

Las primeras llamadas tardan y luego, cuando el circuito se abre, responde 503 al instante con el
cuerpo `SERVICIO_NO_DISPONIBLE`. Levántalo de nuevo con `docker compose start taxista-service`.

**Portal web.** Abre `localhost:8083`, entra con el Superadmin, registra un taxista desde
`/registro` en otra pestaña y comprueba que aparece en la bandeja y que el botón de aprobar
funciona.

---

## 7. Fase 4: cerrar los huecos conocidos

Estos puntos están incompletos a propósito. Cuando todo lo anterior esté en verde, avísame y
decidimos cuáles atacar primero. No los empieces por tu cuenta.

La subida real de fotos a Cloudinary desde el portal. Hoy el formulario pide la URL ya subida.

La pantalla del taxista, para que vea el estado de su solicitud y active su disponibilidad. Hoy
solo existe el panel del Superadmin.

La sincronización de `valoracionPromedio` desde `calificacion-service` hacia `taxista-service`.
El método `actualizarValoracion` ya existe pero nadie lo llama todavía.

Pruebas de integración del gateway con Testcontainers.

---

## 8. Definición de terminado

El encargo está cerrado cuando se cumplen las cinco cosas:

`mvn -B clean verify` termina en verde sin pruebas borradas ni ignoradas.

`docker compose up --build` levanta los cuatro servicios y los cuatro health checks responden UP.

Las pruebas de humo de la sección 6.1 pasan todas, incluida la del circuit breaker.

La tabla de requisitos de la sección 6 está marcada completa.

Me entregas un resumen corto de qué archivos tocaste y por qué. Si cambiaste una versión de
dependencia o el nombre de un artefacto, dímelo explícitamente, porque eso hay que reflejarlo en
el documento de arquitectura del curso.

---

## 9. Si algo no se puede arreglar

No inventes un rodeo. Para y dime tres cosas: qué falla, qué intentaste y cuáles son las opciones
que ves. Prefiero decidir yo con la información en la mano que encontrarme después con una
dependencia comentada o una prueba desactivada.

Dos cosas que nunca son la solución: bajar la versión de Java por debajo de 21, y quitar la
validación de seguridad para que una prueba pase.

---

## 10. Nota sobre el uso de IA

El curso exige declarar y citar el uso de herramientas de inteligencia artificial generativa, y el
profesor puede pedir el reporte de prompts como anexo. Si trabajas sobre este repositorio, deja
constancia en los mensajes de commit de qué parte se hizo con asistencia de IA. No declararlo se
considera falta a la ética académica.
