# ms-prestamos

Microservicio de gestión de préstamos de una biblioteca. Registra el retiro de un
ejemplar por parte de un socio, mantiene el historial de cada usuario y garantiza
que un mismo ejemplar no pueda estar prestado dos veces al mismo tiempo.

Este repositorio es la base de trabajo de la **Evaluación Parcial 1 de Ingeniería
DevOps (DOY0101)**: sobre él se construye el flujo de ramificación, la simulación
de trabajo colaborativo y el pipeline de integración continua.

---

## 1. Contexto

El microservicio nace del proyecto *Biblioteca* desarrollado en la asignatura de
Arquitectura de Software, pero fue reescrito como **servicio autónomo**: no se
registra en Eureka, no pasa por un API Gateway y no valida tokens JWT. Esa
decisión es deliberada — un artefacto que se despliega solo, con un único `.jar`
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
| Spring Boot Actuator | — | `/actuator/health` como healthcheck |
| Bean Validation | — | Validación declarativa de los DTO |
| MapStruct | 1.5.5.Final | Mapeo entidad ↔ DTO en tiempo de compilación |
| Lombok | 1.18.44 | Reducción de boilerplate |
| springdoc-openapi | 2.8.17 | Documentación Swagger UI |
| H2 | 2.3.232 | Base de datos en memoria (perfil `dev` y CI) |
| MySQL | 8.x | Base de datos del perfil `prod` (EC2) |
| Maven Wrapper | 3.9.14 | Build reproducible sin instalar Maven |

## 3. Estructura del repositorio

```
EV1-despliegue-801D/
├── .mvn/wrapper/                  Maven Wrapper (build reproducible)
├── db/
│   └── init-prestamos.sql         Creación de BD y usuario de aplicación en MySQL
├── docs/
│   ├── despliegue-aws.md          Guía de despliegue en EC2 con systemd
│   └── enunciado/                 Material de la evaluación
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
│       └── service/PrestamoServiceTest.java   Pruebas unitarias con Mockito
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

## 5. API REST

Base: `http://localhost:9005/api/v1/prestamos`

| Método | Ruta | Descripción | Códigos |
|---|---|---|---|
| `GET` | `/` | Lista todos los préstamos | 200 |
| `GET` | `/{id}` | Obtiene un préstamo por ID | 200, 404 |
| `GET` | `/usuario/{rut}` | Historial de un socio, del más reciente al más antiguo | 200 |
| `POST` | `/` | Registra un préstamo en estado `VIGENTE` | 201, 400, 409 |
| `PUT` | `/{id}` | Actualiza los datos de un préstamo | 200, 400, 404, 409 |
| `PATCH` | `/{id}/devolucion` | Registra la devolución: estado `DEVUELTO` y fecha de hoy. Solo para préstamos `VIGENTE` | 200, 404, 409 |
| `DELETE` | `/{id}` | Elimina un préstamo | 204, 404 |

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
# genera target/ms-prestamos-0.0.1-SNAPSHOT.jar
```

## 7. Perfiles y configuración

| Perfil | Base de datos | `ddl-auto` | Datos de prueba | Uso |
|---|---|---|---|---|
| `dev` (por defecto) | H2 en memoria | `create-drop` | Sí (`data.sql`) | Desarrollo local y pipeline de CI |
| `prod` | MySQL 8.x | `update` | No | Instancia EC2 |

El perfil `prod` **no contiene ninguna credencial**. Se inyectan como variables de
entorno desde la unidad de systemd, de manera que las contraseñas del servidor
nunca queden versionadas en el repositorio:

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
./mvnw test
```

- `PrestamoServiceTest` — pruebas unitarias con Mockito sobre las reglas de
  negocio. El repositorio y el mapper se sustituyen por mocks: lo que se verifica
  es la decisión del servicio, no que Hibernate sepa escribir en una tabla.
- `MsPrestamosApplicationTests` — prueba de humo que levanta el contexto completo
  con H2 y detecta beans faltantes, consultas derivadas mal escritas o
  propiedades inválidas en el `application.yml`.

Ambas suites corren **sin base de datos externa**, requisito para que el pipeline
de integración continua sea rápido y no dependa de infraestructura.

## 9. Despliegue

El procedimiento completo de despliegue en una instancia EC2 de AWS con Ubuntu,
gestionado como servicio de `systemd`, está en
[docs/despliegue-aws.md](docs/despliegue-aws.md).

## 10. Flujo de trabajo Git

> Pendiente. La estrategia de ramificación, las convenciones de commits y el
> pipeline de GitHub Actions se documentan en la siguiente etapa del encargo.

## 11. Declaración de uso de Inteligencia Artificial

> Sección a completar por el equipo antes de la entrega, según lo exigido en el
> enunciado. Debe indicar qué herramientas de IA se utilizaron y para qué
> (redacción, diagramas, apoyo en la generación de código base). Las
> justificaciones técnicas y las reflexiones individuales deben ser redactadas
> por cada integrante sin apoyo de IA.

---

**Asignatura:** Ingeniería DevOps (DOY0101) · Duoc UC
**Autor:** Juan Carlos Tapia
