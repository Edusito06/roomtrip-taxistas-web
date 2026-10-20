#!/usr/bin/env bash
# Despliega los cuatro servicios en Google Cloud Run, en el orden que exigen sus dependencias:
# taxista-service -> calificacion-service -> gateway-service -> portal-web.
#
# Requisitos previos (ver docs/DESPLIEGUE.md, pasos 0 a 4): gcloud autenticado, Docker con acceso
# a Artifact Registry y los secretos ya creados en Secret Manager.
#
# Uso:
#   PROJECT_ID=mi-proyecto ./scripts/deploy-cloudrun.sh [tag]
# Variables opcionales: REGION (us-central1), REPO_NAME (taxi-web), SUPERADMIN_CORREO, APP_MOVIL_CORREO.
set -euo pipefail

: "${PROJECT_ID:?Define PROJECT_ID con el id del proyecto de Google Cloud}"
REGION="${REGION:-us-central1}"
REPO_NAME="${REPO_NAME:-taxi-web}"
TAG="${1:-$(git rev-parse --short HEAD)}"
REPO="${REGION}-docker.pkg.dev/${PROJECT_ID}/${REPO_NAME}"
SUPERADMIN_CORREO="${SUPERADMIN_CORREO:-superadmin@roomtrip.pe}"
APP_MOVIL_CORREO="${APP_MOVIL_CORREO:-app-movil@roomtrip.pe}"

cd "$(dirname "$0")/.."

# Flags comunes: escala a cero y tope de 3 instancias para no salirse del free tier.
COMUN=(--region "$REGION" --allow-unauthenticated --memory 512Mi --cpu 1 --min-instances 0 --max-instances 3)

construir() {
  local servicio="$1"
  echo "==> Construyendo ${servicio}:${TAG}"
  docker build -f "${servicio}/Dockerfile" -t "${REPO}/${servicio}:${TAG}" .
  docker push "${REPO}/${servicio}:${TAG}"
}

url_de() {
  gcloud run services describe "$1" --region "$REGION" --format 'value(status.url)'
}

for s in taxista-service calificacion-service gateway-service portal-web; do
  construir "$s"
done

echo "==> Desplegando taxista-service"
gcloud run deploy taxista-service --image "${REPO}/taxista-service:${TAG}" "${COMUN[@]}" \
  --set-env-vars "SUPERADMIN_CORREO=${SUPERADMIN_CORREO},APP_MOVIL_CORREO=${APP_MOVIL_CORREO},EXIGIR_CLAVES_FUERTES=true" \
  --set-secrets "JWT_SECRET=jwt-secret:latest,MONGO_URI_REGISTRY=mongo-uri-registry:latest,SUPERADMIN_PASSWORD=superadmin-password:latest,APP_MOVIL_PASSWORD=app-movil-password:latest"
TAXISTA_URL="$(url_de taxista-service)"

# calificacion-service empuja la valoracion promedio a taxista-service (RN-013), por eso
# necesita su URL ademas del secreto JWT compartido.
echo "==> Desplegando calificacion-service"
gcloud run deploy calificacion-service --image "${REPO}/calificacion-service:${TAG}" "${COMUN[@]}" \
  --set-env-vars "TAXISTA_SERVICE_URL=${TAXISTA_URL}" \
  --set-secrets "JWT_SECRET=jwt-secret:latest,MONGO_URI_RATINGS=mongo-uri-ratings:latest"
CALIF_URL="$(url_de calificacion-service)"

echo "==> Desplegando gateway-service"
gcloud run deploy gateway-service --image "${REPO}/gateway-service:${TAG}" "${COMUN[@]}" \
  --set-env-vars "TAXISTA_SERVICE_URL=${TAXISTA_URL},CALIFICACION_SERVICE_URL=${CALIF_URL}" \
  --set-secrets "JWT_SECRET=jwt-secret:latest"
GATEWAY_URL="$(url_de gateway-service)"

# El portal sube las fotos a Cloudinary, asi que tambien lleva CLOUDINARY_URL.
echo "==> Desplegando portal-web"
gcloud run deploy portal-web --image "${REPO}/portal-web:${TAG}" "${COMUN[@]}" \
  --set-env-vars "GATEWAY_URL=${GATEWAY_URL},THYMELEAF_CACHE=true" \
  --set-secrets "CLOUDINARY_URL=cloudinary-url:latest"
PORTAL_URL="$(url_de portal-web)"

echo "==> Verificando /actuator/health"
fallo=0
for par in "taxista-service=${TAXISTA_URL}" "calificacion-service=${CALIF_URL}" \
           "gateway-service=${GATEWAY_URL}" "portal-web=${PORTAL_URL}"; do
  nombre="${par%%=*}"; url="${par#*=}"
  # El primer request puede tardar por el arranque en frio: se reintenta.
  if curl -fsS --retry 6 --retry-delay 5 --retry-all-errors --max-time 30 "${url}/actuator/health" | grep -q '"UP"'; then
    echo "  OK   ${nombre}  ${url}"
  else
    echo "  FAIL ${nombre}  ${url}"; fallo=1
  fi
done

echo
echo "Portal: ${PORTAL_URL}"
echo "Gateway (API para la app Android): ${GATEWAY_URL}"
exit "$fallo"
