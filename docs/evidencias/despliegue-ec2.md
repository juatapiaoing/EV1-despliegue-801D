# Evidencias del despliegue en AWS EC2

La instancia se creó en **AWS Academy Learner Lab**, que detiene las máquinas y
revoca las credenciales al terminar cada sesión de laboratorio. Por eso las
evidencias del despliegue quedan registradas aquí y en los runs de GitHub
Actions, que son permanentes. Todas las salidas de esta página se capturaron el
8 de septiembre de 2026 contra la instancia real.

## 1. Aprovisionamiento con la AWS CLI

Salida de `./infra/aws/provision-ec2.sh`:

```
==> Cuenta: arn:aws:sts::275597698017:assumed-role/voclabs/user3244040=Juan_Carlos_Tapia
==> Par de llaves ms-prestamos-key ya existe
==> Security group ms-prestamos-sg ya existe (sg-0e61dc04ef7d2a958)
==> AMI Ubuntu 24.04: ami-025d99823a4caad37
==> Lanzando instancia t3.micro
==> Esperando a que i-01e1afc7834b1fdb2 este running...

Instancia lista
  ID            : i-01e1afc7834b1fdb2
  IP publica    : 18.234.175.172
  SSH           : ssh -i ~/.ssh/ms-prestamos-key.pem ubuntu@18.234.175.172
  Healthcheck   : http://18.234.175.172:9005/actuator/health   (tras el primer despliegue)
```

Fin del `user-data.sh` (cloud-init), comprobado por SSH:

```
$ ssh -i ~/.ssh/ms-prestamos-key.pem ubuntu@18.234.175.172 'cat /var/log/ms-prestamos-provision.done'
ms-prestamos: instancia aprovisionada 2026-09-08T20:16:55+00:00
```

## 2. Runs del workflow `CD - Despliegue en EC2`

Cada merge a `main` disparó un despliegue. Todos terminaron con el healthcheck en
`UP`; los logs completos están en la pestaña *Actions*:

| Versión | Disparador | Run |
|---|---|---|
| `v1.0.0` | Merge del release (#6) | https://github.com/juatapiaoing/EV1-despliegue-801D/actions/runs/34275641251 |
| `v1.0.1` | Merge del hotfix `DELETE` vigente (#7) | https://github.com/juatapiaoing/EV1-despliegue-801D/actions/runs/34276224926 |
| `v1.0.2` | Merge del hotfix de documentación (#9) | https://github.com/juatapiaoing/EV1-despliegue-801D/actions/runs/34276752335 |
| `v1.0.3` | Merge de los hotfix de documentación (#12, #14) | https://github.com/juatapiaoing/EV1-despliegue-801D/actions/runs/34279111149 |

Extracto del final del primer run (`v1.0.0`):

```
  ✓ Compilar, probar y empaquetar
  ✓ Preparar acceso SSH a la instancia
  ✓ Copiar el artefacto a la instancia
  ✓ Activar la nueva version y reiniciar el servicio
  ✓ Verificar healthcheck
  ✓ Complete job
```

## 3. Servicio en ejecución

Healthcheck desde fuera de la instancia, con el componente `db` conectado a
MySQL:

```
$ curl http://18.234.175.172:9005/actuator/health
{"status":"UP","components":{"db":{"status":"UP","details":{"database":"MySQL","validationQuery":"isValid()"}},"diskSpace":{"status":"UP","details":{"total":11362635776,"free":8038993920,"threshold":10485760,"path":"/opt/ms-prestamos/.","exists":true}},"ping":{"status":"UP"},"ssl":{"status":"UP","details":{"validChains":[],"invalidChains":[]}}}}
```

Versión desplegada (tras `v1.0.2`, `/actuator/info` toma la versión del `pom.xml`):

```
$ curl http://18.234.175.172:9005/actuator/info
{"app":{"nombre":"ms-prestamos","descripcion":"Microservicio de gestion de prestamos de biblioteca","version":"1.0.3"}}
```

## 4. Verificación del hotfix `v1.0.1` sobre la instancia

Secuencia completa contra la API desplegada: crear un préstamo, intentar
borrarlo estando `VIGENTE` (rechazado), devolverlo y borrarlo (aceptado).

```
$ curl -X POST http://18.234.175.172:9005/api/v1/prestamos \
    -H "Content-Type: application/json" \
    -d '{"codigoLibro":"BIB-7777","rutUsuario":"11222333-4","fechaPrestamo":"2026-09-08","fechaVencimiento":"2026-09-22","observacion":"prueba hotfix"}'
{"id":1,"codigoLibro":"BIB-7777","rutUsuario":"11222333-4","fechaPrestamo":"2026-09-08","fechaVencimiento":"2026-09-22","estado":"VIGENTE","observacion":"prueba hotfix","_links":{...}}

$ curl -i -X DELETE http://18.234.175.172:9005/api/v1/prestamos/1
HTTP/1.1 409
{"timestamp":"2026-09-08T20:43:11.007440627","status":409,"error":"Conflict","message":"El prestamo 1 esta VIGENTE y no puede eliminarse: registre la devolucion o cancelelo primero","path":"/api/v1/prestamos/1"}

$ curl -i -X PATCH http://18.234.175.172:9005/api/v1/prestamos/1/devolucion
HTTP/1.1 200

$ curl -i -X DELETE http://18.234.175.172:9005/api/v1/prestamos/1
HTTP/1.1 204

$ curl http://18.234.175.172:9005/api/v1/prestamos/atrasados
{"_links":{"self":{"href":"http://18.234.175.172:9005/api/v1/prestamos/atrasados"}}}
```

## 5. Cómo volver a levantar el entorno para una revisión en vivo

1. Iniciar la sesión del Learner Lab y tomar las credenciales nuevas de la CLI.
2. Arrancar la instancia (el lab la deja detenida, no la borra):
   `aws ec2 start-instances --instance-ids i-01e1afc7834b1fdb2`
3. Obtener la nueva IP pública (cambia en cada arranque) y actualizar el secreto:
   `gh secret set EC2_HOST --body "<NUEVA_IP>"`
4. Relanzar el workflow **CD - Despliegue en EC2** desde la pestaña *Actions*
   (`workflow_dispatch`) o con `gh workflow run cd-deploy.yml`.
5. Comprobar `http://<NUEVA_IP>:9005/swagger-ui.html`.

Si la instancia no responde en el momento de un push a `main`, el workflow lo
detecta y **omite el despliegue** dejando un aviso en el run, en lugar de fallar.
