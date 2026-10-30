# ms-prestamos

[![CI - Build y pruebas](https://github.com/juatapiaoing/EV1-despliegue-801D/actions/workflows/ci.yml/badge.svg?branch=develop)](https://github.com/juatapiaoing/EV1-despliegue-801D/actions/workflows/ci.yml)
[![CD - Despliegue con Docker Compose](https://github.com/juatapiaoing/EV1-despliegue-801D/actions/workflows/cd-deploy.yml/badge.svg?branch=main)](https://github.com/juatapiaoing/EV1-despliegue-801D/actions/workflows/cd-deploy.yml)
[![Quality Gate](https://sonarcloud.io/api/project_badges/measure?project=juatapiaoing_EV1-despliegue-801D&metric=alert_status)](https://sonarcloud.io/project/overview?id=juatapiaoing_EV1-despliegue-801D)
[![Cobertura](https://sonarcloud.io/api/project_badges/measure?project=juatapiaoing_EV1-despliegue-801D&metric=coverage)](https://sonarcloud.io/project/overview?id=juatapiaoing_EV1-despliegue-801D)

Microservicio de gestión de préstamos de una biblioteca. Registra el retiro de un
ejemplar por parte de un socio, mantiene el historial de cada usuario, permite
registrar la devolución y detecta los préstamos atrasados.

Este repositorio reúne las dos evaluaciones parciales de **Ingeniería DevOps
(DOY0101, Duoc UC)**:

- **Evaluación Parcial 1:** flujo de ramificación GitFlow, simulación de trabajo
  colaborativo con pull requests, integración continua con GitHub Actions y
  despliegue en una instancia EC2 de AWS (secciones 10 a 12).
- **Evaluación Parcial 2:** el microservicio se contenerizó con Docker y su pipeline
  CI/CD ahora ejecuta pruebas con umbral de cobertura, análisis de código con
  SonarQube Cloud, vigilancia de dependencias con Dependabot y despliegue
  automático con Docker Compose (secciones 9 y 13).

## Índice

1. [Contexto](#1-contexto)
2. [Stack técnico](#2-stack-técnico)
3. [Estructura del repositorio](#3-estructura-del-repositorio)
4. [Modelo de dominio](#4-modelo-de-dominio)
5. [API REST](#5-api-rest)
6. [Ejecución local](#6-ejecución-local)
7. [Perfiles y configuración](#7-perfiles-y-configuración)
8. [Pruebas](#8-pruebas)
9. [Contenedores y orquestación](#9-contenedores-y-orquestación)
10. [Estrategia de ramificación](#10-estrategia-de-ramificación)
11. [Guía de buenas prácticas del repositorio](#11-guía-de-buenas-prácticas-del-repositorio)
12. [Simulación de trabajo colaborativo y trazabilidad](#12-simulación-de-trabajo-colaborativo-y-trazabilidad)
13. [Integración y despliegue continuo](#13-integración-y-despliegue-continuo)
14. [Declaración de uso de Inteligencia Artificial](#14-declaración-de-uso-de-inteligencia-artificial)
15. [Conclusiones y reflexión personal](#15-conclusiones-y-reflexión-personal)

---

## 1. Contexto

El microservicio nace del proyecto *Biblioteca* desarrollado en la asignatura de
Arquitectura de Software, pero fue reescrito como **servicio autónomo**: no se
registra en Eureka, no pasa por un API Gateway y no valida tokens JWT. Esa
decisión es deliberada: un artefacto que se despliega solo, con un único `.jar`
y sin dependencias de otros servicios, es el caso más simple sobre el que montar
un pipeline de CI/CD y el que permite concentrar el esfuerzo en el flujo DevOps y
no en la orquestación.

## 2. Stack técnico

| Componente | Versión | Rol |
|---|---|---|
| Java | 21 (LTS) | Lenguaje y runtime |
| Spring Boot | 3.5.15 | Framework base |
| Spring Web | — | Controladores REST |
| Spring Data JPA + Hibernate | — | Persistencia |
| Spring HATEOAS | — | Enlaces de navegación (`_links`) |
| Spring Boot Actuator | — | `/actuator/health` como healthcheck del despliegue |
| Bean Validation | — | Validación declarativa de los DTO |
| MapStruct | 1.5.5.Final | Mapeo entidad ↔ DTO en tiempo de compilación |
| Lombok | 1.18.44 | Reducción de boilerplate |
| springdoc-openapi | 2.8.17 | Documentación Swagger UI |
| H2 | 2.3.232 | Base de datos en memoria (perfil `dev` y CI) |
| MySQL | 8.4 | Base de datos del perfil `prod` (contenedor `db`) |
| Maven Wrapper | 3.9.14 | Build reproducible sin instalar Maven |
| Docker | — | Imagen del microservicio (multi-stage, JRE 21 alpine) |
| Docker Compose + nginx | — | Orquestación de proxy, API y base de datos |
| JaCoCo | 0.8.13 | Cobertura de pruebas con umbral que bloquea el build |
| SonarQube Cloud | — | Análisis de código y Quality Gate |
| Dependabot | — | Vigilancia de dependencias (Maven, Docker, Actions) |
| GitHub Actions | — | Integración y despliegue continuo |
| AWS EC2 + systemd | Ubuntu 24.04 | Despliegue de la EP1 (ver `docs/despliegue-aws.md`) |

## 3. Estructura del repositorio

```
EV1-despliegue-801D/
├── .github/
│   ├── dependabot.yml             Dependabot: revisión semanal de Maven, Docker y Actions
│   ├── pull_request_template.md   Plantilla que guía la descripción y revisión de cada PR
│   └── workflows/
│       ├── ci.yml                 CI: pruebas, cobertura, SonarQube Cloud y build de la imagen
│       └── cd-deploy.yml          CD: despliegue con Docker Compose en cada push a main
├── .mvn/wrapper/                  Maven Wrapper (build reproducible)
├── nginx/
│   └── default.conf               Configuración del proxy que balancea entre las réplicas
├── db/
│   └── init-prestamos.sql         Creación de BD y usuario de aplicación en MySQL (instalación manual)
├── docs/
│   ├── despliegue-aws.md          Guía de despliegue en EC2 (aprovisionamiento, CD, operación)
│   ├── evidencias/                Salidas reales del despliegue en EC2 y runs de CD
│   └── enunciado/                 Material de la evaluación
├── infra/aws/
│   ├── provision-ec2.sh           Aprovisiona llaves, security group e instancia con la AWS CLI
│   └── user-data.sh               cloud-init: JRE 21, MySQL, credenciales y unidad systemd
├── src/
│   ├── main/
│   │   ├── java/cl/jctapia/prestamos/
│   │   │   ├── config/            Configuración de OpenAPI
│   │   │   ├── controller/        Capa HTTP (REST + HATEOAS + Swagger)
│   │   │   ├── dto/               Contratos de entrada y salida
│   │   │   ├── exception/         Excepciones propias y manejador global
│   │   │   ├── mapper/            MapStruct: entidad ↔ DTO
│   │   │   ├── model/             Entidad JPA y enum de estados
│   │   │   ├── repository/        Spring Data JPA
│   │   │   └── service/           Reglas de negocio
│   │   └── resources/
│   │       ├── application.yml        Configuración común
│   │       ├── application-dev.yml    H2 en memoria
│   │       ├── application-prod.yml   MySQL vía variables de entorno
│   │       └── data.sql               Datos de prueba (solo perfil dev)
│   └── test/java/cl/jctapia/prestamos/
│       ├── MsPrestamosApplicationTests.java   Prueba de arranque del contexto
│       ├── PrestamoApiIntegrationTest.java    Recorridos completos de la API
│       ├── controller/PrestamoControllerTest.java   Contrato HTTP (capa web)
│       ├── mapper/PrestamoMapperTest.java     Campos protegidos del mapper
│       ├── repository/PrestamoRepositoryTest.java   Consultas SQL contra H2
│       └── service/PrestamoServiceTest.java   Reglas de negocio con Mockito
├── .dockerignore                  Excluye del contexto de build lo que no debe entrar a la imagen
├── .env.example                   Plantilla de credenciales para Docker Compose
├── .gitattributes                 Finales de línea LF para scripts que corren en Linux
├── .gitignore                     Excluye target/, IDE y secretos (.env, *.pem)
├── Dockerfile                     Imagen multi-stage del microservicio
├── compose.yaml                   Orquestación: proxy + API + MySQL
├── lombok.config                  Hace que JaCoCo ignore el código generado por Lombok
├── mvnw / mvnw.cmd
├── pom.xml
└── README.md
```

La estructura sigue una **arquitectura en capas**: `controller → service →
repository`, con los DTO como frontera hacia el exterior. Ninguna capa salta a la
siguiente: el controlador nunca toca el repositorio y la entidad JPA nunca se
serializa directamente hacia el cliente.

## 4. Modelo de dominio

### Entidad `Prestamo`

| Campo | Tipo | Notas |
|---|---|---|
| `id` | `Long` | Autogenerado |
| `codigoLibro` | `String(20)` | Código interno del ejemplar físico, no el ISBN del título |
| `rutUsuario` | `String(12)` | RUT del socio, formato `12345678-9` |
| `fechaPrestamo` | `LocalDate` | Día en que se retira el ejemplar |
| `fechaVencimiento` | `LocalDate` | Fecha límite de devolución |
| `fechaDevolucion` | `LocalDate` | `null` mientras el ejemplar no regrese |
| `estado` | `EstadoPrestamo` | `VIGENTE` · `DEVUELTO` · `CANCELADO` |
| `observacion` | `String(255)` | Texto libre, opcional |

El **atraso no se persiste**: se deriva comparando `fechaVencimiento` con la fecha
actual mientras el préstamo siga `VIGENTE`. Guardarlo como columna obligaría a un
proceso que recorriera la tabla todos los días para mantenerla al corriente.

### Reglas de negocio

1. La fecha de vencimiento debe ser **posterior** a la fecha de préstamo.
2. Un mismo ejemplar **no puede tener dos préstamos `VIGENTE` simultáneos**.
3. Todo préstamo nace en estado `VIGENTE`; el estado y la fecha de devolución no
   se pueden alterar desde el `PUT`, para que nadie cierre un préstamo enviando un
   JSON manipulado.
4. La **devolución** es la única vía para cerrar un préstamo y solo aplica a
   préstamos `VIGENTE`; fija la fecha de devolución en el día actual.
5. Un préstamo está **atrasado** si sigue `VIGENTE` y su vencimiento es anterior a
   hoy. El que vence hoy todavía no lo está.
6. Un préstamo `VIGENTE` **no se puede eliminar**: el ejemplar sigue fuera de la
   biblioteca y borrar el registro haría perder su rastro. Primero se devuelve o
   se cancela (corregido en el hotfix `v1.0.1`).

## 5. API REST

Base: `http://localhost:9005/api/v1/prestamos`

| Método | Ruta | Descripción | Códigos |
|---|---|---|---|
| `GET` | `/` | Lista todos los préstamos | 200 |
| `GET` | `/{id}` | Obtiene un préstamo por ID | 200, 404 |
| `GET` | `/usuario/{rut}` | Historial de un socio, del más reciente al más antiguo | 200 |
| `GET` | `/atrasados` | Préstamos `VIGENTE` con fecha de vencimiento anterior a hoy, del más atrasado al más reciente | 200 |
| `POST` | `/` | Registra un préstamo en estado `VIGENTE` | 201, 400, 409 |
| `PUT` | `/{id}` | Actualiza los datos de un préstamo | 200, 400, 404, 409 |
| `PATCH` | `/{id}/devolucion` | Registra la devolución: estado `DEVUELTO` y fecha de hoy. Solo para préstamos `VIGENTE` | 200, 404, 409 |
| `DELETE` | `/{id}` | Elimina un préstamo. Un préstamo `VIGENTE` no se puede eliminar: primero debe devolverse o cancelarse | 204, 404, 409 |

Todas las respuestas de error comparten el mismo envelope `ApiError`
(`timestamp`, `status`, `error`, `message`, `errors`, `path`), de modo que el
cliente puede parsear cualquier fallo con un único modelo.

**Ejemplo de creación**

```bash
curl -X POST http://localhost:9005/api/v1/prestamos \
  -H "Content-Type: application/json" \
  -d '{
        "codigoLibro": "BIB-5501",
        "rutUsuario": "18345678-9",
        "fechaPrestamo": "2026-09-08",
        "fechaVencimiento": "2026-09-22",
        "observacion": "Retiro en mesón central"
      }'
```

**Ejemplo de devolución y consulta de atrasados**

```bash
curl -X PATCH http://localhost:9005/api/v1/prestamos/2/devolucion
curl http://localhost:9005/api/v1/prestamos/atrasados
```

## 6. Ejecución local

### Requisitos

- JDK 21
- No se necesita Maven instalado (se usa el Maven Wrapper)
- No se necesita MySQL para desarrollar: el perfil `dev` usa H2 en memoria

### Levantar el servicio

```bash
# Linux / macOS
./mvnw spring-boot:run

# Windows
.\mvnw.cmd spring-boot:run
```

| Recurso | URL |
|---|---|
| Swagger UI | http://localhost:9005/swagger-ui.html |
| OpenAPI JSON | http://localhost:9005/v3/api-docs |
| Consola H2 (solo `dev`) | http://localhost:9005/h2-console |
| Healthcheck | http://localhost:9005/actuator/health |

La consola H2 se conecta con JDBC URL `jdbc:h2:mem:prestamosdb`, usuario `sa` y
contraseña vacía. El perfil `dev` carga cinco préstamos de ejemplo desde
`data.sql` en cada arranque.

### Compilar el artefacto

```bash
./mvnw clean package
# genera target/ms-prestamos-<version>.jar
```

## 7. Perfiles y configuración

| Perfil | Base de datos | `ddl-auto` | Datos de prueba | Uso |
|---|---|---|---|---|
| `dev` (por defecto) | H2 en memoria | `create-drop` | Sí (`data.sql`) | Desarrollo local y pipeline de CI |
| `prod` | MySQL 8.x | `update` | No | Contenedores de Docker Compose (y la instancia EC2 de la EP1) |

El perfil `prod` **no contiene ninguna credencial**. Se inyectan como variables de
entorno desde `compose.yaml` (que las toma del archivo `.env`) o, en el despliegue
de la EP1, desde la unidad de systemd, de manera que las contraseñas nunca queden
versionadas en el repositorio:

| Variable | Por defecto | Descripción |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | Perfil activo |
| `SERVER_PORT` | `9005` | Puerto HTTP |
| `DB_HOST` | `localhost` | Host de MySQL |
| `DB_PORT` | `3306` | Puerto de MySQL |
| `DB_NAME` | `prestamos` | Nombre de la base de datos |
| `DB_USER` | — (obligatoria) | Usuario de aplicación |
| `DB_PASSWORD` | — (obligatoria) | Contraseña del usuario |

## 8. Pruebas

```bash
./mvnw clean verify    # corre las pruebas y verifica el umbral de cobertura
```

Son **53 pruebas** que corren **sin base de datos externa** (H2 en memoria),
requisito para que el pipeline sea rápido y no dependa de infraestructura:

| Clase | Pruebas | Qué verifica |
|---|---|---|
| `PrestamoServiceTest` | 14 | Reglas de negocio con Mockito: creación, actualización, devolución, atrasados, consultas y borrado. El repositorio y el mapper son mocks: se verifica la decisión del servicio. |
| `PrestamoControllerTest` | 20 | Contrato HTTP (`@WebMvcTest`): códigos de estado, sobre de error, validación de todos los campos, enlaces HATEOAS y que un fallo inesperado no filtre detalle técnico. |
| `PrestamoRepositoryTest` | 6 | SQL real sobre H2 (`@DataJpaTest`): orden del historial, atrasados y disponibilidad del ejemplar. |
| `PrestamoMapperTest` | 4 | El mapper no copia `id`, `estado` ni `fechaDevolucion` desde el cliente. |
| `PrestamoApiIntegrationTest` | 8 | La aplicación completa sin mocks: ciclo de vida de un préstamo, un ejemplar un préstamo vigente, detección de atrasos, healthcheck y OpenAPI. |
| `MsPrestamosApplicationTests` | 1 | Prueba de humo del contexto de Spring. |

**Política de calidad:** JaCoCo mide la cobertura y `mvn verify` falla si baja de
**90 % de líneas** o **80 % de ramas** (propiedades `jacoco.minimum.*` del
`pom.xml`). La cobertura actual es de 98,3 % en líneas y 93,8 % en ramas. El
reporte HTML queda en `target/site/jacoco/index.html` y, en el pipeline, como
artefacto del run.

## 9. Contenedores y orquestación

### 9.1 La imagen (`Dockerfile`)

El microservicio se empaqueta en una imagen **multi-stage**:

| Etapa | Base | Qué hace |
|---|---|---|
| `build` | `eclipse-temurin:21-jdk-alpine` | Descarga las dependencias (capa que Docker reutiliza mientras el `pom.xml` no cambie) y compila el `.jar` con el Maven Wrapper. |
| runtime | `eclipse-temurin:21-jre-alpine` | Solo JRE y el `.jar`: sin JDK, Maven ni código fuente. |

Configuración de la imagen:

- **Usuario sin privilegios** (`app`): el proceso no corre como root.
- **`HEALTHCHECK`** contra `/actuator/health`: Docker marca el contenedor como
  `healthy` o `unhealthy`.
- **`JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=75.0`**: la JVM usa hasta el 75 % del
  límite de memoria del contenedor y no el de la máquina anfitriona.
- **Etiquetas OCI** `version` y `revision`: grabadas en la imagen, permiten saber
  qué versión y qué commit generaron un contenedor (ver 13.7).
- **`.dockerignore`**: deja fuera `target/`, `.git`, documentación y secretos.

```bash
docker build -t ms-prestamos .
docker run --rm -p 9005:9005 ms-prestamos    # perfil dev: H2 en memoria
```

### 9.2 Orquestación con Docker Compose (`compose.yaml`)

```
                    ┌──────────────┐        ┌────────────────┐        ┌──────────────┐
 cliente ─ :9005 ─► │ proxy (nginx)│ ─────► │ app (API) × N  │ ─────► │ db (MySQL 8.4)│
                    └──────────────┘        └────────────────┘        └──────────────┘
                     único puerto            sin puerto publicado      sin puerto publicado
                     publicado               perfil prod               datos en un volumen
```

| Servicio | Imagen | Responsabilidad |
|---|---|---|
| `proxy` | `nginx:alpine` | Único punto de entrada (puerto 9005). Reparte las peticiones entre las réplicas de la API y reenvía el `Host` original para que los enlaces HATEOAS sean alcanzables. |
| `app` | `ms-prestamos` (construida del `Dockerfile`) | La API con perfil `prod`. Al no publicar puertos puede tener varias réplicas. |
| `db` | `mysql:8.4` | Base de datos. Los datos viven en el volumen `mysql-data`. |

Parámetros de escalabilidad y seguridad:

| Parámetro | Valor | Efecto |
|---|---|---|
| Límite de recursos de `app` | 1 CPU, 512 MB | Una réplica no puede consumir más que eso. |
| Límite de recursos de `db` | 1 CPU, 768 MB | Idem para MySQL. |
| Réplicas | `--scale app=N` | Escala la API; nginx las balancea (resolver DNS cada 5 s). |
| `restart: unless-stopped` | todos | El contenedor se levanta solo ante un fallo. |
| `depends_on` con `service_healthy` | `app` espera a `db`; `proxy` espera a `app` | El orden de arranque depende de la salud real, no de que el proceso exista. |
| Sin `ports` en `db` y `app` | — | MySQL y la API solo se alcanzan por la red interna de Compose. |
| Credenciales en `.env` | ignorado por Git | `.env.example` es la plantilla; el CD genera credenciales nuevas en cada despliegue. |

```bash
cp .env.example .env                       # una sola vez
docker compose up -d --build               # construye la imagen y levanta todo
curl http://localhost:9005/actuator/health
docker compose up -d --scale app=3         # 3 réplicas detrás del proxy
docker compose logs proxy                  # el log indica qué réplica atendió cada petición
docker compose down                        # detiene todo; los datos quedan en el volumen
```

### 9.3 Despliegue en AWS de la EP1

El despliegue de la EP1 (un `.jar` copiado por SSH a una instancia EC2 y
administrado con `systemd`) queda documentado en
[docs/despliegue-aws.md](docs/despliegue-aws.md) y
[docs/evidencias/despliegue-ec2.md](docs/evidencias/despliegue-ec2.md). El
workflow de CD actual ya no lo usa: despliega con Docker Compose (sección 13.3).

---

## 10. Estrategia de ramificación

### 10.1 Modelos considerados

Antes de elegir se compararon los tres modelos de ramificación más usados en
equipos que desarrollan y despliegan en la nube:

| Modelo | Cómo funciona | Dónde encaja mejor | Limitaciones |
|---|---|---|---|
| **GitFlow** | Dos ramas permanentes: `main` (lo que está en producción) y `develop` (integración). Las features salen de `develop`; los releases se preparan en `release/*`; las correcciones urgentes en `hotfix/*` salen de `main` y vuelven a ambas ramas. | Productos con **versiones publicadas** y ciclos de entrega definidos; equipos que necesitan separar "lo que se está integrando" de "lo que está desplegado"; proyectos donde varias personas trabajan features en paralelo. | Más ramas y más merges; si el equipo despliega muchas veces al día, `develop` y los releases agregan latencia. |
| **GitHub Flow** | Una sola rama permanente (`main`), siempre desplegable. Cada cambio nace en una rama corta y entra por pull request con CI en verde; el despliegue ocurre al fusionar. | Servicios web con **despliegue continuo** a un único entorno, equipos pequeños, cadencia de entrega alta. | No distingue versiones ni entornos: un hotfix y una feature siguen el mismo camino; sin `develop` no hay un lugar para integrar varias features antes de publicarlas. |
| **Trunk-based development** | Todo el equipo integra en `trunk` (`main`) varias veces al día, con ramas de horas de duración o commits directos. Lo incompleto se oculta con *feature flags*; los releases se cortan desde `trunk` con tags o ramas de release efímeras. | Equipos maduros con **CI muy robusta**, pruebas automatizadas amplias y cultura de commits pequeños; es el modelo que recomiendan los estudios de DevOps (DORA) para alta frecuencia de despliegue. | Exige disciplina y tooling (flags, pruebas rápidas, revisión casi inmediata). Con un equipo nuevo o con pruebas escasas, `main` se rompe con facilidad. |

En un contexto colaborativo en la nube los tres modelos comparten dos pilares:
**pull requests** como punto de revisión y **CI automática** como guardia de la
rama que se despliega. La diferencia está en cuántas ramas permanentes se
mantienen y en qué momento un cambio se considera "listo para producción".

### 10.2 Elección: GitFlow

Para esta entrega se adoptó **GitFlow**, por estas razones:

1. **El encargo exige las ramas `main`, `develop`, `feature/*` y `hotfix/*`** y
   distingue explícitamente cambios tipo feature de cambios tipo hotfix. GitFlow
   es el modelo que define esos roles de forma nativa; GitHub Flow o trunk-based
   obligarían a simularlos.
2. **Hay dos entornos con significado distinto.** `develop` se valida con CI en
   H2, mientras que `main` se despliega en la instancia EC2 con MySQL. Tener una
   rama de integración permite acumular varias features (devolución y atrasados
   entraron por separado) y publicarlas juntas como una versión numerada.
3. **El hotfix tiene un camino propio.** Una corrección urgente sobre lo
   desplegado sale de `main`, se publica de inmediato y vuelve a `develop` sin
   arrastrar features a medio integrar. Esa separación es justamente lo que se
   pide simular.
4. **Sirve igual para una persona que para un equipo.** Aunque este encargo lo
   hice solo, trabajé cada cambio en su rama `feature/*` como si otra persona
   estuviera avanzando en paralelo. Eso permitió reproducir lo que pasa en un
   equipo real: dos features abiertas al mismo tiempo, un conflicto al integrar
   la segunda y su resolución en un punto controlado (ver PR #4 en la sección
   12).

El costo de GitFlow (más merges) es asumible en un proyecto con releases
esporádicos. Si el servicio pasara a desplegarse varias veces al día, el camino
natural sería migrar a trunk-based conservando los pull requests y el pipeline.

### 10.3 Ramas y ciclo de vida

| Rama | Origen | Destino | Vida | Qué contiene |
|---|---|---|---|---|
| `main` | — | — | Permanente | Solo versiones publicadas. Cada merge lleva un tag `vX.Y.Z` y dispara el despliegue en EC2. **No se hace push directo.** |
| `develop` | `main` | `main` (vía release) | Permanente | Rama de integración. Recibe features por PR; cada push ejecuta la CI. |
| `feature/<nombre>` | `develop` | `develop` | Días | Una funcionalidad o mejora. Se borra al fusionar. |
| `release/<version>` | `develop` | `main` y `develop` | Horas/días | Congela el alcance de una versión: solo bump de versión, docs y correcciones menores. |
| `hotfix/<nombre>` | `main` | `main` y `develop` | Horas | Corrección urgente de algo que ya está en producción. |

```mermaid
gitGraph
   commit id: "chore: maven wrapper"
   commit id: "feat: CRUD prestamos"
   branch develop
   checkout develop
   branch "feature/ci-pipeline"
   commit id: "ci: workflow"
   checkout develop
   merge "feature/ci-pipeline" id: "PR #1"
   branch "feature/despliegue-ec2"
   commit id: "feat(infra): CD EC2"
   checkout develop
   merge "feature/despliegue-ec2" id: "PR #2"
   branch "feature/devolucion-prestamo"
   commit id: "feat: devolucion"
   checkout develop
   branch "feature/prestamos-atrasados"
   commit id: "feat: atrasados"
   checkout develop
   merge "feature/devolucion-prestamo" id: "PR #3"
   checkout "feature/prestamos-atrasados"
   merge develop id: "resuelve conflicto"
   checkout develop
   merge "feature/prestamos-atrasados" id: "PR #4"
   branch "release/1.0.0"
   commit id: "chore(release): 1.0.0"
   checkout main
   merge "release/1.0.0" id: "PR release" tag: "v1.0.0"
   checkout develop
   merge main id: "sync release"
   checkout main
   branch "hotfix/eliminar-prestamo-vigente"
   commit id: "fix: no borrar VIGENTE"
   checkout main
   merge "hotfix/eliminar-prestamo-vigente" id: "PR hotfix" tag: "v1.0.1"
   checkout develop
   merge main id: "sync hotfix"
```

---

## 11. Guía de buenas prácticas del repositorio

Esta sección es la guía de uso del repositorio. Aplica a
cualquiera que clone el proyecto.

### 11.1 Naming de ramas

Formato: `<tipo>/<descripcion-en-kebab-case>`, en minúsculas, sin acentos ni
espacios, con un nombre que describa **qué** cambia (no quién ni cuándo).

| Tipo | Uso | Ejemplos de este repositorio |
|---|---|---|
| `feature/` | Nueva funcionalidad o mejora | `feature/devolucion-prestamo`, `feature/prestamos-atrasados`, `feature/ci-pipeline` |
| `hotfix/` | Corrección urgente sobre `main` (código o documentación publicada) | `hotfix/eliminar-prestamo-vigente`, `hotfix/ramas-visibles-en-remoto` |
| `release/` | Preparación de una versión | `release/1.0.0` |
| `docs/` | Cambios solo de documentación (opcional; también puede ir como `feature/`) | `docs/guia-repositorio` |

Reglas:

- Una rama, un propósito. Si una feature crece, se divide en dos ramas.
- Al fusionar el PR la rama queda cerrada: no recibe más commits. En un proyecto en
  producción se borra del remoto con *Delete branch*; **en esta entrega las ramas se
  conservan publicadas** para que el docente pueda revisarlas con `git branch -r`.
- Nunca se hace commit directo en `main` ni en `develop`.

### 11.2 Mensajes de commit

Se usa **[Conventional Commits](https://www.conventionalcommits.org/es/)**:

```
<tipo>(<ámbito opcional>): <resumen en imperativo, minúsculas, sin punto final>

<cuerpo opcional: qué cambia y POR QUÉ, no cómo>

<pie opcional: referencias, coautores>
```

| Tipo | Cuándo | Ejemplo real |
|---|---|---|
| `feat` | Nueva funcionalidad visible para el usuario de la API | `feat(prestamos): registrar devolucion de un prestamo` |
| `fix` | Corrección de un defecto | `fix(prestamos): impedir eliminar un prestamo vigente` |
| `docs` | Solo documentación | `docs: README inicial, guia de despliegue en EC2 y script de base de datos` |
| `ci` | Workflows de GitHub Actions | `ci: workflow de GitHub Actions para build y pruebas` |
| `chore` | Mantenimiento sin efecto funcional (wrapper, .gitignore, versión) | `chore: agregar Maven Wrapper y .gitignore` |
| `test` | Solo pruebas | `test(prestamos): cubrir devolucion con prestamo cancelado` |
| `refactor` | Cambio interno sin alterar comportamiento | `refactor(service): extraer validacion de fechas` |

Reglas:

- Resumen de **máximo 72 caracteres**, en imperativo ("agregar", no "agregado").
- El cuerpo explica la motivación y las decisiones; se escribe siempre que el
  cambio no sea trivial.
- Un commit = un cambio coherente que compila y pasa las pruebas. No se mezclan
  refactors con features.
- Los commits hechos a cuatro manos o con apoyo de herramientas llevan el trailer
  `Co-Authored-By:` en el pie.
- El ámbito (`prestamos`, `infra`, `service`, `release`) es opcional pero
  recomendado cuando aclara qué parte del sistema cambia.

### 11.3 Flujo de merge

| Situación | Cómo se integra | Motivo |
|---|---|---|
| `feature/*` → `develop` | Pull request + **merge commit** (`Create a merge commit`) | El merge commit conserva la rama en el historial y muestra qué commits pertenecen a la feature. |
| `release/*` → `main` | Pull request + merge commit + **tag `vX.Y.Z`** | El tag marca exactamente qué commit está desplegado. |
| `hotfix/*` → `main` | Pull request + merge commit + tag de parche (`vX.Y.Z+1`) | Igual que el release, pero con alcance mínimo. |
| `main` → `develop` (tras release u hotfix) | Pull request de sincronización o merge directo por quien cerró el release | `develop` nunca debe quedar atrás de `main`. |
| Rama desactualizada respecto a `develop` | `git fetch` + `git merge origin/develop` en la rama de la feature | Los conflictos se resuelven en la rama, nunca en `develop`. |

No se usa *squash* ni *rebase* al fusionar: se prefiere preservar la historia
real, incluido el commit que resolvió un conflicto, porque esa historia es la
trazabilidad que la evaluación pide documentar.

**Resolución de conflictos** (procedimiento aplicado en el PR #4):

1. `git fetch origin develop` y `git merge origin/develop` en la rama de la feature.
2. Abrir los archivos marcados `UU`, decidir qué se conserva (normalmente ambas
   partes), y quitar los marcadores `<<<<<<<`, `=======`, `>>>>>>>`.
3. Compilar y ejecutar **toda** la suite antes de continuar.
4. Commit del merge explicando en el cuerpo qué se resolvió y por qué.
5. Push; la CI vuelve a correr sobre el resultado del merge.

### 11.4 Estrategia de revisión

- **Todo cambio entra por pull request**, incluso los de una sola línea.
- El PR usa la plantilla de `.github/pull_request_template.md`: qué incluye, por
  qué, cómo probarlo y una lista de verificación.
- En un equipo, el autor no fusiona su propio PR sin que otra persona lo
  revise. En esta entrega, hecha por una sola persona y con un solo usuario de
  GitHub, la revisión se documenta en la descripción del PR (qué incluye, por
  qué, cómo probarlo) y el merge se hace desde la CLI (`gh pr merge --merge`)
  solo cuando la CI está en verde.
- **La CI en verde es condición necesaria** para fusionar. Un check rojo bloquea
  el merge hasta que se corrija en la misma rama.
- El revisor mira: que el cambio haga solo lo que dice el título, que tenga
  pruebas, que no exponga secretos y que el README quede coherente si cambia la
  API.
- Se prefieren PR pequeños (una feature) a PR grandes: se revisan mejor y
  generan menos conflictos.

### 11.5 Control de versiones

- **SemVer** (`MAJOR.MINOR.PATCH`): `MAJOR` rompe compatibilidad de la API,
  `MINOR` agrega funcionalidad compatible, `PATCH` corrige sin cambiar la API.
- `develop` lleva siempre una versión `-SNAPSHOT` en el `pom.xml`. La rama
  `release/*` fija la versión definitiva; el hotfix sube el `PATCH`.
- Cada merge a `main` recibe un **tag anotado** `vX.Y.Z` y una *release* en
  GitHub con el resumen de cambios.
- El artefacto desplegado se llama siempre `ms-prestamos.jar` en la instancia; la
  versión real se consulta en `/actuator/info` o en el tag del commit
  desplegado.

### 11.6 Estructura de carpetas

- Código de producción en `src/main`, pruebas en `src/test`, espejo de paquetes.
- Un paquete por responsabilidad (`controller`, `service`, `repository`, `dto`,
  `model`, `mapper`, `exception`, `config`). Una clase nueva va al paquete que
  corresponde a su rol, no al del archivo que la usa.
- Automatización en `.github/workflows`, infraestructura en `infra/<proveedor>`,
  documentación larga en `docs/`, scripts de base de datos en `db/`.
- **Nunca se versionan**: `target/`, archivos del IDE, `.env`, llaves `.pem`,
  `application-local.yml` (ver `.gitignore`). Las credenciales viajan por
  variables de entorno o por secretos de GitHub Actions.

### 11.7 Comandos Git del flujo diario

| Paso | Comando | Notas |
|---|---|---|
| Clonar | `git clone https://github.com/juatapiaoing/EV1-despliegue-801D.git` | Una sola vez |
| Actualizar `develop` | `git checkout develop && git pull origin develop` | Antes de crear cualquier rama |
| Crear feature | `git checkout -b feature/<nombre> develop` | Siempre desde `develop` |
| Guardar trabajo | `git add <archivos>` · `git commit -m "feat(...): ..."` | Commits pequeños y frecuentes |
| Publicar rama | `git push -u origin feature/<nombre>` | `-u` deja la rama enlazada para los siguientes push |
| Abrir PR | `gh pr create --base develop --head feature/<nombre>` o desde la web | Usar la plantilla |
| Traer cambios de `develop` | `git fetch origin develop && git merge origin/develop` | Resolver conflictos en la rama |
| Fusionar PR | `gh pr merge <n> --merge --delete-branch` | Solo con CI en verde |
| Limpiar | `git checkout develop && git pull && git branch -d feature/<nombre>` | La remota ya la borró GitHub |
| Release | `git checkout -b release/1.0.0 develop` → bump `pom.xml` → PR a `main` → `git tag -a v1.0.0 -m "..."` → `git push origin v1.0.0` | Después, PR `main` → `develop` |
| Hotfix | `git checkout -b hotfix/<nombre> main` → fix → PR a `main` → tag `v1.0.1` | Después, PR `main` → `develop` |

---

## 12. Simulación de trabajo colaborativo y trazabilidad

La simulación reproduce el trabajo de un equipo que integra cambios en
paralelo, aunque en la práctica todas las ramas las trabajé yo. Todo cambio
entró por pull request, y cada PR ejecutó la CI antes del merge. Los enlaces llevan al PR con su descripción, sus commits y el resultado
del workflow.

| PR | Rama | Tipo | Destino | Qué integró | CI |
|---|---|---|---|---|---|
| [#1](https://github.com/juatapiaoing/EV1-despliegue-801D/pull/1) | `feature/ci-pipeline` | feature (CI) | `develop` | Workflow de GitHub Actions y `.gitattributes` | ✅ |
| [#2](https://github.com/juatapiaoing/EV1-despliegue-801D/pull/2) | `feature/despliegue-ec2` | feature (CD) | `develop` | Aprovisionamiento EC2 con AWS CLI, cloud-init y workflow de despliegue | ✅ |
| [#3](https://github.com/juatapiaoing/EV1-despliegue-801D/pull/3) | `feature/devolucion-prestamo` | feature | `develop` | `PATCH /{id}/devolucion` + 3 pruebas | ✅ |
| [#4](https://github.com/juatapiaoing/EV1-despliegue-801D/pull/4) | `feature/prestamos-atrasados` | feature | `develop` | `GET /atrasados` + 2 pruebas. **Conflicto con #3** en `PrestamoServiceTest` resuelto en la rama | ✅ |
| [#5](https://github.com/juatapiaoing/EV1-despliegue-801D/pull/5) | `feature/guia-repositorio` | docs | `develop` | Este README y la plantilla de PR | ✅ |
| [#6](https://github.com/juatapiaoing/EV1-despliegue-801D/pull/6) | `release/1.0.0` | release | `main` | Versión 1.0.0 → tag `v1.0.0` → despliegue automático en EC2 | ✅ |
| [#7](https://github.com/juatapiaoing/EV1-despliegue-801D/pull/7) | `hotfix/eliminar-prestamo-vigente` | hotfix | `main` | `DELETE` rechaza préstamos `VIGENTE` → tag `v1.0.1` → redespliegue | ✅ |
| [#8](https://github.com/juatapiaoing/EV1-despliegue-801D/pull/8) | `main` | sync | `develop` | Devuelve el hotfix a la rama de integración | ✅ |
| [#9](https://github.com/juatapiaoing/EV1-despliegue-801D/pull/9) | `hotfix/ramas-visibles-en-remoto` | hotfix (docs) | `main` | Aclara en la guía que las ramas de la simulación se conservan en el remoto → tag `v1.0.2` | ✅ |
| [#10](https://github.com/juatapiaoing/EV1-despliegue-801D/pull/10) | `main` | sync | `develop` | Devuelve el ajuste de documentación a `develop` | ✅ |
| [#11](https://github.com/juatapiaoing/EV1-despliegue-801D/pull/11) | `develop` | sync | `main` | PR abierto desde la web de GitHub; sin cambios de contenido porque `develop` ya estaba al día con `main` | ✅ |
| [#12](https://github.com/juatapiaoing/EV1-despliegue-801D/pull/12) | `hotfix/readme-autor-unico` | hotfix (docs) | `main` | El encargo se hizo de forma individual: se quita al segundo integrante y se agrega la reflexión personal | ✅ |
| [#13](https://github.com/juatapiaoing/EV1-despliegue-801D/pull/13) | `main` | sync | `develop` | Cerrado sin fusionar: lo reemplazó #15 | — |
| [#14](https://github.com/juatapiaoing/EV1-despliegue-801D/pull/14) | `hotfix/trazabilidad-pr` | hotfix (docs) | `main` | Corrige la numeración de los PR en esta tabla → tag `v1.0.3` | ✅ |
| [#15](https://github.com/juatapiaoing/EV1-despliegue-801D/pull/15) | `main` | sync | `develop` | Devuelve los dos ajustes de documentación a `develop` | ✅ |
| [#16](https://github.com/juatapiaoing/EV1-despliegue-801D/pull/16) | `hotfix/entrega-final` | hotfix | `main` | Evidencias del despliegue, CD que omite el despliegue si la instancia está detenida, ajustes finales del README → tag `v1.0.4` | ✅ |
| [#17](https://github.com/juatapiaoing/EV1-despliegue-801D/pull/17) | `main` | sync | `develop` | Devuelve el cierre de la entrega a `develop` | ✅ |
| [#18](https://github.com/juatapiaoing/EV1-despliegue-801D/pull/18) | `feature/dockerfile` | feature (EP2, contenedores) | `develop` | Dockerfile multi-stage y job de CI que construye la imagen | ✅ |
| [#19](https://github.com/juatapiaoing/EV1-despliegue-801D/pull/19) | `feature/calidad-pruebas` | feature (EP2, pruebas) | `develop` | JaCoCo con umbral de cobertura y 38 pruebas nuevas (controlador, repositorio, mapper e integración) | ✅ |
| [#20](https://github.com/juatapiaoing/EV1-despliegue-801D/pull/20) | `feature/seguridad` | feature (EP2, seguridad) | `develop` | SonarQube Cloud con Quality Gate bloqueante, Dependabot y simplificación del Dockerfile y del CI | ✅ |
| [#21](https://github.com/juatapiaoing/EV1-despliegue-801D/pull/21) | `feature/compose-cd` | feature (EP2, orquestación y CD) | `develop` | Docker Compose (proxy, API y MySQL) y CD que despliega con Compose | ✅ |
| [#22](https://github.com/juatapiaoing/EV1-despliegue-801D/pull/22) | `docs/readme-ep2` | docs (EP2) | `develop` | Documentación del pipeline, la calidad y la trazabilidad | ✅ |
| [#23](https://github.com/juatapiaoing/EV1-despliegue-801D/pull/23) | `feature/vulnerabilidades` | fix (EP2, seguridad) | `develop` | Tomcat 10.1.61 y Jackson 2.21.8 con parches de seguridad. En su primer intento el Quality Gate de SonarQube Cloud bloqueó el PR por una acción de terceros sin fijar por SHA | ✅ |

> Nota sobre #11 y #12: el PR #11 se abrió desde la web mientras un script de la
> CLI fusionaba el hotfix asumiendo que le tocaría ese número. El resultado es que
> los merge commits de #11 y #12 en `main` quedaron con el asunto intercambiado.
> `main` está protegida contra force-push, así que no se reescribe la historia: se
> deja constancia aquí. Lección: tomar el número del PR de la respuesta de
> `gh pr create`, nunca darlo por supuesto.

### Cómo se produjo el conflicto del PR #4 y cómo se resolvió

`feature/prestamos-atrasados` se abrió desde `develop` **antes** de que se
fusionara `feature/devolucion-prestamo` (#3). Ambas ramas agregaron pruebas en el
mismo punto de `PrestamoServiceTest.java` (justo antes de la sección `delete`),
así que al fusionar #3 GitHub marcó el PR #4 como `CONFLICTING`.

```bash
git fetch origin develop
git merge origin/develop          # CONFLICT (content): PrestamoServiceTest.java
# se conservaron ambas secciones de pruebas y se quitaron los marcadores
./mvnw clean verify               # 14 pruebas en verde
git add src/test/.../PrestamoServiceTest.java
git commit                        # "Merge branch 'develop' into feature/prestamos-atrasados"
git push origin feature/prestamos-atrasados
```

El commit de merge quedó en el historial de la rama y la CI volvió a ejecutarse
sobre el resultado antes de fusionar el PR.

### Historial resultante

```bash
git log --oneline --graph --all
```

Se puede reproducir en local, o ver en
[Insights → Network](https://github.com/juatapiaoing/EV1-despliegue-801D/network)
el grafo con `main`, `develop`, las features, el release y el hotfix.

---

## 13. Integración y despliegue continuo

### 13.1 Rol de GitHub Actions en el proceso CI/CD

GitHub Actions es el motor que convierte el repositorio en un pipeline: cada
evento de Git (push, pull request, tag) puede disparar un *workflow* que ejecuta
pasos en una máquina virtual efímera. En este proyecto cumple dos roles:

- **Integración continua (CI):** compilar, probar y analizar cada cambio *antes*
  de que llegue a la rama de integración o a producción. Es la red de seguridad
  de `develop` y de `main`: un PR con pruebas rotas, cobertura insuficiente o un
  análisis de calidad en rojo no se fusiona.
- **Despliegue continuo (CD):** levantar automáticamente la versión que acaba de
  entrar en `main` en un entorno de ejecución y comprobar que funciona. El
  despliegue es un paso reproducible y auditable en la pestaña *Actions*.

```
 feature/* ─► PR a develop ─► CI ─┬─ pruebas + cobertura (JaCoCo)
                                  ├─ análisis SonarQube Cloud (Quality Gate)
                                  └─ build de la imagen Docker
                                  │
 develop ─► release/* ─► PR a main ─► CI ─► merge ─► tag vX.Y.Z ─► CD ─► Docker Compose
```

### 13.2 Workflow de CI (`.github/workflows/ci.yml`)

| Disparador | Rama | Propósito |
|---|---|---|
| `push` | `develop` | Validar la rama de integración tras cada merge |
| `push` | `main` | Dejar analizada en SonarQube Cloud la versión publicada |
| `pull_request` | `main` | Bloquear releases y hotfixes rotos antes de desplegar |
| `pull_request` | `develop` | Validar cada feature en su PR, antes de integrarla |
| `workflow_dispatch` | cualquiera | Ejecución manual desde la pestaña *Actions* |

Dos jobs encadenados:

| Job | Pasos |
|---|---|
| `Compilar y probar (Java 21)` | checkout con historial completo → JDK 21 Temurin con caché de Maven → `./mvnw -B clean verify` (compila, corre las 53 pruebas y verifica el umbral de cobertura de JaCoCo) → análisis de SonarQube Cloud (solo en PR y en `main`) → publica como artefactos los reportes de Surefire, el de cobertura y el `.jar`. |
| `Construir imagen Docker` | Se ejecuta solo si el anterior terminó en verde. Construye la imagen con `docker build`, de modo que un `Dockerfile` roto se detecta antes del merge. |

**Bloqueos:** cualquiera de estos dos deja el run en rojo y, como el check
`Compilar y probar (Java 21)` es obligatorio en los rulesets de `main` y `develop`,
impide fusionar el pull request:

1. La cobertura de pruebas baja del umbral (90 % de líneas o 80 % de ramas).
2. El **Quality Gate** de SonarQube Cloud queda en rojo
   (`sonar.qualitygate.wait=true` hace que el scanner espere el veredicto).

### 13.3 Workflow de CD (`.github/workflows/cd-deploy.yml`)

| Disparador | Rama | Propósito |
|---|---|---|
| `push` | `main` | Desplegar cada release y cada hotfix |
| `workflow_dispatch` | cualquiera | Ejecutar el despliegue a mano |

Pasos del job `Desplegar en el entorno simulado`:

1. Calcula la **versión** (desde el `pom.xml`) y el **commit** que se despliegan.
2. Genera credenciales nuevas para la base de datos (`openssl rand`) en un `.env`
   que no se versiona.
3. `docker compose up -d --build`: construye la imagen, etiquetada con el commit, y
   levanta proxy, API y MySQL en el orden que marcan los healthchecks.
4. Espera hasta 120 s a que `/actuator/health` responda `UP` a través del proxy.
5. **Prueba de punta a punta:** registra un préstamo por el proxy y lo lee de
   vuelta, lo que comprueba la cadena completa proxy → API → MySQL.
6. Escribe en el resumen del run la versión, el commit y la imagen desplegados.
7. Muestra los logs si algo falló y retira el entorno.

El job declara `environment: produccion`, de modo que cada despliegue queda
registrado en la pestaña *Deployments* del repositorio. Un `concurrency` evita dos
despliegues simultáneos.

### 13.4 Entorno cloud simulado

El entorno de despliegue es la máquina virtual efímera de GitHub Actions
(`ubuntu-latest`): ejecuta el mismo `compose.yaml` que se usaría en un servidor
real, con las mismas imágenes, red interna, volumen y healthchecks. Al terminar se
destruye, por lo que cada despliegue parte de un entorno limpio y es reproducible.
El mismo stack se levanta en cualquier máquina con Docker siguiendo la sección 9.2.

### 13.5 Seguridad: Dependabot y SonarQube Cloud

- **Dependabot** (`.github/dependabot.yml`): cada semana revisa las dependencias
  Maven del `pom.xml`, las imágenes base del `Dockerfile` y las acciones de los
  workflows, y abre un pull request hacia `develop` por cada actualización. Además
  están activadas en el repositorio las **alertas de vulnerabilidades** y las
  **actualizaciones de seguridad**, que no esperan a la revisión semanal. Los PR de
  Dependabot pasan por el mismo CI que cualquier otro cambio.
- **SonarQube Cloud:** analiza el código en cada pull request y en `main`. Su
  Quality Gate por defecto (*Sonar way*) evalúa el código nuevo: sin bugs ni
  vulnerabilidades nuevas, puntos sensibles revisados, cobertura y duplicación
  dentro de los límites. El token vive en el secreto `SONAR_TOKEN` de GitHub
  (disponible también para Dependabot); los identificadores del proyecto están en
  el `pom.xml` porque no son secretos.
- **Alertas:** el run en rojo y el check del PR avisan de un análisis fallido;
  las vulnerabilidades de dependencias aparecen en la pestaña *Security* del
  repositorio.

### 13.6 Cómo se garantiza la calidad

| Mecanismo | Qué garantiza | Dónde se aplica |
|---|---|---|
| 53 pruebas automatizadas (unitarias, de capa web, de repositorio y de integración) | Que los casos críticos del negocio y el contrato HTTP no se rompan | Cada push y cada PR |
| Umbral de cobertura de JaCoCo (90 % líneas, 80 % ramas) | Que el código nuevo llegue con pruebas | `mvn verify` |
| Quality Gate de SonarQube Cloud | Que no entren bugs, vulnerabilidades ni duplicación | PR y `main` |
| Rulesets de `main` y `develop` | Que nada se fusione sin pull request ni con el check en rojo; sin force-push ni borrado | GitHub |
| Plantilla de pull request | Que cada cambio explique qué incluye, por qué y cómo probarlo | GitHub |
| `HEALTHCHECK` y prueba de punta a punta del CD | Que lo desplegado realmente funciona, no solo que arrancó | Despliegue |

### 13.7 Cómo se garantiza la trazabilidad

Cada versión desplegada puede seguirse hacia atrás hasta el cambio que la originó:

1. **Cambio → PR:** todo cambio entra por un pull request de una rama con nombre
   convencional, con commits en formato *Conventional Commits* (sección 11). La
   tabla de la sección 12 enlaza cada PR con su CI.
2. **PR → versión:** el merge a `main` lleva un tag `vX.Y.Z` y una *release* de
   GitHub, y el `pom.xml` declara la misma versión.
3. **Versión → imagen:** el CD construye la imagen `ms-prestamos:<commit>` con las
   etiquetas OCI `org.opencontainers.image.version` y `.revision`. Se consultan con
   `docker inspect ms-prestamos:<commit> --format '{{json .Config.Labels}}'`.
4. **Imagen → ejecución:** la API informa su versión en `/actuator/info`, y el
   resumen de cada run de CD muestra versión, commit e imagen desplegados.
5. **Despliegue → registro:** cada despliegue queda en *Deployments* →
   `produccion`, con el commit y la fecha.
6. **Calidad → commit:** SonarQube Cloud guarda el análisis de cada PR y de `main`
   asociado al commit analizado.

### 13.8 Cómo ver los resultados

- Pestaña **Actions** del repositorio: un run por cada push/PR, con logs paso a
  paso, resumen de despliegue y artefactos descargables (reportes de pruebas y de
  cobertura).
- Badges al inicio de este README: Quality Gate y cobertura de SonarQube Cloud, y
  estado del último CD en `main`.
- En cada PR, la sección *Checks* muestra `Compilar y probar (Java 21)`,
  `Construir imagen Docker` y `SonarCloud Code Analysis`.
- Dashboard de calidad:
  https://sonarcloud.io/project/overview?id=juatapiaoing_EV1-despliegue-801D
- Ejemplo de CI de la EP2 (PR #21):
  https://github.com/juatapiaoing/EV1-despliegue-801D/actions/runs/40992433285
- Ejemplo de CD con Docker Compose en un runner real:
  https://github.com/juatapiaoing/EV1-despliegue-801D/actions/runs/40993443498

### 13.9 Justificación de las decisiones técnicas

_Sección por redactar por el autor._ Temas que conviene justificar: Docker
Compose frente a Kubernetes, el proxy nginx y el escalado por réplicas, los
umbrales de cobertura elegidos, SonarQube Cloud frente a Snyk, y el runner de
Actions como entorno cloud simulado frente a una instancia en la nube.

---

## 14. Declaración de uso de Inteligencia Artificial

Conforme a las indicaciones del encargo, declaro el uso de IA en este trabajo.

### Evaluación Parcial 1

| Herramienta | Para qué se usó | Qué revisé y validé yo |
|---|---|---|
| **Claude Code** (Anthropic, modelo Claude) | Apoyo en la generación del código base del microservicio y sus pruebas, redacción inicial de este README y de `docs/despliegue-aws.md`, escritura de los workflows de GitHub Actions y de los scripts de aprovisionamiento, y ejecución guiada de los comandos Git, `gh` y AWS CLI de la simulación. | Leí y probé cada archivo generado: la suite de pruebas se ejecutó en local y en CI, el despliegue se verificó contra la instancia real, y el contenido del README lo contrasté con el enunciado y la rúbrica. |

La comparación de modelos de ramificación y la justificación de la elección
(sección 10) las revisé y las asumo como propias. La reflexión de la sección 15
es mi experiencia personal con el encargo.

### Evaluación Parcial 2

| Herramienta | Para qué se usó | Cómo se validó |
|---|---|---|
| **Claude Code** (Anthropic; modelos Claude Sonnet 5.5 y Claude Opus 5.5) | Generación del `Dockerfile`, el `compose.yaml`, la configuración de nginx y los workflows de GitHub Actions; configuración de JaCoCo, SonarQube Cloud y Dependabot; las 38 pruebas nuevas; las secciones descriptivas 9 y 13 de este README; y ejecución guiada de los comandos Git, `gh` y Docker (ramas, commits, pull requests, secretos y activación de Dependabot). | Cada cambio entró por pull request con el pipeline en verde: pruebas, umbral de cobertura, Quality Gate de SonarQube Cloud y build de la imagen. El stack de Docker Compose y el despliegue se probaron en local y en un runner de GitHub Actions. |

La justificación de las decisiones técnicas (sección 13.9) y la reflexión
personal de la EP2 (sección 15) las redacté yo, sin apoyo de IA.

### Referencias

Anthropic. (2026). *Claude Code* (Claude Sonnet 5.5 y Claude Opus 5.5) [Modelo Grande de Lenguaje]. https://claude.com/claude-code

Referencia institucional: https://bibliotecas.duoc.cl/ia

---

## 15. Conclusiones y reflexión personal

### Juan Carlos Tapia

Este encargo lo hice solo, así que me tocó cubrir todos los roles: escribir el
código, abrir los PR, "revisarlos" y fusionarlos. Al principio pensé que
trabajar con ramas y pull requests estando solo iba a ser puro trámite, pero
terminó siendo lo más útil de la evaluación. Me obligó a pensar cada cambio como
algo cerrado, con su nombre, su descripción y sus pruebas, en vez de ir
haciendo commits sueltos en `main` como venía acostumbrado.

Lo que más me costó fue entender bien la diferencia entre `develop` y `main` y
para qué sirve realmente una rama de release o un hotfix. Cuando lo ves en un
diagrama parece obvio, pero recién lo entendí cuando tuve que sacar el hotfix
desde `main` y después acordarme de devolverlo a `develop`. También me sirvió
mucho el conflicto que se armó entre las dos features: la primera vez que vi
los marcadores `<<<<<<<` en el archivo de pruebas no sabía muy bien por dónde
partir, y ahora entiendo que la idea es resolverlo en la rama, correr las
pruebas y recién ahí volver a subir.

De la parte de CI/CD me quedo con algo simple: que el workflow corra solo en
cada push te quita el miedo de romper algo sin darte cuenta. Y ver que un merge
a `main` termina con el servicio actualizado en la instancia de AWS, sin entrar
por SSH a copiar el jar a mano, fue lo que me hizo sentir que esto ya es un
pipeline y no solo un repositorio ordenado. Lo de AWS con la CLI fue lo más
nuevo para mí; tuve varios problemas tontos con rutas y con la llave SSH que me
hicieron perder tiempo, pero también me quedó claro por qué conviene dejar esos
pasos en un script y no hacerlos por la consola.

Usé IA como apoyo, sobre todo para el código base, la documentación y para no
trabarme con los comandos, y creo que lo importante es que ahora entiendo lo
que hay en el repositorio y podría explicarlo o repetirlo sin ayuda. Si tuviera
que hacerlo de nuevo, probablemente partiría el trabajo en ramas más chicas
desde el principio y dejaría la documentación al día en cada PR, en vez de
escribir el README grande al final.

### Juan Carlos Tapia — Evaluación Parcial 2

_Reflexión personal por redactar por el autor: qué aprendió sobre contenedores,
pruebas automatizadas, análisis de seguridad y orquestación, y cuál fue su
contribución al proyecto._

---

**Asignatura:** Ingeniería DevOps (DOY0101) · Duoc UC
**Repositorio:** https://github.com/juatapiaoing/EV1-despliegue-801D
**Autor:** Juan Carlos Tapia
