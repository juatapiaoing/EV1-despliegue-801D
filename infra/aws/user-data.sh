#!/bin/bash
# ============================================================
# cloud-init de la instancia EC2 de ms-prestamos
# ============================================================
# Se ejecuta UNA sola vez, en el primer arranque de la instancia.
# Deja el servidor listo para recibir el .jar desde el pipeline:
#   - JRE 21 y MySQL 8 instalados
#   - base de datos "prestamos" y usuario de aplicacion creados
#   - credenciales en /etc/ms-prestamos.env (solo legible por root)
#   - unidad systemd habilitada, a la espera del artefacto
#
# El .jar NO se instala aqui: lo publica el workflow de despliegue
# (.github/workflows/cd-deploy.yml) en cada push a main.
# ============================================================
set -euxo pipefail
export DEBIAN_FRONTEND=noninteractive

APP_DIR=/opt/ms-prestamos
ENV_FILE=/etc/ms-prestamos.env
SERVICE=ms-prestamos

# ── 1. Runtime y base de datos ───────────────────────────────
apt-get update
apt-get install -y openjdk-21-jre-headless mysql-server
systemctl enable --now mysql

# ── 2. Base de datos y usuario de aplicacion ─────────────────
# La contrasena se genera en la propia instancia: nunca viaja por
# el repositorio ni por la consola de AWS.
DB_PASSWORD=$(openssl rand -hex 16)

mysql <<SQL
CREATE DATABASE IF NOT EXISTS prestamos
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'prestamos_app'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, INDEX, REFERENCES
    ON prestamos.* TO 'prestamos_app'@'localhost';
FLUSH PRIVILEGES;
SQL

# ── 3. Variables de entorno del servicio ─────────────────────
cat > "${ENV_FILE}" <<ENV
SPRING_PROFILES_ACTIVE=prod
SERVER_PORT=9005
DB_HOST=localhost
DB_PORT=3306
DB_NAME=prestamos
DB_USER=prestamos_app
DB_PASSWORD=${DB_PASSWORD}
ENV
chmod 600 "${ENV_FILE}"
chown root:root "${ENV_FILE}"

# ── 4. Directorio de despliegue ──────────────────────────────
mkdir -p "${APP_DIR}"
chown ubuntu:ubuntu "${APP_DIR}"

# ── 5. Unidad systemd ────────────────────────────────────────
# El jar se despliega siempre con el mismo nombre (ms-prestamos.jar)
# para que ExecStart no dependa de la version declarada en el pom.
cat > /etc/systemd/system/${SERVICE}.service <<UNIT
[Unit]
Description=Microservicio de prestamos de biblioteca (ms-prestamos)
After=network.target mysql.service
Wants=mysql.service

[Service]
Type=simple
User=ubuntu
WorkingDirectory=${APP_DIR}
EnvironmentFile=${ENV_FILE}
ExecStart=/usr/bin/java -jar ${APP_DIR}/ms-prestamos.jar
SuccessExitStatus=143
Restart=on-failure
RestartSec=10
StandardOutput=journal
StandardError=journal

[Install]
WantedBy=multi-user.target
UNIT

systemctl daemon-reload
systemctl enable ${SERVICE}.service
# No se arranca todavia: el .jar llega con el primer despliegue.

# ── 6. Permisos minimos para el pipeline de despliegue ───────
# El usuario ubuntu (con el que entra GitHub Actions por SSH) solo
# puede reiniciar y consultar este servicio, nada mas.
cat > /etc/sudoers.d/${SERVICE} <<SUDO
ubuntu ALL=(root) NOPASSWD: /usr/bin/systemctl restart ${SERVICE}.service
ubuntu ALL=(root) NOPASSWD: /usr/bin/systemctl start ${SERVICE}.service
ubuntu ALL=(root) NOPASSWD: /usr/bin/systemctl stop ${SERVICE}.service
ubuntu ALL=(root) NOPASSWD: /usr/bin/systemctl status ${SERVICE}.service
ubuntu ALL=(root) NOPASSWD: /usr/bin/systemctl is-active ${SERVICE}.service
ubuntu ALL=(root) NOPASSWD: /usr/bin/journalctl -u ${SERVICE}.service *
SUDO
chmod 440 /etc/sudoers.d/${SERVICE}

echo "ms-prestamos: instancia aprovisionada $(date -Is)" > /var/log/ms-prestamos-provision.done
