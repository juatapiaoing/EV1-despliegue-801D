# Guía de despliegue — ms-prestamos en AWS EC2

**Proyecto:** `cl.jctapia:ms-prestamos`
**Entorno de destino:** Amazon EC2 (Ubuntu 24.04 LTS, `t3.micro`)
**Gestión del proceso:** `systemd`
**Mecanismo de despliegue:** GitHub Actions → SSH → EC2 (continuo, en cada push a `main`)

---

## 1. Objetivo

Dejar el microservicio de préstamos corriendo en una instancia EC2 como un
servicio administrado por `systemd`, con arranque automático, reinicio ante
fallos y credenciales fuera del repositorio, y que **cada versión que llega a
`main` se publique sola** sin pasos manuales.

El artefacto que se despliega es un único `.jar` autocontenido: `ms-prestamos`
no depende de Eureka, de un API Gateway ni de ningún otro servicio, así que la
instancia solo necesita un JRE y una base de datos MySQL.

## 2. Arquitectura del despliegue

```
 desarrollador            GitHub                          AWS (us-east-1)
 ─────────────   ┌──────────────────────────┐   ┌──────────────────────────────┐
 git push ─────► │ main                     │   │ EC2 t3.micro  Ubuntu 24.04   │
 (release /      │   └─► CD - Despliegue    │   │  ┌────────────────────────┐  │
  hotfix)        │        en EC2            │   │  │ systemd: ms-prestamos  │  │
                 │        1. mvnw package   │   │  │  java -jar             │  │
                 │        2. scp .jar ──────┼───┼─►│  /opt/ms-prestamos/    │  │
                 │        3. ssh restart ───┼───┼─►│  ms-prestamos.jar      │  │
                 │        4. curl /health ◄─┼───┼──│  :9005                 │  │
                 └──────────────────────────┘   │  └───────────┬────────────┘  │
                                                │              │ localhost:3306 │
                                                │  ┌───────────▼────────────┐  │
                                                │  │ MySQL 8  (prestamos)   │  │
                                                │  └────────────────────────┘  │
                                                └──────────────────────────────┘
```

| Pieza | Archivo | Rol |
|---|---|---|
| Aprovisionamiento | [`infra/aws/provision-ec2.sh`](../infra/aws/provision-ec2.sh) | Crea par de llaves, security group e instancia con la AWS CLI |
| Configuración inicial | [`infra/aws/user-data.sh`](../infra/aws/user-data.sh) | cloud-init: instala JRE 21 y MySQL, crea la BD, el `.env` y la unidad `systemd` |
| Despliegue continuo | [`.github/workflows/cd-deploy.yml`](../.github/workflows/cd-deploy.yml) | Compila, copia el `.jar` por SSH, reinicia el servicio y verifica el healthcheck |
| Esquema manual | [`db/init-prestamos.sql`](../db/init-prestamos.sql) | Equivalente a lo que hace `user-data.sh` en la BD, para instalaciones a mano |

## 3. Requisitos previos

**En el equipo que aprovisiona**

- AWS CLI v1 o v2 con credenciales activas (`aws sts get-caller-identity` debe responder)
- Bash (Git Bash sirve en Windows)
- GitHub CLI (`gh`) autenticado en el repositorio, para cargar los secretos

**En AWS**

- Permisos sobre EC2 en la región (`us-east-1` por defecto). En AWS Academy
  Learner Lab bastan los del rol `voclabs`.

No hace falta JDK ni Maven en este equipo: el `.jar` lo compila GitHub Actions.

---

## 4. Aprovisionar la instancia (una sola vez)

```bash
./infra/aws/provision-ec2.sh
```

El script es idempotente: si el par de llaves, el security group o la
instancia ya existen, los reutiliza. Al terminar imprime la IP pública y los
valores que hay que cargar como secretos.

Qué crea:

| Recurso | Nombre | Detalle |
|---|---|---|
| Par de llaves | `ms-prestamos-key` | ed25519. La privada queda en `~/.ssh/ms-prestamos-key.pem`, **fuera del repo** (`*.pem` está en `.gitignore`) |
| Security group | `ms-prestamos-sg` | Entrada TCP 22 (SSH para el pipeline) y TCP 9005 (API). **3306 no se abre** |
| Instancia | `ms-prestamos` | `t3.micro`, AMI Ubuntu 24.04 más reciente de Canonical, disco gp3 de 12 GB |

> **Por qué 22 y 9005 a `0.0.0.0/0`:** los runners de GitHub Actions no tienen
> IP fija, y la API debe poder demostrarse desde cualquier red. En un entorno
> real el puerto 22 se restringiría a una VPN o se reemplazaría por AWS SSM, y
> la API iría detrás de un balanceador con TLS.

### 4.1 Qué hace `user-data.sh` en el primer arranque

1. Instala `openjdk-21-jre-headless` y `mysql-server`.
2. Crea la base `prestamos` y el usuario `prestamos_app` con una **contraseña
   aleatoria generada en la propia instancia** (`openssl rand`), con permisos
   acotados a ese esquema. El servicio nunca se conecta como `root`.
3. Escribe `/etc/ms-prestamos.env` (perfil `prod`, puerto, credenciales) con
   permisos `600` y dueño `root`.
4. Crea `/opt/ms-prestamos` y la unidad `ms-prestamos.service`, que ejecuta
   siempre `/opt/ms-prestamos/ms-prestamos.jar` (nombre fijo, independiente de
   la versión del `pom.xml`).
5. Habilita el servicio pero **no lo arranca**: el `.jar` llega con el primer
   despliegue.
6. Da al usuario `ubuntu` permiso `sudo` sin contraseña **solo** para
   `systemctl start|stop|restart|status ms-prestamos` y `journalctl -u
   ms-prestamos`. Es lo mínimo que necesita el pipeline.

Comprobar que terminó (tarda 2–4 minutos):

```bash
ssh -i ~/.ssh/ms-prestamos-key.pem ubuntu@<IP_PUBLICA> 'cat /var/log/ms-prestamos-provision.done'
```

---

## 5. Cargar los secretos en GitHub

El workflow de despliegue lee tres secretos del repositorio
(*Settings → Secrets and variables → Actions*):

| Secreto | Valor |
|---|---|
| `EC2_HOST` | IP pública (o DNS) de la instancia |
| `EC2_USER` | `ubuntu` |
| `EC2_SSH_KEY` | Contenido completo de `~/.ssh/ms-prestamos-key.pem` |

Con GitHub CLI:

```bash
gh secret set EC2_HOST --body "<IP_PUBLICA>"
gh secret set EC2_USER --body "ubuntu"
gh secret set EC2_SSH_KEY < ~/.ssh/ms-prestamos-key.pem
```

> Si la instancia se detiene y vuelve a arrancar, la IP pública cambia:
> hay que actualizar `EC2_HOST`.

---

## 6. Despliegue continuo

Cada push a `main` (es decir, cada merge de un release o de un hotfix) ejecuta
`CD - Despliegue en EC2`:

| Paso | Qué hace | Por qué |
|---|---|---|
| Compilar, probar y empaquetar | `./mvnw clean package` | El `.jar` que se despliega es el que acaba de pasar las pruebas; nunca se sube uno compilado a mano |
| Preparar SSH | Escribe la llave desde el secreto y registra la huella del host | El runner es efímero: no tiene nada configurado |
| Copiar el artefacto | `scp` a `/opt/ms-prestamos/ms-prestamos.jar.new` | Se copia con otro nombre para no pisar el `.jar` en uso a medias |
| Activar y reiniciar | `mv` atómico + `systemctl restart` | Si el `scp` falla, el servicio sigue con la versión anterior |
| Verificar healthcheck | `curl /actuator/health` hasta 120 s | El job falla si el servicio no llega a `UP`, y vuelca el `journalctl` para diagnosticar |

También se puede lanzar a mano desde la pestaña *Actions* (`workflow_dispatch`),
por ejemplo tras reaprovisionar la instancia.

---

## 7. Verificar el despliegue

```bash
curl http://<IP_PUBLICA>:9005/actuator/health
# {"status":"UP","components":{"db":{"status":"UP",...}}}

curl http://<IP_PUBLICA>:9005/api/v1/prestamos
```

| Recurso | URL |
|---|---|
| Healthcheck | `http://<IP_PUBLICA>:9005/actuator/health` |
| Swagger UI | `http://<IP_PUBLICA>:9005/swagger-ui.html` |
| API | `http://<IP_PUBLICA>:9005/api/v1/prestamos` |

`/actuator/health` incluye el estado del componente `db`, así que distingue
"la aplicación no arrancó" de "arrancó pero no alcanza MySQL".

---

## 8. Operación

```bash
ssh -i ~/.ssh/ms-prestamos-key.pem ubuntu@<IP_PUBLICA>

sudo journalctl -u ms-prestamos.service -f                  # logs en tiempo real
sudo journalctl -u ms-prestamos.service -n 100 --no-pager   # últimas 100 líneas
sudo systemctl restart ms-prestamos.service                 # reiniciar
sudo systemctl stop ms-prestamos.service                    # detener
sudo systemctl status ms-prestamos.service                  # estado
```

Detener o arrancar la instancia desde el equipo local:

```bash
aws ec2 stop-instances  --instance-ids <ID>
aws ec2 start-instances --instance-ids <ID>
```

---

## 9. Despliegue manual (plan B)

Si GitHub Actions no está disponible, el mismo procedimiento se hace a mano
desde un equipo con JDK 21:

```bash
./mvnw clean package
scp -i ~/.ssh/ms-prestamos-key.pem target/ms-prestamos-0.0.1-SNAPSHOT.jar \
    ubuntu@<IP_PUBLICA>:/opt/ms-prestamos/ms-prestamos.jar.new
ssh -i ~/.ssh/ms-prestamos-key.pem ubuntu@<IP_PUBLICA> \
    'cd /opt/ms-prestamos && mv -f ms-prestamos.jar.new ms-prestamos.jar && sudo systemctl restart ms-prestamos.service'
```

Para una instancia **no** aprovisionada con `user-data.sh` (por ejemplo, un
servidor ya existente), replicar los pasos de la sección 4.1 a mano: instalar
JRE y MySQL, ejecutar `db/init-prestamos.sql` reemplazando
`CAMBIAR_ESTA_PASSWORD`, crear `/etc/ms-prestamos.env` con `chmod 600` y copiar
la unidad `systemd` que aparece en `infra/aws/user-data.sh`.

---

## 10. Resolución de problemas

| Síntoma | Causa habitual | Cómo verificarlo |
|---|---|---|
| El job falla en *Preparar acceso SSH* / *Copiar el artefacto* | `EC2_HOST` desactualizado (la IP cambió) o puerto 22 cerrado | `aws ec2 describe-instances --filters Name=tag:Name,Values=ms-prestamos` |
| `Permission denied (publickey)` | `EC2_SSH_KEY` no corresponde al par de llaves de la instancia | `ssh -i ~/.ssh/ms-prestamos-key.pem ubuntu@<IP>` desde el equipo local |
| El healthcheck nunca llega a `UP` | La instancia aún ejecuta `user-data.sh` o MySQL no arrancó | `cat /var/log/ms-prestamos-provision.done`; `systemctl status mysql` |
| `Unable to access jarfile` | El `.jar` no se copió a `/opt/ms-prestamos/ms-prestamos.jar` | `ls -la /opt/ms-prestamos/` |
| El servicio reinicia en bucle | Excepción al arrancar (BD, puerto ocupado, variable faltante) | `sudo journalctl -u ms-prestamos.service -n 100 --no-pager` |
| `Access denied for user 'prestamos_app'` | `/etc/ms-prestamos.env` editado a mano con otra contraseña | `sudo cat /etc/ms-prestamos.env` y `mysql -u prestamos_app -p prestamos` |
| Responde en `localhost` pero no desde fuera | Puerto 9005 cerrado en el security group | `aws ec2 describe-security-groups --group-names ms-prestamos-sg` |
| `Port 9005 was already in use` | Quedó un proceso Java anterior vivo | `sudo lsof -i :9005` |

Para ver el stacktrace completo sin el ruido de `systemd`, ejecutar el `.jar` a
mano cargando las variables de entorno:

```bash
set -a && sudo cat /etc/ms-prestamos.env > /tmp/env && source /tmp/env && set +a && rm /tmp/env
java -jar /opt/ms-prestamos/ms-prestamos.jar
```

---

## 11. Diferencias respecto al entorno local

| | Local (`dev`) | EC2 (`prod`) |
|---|---|---|
| Base de datos | H2 en memoria | MySQL 8.x |
| Esquema | `create-drop` (se recrea cada arranque) | `update` (persiste) |
| Datos de prueba | Sí, `data.sql` | No |
| Credenciales | No aplica | Variables de entorno en `/etc/ms-prestamos.env` |
| Consola H2 | Habilitada | Deshabilitada |
| Nivel de log | `DEBUG` | `INFO` |
| Ejecución | `./mvnw spring-boot:run` | `systemd`, desplegado por GitHub Actions |
