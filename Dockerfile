# ============================================================
# Imagen de ms-prestamos (multi-stage)
# ============================================================
# Etapa 1 (build):   compila el proyecto con el Maven Wrapper.
# Etapa 2 (runtime): solo JRE 21 y el .jar, sin JDK, Maven ni codigo fuente.
#
# Las pruebas NO se ejecutan aqui: las corre el job de CI antes de construir
# la imagen.
#
#   docker build -t ms-prestamos .
#   docker run --rm -p 9005:9005 ms-prestamos
# ============================================================

# ── Etapa 1: build ───────────────────────────────────────────
FROM eclipse-temurin:24-jdk-alpine AS build
WORKDIR /build

# Primero solo lo que define las dependencias: mientras el pom.xml no cambie,
# Docker reutiliza esta capa y no vuelve a descargarlas.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src src
RUN ./mvnw -B -q package -DskipTests

# ── Etapa 2: runtime ─────────────────────────────────────────
FROM eclipse-temurin:24-jre-alpine

# Version y commit quedan grabados en la imagen: permiten rastrear un
# contenedor en ejecucion hasta el codigo que lo genero.
ARG VERSION=dev
ARG GIT_SHA=local
LABEL org.opencontainers.image.source="https://github.com/juatapiaoing/EV1-despliegue-801D" \
      org.opencontainers.image.version="${VERSION}" \
      org.opencontainers.image.revision="${GIT_SHA}"

# Usuario sin privilegios: si alguien explota la aplicacion, no obtiene root
# dentro del contenedor.
RUN addgroup -S app && adduser -S -G app app
WORKDIR /application
COPY --from=build --chown=app:app /build/target/ms-prestamos-*.jar application.jar
USER app

# La JVM usa hasta el 75 % del limite de memoria del contenedor (y no el de la
# maquina anfitriona), asi respeta el limite que fije el orquestador.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

EXPOSE 9005

HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
  CMD wget -qO- http://localhost:9005/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["java", "-jar", "application.jar"]
