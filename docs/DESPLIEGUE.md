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

Nunca en el repositorio, nunca en el `Dockerfile`, nunca como `--set-env-vars`.

```bash
# Secreto del JWT, compartido por los cuatro servicios
openssl rand -base64 48 | gcloud secrets create jwt-secret --data-file=-

# Conexiones a Atlas (una por servicio, bases distintas)
echo -n "mongodb+srv://usuario:clave@cluster.mongodb.net/taxi_registry" \
  | gcloud secrets create mongo-uri-registry --data-file=-
echo -n "mongodb+srv://usuario:clave@cluster.mongodb.net/taxi_ratings" \
  | gcloud secrets create mongo-uri-ratings --data-file=-

# Cloudinary
echo -n "cloudinary://api_key:api_secret@cloud_name" \
  | gcloud secrets create cloudinary-url --data-file=-

# Credenciales iniciales
echo -n "UNA_CLAVE_FUERTE" | gcloud secrets create superadmin-password --data-file=-
echo -n "OTRA_CLAVE_FUERTE" | gcloud secrets create app-movil-password --data-file=-
```

Las claves deben tener **al menos 12 caracteres** y no pueden ser `cambiame`. El script de despliegue
activa `EXIGIR_CLAVES_FUERTES=true` en `taxista-service`: si `JWT_SECRET`, `SUPERADMIN_PASSWORD` o
`APP_MOVIL_PASSWORD` son por defecto o cortas, el servicio **se niega a arrancar** en vez de quedar expuesto.
Para rotar una clave, cambia el secreto en Secret Manager y vuelve a desplegar: al arrancar, el servicio
actualiza la contraseña de la cuenta existente.

### 4. Repositorio de imágenes

```bash
gcloud artifacts repositories create taxi-web \
  --repository-format=docker \
  --location=us-central1 \
  --description="Imagenes del subsistema web de taxistas"

gcloud auth configure-docker us-central1-docker.pkg.dev
```

### 5. Desplegar

**Camino corto:** `scripts/deploy-cloudrun.sh` hace todo lo de este paso (construir, subir, desplegar
en orden y verificar `/actuator/health`). Es el mismo script que usa el workflow del paso 7.

```bash
PROJECT_ID=TU_PROJECT_ID ./scripts/deploy-cloudrun.sh v1
```

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
docker build -f taxista-service/Dockerfile -t $REPO/taxista-service:v1 .
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

docker build -f calificacion-service/Dockerfile -t $REPO/calificacion-service:v1 .
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

docker build -f gateway-service/Dockerfile -t $REPO/gateway-service:v1 .
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

docker build -f portal-web/Dockerfile -t $REPO/portal-web:v1 .
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
- [ ] Un taxista se puede autoregistrar desde el portal en producción.
- [ ] El Superadmin aprueba esa solicitud y el taxista aparece en el padrón.
- [ ] La app Android lista taxistas disponibles contra el gateway de producción.
- [ ] Apagar `taxista-service` y comprobar que la app móvil muestra el aviso y sigue reservando.
- [ ] `--min-instances=1` en el gateway el día de la demo, de vuelta a 0 al terminar.
- [ ] Presupuesto con alerta configurado en Cloud Billing.
