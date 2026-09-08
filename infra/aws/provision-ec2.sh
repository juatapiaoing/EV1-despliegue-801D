#!/usr/bin/env bash
# ============================================================
# Aprovisionamiento de la instancia EC2 de ms-prestamos con AWS CLI
# ============================================================
# Crea (si no existen) el par de llaves, el security group y la
# instancia Ubuntu 24.04, y le inyecta user-data.sh para que quede
# lista para recibir despliegues desde GitHub Actions.
#
# Uso:
#   ./infra/aws/provision-ec2.sh
#
# Requisitos: AWS CLI configurado (aws sts get-caller-identity debe
# responder) y permisos sobre EC2 en la region indicada.
#
# Variables opcionales:
#   AWS_REGION      (us-east-1)      KEY_NAME  (ms-prestamos-key)
#   INSTANCE_TYPE   (t3.micro)       KEY_FILE  (~/.ssh/<KEY_NAME>.pem)
# ============================================================
set -euo pipefail

REGION="${AWS_REGION:-us-east-1}"
KEY_NAME="${KEY_NAME:-ms-prestamos-key}"
KEY_FILE="${KEY_FILE:-$HOME/.ssh/${KEY_NAME}.pem}"
SG_NAME="ms-prestamos-sg"
INSTANCE_TYPE="${INSTANCE_TYPE:-t3.micro}"
NAME_TAG="ms-prestamos"
APP_PORT=9005
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

export AWS_DEFAULT_REGION="${REGION}"

echo "==> Cuenta: $(aws sts get-caller-identity --query Arn --output text)"

# ── 1. Par de llaves ─────────────────────────────────────────
# La llave privada se guarda FUERA del repositorio (*.pem esta en .gitignore).
if aws ec2 describe-key-pairs --key-names "${KEY_NAME}" >/dev/null 2>&1; then
    echo "==> Par de llaves ${KEY_NAME} ya existe"
else
    echo "==> Creando par de llaves ${KEY_NAME} -> ${KEY_FILE}"
    mkdir -p "$(dirname "${KEY_FILE}")"
    # Se escribe en un temporal: si la llamada falla no queda un .pem vacio.
    # tr -d '\r': en Windows la CLI emite CRLF y OpenSSH rechaza la llave.
    if ! aws ec2 create-key-pair --key-name "${KEY_NAME}" --key-type ed25519 \
            --query KeyMaterial --output text | tr -d '\r' > "${KEY_FILE}.tmp"; then
        rm -f "${KEY_FILE}.tmp"
        echo "ERROR: no se pudo crear el par de llaves" >&2
        exit 1
    fi
    mv "${KEY_FILE}.tmp" "${KEY_FILE}"
    chmod 600 "${KEY_FILE}"
fi

# ── 2. Security group ────────────────────────────────────────
VPC_ID=$(aws ec2 describe-vpcs --filters Name=is-default,Values=true \
    --query 'Vpcs[0].VpcId' --output text)

SG_ID=$(aws ec2 describe-security-groups \
    --filters Name=group-name,Values="${SG_NAME}" Name=vpc-id,Values="${VPC_ID}" \
    --query 'SecurityGroups[0].GroupId' --output text 2>/dev/null || true)

if [[ -z "${SG_ID}" || "${SG_ID}" == "None" ]]; then
    echo "==> Creando security group ${SG_NAME} en ${VPC_ID}"
    SG_ID=$(aws ec2 create-security-group --group-name "${SG_NAME}" \
        --description "ms-prestamos: SSH para despliegue y puerto de la API" \
        --vpc-id "${VPC_ID}" --query GroupId --output text)

    # 22   : GitHub Actions despliega por SSH desde runners con IP variable.
    # 9005 : la API queda accesible para la demostracion de la evaluacion.
    # 3306 : NO se abre; el servicio se conecta a MySQL por localhost.
    aws ec2 authorize-security-group-ingress --group-id "${SG_ID}" \
        --ip-permissions \
        "IpProtocol=tcp,FromPort=22,ToPort=22,IpRanges=[{CidrIp=0.0.0.0/0,Description=SSH despliegue}]" \
        "IpProtocol=tcp,FromPort=${APP_PORT},ToPort=${APP_PORT},IpRanges=[{CidrIp=0.0.0.0/0,Description=API ms-prestamos}]" \
        >/dev/null
else
    echo "==> Security group ${SG_NAME} ya existe (${SG_ID})"
fi

# ── 3. AMI Ubuntu 24.04 LTS (amd64) ──────────────────────────
# Se busca la imagen mas reciente publicada por Canonical (owner
# 099720109477) en lugar de fijar un ami-id que cambia por region.
AMI_ID=$(aws ec2 describe-images --owners 099720109477 \
    --filters "Name=name,Values=ubuntu/images/hvm-ssd-gp3/ubuntu-noble-24.04-amd64-server-*" \
              "Name=state,Values=available" \
    --query 'sort_by(Images,&CreationDate)[-1].ImageId' --output text)
echo "==> AMI Ubuntu 24.04: ${AMI_ID}"

# ── 4. Instancia ─────────────────────────────────────────────
# En Git Bash (Windows) la CLI de AWS es un proceso nativo y no entiende
# rutas /c/...: se convierte a C:/... con cygpath.
USER_DATA="${SCRIPT_DIR}/user-data.sh"
if command -v cygpath >/dev/null 2>&1; then USER_DATA="$(cygpath -m "${USER_DATA}")"; fi
INSTANCE_ID=$(aws ec2 describe-instances \
    --filters "Name=tag:Name,Values=${NAME_TAG}" "Name=instance-state-name,Values=pending,running,stopping,stopped" \
    --query 'Reservations[0].Instances[0].InstanceId' --output text 2>/dev/null || true)

if [[ -z "${INSTANCE_ID}" || "${INSTANCE_ID}" == "None" ]]; then
    echo "==> Lanzando instancia ${INSTANCE_TYPE}"
    INSTANCE_ID=$(aws ec2 run-instances \
        --image-id "${AMI_ID}" \
        --instance-type "${INSTANCE_TYPE}" \
        --key-name "${KEY_NAME}" \
        --security-group-ids "${SG_ID}" \
        --user-data "file://${USER_DATA}" \
        --block-device-mappings '[{"DeviceName":"/dev/sda1","Ebs":{"VolumeSize":12,"VolumeType":"gp3"}}]' \
        --tag-specifications "ResourceType=instance,Tags=[{Key=Name,Value=${NAME_TAG}},{Key=Proyecto,Value=EV1-DOY0101}]" \
        --query 'Instances[0].InstanceId' --output text)
else
    echo "==> Instancia ${NAME_TAG} ya existe (${INSTANCE_ID})"
    aws ec2 start-instances --instance-ids "${INSTANCE_ID}" >/dev/null 2>&1 || true
fi

echo "==> Esperando a que ${INSTANCE_ID} este running..."
aws ec2 wait instance-running --instance-ids "${INSTANCE_ID}"

PUBLIC_IP=$(aws ec2 describe-instances --instance-ids "${INSTANCE_ID}" \
    --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)

cat <<RESUMEN

Instancia lista
  ID            : ${INSTANCE_ID}
  IP publica    : ${PUBLIC_IP}
  SSH           : ssh -i ${KEY_FILE} ubuntu@${PUBLIC_IP}
  Healthcheck   : http://${PUBLIC_IP}:${APP_PORT}/actuator/health   (tras el primer despliegue)

El user-data tarda 2-4 minutos en terminar (apt + MySQL). Comprobar con:
  ssh -i ${KEY_FILE} ubuntu@${PUBLIC_IP} 'cat /var/log/ms-prestamos-provision.done'

Secretos que espera el workflow de despliegue (gh secret set ...):
  EC2_HOST      = ${PUBLIC_IP}
  EC2_USER      = ubuntu
  EC2_SSH_KEY   = contenido de ${KEY_FILE}
RESUMEN
