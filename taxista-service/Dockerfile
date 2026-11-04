# --- Etapa 1: compilar ---
# El JAR no depende de la arquitectura: se compila en la del equipo que construye (rapido) y solo
# la etapa final se fuerza a la arquitectura del destino (BuildKit rellena TARGETPLATFORM).
FROM --platform=$BUILDPLATFORM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copiar primero los POMs para aprovechar la cache de capas de Docker:
# si no cambian las dependencias, esta capa se reutiliza.
COPY pom.xml .
COPY gateway-service/pom.xml gateway-service/
COPY taxista-service/pom.xml taxista-service/
COPY calificacion-service/pom.xml calificacion-service/
COPY portal-web/pom.xml portal-web/
RUN mvn -q -pl taxista-service -am dependency:go-offline -DskipTests

COPY . .
RUN mvn -q -pl taxista-service -am clean package -DskipTests

# --- Etapa 2: ejecutar ---
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Usuario sin privilegios: buena practica y requisito de varios scanners
RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /build/taxista-service/target/*.jar app.jar

# Cloud Run inyecta PORT; en local cae al 8081 del application.yml
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"
EXPOSE 8081

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
