# Despliegue en la nube y costo de operación

Cubre dos entregables finales del plan de proyecto: "el sistema desplegado en la nube" y "costo
total de la solución, OPEX".

Plataforma elegida: **Google Cloud Run**, en la misma cuenta de facturación donde vive el Firebase
de la app móvil. Una sola consola, una sola factura, y el OPEX del proyecto completo sale de un
solo lugar.

---

## Por qué Cloud Run y no AWS Academy

| Opción | A favor | En contra |
|---|---|---|
| **Cloud Run** | Escala a cero, contenedores nativos, HTTPS y dominio incluidos, misma cuenta que Firebase | Pide tarjeta para activar la facturación |
| AWS Academy | Créditos del curso, aparece en el sílabo | El laboratorio se apaga al cerrar la sesión: el sistema no queda disponible de forma continua, que es justo lo que pide el entregable |
| Render free | No pide tarjeta | El servicio se duerme a los 15 min y despierta en ~50 s; con cuatro servicios encadenados la demo se siente rota |

Cloud Run pide tarjeta pero dentro del free tier no se cobra nada, y además Google da 300 USD de
crédito para cuentas nuevas. Si el profesor exige AWS Academy, el mismo `Dockerfile` corre en
ECS Fargate o en una EC2 con `docker compose`: nada de lo construido se pierde.

---

## Procedimiento, paso a paso

### 0. Antes de empezar

Una sola persona del equipo hace esto; los demás solo necesitan permisos en el proyecto.

```bash
gcloud auth login
gcloud config set project TU_PROJECT_ID
gcloud services enable run.googleapis.com artifactregistry.googleapis.com secretmanager.googleapis.com
```

### 1. MongoDB Atlas

1. Crear cuenta en Atlas y un clúster **M0 (Free)**, alojado en **GCP / us-central1** para quedar
   en la misma región que Cloud Run y no pagar latencia entre nubes.
2. Crear un usuario de base de datos con permisos de lectura y escritura.
3. En Network Access, permitir `0.0.0.0/0`. Cloud Run no tiene IP fija, así que no hay forma de
   restringir por IP sin montar un conector VPC con Serverless VPC Access, que no vale la pena
   para un proyecto de curso. La protección real es el usuario y la contraseña, que van en Secret
   Manager.
4. Copiar el connection string.

### 2. Cloudinary

Crear cuenta gratuita y copiar el `CLOUDINARY_URL` del dashboard. No pide tarjeta.

### 3. Guardar los secretos

Nunca en el repositorio, nunca en el `Dockerfile`, nunca como `--set-env-vars`. Se guardan en Secret
Manager con este bloque, que **no obliga a pegar ninguna cadena completa**: genera lo que se puede
generar, toma la URL de Cloudinary del `.env` y de Atlas pide solo la contraseña, con la entrada oculta.
Si un secreto ya existe, no lo toca, así que se puede volver a correr.

```bash
cd /ruta/al/proyecto
P=TU_PROJECT_ID
mk(){ gcloud secrets describe "$1" --project $P >/dev/null 2>&1 && echo "$1: ya existe" \
      || { gcloud secrets create "$1" --replication-policy=automatic --data-file=- --project $P >/dev/null && echo "$1: creado"; }; }

openssl rand -base64 48 | tr -d '\n' | mk jwt-secret                  # compartido por los cuatro servicios
printf 'Sa-%s' "$(openssl rand -hex 10)" | mk superadmin-password
printf 'Am-%s' "$(openssl rand -hex 10)" | mk app-movil-password
grep -E '^CLOUDINARY_URL=' .env | cut -d= -f2- | tr -d '\n' | mk cloudinary-url

# Atlas: una cadena por servicio (bases distintas). Solo se escribe la contrasenia, y no se ve.
read -s "PW?Contrasenia del usuario de Atlas: "; echo     # zsh; en bash: read -s -p "..." PW
HOST=cluster0.xxxxx.mongodb.net; USUARIO=el_usuario_de_atlas       # ajusta estos dos valores
printf '%s' "mongodb+srv://${USUARIO}:${PW}@${HOST}/taxi_registry" | mk mongo-uri-registry
printf '%s' "mongodb+srv://${USUARIO}:${PW}@${HOST}/taxi_ratings"  | mk mongo-uri-ratings
unset PW
```

> **Por qué así y no con `echo -n "mongodb+srv://usuario:clave@..."`.** Pegar la cadena completa en un
> comando deja la contraseña en el historial del terminal y es fácil que el pegado se duplique: en el
> despliegue real quedó guardada **tres veces seguida**, y `taxista-service` se negó a arrancar con
> `databaseName does not contain ' '`. Con una contraseña de **solo letras y números** además se evitan los
> símbolos (`@`, `:`, `/`) que rompen la cadena.

Las claves deben tener **al menos 12 caracteres** y no pueden ser `cambiame`. El script de despliegue
activa `EXIGIR_CLAVES_FUERTES=true` en `taxista-service`: si `JWT_SECRET`, `SUPERADMIN_PASSWORD` o
`APP_MOVIL_PASSWORD` son por defecto o cortas, el servicio **se niega a arrancar** en vez de quedar expuesto.

**Rotar una clave.** Crea una versión nueva del secreto, desactiva la anterior (el límite gratuito es de
6 versiones *activas*) y reinicia el servicio; al arrancar, `taxista-service` actualiza la contraseña de la
cuenta existente:

```bash
printf '%s' "$NUEVA" | gcloud secrets versions add superadmin-password --data-file=- --project $P
gcloud secrets versions disable 1 --secret=superadmin-password --project $P --quiet
gcloud run services update taxista-service --project $P --region us-central1 --update-env-vars REINICIO=$(date +%s)
```

**Leer la contraseña del Superadmin** sin que se cuele un carácter al copiar (el terminal marca con `%` el
final de un texto sin salto de línea, y al seleccionar a mano ese `%` se copia):

```bash
gcloud secrets versions access latest --secret=superadmin-password --project $P | pbcopy   # macOS
```

### 3.1. Permiso para que Cloud Run lea los secretos

Los servicios corren con la cuenta de servicio por defecto de Compute. Dale permiso explícito de lectura
sobre los secretos (el rol de *Editor* no lo garantiza):

```bash
N=$(gcloud projects describe $P --format="value(projectNumber)")
gcloud projects add-iam-policy-binding $P \
  --member="serviceAccount:${N}-compute@developer.gserviceaccount.com" \
  --role="roles/secretmanager.secretAccessor"
```

### 4. Repositorio de imágenes

```bash
gcloud artifacts repositories create taxi-web \
  --repository-format=docker \
  --location=us-central1 \
  --description="Imagenes del subsistema web de taxistas"

gcloud auth configure-docker us-central1-docker.pkg.dev
```

### 5. Desplegar

> **Apple Silicon (Mac M1 o posterior).** Cloud Run corre en `linux/amd64`, pero un Mac construye `arm64`
> por defecto y esas imágenes **no arrancan** en la nube. Los Dockerfiles compilan el JAR en la arquitectura
> del equipo (rápido) y fuerzan solo la etapa final a `amd64`, y el script construye con
> `--platform linux/amd64`. Si construyes a mano, usa ese mismo parámetro.

**Camino corto:** `scripts/deploy-cloudrun.sh` hace todo lo de este paso (construir, subir, desplegar
en orden y verificar `/actuator/health`). Es el mismo script que usa el workflow del paso 7.

```bash
PROJECT_ID=TU_PROJECT_ID ./scripts/deploy-cloudrun.sh v1
```

Al final de un despliegue correcto, el script **borra las versiones viejas de las imágenes** y conserva las
`KEEP_IMAGES` más recientes de cada servicio (2 por defecto), para no pasar los 0,5 GB gratuitos de Artifact
Registry. Con `DRY_RUN=1` solo muestra qué borraría, y con `LIMPIAR_SOLO=1` no despliega, solo limpia.
Las versiones sin etiqueta no se tocan. Las imágenes se construyen con `--provenance=false --sbom=false`: sin
eso, Docker Desktop agrega manifiestos de atestación que dejan entradas sueltas en el registro.

El detalle manual de abajo sirve para entender qué hace y para depurar un servicio suelto.

El orden importa: los servicios de dominio primero, porque el gateway necesita sus URLs, y el
portal al final porque necesita la URL del gateway.

```bash
export PROJECT_ID=TU_PROJECT_ID
export REGION=us-central1
export REPO=us-central1-docker.pkg.dev/$PROJECT_ID/taxi-web
```

**5.1 taxista-service**

```bash
docker build --platform linux/amd64 -f taxista-service/Dockerfile -t $REPO/taxista-service:v1 .
docker push $REPO/taxista-service:v1

gcloud run deploy taxista-service \
  --image $REPO/taxista-service:v1 \
  --region $REGION --allow-unauthenticated \
  --memory 512Mi --cpu 1 --min-instances 0 --max-instances 3 \
  --set-secrets "JWT_SECRET=jwt-secret:latest,MONGO_URI_REGISTRY=mongo-uri-registry:latest,SUPERADMIN_PASSWORD=superadmin-password:latest,APP_MOVIL_PASSWORD=app-movil-password:latest"
```

**5.2 calificacion-service**

Necesita la URL de `taxista-service`: empuja allí la valoración promedio (RN-013).

```bash
TAXISTA_URL=$(gcloud run services describe taxista-service --region $REGION --format 'value(status.url)')

docker build --platform linux/amd64 -f calificacion-service/Dockerfile -t $REPO/calificacion-service:v1 .
docker push $REPO/calificacion-service:v1

gcloud run deploy calificacion-service \
  --image $REPO/calificacion-service:v1 \
  --region $REGION --allow-unauthenticated \
  --memory 512Mi --cpu 1 --min-instances 0 --max-instances 3 \
  --set-env-vars "TAXISTA_SERVICE_URL=$TAXISTA_URL" \
  --set-secrets "JWT_SECRET=jwt-secret:latest,MONGO_URI_RATINGS=mongo-uri-ratings:latest"
```

**5.3 gateway-service**

```bash
TAXISTA_URL=$(gcloud run services describe taxista-service --region $REGION --format 'value(status.url)')
CALIF_URL=$(gcloud run services describe calificacion-service --region $REGION --format 'value(status.url)')

docker build --platform linux/amd64 -f gateway-service/Dockerfile -t $REPO/gateway-service:v1 .
docker push $REPO/gateway-service:v1

gcloud run deploy gateway-service \
  --image $REPO/gateway-service:v1 \
  --region $REGION --allow-unauthenticated \
  --memory 512Mi --cpu 1 --min-instances 0 --max-instances 3 \
  --set-env-vars "TAXISTA_SERVICE_URL=$TAXISTA_URL,CALIFICACION_SERVICE_URL=$CALIF_URL" \
  --set-secrets "JWT_SECRET=jwt-secret:latest"
```

**5.4 portal-web**

```bash
GATEWAY_URL=$(gcloud run services describe gateway-service --region $REGION --format 'value(status.url)')

docker build --platform linux/amd64 -f portal-web/Dockerfile -t $REPO/portal-web:v1 .
docker push $REPO/portal-web:v1

gcloud run deploy portal-web \
  --image $REPO/portal-web:v1 \
  --region $REGION --allow-unauthenticated \
  --memory 512Mi --cpu 1 --min-instances 0 --max-instances 3 \
  --set-env-vars "GATEWAY_URL=$GATEWAY_URL,THYMELEAF_CACHE=true" \
  --set-secrets "CLOUDINARY_URL=cloudinary-url:latest"
```

El portal es quien sube las fotos a Cloudinary, por eso lleva `CLOUDINARY_URL` y `taxista-service` no.

La URL del portal que imprime el último comando es la que se entrega al profesor.

### 6. Endurecer (opcional pero suma)

Ahora mismo los cuatro servicios aceptan tráfico público. Lo correcto es que solo el gateway y el
portal sean públicos, y que los dos de dominio solo acepten llamadas autenticadas del gateway:

```bash
gcloud run services update taxista-service --region $REGION --no-allow-unauthenticated
gcloud run services update calificacion-service --region $REGION --no-allow-unauthenticated
```

Eso exige que el gateway envíe un token de identidad de Google en cada llamada interna. Es la
mejora que más sube la nota en la parte de seguridad, pero déjenla para cuando lo demás funcione.

### 7. Automatizar

Con `GCP_PROJECT_ID` y `GCP_SA_KEY` cargados como secrets del repositorio, el workflow
`.github/workflows/deploy-cloudrun.yml` corre `mvn verify` y luego `scripts/deploy-cloudrun.sh` en
cada merge a `main`. El workflow `.github/workflows/ci.yml` compila y prueba en cada PR.

La cuenta de servicio de `GCP_SA_KEY` necesita los roles Cloud Run Admin, Artifact Registry Writer,
Service Account User y Secret Manager Secret Accessor. Para esa cuenta se crea la clave JSON con:

```bash
gcloud iam service-accounts keys create key.json --iam-account=NOMBRE@TU_PROJECT_ID.iam.gserviceaccount.com
```

Pega el contenido de `key.json` en el secret de GitHub y borra el archivo local. No lo commitees.

---

## Despliegue realizado

Proyecto de Google Cloud `roomtrip-taxistas`, región `us-central1`, repositorio de imágenes `taxi-web`.

| Servicio | URL pública |
|---|---|
| Portal (la web) | https://portal-web-7oe536zvqa-uc.a.run.app |
| Gateway (API de la app Android) | https://gateway-service-7oe536zvqa-uc.a.run.app |
| `taxista-service` y `calificacion-service` | Públicos por ahora, pero solo el gateway debe usarlos (ver el paso 6) |

La primera petición tras un rato sin tráfico tarda unos segundos (arranque en frío), porque los servicios
escalan a cero. El día de la demo conviene `--min-instances=1` en el gateway y volverlo a 0 después.

## Problemas encontrados al desplegar

Lo que falló en el primer despliegue real y cómo se resolvió; sirve para no repetirlo.

| Síntoma | Causa | Solución |
|---|---|---|
| `taxista-service` no arranca: `databaseName does not contain ' '` | La cadena de Atlas quedó guardada **triplicada** al pegarla | Armar la cadena desde la contraseña con `read -s` (paso 3), sin pegar la cadena completa |
| La imagen no arranca en Cloud Run (`exec format error`) | Imagen `arm64` construida en un Mac con Apple Silicon | Construir con `--platform linux/amd64` (ya lo hace el script) |
| Las redirecciones del portal salían como `http://` | El HTTPS termina en el proxy de Cloud Run y Spring no lo sabía | `server.forward-headers-strategy: framework` en el portal (ya incluido) |
| La URL llevaba `;jsessionid=...` | Seguimiento de sesión por URL | `tracking-modes: cookie` y cookie `Secure` en la nube (`SESSION_COOKIE_SECURE=true`) |
| El despliegue falla leyendo un secreto | Falta el permiso `secretmanager.secretAccessor` | Paso 3.1 |
| El login del Superadmin "no funciona" con la contraseña correcta | Se copió a mano con un `%` o espacios de más | Copiarla con `\| pbcopy` (paso 3) |
| La primera petición tras un rato sin uso devuelve `503` o `500` y las siguientes funcionan | Arranque en frío: `taxista-service` tarda 10 a 14 s en arrancar y el gateway esperaba 4 s | `GATEWAY_TIMEOUT=20s` en la nube, `--cpu-boost` al desplegar y `-XX:TieredStopAtLevel=1` en la JVM. Para una demo, abrir el portal unos minutos antes o usar `--min-instances=1` ese día |
| El script marca `FAIL` en un servicio recién publicado, pero responde bien después | `HTTP/2 PROTOCOL_ERROR` pasajero justo tras publicar la revisión | Volver a consultar su `/actuator/health` |
| El proceso del despliegue "se cayó" a medias y sigue sin terminar | La sesión que lo lanzó se cerró | El script es repetible: volver a correrlo (idealmente con `nohup`) |

## Variables de configuración

Todo se configura por variables de entorno. Las marcadas como *secreto* van en Secret Manager (paso 3),
nunca en el repositorio ni como `--set-env-vars`. Cloud Run inyecta `PORT` solo.

| Variable | Servicio | Por defecto | Para qué |
|---|---|---|---|
| `JWT_SECRET` *(secreto)* | los cuatro | — | Firma y valida los JWT; debe ser el mismo en todos y de 32+ caracteres |
| `JWT_EXPIRACION_MINUTOS` | taxista | `120` | Vigencia del token |
| `MONGO_URI_REGISTRY` *(secreto)* | taxista | Mongo local | Conexión a `taxi_registry` |
| `MONGO_URI_RATINGS` *(secreto)* | calificación | Mongo local | Conexión a `taxi_ratings` |
| `SUPERADMIN_CORREO` | taxista | `superadmin@roomtrip.pe` | Cuenta inicial del Superadmin |
| `SUPERADMIN_PASSWORD` *(secreto)* | taxista | `cambiame` | Su contraseña (12+ caracteres en producción) |
| `APP_MOVIL_CORREO` | taxista | `app-movil@roomtrip.pe` | Credencial de servicio de la app Android |
| `APP_MOVIL_PASSWORD` *(secreto)* | taxista | `cambiame` | Su contraseña (12+ caracteres en producción) |
| `EXIGIR_CLAVES_FUERTES` | taxista | `false` | `true` en producción: no arranca con claves por defecto o cortas |
| `LOGIN_MAX_INTENTOS` | taxista | `5` | Fallos seguidos antes de bloquear un correo |
| `LOGIN_BLOQUEO_MINUTOS` | taxista | `15` | Duración del bloqueo |
| `GATEWAY_TIMEOUT` | gateway | `4s` | Tiempo máximo de espera a un servicio. En Cloud Run el script lo sube a `20s` para aguantar el arranque en frío (10 a 14 s) |
| `TAXISTA_SERVICE_URL` | gateway, calificación | local | URL de `taxista-service` |
| `CALIFICACION_SERVICE_URL` | gateway | local | URL de `calificacion-service` |
| `GATEWAY_URL` | portal | `http://localhost:8080` | URL pública del gateway |
| `CLOUDINARY_URL` *(secreto)* | portal | vacío | Subida de fotos; sin ella el registro falla con un mensaje claro |
| `SESSION_TIMEOUT` | portal | `30m` | Inactividad máxima de la sesión |
| `THYMELEAF_CACHE` | portal | `false` | `true` en producción (el script lo activa) |
| `SESSION_COOKIE_SECURE` | portal | `false` | `true` en la nube (HTTPS): la cookie de sesión solo viaja cifrada (el script lo activa) |

`scripts/deploy-cloudrun.sh` ya activa `EXIGIR_CLAVES_FUERTES` y `THYMELEAF_CACHE`.

## Compilar y probar

```bash
mvn clean verify
```

Corre las pruebas de los cuatro módulos. `GatewayIntegrationTest` usa Testcontainers, así que **necesita
Docker corriendo**. Con Docker 29 o superior el POM raíz fija `-Dapi.version=1.44` para que el cliente
de Testcontainers hable con el demonio; no hace falta tocar nada. En GitHub Actions lo hace el job
`verificar` de `ci.yml` y `deploy-cloudrun.yml`.

---

## OPEX

### Escenario real del curso

Uso estimado: 5 desarrolladores, unas 30 solicitudes de taxista, unos cientos de requests al día
durante las demos, cuatro meses.

| Componente | Plan | Consumo estimado | Costo mensual |
|---|---|---|---|
| Cloud Run, 4 servicios | Free tier | Muy por debajo de 180 000 vCPU-s y 2 M de requests al mes | USD 0.00 |
| Artifact Registry | Free tier | ~2 GB de imágenes | USD 0.00 |
| Secret Manager | Free tier | 6 secretos, pocos accesos | USD 0.00 |
| MongoDB Atlas | M0 Free | < 50 MB | USD 0.00 |
| Cloudinary | Free | ~60 imágenes | USD 0.00 |
| **Total** | | | **USD 0.00** |

El proyecto completo corre en cero. Conviene igual poner un **presupuesto con alerta en USD 5** en
Cloud Billing: si alguien deja `--min-instances=1` encendido por descuido, el correo llega antes
que el cargo.

### Escenario de producción (para sustentar el entregable)

El OPEX interesante no es el del curso sino el que tendría el sistema operando de verdad. Supuesto:
500 taxistas activos, 2 000 servicios de taxi al mes, unos 300 000 requests mensuales.

| Componente | Configuración | Costo mensual estimado |
|---|---|---|
| Cloud Run | Gateway con 1 instancia mínima, resto escalando a cero | USD 12 a 18 |
| MongoDB Atlas | Flex / M10 según crecimiento | USD 9 a 60 |
| Cloudinary | Plan pago si se superan los 25 créditos | USD 0 a 89 |
| Secret Manager y Artifact Registry | Uso bajo | < USD 1 |
| **Total** | | **USD 21 a 168** |

El rango es amplio a propósito: depende de si se mantiene una instancia caliente y del tier de
Atlas. La conclusión que vale para el informe es que el costo está dominado por dos decisiones,
el arranque en frío y el tamaño de la base, y ambas son ajustables sin tocar el código.

> Los precios de los planes gratuitos cambian. Verificar en
> [cloud.google.com/run/pricing](https://cloud.google.com/run/pricing),
> [mongodb.com/pricing](https://www.mongodb.com/pricing) y
> [cloudinary.com/pricing](https://cloudinary.com/pricing) antes de entregar el documento final.

---

## Checklist antes de la presentación prefinal (14/12/2026)

- [ ] `./scripts/deploy-cloudrun.sh` termina sin `FAIL`.
- [ ] Los cuatro servicios responden en sus URLs de Cloud Run.
- [ ] El `/actuator/health` de cada uno devuelve `UP`.
- [ ] Un correo inventado se bloquea con 429 tras 5 logins fallidos, y el Superadmin sigue entrando.
- [ ] El recorrido completo de un traslado funciona: pedido → aceptar → en camino → en traslado →
      finalizar con QR, y el Superadmin lo ve en **Servicios** y en **Eventos**.
- [ ] Desactivar un taxista lo saca de `/disponibles`; reactivarlo lo devuelve.
- [ ] Un taxista se puede autoregistrar desde el portal en producción.
- [ ] El Superadmin aprueba esa solicitud y el taxista aparece en el padrón.
- [ ] La app Android lista taxistas disponibles contra el gateway de producción.
- [ ] Apagar `taxista-service` y comprobar que la app móvil muestra el aviso y sigue reservando.
- [ ] `--min-instances=1` en el gateway el día de la demo, de vuelta a 0 al terminar.
- [ ] Presupuesto con alerta configurado en Cloud Billing.
