# ============================================================
# Imagen de ms-prestamos (multi-stage)
# ============================================================
# Etapa 1 (build):   compila el proyecto con el Maven Wrapper y divide el
#                    .jar en capas (dependencias / aplicacion).
# Etapa 2 (runtime): solo JRE 21 + las capas extraidas, sin JDK, sin Maven
#                    ni codigo fuente. Corre con un usuario sin privilegios.
#
# Las pruebas NO se ejecutan aqui: las corre el job de CI antes de construir
# la imagen. Asi un cambio solo llega a esta etapa si ya paso las pruebas.
#
#   docker build -t ms-prestamos .
#   docker run --rm -p 9005:9005 ms-prestamos
# ============================================================

# ── Etapa 1: build ───────────────────────────────────────────
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /build

# Primero solo lo que define las dependencias: mientras el pom.xml no cambie,
# Docker reutiliza esta capa y no vuelve a descargar medio Maven Central.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
# El extractor conserva el nombre con version (ms-prestamos-1.0.4.jar). Se
# renombra a application.jar para que el ENTRYPOINT no cambie con cada release.
RUN ./mvnw -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/ms-prestamos-*.jar extract --layers --destination extracted \
 && mv extracted/application/ms-prestamos-*.jar extracted/application/application.jar

# ── Etapa 2: runtime ─────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine AS runtime

# Metadatos OCI: permiten saber, a partir de la imagen, que commit y que
# version la generaron (trazabilidad). Los inyecta el pipeline.
ARG VERSION=dev
ARG GIT_SHA=local
ARG BUILD_DATE=unknown
LABEL org.opencontainers.image.title="ms-prestamos" \
      org.opencontainers.image.description="Microservicio de gestion de prestamos de biblioteca" \
      org.opencontainers.image.source="https://github.com/juatapiaoing/EV1-despliegue-801D" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${GIT_SHA}" \
      org.opencontainers.image.created="${BUILD_DATE}"

# Usuario sin privilegios: si alguien explota la aplicacion, no obtiene root
# dentro del contenedor.
RUN addgroup -S app && adduser -S -G app app
WORKDIR /application

# Las capas van de la que menos cambia a la que mas cambia: en cada despliegue
# solo se transfiere la capa "application" (unos pocos KB), no las dependencias.
COPY --from=build --chown=app:app /build/extracted/dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /build/extracted/application/ ./

USER app

# Parametros de memoria de la JVM: toma el 75 % del limite del contenedor (en
# lugar de la RAM del host) y se cae si se queda sin heap para que el
# orquestador lo reinicie. Se pueden sobrescribir con -e JAVA_TOOL_OPTIONS=...
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

EXPOSE 9005

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:9005/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "application.jar"]
