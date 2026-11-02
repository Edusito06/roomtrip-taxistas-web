# Manual de instalación

Cómo instalar y poner en marcha el **subsistema web de gestión de taxistas** de RoomTrip en una
computadora, y cómo comprobar que quedó bien. Está pensado para quien lo recibe por primera vez (el
jefe de práctica, el profesor o un compañero): no hace falta haber participado en el desarrollo.

La instalación en la nube (Google Cloud Run) tiene su propio documento: [`DESPLIEGUE.md`](DESPLIEGUE.md).

---

## 1. Qué se instala

Cinco contenedores que se levantan juntos con un solo comando:

```
  Navegador ──▶ portal-web :8083 ──▶ gateway-service :8080 ──┬─▶ taxista-service :8081 ──┐
  App Android ─────────────────────▶ gateway-service :8080 ──┘                           ├─▶ MongoDB :27017
                                                              └─▶ calificacion-service :8082 ┘
```

| Contenedor | Puerto | Qué es |
|---|---|---|
| `portal-web` | 8083 | La web: registro de taxistas y panel del Superadmin |
| `gateway-service` | 8080 | La puerta de la API REST que usa la app Android |
| `taxista-service` | 8081 | Taxistas, servicios de taxi, eventos y autenticación |
| `calificacion-service` | 8082 | Calificaciones y valoración promedio |
| `mongo` | 27017 | La base de datos (MongoDB 7) |

No se necesita ninguna cuenta en la nube para instalarlo en local: Mongo corre en un contenedor.

---

## 2. Requisitos

| Necesitas | Para qué | Versión |
|---|---|---|
| **Docker Desktop** | Correr todo el sistema | Reciente (probado con Docker 29) |
| **Git** | Descargar el código | Cualquiera |
| JDK 21 y Maven 3.9+ | **Solo** para compilar y correr las pruebas en tu máquina | JDK 21 (no 22 ni superior) |
| Cuenta de Cloudinary | **Solo** para subir fotos en el registro | Gratuita, sin tarjeta |

Para **solo correr el sistema** bastan Docker y Git: las imágenes se compilan dentro de Docker, así que no
hace falta tener Java ni Maven instalados.

Recursos recomendados: 8 GB de memoria en la computadora (el sistema levanta cuatro aplicaciones Java y
Mongo) y unos 5 GB de disco libre (las imágenes ocupan unos 2,2 GB, más la caché de construcción). La primera construcción descarga dependencias y
tarda varios minutos; las siguientes son rápidas.

### Instalar los requisitos

**macOS** (con [Homebrew](https://brew.sh)):

```bash
brew install --cask docker     # Docker Desktop
brew install git
brew install openjdk@21 maven  # opcional: solo para compilar y probar
```

Después abre la aplicación **Docker** una vez, acepta los permisos y espera a que su ícono indique que
está corriendo.

**Windows:** instala [Docker Desktop](https://www.docker.com/products/docker-desktop/) (con WSL 2 activado)
y [Git for Windows](https://git-scm.com). Los comandos de este manual se escriben en PowerShell; donde
dice `cp` usa `copy`.

**Linux:** instala Docker Engine con el plugin `docker compose` y Git desde el gestor de paquetes de tu
distribución.

---

## 3. Instalación paso a paso

### Paso 1. Descargar el código

```bash
git clone https://github.com/Edusito06/roomtrip-taxistas-web.git
cd roomtrip-taxistas-web
```

Si el repositorio es privado, tu cuenta de GitHub necesita acceso.

### Paso 2. Crear el archivo de configuración

```bash
cp .env.example .env
```

El archivo `.env` guarda las claves y **nunca se sube al repositorio** (está en `.gitignore`). Ábrelo con
cualquier editor y completa, como mínimo, estas líneas:

| Variable | Qué poner |
|---|---|
| `JWT_SECRET` | Un secreto largo y propio, de al menos 32 caracteres. Se genera con `openssl rand -base64 48` |
| `SUPERADMIN_PASSWORD` | La contraseña con la que entrarás como Superadmin |
| `APP_MOVIL_PASSWORD` | La contraseña de la credencial de servicio de la app Android |
| `CLOUDINARY_URL` | La URL de tu cuenta de Cloudinary (ver más abajo). Si no subes fotos, déjala como está |

Reglas del archivo: una variable por línea con la forma `NOMBRE=valor`, **sin comillas ni espacios** y sin
dejar líneas sueltas de texto (una línea sin `NOMBRE=` rompe la lectura de todo el archivo).

Las líneas `MONGO_URI_*` y `*_URL` de servicios internos no se tocan en local: Docker ya las resuelve.

**Cloudinary (para las fotos).** Crea una cuenta gratuita en cloudinary.com. En su panel busca
*API Environment variable*: es una línea `cloudinary://<clave>:<secreto>@<nombre>`. Cópiala completa y
**quita los símbolos `<` y `>`**, que son solo marcadores. Ejemplo de forma final:
`CLOUDINARY_URL=cloudinary://123456789012345:AbCdEfGhIjKlMnOp@micuenta`.

### Paso 3. Levantar el sistema

```bash
docker compose up --build -d
```

La primera vez construye las cuatro imágenes (varios minutos). `-d` lo deja corriendo en segundo plano.

### Paso 4. Esperar a que arranque

Los servicios tardan unos segundos en estar listos después de que el contenedor aparece como iniciado.
Comprueba el estado:

```bash
docker compose ps
```

Los cinco contenedores deben figurar como `Up` (y `mongo` como `healthy`).

### Paso 5. Entrar a la web

Abre **http://localhost:8083**. Debe aparecer la pantalla de inicio de sesión de RoomTrip Taxi.

Entra como Superadmin con el correo `superadmin@roomtrip.pe` (o el que hayas puesto en
`SUPERADMIN_CORREO`) y la contraseña de `SUPERADMIN_PASSWORD`.

---

## 4. Cómo comprobar que quedó bien

| Comprobación | Resultado esperado |
|---|---|
| `docker compose ps` | Cinco contenedores `Up`, `mongo` en `healthy` |
| `curl http://localhost:8083/actuator/health` | Una respuesta con `"status":"UP"` (igual en los puertos 8080, 8081 y 8082) |
| Abrir http://localhost:8083 | La pantalla de login con el diseño turquesa de RoomTrip |
| Entrar como Superadmin | Se abre **Solicitudes pendientes** (vacía en una instalación nueva) |
| Abrir **Padrón**, **Servicios** y **Eventos** | Las tres cargan sin error; en **Eventos** aparece tu inicio de sesión |

**Prueba de punta a punta (5 minutos).**

1. Cierra sesión y abre `/registro` (enlace "Crear solicitud"). Llena el formulario con datos ficticios y dos
   fotos (JPG o PNG de hasta 5 MB). Debe responder que la solicitud quedó pendiente.
2. Entra como Superadmin → **Solicitudes**: aparece la solicitud con sus fotos. Pulsa **Aprobar**.
3. Cierra sesión y entra con el correo y la contraseña del taxista: **Mi cuenta** muestra *Aprobado* y deja
   marcarse como disponible.
4. Como Superadmin, **Padrón** muestra al taxista como `APROBADO`, y **Eventos** registra el registro, la
   aprobación y los inicios de sesión.

Si los cuatro pasos funcionan, la instalación está completa.

> **No es un error:** abrir `http://localhost:8080` o `http://localhost:8081` en el navegador muestra
> **401**. Son APIs y exigen un token; la web se usa siempre desde el puerto **8083**.

---

## 5. Uso diario

| Quieres | Comando |
|---|---|
| Ver el estado | `docker compose ps` |
| Ver los registros de un servicio | `docker compose logs -f taxista-service` |
| Apagar todo (conserva los datos) | `docker compose down` |
| Encender de nuevo | `docker compose up -d` |
| Aplicar cambios de código | `docker compose up --build -d` |
| **Borrar también todos los datos** y empezar de cero | `docker compose down -v` |

Los datos viven en un volumen de Docker (`mongo-data`): sobreviven a `down` y a los reinicios, pero
`down -v` los elimina.

Si cambias una variable del `.env`, aplícala con `docker compose up -d --force-recreate`. Si cambias la
contraseña del Superadmin o de la app móvil, al reiniciar `taxista-service` se actualiza la de la cuenta
existente.

---

## 6. Para desarrolladores

**Compilar y correr las pruebas** (necesita JDK 21, Maven y Docker corriendo, porque una prueba usa
Testcontainers):

```bash
mvn clean verify
```

Debe terminar en `BUILD SUCCESS` con unas 140 pruebas en verde. Si tienes varias versiones de Java, fija
la 21 antes de correrlo:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)    # macOS
```

**Correr un solo servicio sin levantar todo:**

```bash
docker compose up -d mongo
mvn -pl taxista-service -am spring-boot:run
```

La estructura del código, la API y las decisiones de diseño están en [`API.md`](API.md),
[`ARQUITECTURA.md`](ARQUITECTURA.md) y [`ESQUEMA-BD.md`](ESQUEMA-BD.md).

---

## 7. Problemas frecuentes

| Lo que ves | Causa | Solución |
|---|---|---|
| `Cannot connect to the Docker daemon` | Docker Desktop no está abierto | Ábrelo y espera a que termine de iniciar; repite el comando |
| `failed to read .env: line N: unexpected character` | Hay una línea suelta o con formato incorrecto en el `.env` | Corrige la línea N: debe ser `NOMBRE=valor`, sin espacios ni comillas |
| El navegador muestra **401** en el puerto 8080 u 8081 | Son APIs, no la web | Usa http://localhost:8083 |
| `port is already allocated` o `address already in use` | Otro programa usa 8080–8083 o 27017 | Cierra ese programa, o apaga otra copia del sistema con `docker compose down` |
| El portal carga pero el login falla justo después de reiniciar | Los servicios aún están arrancando | Espera medio minuto y reintenta |
| *"No se pudo subir la imagen a Cloudinary"* | `CLOUDINARY_URL` con valores de ejemplo o mal copiada (con `<>`) | Corrige la línea en el `.env` y aplica con `docker compose up -d --force-recreate portal-web` |
| *"El portal no tiene configurado Cloudinary"* | `CLOUDINARY_URL` vacía | Agrega la URL de tu cuenta, como en el paso 2 |
| *"La foto debe ser una imagen JPG, PNG o WebP"* | El archivo no es una imagen real (se revisa su contenido, no su extensión) | Sube un JPG, PNG o WebP auténtico |
| *"Demasiados intentos fallidos"* | 5 contraseñas incorrectas seguidas para ese correo | Espera 15 minutos o reinicia `taxista-service` (`docker compose restart taxista-service`) |
| `mvn: command not found` | Maven no está instalado | Instálalo (ver sección 2) o corre solo con Docker |
| `Unable to locate a Java Runtime` o Maven usa otra versión | No hay JDK 21 en uso | Instala JDK 21 y fija `JAVA_HOME` (sección 6) |
| `mvn verify` falla en `GatewayIntegrationTest` | Docker no está corriendo | Abre Docker Desktop y repite |
| La sesión del portal se cierra sola | Expira tras 30 minutos sin actividad | Es el comportamiento esperado; vuelve a iniciar sesión |

Para ver qué falló en un servicio:

```bash
docker compose logs taxista-service --tail 100
```

---

## 8. Instalación en la nube

Para publicar el sistema en Google Cloud Run (con MongoDB Atlas y Cloudinary) sigue
[`DESPLIEGUE.md`](DESPLIEGUE.md). Resume lo que cambia respecto a esta instalación local:

- La base de datos pasa de un contenedor a un clúster gratuito de **MongoDB Atlas**.
- Los secretos (JWT, claves, conexiones) van en **Secret Manager** y no en un archivo `.env`.
- En producción el servicio exige claves de al menos 12 caracteres y rechaza `cambiame`.
- `scripts/deploy-cloudrun.sh` hace el despliegue y verifica la salud de cada servicio.

---

## 9. Desinstalar

```bash
docker compose down -v        # detiene todo y borra los datos
docker image prune -a         # opcional: libera el espacio de las imágenes
```

Después puedes borrar la carpeta del proyecto. Si no vas a usar más Docker, también puedes desinstalar
Docker Desktop de forma normal.
