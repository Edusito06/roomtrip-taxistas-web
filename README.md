# RoomTrip - Subsistema web de gestión de taxistas

Subsistema 2 del proyecto del curso **1TEL05 Servicios y Aplicaciones para IoT** (PUCP, 2026-2).
Cubre los módulos **MOD-WTX** (gestión web de taxistas) y **MOD-API** (API REST de integración)
definidos en el ERS v1.0 del proyecto.

La app móvil Android es un repositorio aparte: [Markernan/roomtrip](https://github.com/Markernan/roomtrip).
Estos dos sistemas solo se hablan por la API REST
que expone el gateway de este repositorio, nunca por base de datos (RES-04, RF-WTX-008).

---

## Arquitectura en una línea

Cuatro servicios en contenedores: un gateway público, dos microservicios de dominio con base de
datos propia cada uno, y un portal web server-side para el taxista y el Superadmin.

```
                    ┌──────────────────┐
  App Android ────► │                  │ ──► taxista-service ──► MongoDB taxi_registry
  (Retrofit)        │  gateway-service │
                    │  JWT + circuit   │
  portal-web  ────► │  breaker         │ ──► calificacion-service ──► MongoDB taxi_ratings
  (Thymeleaf)       └──────────────────┘
```

| Servicio | Puerto local | Responsabilidad | Requisitos que cubre |
|---|---|---|---|
| `gateway-service` | 8080 | Único punto público. Valida el JWT, enruta, corta el circuito y devuelve el fallback | MOD-API, RF-TAX-018, RNF-FIA-001 |
| `taxista-service` | 8081 | Autoregistro, aprobación, datos, disponibilidad, emisión de tokens | RF-WTX-001..005, 007, 008, 009 |
| `calificacion-service` | 8082 | Calificaciones y valoración promedio | RF-WTX-006, RN-013, RF-TAX-017 |
| `portal-web` | 8083 | Formulario de autoregistro y panel del Superadmin | RF-WTX-001, 004, 005, RNF-POR-004 |

El detalle de por qué se cortó así está en [`docs/ARQUITECTURA.md`](docs/ARQUITECTURA.md).
El procedimiento de despliegue y el OPEX están en [`docs/DESPLIEGUE.md`](docs/DESPLIEGUE.md).

---

## Stack

| Capa | Elección | Por qué |
|---|---|---|
| Lenguaje | Java 21 (LTS) | El mismo que el equipo ya escribe en Android |
| Framework | Spring Boot 3.5.x | Unidad 5 del sílabo; el libro *Beginning Spring Boot* es bibliografía del curso |
| Gateway y resiliencia | Spring Cloud Gateway + Resilience4j | Corresponde literalmente a "Gateway y resiliencia" de la Unidad 5 |
| Base de datos | MongoDB Atlas M0 | NoSQL (Unidad 3), gratis para siempre, sin tarjeta, independiente de Firebase |
| Fotos | Cloudinary | Aparece en la Unidad 4 del sílabo junto a Firebase Storage; evita guardar binarios en Mongo |
| Frontend | Thymeleaf + Bootstrap 5 | Server-side: un solo despliegue, sin CORS, sin build de JS |
| Seguridad | Spring Security + JWT HS256 + BCrypt | Contraseñas hasheadas y RBAC, ambos requisitos de calidad del plan de proyecto |
| Nube | Google Cloud Run | Contenedores, escala a cero, misma consola y facturación que el Firebase de la app móvil |
| CI/CD | GitHub Actions | Build en cada PR, deploy automático al hacer merge a `main` |

---

## Arrancar en local

Requisitos: Docker Desktop, JDK 21 y Maven 3.9 o superior.

```bash
git clone <url-del-repo>
cd roomtrip-taxi-web
cp .env.example .env     # editar JWT_SECRET y las contraseñas iniciales
docker compose up --build
```

Levanta Mongo y los cuatro servicios:

- Portal web: http://localhost:8083
- Gateway (API): http://localhost:8080
- Superadmin inicial: el correo y contraseña que pusiste en `.env`

Para trabajar sobre un solo servicio sin levantar todo:

```bash
docker compose up -d mongo
mvn -pl taxista-service -am spring-boot:run
```

### Compilar y probar

```bash
mvn clean verify
```

Esto es lo primero que hay que correr al clonar. Si `spring-cloud-starter-gateway` no resuelve,
cambiarlo en `gateway-service/pom.xml` por `spring-cloud-starter-gateway-server-webflux`, que es
el nombre nuevo del artefacto a partir de Gateway 4.3.

---

## La API que consume la app Android

Todo pasa por el gateway. El flujo de la app móvil es:

1. `POST /api/auth/login` con la credencial de servicio `APP_MOVIL` → devuelve un JWT.
2. `GET /api/taxistas/disponibles` con `Authorization: Bearer <token>` → solo taxistas
   `APROBADO` + `DISPONIBLE` (RF-API-001).
3. `PATCH /api/taxistas/{id}/disponibilidad` con `EN_SERVICIO` al asignarle un traslado.
4. `POST /api/calificaciones` al finalizar el servicio (RF-TAX-017).

| Método | Ruta | Rol | Requisito |
|---|---|---|---|
| POST | `/api/auth/login` | público | RF-WTX-009 |
| POST | `/api/taxistas/registro` | público | RF-WTX-001, 002 |
| GET | `/api/taxistas/pendientes` | SUPERADMIN | RF-WTX-004 |
| GET | `/api/taxistas` | SUPERADMIN | RF-WTX-005 |
| GET | `/api/taxistas/disponibles` | SUPERADMIN, APP_MOVIL | RF-API-001 |
| GET | `/api/taxistas/{id}` | SUPERADMIN, APP_MOVIL, el propio taxista | RF-WTX-005 |
| PATCH | `/api/taxistas/{id}/aprobacion` | SUPERADMIN | RF-WTX-004 |
| PATCH | `/api/taxistas/{id}/disponibilidad` | SUPERADMIN, APP_MOVIL, el propio taxista | RF-WTX-007 |
| POST | `/api/calificaciones` | APP_MOVIL | RF-WTX-006, RF-TAX-017 |
| GET | `/api/calificaciones/taxista/{id}/valoracion` | SUPERADMIN, APP_MOVIL, el propio taxista | RN-013 |
| PUT | `/api/taxistas/{id}/ubicacion` | APP_MOVIL, el propio taxista (solo EN_SERVICIO) | Ubicación durante servicio activo |
| GET | `/api/taxistas/{id}/ubicacion` | SUPERADMIN, APP_MOVIL, el propio taxista | Mapa del taxista para el cliente |
| POST | `/api/servicios` | SUPERADMIN, APP_MOVIL | Crea el pedido de taxi (SOLICITADO) y su QR |
| GET | `/api/servicios/solicitados` | SUPERADMIN, APP_MOVIL, TAXISTA | Pedidos que se pueden aceptar |
| GET | `/api/servicios/{id}` | SUPERADMIN, APP_MOVIL, el taxista asignado | Estado del servicio |
| POST | `/api/servicios/{id}/aceptar` | TAXISTA | SOLICITADO → ASIGNADO |
| PATCH | `/api/servicios/{id}/estado` | TAXISTA asignado | ASIGNADO → EN_CAMINO → EN_TRASLADO |
| POST | `/api/servicios/{id}/finalizar` | TAXISTA asignado | EN_TRASLADO → FINALIZADO validando el QR |
| GET | `/api/eventos` | SUPERADMIN | Log de eventos relevantes |
| GET | `/api/servicios` | SUPERADMIN | Servicios de taxi recientes (panel del Superadmin) |

El detalle de cada endpoint (cuerpos, errores, flujo y esqueleto de Retrofit para la app) está en [`docs/API.md`](docs/API.md).

Cuando un servicio de atrás no responde, el gateway devuelve **503** con este cuerpo, que es
exactamente lo que la app móvil necesita para mostrar el aviso sin romper las reservas
(RF-TAX-018):

```json
{
  "status": 503,
  "error": "SERVICIO_NO_DISPONIBLE",
  "mensaje": "El servicio de gestion de taxistas no esta disponible en este momento. Intentalo nuevamente en unos minutos."
}
```

### Probar el circuit breaker en la demo

```bash
docker compose stop taxista-service
curl -i http://localhost:8080/api/taxistas/disponibles -H "Authorization: Bearer <token>"
# Las primeras llamadas dan timeout, luego el circuito se abre y responde el 503 al instante
docker compose start taxista-service
```

---

## Convenciones del equipo

- Ramas: `main` (estable, se despliega solo), `develop` (integración), `feature/<lab>-<tema>`.
- Nadie hace push directo a `main`: todo entra por Pull Request con el CI en verde.
- Los secretos nunca se commitean. `.env` está en `.gitignore` y en la nube se usa Secret Manager.
- Los IDs de requisito del ERS (`RF-WTX-nnn`, `RNF-nnn`, `RN-nnn`) se citan en el código y en los
  mensajes de commit, para que la matriz de trazabilidad del documento final salga sola.

## Estado y siguiente paso

El scaffold cubre el camino completo de autoregistro, aprobación, disponibilidad y calificación.
Lo que falta antes del Lab 7:

- [x] Subida real de fotos a Cloudinary desde el portal (`portal-web` sube el archivo con el SDK
      de Cloudinary al enviar `/registro`; requiere un `CLOUDINARY_URL` real en `.env`).
- [x] Pantalla del taxista para ver el estado de su solicitud y activar su disponibilidad
      (`/mi-cuenta`, solo visible para el rol TAXISTA).
- [x] Sincronizar `valoracionPromedio` desde `calificacion-service` hacia `taxista-service`
      (`PATCH /api/taxistas/{id}/valoracion`, ver `docs/ARQUITECTURA.md` sección 3.1).
- [x] Pruebas de integración del gateway con Testcontainers
      (`gateway-service/src/test/.../GatewayIntegrationTest.java`, backend simulado con WireMock).
- [ ] Manual de instalación y manual de usuario (entregables finales del curso).
