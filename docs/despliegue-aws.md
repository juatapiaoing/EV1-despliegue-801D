# Guía de despliegue — ms-prestamos en AWS EC2

**Proyecto:** `cl.jctapia:ms-prestamos`
**Entorno de destino:** Amazon EC2 (Ubuntu 24.04 LTS)
**Gestión del proceso:** `systemd`

---

## 1. Objetivo

Dejar el microservicio de préstamos corriendo en una instancia EC2 como un
servicio administrado por `systemd`, con arranque automático al encender la
máquina, reinicio ante fallos y credenciales fuera del repositorio.

El artefacto que se despliega es un único `.jar` autocontenido: `ms-prestamos`
no depende de Eureka, de un API Gateway ni de ningún otro servicio, así que la
instancia solo necesita un JRE y una base de datos MySQL.

## 2. Requisitos previos

**En el equipo local**

- JDK 21
- Este repositorio clonado (incluye el Maven Wrapper, no hace falta instalar Maven)
- Un cliente SSH/SFTP: OpenSSH, MobaXterm o PuTTY + WinSCP

**En AWS**

- Una instancia EC2 con AMI Ubuntu 24.04 LTS (`t3.micro` es suficiente)
- El par de llaves (`.pem` / `.ppk`) asociado a la instancia
- Acceso `sudo` en la instancia

---

## 3. Compilar el artefacto

Desde la raíz del repositorio, en el equipo local:

```bash
# Linux / macOS
./mvnw clean package

# Windows
.\mvnw.cmd clean package
```

Esto ejecuta las pruebas y genera:

```
target/ms-prestamos-0.0.1-SNAPSHOT.jar
```

> Si las pruebas fallan, el `.jar` no se genera. Es intencional: un artefacto que
> no pasa su propia suite no debería llegar nunca a un servidor.

Para saltar las pruebas puntualmente (solo si ya se verificaron en el pipeline):
`./mvnw clean package -DskipTests`.

---

## 4. Preparar la instancia EC2

### 4.1 Conectarse

```bash
ssh -i ~/.ssh/mi-llave.pem ubuntu@<IP_PUBLICA>
```

| Dato | Valor |
|---|---|
| Host | IP pública de la instancia |
| Puerto | 22 |
| Usuario | `ubuntu` |
| Llave | archivo `.pem` (o `.ppk` en PuTTY) |

### 4.2 Instalar el runtime de Java

El `.jar` ya trae embebido Tomcat y todas las librerías, así que basta con el JRE
(no el JDK completo):

```bash
sudo apt update
sudo apt install -y openjdk-21-jre-headless
java -version    # debe mostrar 21.x
```

### 4.3 Instalar y asegurar MySQL

```bash
sudo apt install -y mysql-server
sudo systemctl enable --now mysql
sudo mysql_secure_installation
```

### 4.4 Crear la base de datos y el usuario de aplicación

Copiar `db/init-prestamos.sql` a la instancia y ejecutarlo. **Antes de ejecutarlo,
reemplazar `CAMBIAR_ESTA_PASSWORD` por una contraseña real**:

```bash
# desde el equipo local
scp -i ~/.ssh/mi-llave.pem db/init-prestamos.sql ubuntu@<IP_PUBLICA>:/home/ubuntu/

# ya dentro de la instancia
nano /home/ubuntu/init-prestamos.sql      # cambiar la contraseña
sudo mysql < /home/ubuntu/init-prestamos.sql
rm /home/ubuntu/init-prestamos.sql        # no dejar la contraseña en el disco
```

El script crea la base `prestamos` y el usuario `prestamos_app` con permisos
acotados a esa única base. El microservicio **no se conecta como `root`**: si las
credenciales de la aplicación se filtraran, el daño queda contenido a un solo
esquema.

Las tablas no se crean aquí — las genera Hibernate en el primer arranque, porque
el perfil `prod` usa `ddl-auto: update`.

---

## 5. Publicar el artefacto

### 5.1 Crear el directorio de despliegue

```bash
sudo mkdir -p /opt/ms-prestamos
sudo chown ubuntu:ubuntu /opt/ms-prestamos
```

### 5.2 Copiar el `.jar`

Desde el equipo local:

```bash
scp -i ~/.ssh/mi-llave.pem \
    target/ms-prestamos-0.0.1-SNAPSHOT.jar \
    ubuntu@<IP_PUBLICA>:/opt/ms-prestamos/
```

Verificar en la instancia que el archivo llegó completo:

```bash
ls -lh /opt/ms-prestamos/
```

---

## 6. Configurar las credenciales

Las contraseñas **no viajan en el repositorio ni en el `.jar`**. Se declaran en un
archivo de entorno que solo puede leer `root`, y `systemd` las inyecta al proceso:

```bash
sudo nano /etc/ms-prestamos.env
```

Contenido:

```ini
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=9005
DB_HOST=localhost
DB_PORT=3306
DB_NAME=prestamos
DB_USER=prestamos_app
DB_PASSWORD=la_password_definida_en_el_paso_4.4
```

Restringir los permisos del archivo:

```bash
sudo chmod 600 /etc/ms-prestamos.env
sudo chown root:root /etc/ms-prestamos.env
```

> `chmod 600` es la parte que importa: sin ella, cualquier usuario de la instancia
> podría leer la contraseña de la base de datos con un simple `cat`.

---

## 7. Crear el servicio de systemd

```bash
sudo nano /etc/systemd/system/ms-prestamos.service
```

```ini
[Unit]
Description=Microservicio de prestamos de biblioteca (ms-prestamos)
# Arrancar despues de la red y de MySQL: si la base no esta lista,
# Hibernate falla al validar el esquema y el servicio muere al iniciar.
After=network.target mysql.service
Wants=mysql.service

[Service]
Type=simple
User=ubuntu
WorkingDirectory=/opt/ms-prestamos

# Las credenciales entran por aqui, nunca en la linea de comandos:
# un ExecStart con la password seria visible para todos en 'ps aux'.
EnvironmentFile=/etc/ms-prestamos.env

ExecStart=/usr/bin/java -jar /opt/ms-prestamos/ms-prestamos-0.0.1-SNAPSHOT.jar

# 143 = SIGTERM. Spring Boot termina asi en un apagado limpio; sin esta linea
# systemd lo reportaria como fallo cada vez que se detiene el servicio.
SuccessExitStatus=143

Restart=on-failure
RestartSec=10

StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
```

> El valor de `ExecStart` debe coincidir **exactamente** con el nombre del archivo
> que hay en `/opt/ms-prestamos` (verificar con `ls -la`). Un nombre incorrecto
> produce el error *"Unable to access jarfile"*.

### Habilitar e iniciar

```bash
sudo systemctl daemon-reload
sudo systemctl enable ms-prestamos.service
sudo systemctl start ms-prestamos.service
sudo systemctl status ms-prestamos.service
```

El estado debe indicar `Active: active (running)`.

---

## 8. Abrir el puerto en el Security Group

En la consola de AWS → **EC2 → Instancias → Seguridad → Security Group → Editar
reglas de entrada**, agregar:

| Tipo | Protocolo | Puerto | Origen |
|---|---|---|---|
| TCP personalizado | TCP | `9005` | El rango de IP que deba consumir la API |

> Evitar `0.0.0.0/0` salvo que la API deba ser realmente pública: el
> microservicio no tiene autenticación, así que abrirlo a todo internet expone
> los datos de préstamos y sus operaciones de escritura a cualquiera.

MySQL (3306) **no debe abrirse**: el microservicio se conecta por `localhost`
dentro de la misma instancia.

---

## 9. Verificar el despliegue

```bash
# Dentro de la instancia
curl http://localhost:9005/actuator/health
# {"status":"UP","components":{"db":{"status":"UP",...}}}

curl http://localhost:9005/api/v1/prestamos
```

Desde fuera, en un navegador:

| Recurso | URL |
|---|---|
| Healthcheck | `http://<IP_PUBLICA>:9005/actuator/health` |
| Swagger UI | `http://<IP_PUBLICA>:9005/swagger-ui.html` |
| API | `http://<IP_PUBLICA>:9005/api/v1/prestamos` |

`/actuator/health` devuelve además el estado del componente `db`, así que sirve
para distinguir "la aplicación no arrancó" de "arrancó pero no alcanza MySQL".

---

## 10. Operación

```bash
sudo journalctl -u ms-prestamos.service -f        # logs en tiempo real
sudo journalctl -u ms-prestamos.service -n 100 --no-pager   # últimas 100 líneas
sudo systemctl restart ms-prestamos.service       # reiniciar
sudo systemctl stop ms-prestamos.service          # detener
sudo systemctl disable ms-prestamos.service       # quitar del arranque automático
```

### Desplegar una versión nueva

```bash
sudo systemctl stop ms-prestamos.service
# copiar el nuevo .jar por scp
sudo systemctl start ms-prestamos.service
sudo journalctl -u ms-prestamos.service -f
```

---

## 11. Resolución de problemas

| Síntoma | Causa habitual | Cómo verificarlo |
|---|---|---|
| `Unable to access jarfile` | El nombre del `.jar` en `ExecStart` no coincide con el real | `ls -la /opt/ms-prestamos/` |
| El servicio reinicia en bucle | Excepción al arrancar (BD, puerto ocupado, variable faltante) | `sudo journalctl -u ms-prestamos.service -n 100 --no-pager` |
| `Access denied for user 'prestamos_app'` | `DB_PASSWORD` no coincide con la del paso 4.4 | `mysql -u prestamos_app -p prestamos` |
| `Unknown database 'prestamos'` | No se ejecutó `init-prestamos.sql` | `sudo mysql -e "SHOW DATABASES;"` |
| `Could not resolve placeholder 'DB_USER'` | `EnvironmentFile` mal referenciado o sin la variable | `sudo cat /etc/ms-prestamos.env` |
| Responde en `localhost` pero no desde fuera | Puerto cerrado en el Security Group | Revisar reglas de entrada (paso 8) |
| `Web server failed to start. Port 9005 was already in use` | Quedó un proceso Java anterior vivo | `sudo lsof -i :9005` |

Para ver el stacktrace completo sin el ruido de systemd, ejecutar el `.jar` a
mano cargando las variables de entorno:

```bash
set -a && source /etc/ms-prestamos.env && set +a
java -jar /opt/ms-prestamos/ms-prestamos-0.0.1-SNAPSHOT.jar
```

---

## 12. Diferencias respecto al entorno local

| | Local (`dev`) | EC2 (`prod`) |
|---|---|---|
| Base de datos | H2 en memoria | MySQL 8.x |
| Esquema | `create-drop` (se recrea cada arranque) | `update` (persiste) |
| Datos de prueba | Sí, `data.sql` | No |
| Credenciales | No aplica | Variables de entorno en `/etc/ms-prestamos.env` |
| Consola H2 | Habilitada | Deshabilitada |
| Nivel de log | `DEBUG` | `INFO` |
| Ejecución | `./mvnw spring-boot:run` | `systemd` |
