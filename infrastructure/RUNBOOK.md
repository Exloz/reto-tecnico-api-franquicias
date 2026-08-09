# Runbook AWS DEV

Este documento explica la arquitectura desplegada, cómo probarla, cómo inspeccionar PostgreSQL y cómo pausar, destruir o reconstruir el ambiente `dev`.

## Estado actual

- Cuenta: `127321794531`.
- Región: `us-east-1`.
- Endpoint público: `https://sxhuakqh88.execute-api.us-east-1.amazonaws.com`.
- Terraform root: `infrastructure/environments/dev`.
- Estado remoto: `s3://franchise-127321794531-terraform-state/dev/infrastructure.tfstate`.
- ECS: `franchise-dev-cluster` / `franchise-dev-api`.
- RDS: `franchise-dev-database`.
- Dashboard: `franchise-dev-operations`.

La API sí está detrás de API Gateway:

```text
Internet HTTPS
    -> API Gateway HTTP API
    -> VPC Link
    -> ALB interno HTTP
    -> ECS Fargate privado:8080
    -> RDS PostgreSQL privado:5432 con TLS VERIFY_FULL
```

API Gateway es el único punto público. El ALB es interno, la tarea ECS no tiene IP pública y RDS no acepta tráfico público. TLS termina en API Gateway; el salto VPC Link a ALB usa HTTP restringido por security groups. ECS valida el certificado y hostname de RDS.

## Requisitos locales

- AWS CLI autenticado mediante `aws login`.
- `jq`.
- Terraform 1.10 o superior.
- Docker con Buildx para reconstruir imágenes.
- k6 local o Docker para carga.

Ejecuta los comandos desde la raíz del repositorio.

## Autenticación

Confirma primero la cuenta:

```sh
aws login
aws sts get-caller-identity
```

Terraform y las operaciones ECS deben usar el rol de aplicación:

```sh
credentials="$(aws sts assume-role \
  --role-arn arn:aws:iam::127321794531:role/franchise-terraform-apply \
  --role-session-name franchise-operator \
  --output json)"

export AWS_ACCESS_KEY_ID="$(printf '%s' "$credentials" | jq -r '.Credentials.AccessKeyId')"
export AWS_SECRET_ACCESS_KEY="$(printf '%s' "$credentials" | jq -r '.Credentials.SecretAccessKey')"
export AWS_SESSION_TOKEN="$(printf '%s' "$credentials" | jq -r '.Credentials.SessionToken')"
unset AWS_PROFILE

aws sts get-caller-identity
```

Las credenciales son temporales. Abre una terminal nueva o repite el proceso cuando expiren.

Inicializa Terraform:

```sh
terraform -chdir=infrastructure/environments/dev init
```

## Revisar el ambiente

Obtén la URL actual:

```sh
API_URL="$(terraform -chdir=infrastructure/environments/dev output -raw api_endpoint)"
printf '%s\n' "$API_URL"
```

Comprueba readiness:

```sh
curl --fail-with-body --silent --show-error \
  "$API_URL/actuator/health/readiness"
```

Comprueba ECS:

```sh
aws ecs describe-services \
  --cluster franchise-dev-cluster \
  --services franchise-dev-api \
  --query 'services[0].{desired:desiredCount,running:runningCount,pending:pendingCount,rollout:deployments[0].rolloutState}' \
  --output table
```

Comprueba el target del ALB:

```sh
TARGET_GROUP_ARN="$(aws elbv2 describe-target-groups \
  --names franchise-dev-api \
  --query 'TargetGroups[0].TargetGroupArn' \
  --output text)"

aws elbv2 describe-target-health \
  --target-group-arn "$TARGET_GROUP_ARN" \
  --query 'TargetHealthDescriptions[].{target:Target.Id,state:TargetHealth.State,reason:TargetHealth.Reason}' \
  --output table
```

Comprueba el VPC Link:

```sh
aws apigatewayv2 get-vpc-links \
  --query 'Items[?Name==`franchise-dev-vpc-link`].{id:VpcLinkId,status:VpcLinkStatus,message:VpcLinkStatusMessage}' \
  --output table
```

Revisa logs:

```sh
aws logs tail /ecs/franchise-dev-api --since 15m --follow
```

Busca una solicitud completa por `X-Correlation-ID`:

```sh
CORRELATION_ID=phase08-live-validation

aws logs filter-log-events \
  --log-group-name /ecs/franchise-dev-api \
  --filter-pattern "{ $.correlationId = \"$CORRELATION_ID\" }" \
  --query 'events[].message' \
  --output text
```

El evento `request.completed` incluye `correlationId`, `apiGatewayRequestId`, método, ruta normalizada, caso de uso, estado, duración, resultado y código de error. Usa `apiGatewayRequestId` para localizar el mismo request en `/aws/apigateway/franchise-dev`:

```sh
API_GATEWAY_REQUEST_ID='Bzu-ejocoAMEPCg='

aws logs filter-log-events \
  --log-group-name /aws/apigateway/franchise-dev \
  --filter-pattern "{ $.requestId = \"$API_GATEWAY_REQUEST_ID\" }" \
  --query 'events[].message' \
  --output text
```

Busca errores recientes:

```sh
aws logs filter-log-events \
  --log-group-name /ecs/franchise-dev-api \
  --start-time "$((($(date +%s)-1800)*1000))" \
  --filter-pattern '?ERROR ?FATAL ?Exception' \
  --query 'events[].message' \
  --output text
```

Revisa alarmas:

```sh
aws cloudwatch describe-alarms \
  --alarm-name-prefix franchise-dev \
  --query 'MetricAlarms[].{name:AlarmName,state:StateValue}' \
  --output table
```

El canal aprobado para esta entrega es la consola de CloudWatch, sin acciones SNS. El maintainer del repositorio es responsable de revisar el dashboard y las alarmas; no existe notificación automática ni escalamiento secundario.

Los SLO iniciales usan una ventana móvil de 30 días:

- Disponibilidad mínima: 99 %.
- Latencia p95: menos de 500 ms.
- Respuestas 5xx: menos de 1 %.

Abre el dashboard consolidado:

```text
https://console.aws.amazon.com/cloudwatch/home?region=us-east-1#dashboards:name=franchise-dev-operations
```

Comprueba las métricas generadas desde los logs de aplicación:

```sh
aws cloudwatch list-metrics \
  --namespace franchise-dev/Application \
  --query 'Metrics[].{name:MetricName,dimensions:Dimensions}' \
  --output table
```

Comprueba que Terraform no tenga drift:

```sh
terraform -chdir=infrastructure/environments/dev plan \
  -detailed-exitcode \
  -var-file=dev.tfvars
```

El exit code `0` significa cero cambios, `2` significa que hay cambios y `1` indica error.

## Pruebas k6 contra AWS

El script `load-tests/franchise-api.js` crea datos reales en RDS. Empieza siempre con un smoke corto. La API no tiene autenticación en esta versión.

Con k6 instalado localmente:

```sh
API_URL="$(terraform -chdir=infrastructure/environments/dev output -raw api_endpoint)"

BASE_URL="$API_URL" VUS=1 DURATION=10s \
  k6 run load-tests/franchise-api.js
```

Con Docker:

```sh
API_URL="$(terraform -chdir=infrastructure/environments/dev output -raw api_endpoint)"

docker run --rm \
  -e BASE_URL="$API_URL" \
  -e VUS=1 \
  -e DURATION=10s \
  -v "$PWD/load-tests:/scripts:ro" \
  grafana/k6:0.57.0 \
  run /scripts/franchise-api.js
```

Con el servicio Compose del repositorio:

```sh
API_URL="$(terraform -chdir=infrastructure/environments/dev output -raw api_endpoint)"

docker compose run --rm --no-deps \
  -e BASE_URL="$API_URL" \
  -e VUS=1 \
  -e DURATION=10s \
  k6
```

Una prueba moderada:

```sh
BASE_URL="$API_URL" VUS=5 DURATION=30s \
  k6 run load-tests/franchise-api.js
```

La carga predeterminada usa 20 VUs durante 2 minutos:

```sh
BASE_URL="$API_URL" k6 run load-tests/franchise-api.js
```

Observa durante la carga:

```sh
watch -n 10 'aws ecs describe-services \
  --cluster franchise-dev-cluster \
  --services franchise-dev-api \
  --query "services[0].{desired:desiredCount,running:runningCount,pending:pendingCount}" \
  --output table'
```

El API Gateway limita DEV a 100 solicitudes por segundo con burst de 200. ECS escala entre una y dos tareas con objetivos de CPU 60% y memoria 70%. La carga produce solicitudes, logs y filas de prueba, por lo que tiene costo y deja datos de franquicias/sucursales.

## Revisar tablas PostgreSQL

RDS es privado deliberadamente. No se puede conectar con `psql` directamente desde el portátil y no debe abrirse el puerto 5432 a Internet.

El helper registra una tarea Fargate temporal en la subred privada, obtiene dentro de la tarea las credenciales `franchise_app`, fuerza transacciones read-only, ejecuta la consulta con TLS `verify-full`, imprime CloudWatch Logs y elimina la task definition activa al terminar.

Lista las tablas:

```sh
infrastructure/scripts/db-query-readonly.sh \
  "SELECT table_schema, table_name FROM information_schema.tables WHERE table_schema = 'franchise' ORDER BY table_name;"
```

Lista columnas:

```sh
infrastructure/scripts/db-query-readonly.sh \
  "SELECT table_name, column_name, data_type FROM information_schema.columns WHERE table_schema = 'franchise' ORDER BY table_name, ordinal_position;"
```

Cuenta filas:

```sh
infrastructure/scripts/db-query-readonly.sh \
  "SELECT 'franchises' AS table_name, count(*) FROM franchise.franchises UNION ALL SELECT 'branches', count(*) FROM franchise.branches UNION ALL SELECT 'branch_products', count(*) FROM franchise.branch_products;"
```

Muestra las últimas franquicias sin recuperar secretos localmente:

```sh
infrastructure/scripts/db-query-readonly.sh \
  "SELECT id, name, version, created_at FROM franchise.franchises ORDER BY created_at DESC LIMIT 20;"
```

La tarea usa `franchise_app`, no el usuario maestro. Utiliza el helper solo para consultas; las escrituras deben pasar por la API o por una migración controlada.

## Pausar solo la API

Esta opción elimina ECS Service, ALB, API Gateway, VPC Link, autoescalado y alarmas API. Conserva VPC, NAT, RDS, secretos y ECR. Reduce el costo de cómputo/entrada, pero NAT y RDS continúan cobrando.

Genera y revisa el plan:

```sh
terraform -chdir=infrastructure/environments/dev plan \
  -var-file=dev.tfvars \
  -var enable_api_service=false \
  -out=dev-pause.tfplan

terraform -chdir=infrastructure/environments/dev show dev-pause.tfplan
```

Aplica la pausa:

```sh
terraform -chdir=infrastructure/environments/dev apply dev-pause.tfplan
rm infrastructure/environments/dev/dev-pause.tfplan
```

`dev.tfvars` sigue declarando `enable_api_service = true`. Esto hace que la siguiente aplicación normal sea el mecanismo de reactivación.

## Reactivar la API

```sh
terraform -chdir=infrastructure/environments/dev plan \
  -var-file=dev.tfvars \
  -out=dev-resume.tfplan

terraform -chdir=infrastructure/environments/dev apply dev-resume.tfplan
rm infrastructure/environments/dev/dev-resume.tfplan

aws ecs wait services-stable \
  --cluster franchise-dev-cluster \
  --services franchise-dev-api

API_URL="$(terraform -chdir=infrastructure/environments/dev output -raw api_endpoint)"
curl --fail-with-body --silent --show-error \
  "$API_URL/actuator/health/readiness"
```

## Destruir todo DEV

Esta operación elimina VPC, NAT, RDS, secretos, ECR, ECS, ALB, API Gateway, logs, alarmas y dashboard de DEV.

La configuración DEV usa `skip_final_snapshot = true` para RDS y `force_delete = true` para ECR. Sin un snapshot manual se pierden permanentemente la base de datos y las imágenes.

Snapshot opcional antes de destruir:

```sh
SNAPSHOT_ID="franchise-dev-manual-$(date +%Y%m%d%H%M%S)"

aws rds create-db-snapshot \
  --db-instance-identifier franchise-dev-database \
  --db-snapshot-identifier "$SNAPSHOT_ID"

aws rds wait db-snapshot-completed \
  --db-snapshot-identifier "$SNAPSHOT_ID"
```

El snapshot conserva datos y sigue generando costo de almacenamiento.

Revisa el destroy plan:

```sh
terraform -chdir=infrastructure/environments/dev plan \
  -destroy \
  -var-file=dev.tfvars \
  -out=dev-destroy.tfplan

terraform -chdir=infrastructure/environments/dev show dev-destroy.tfplan
```

Destruye DEV:

```sh
terraform -chdir=infrastructure/environments/dev apply dev-destroy.tfplan
rm infrastructure/environments/dev/dev-destroy.tfplan
```

Esto no elimina el bootstrap: bucket S3 de estado, rol Terraform y permissions boundaries. IAM no tiene costo y el bucket vacío/versionado tiene un costo mínimo. El bucket está protegido con `prevent_destroy` porque contiene su propio estado y no debe destruirse como operación diaria.

No uses `aws rds stop-db-instance` como estrategia permanente: AWS vuelve a iniciar una instancia detenida después de siete días y la operación introduce estado fuera de Terraform.

## Reconstruir DEV desde cero

Después de un destroy total, ECR y sus imágenes ya no existen. La reconstrucción debe respetar este orden:

```text
foundation -> imágenes -> bootstrap de roles -> Flyway -> API
```

Aplica la foundation sin workloads:

```sh
terraform -chdir=infrastructure/environments/dev apply \
  -var-file=dev.tfvars \
  -var enable_api_service=false \
  -var enable_bootstrap_task=false \
  -var enable_migration_task=false
```

Construye y publica imágenes ARM64:

```sh
ACCOUNT_ID=127321794531
REGION=us-east-1
REGISTRY="$ACCOUNT_ID.dkr.ecr.$REGION.amazonaws.com"
IMAGE_TAG="$(git rev-parse --short HEAD)-$(date +%Y%m%d%H%M%S)"

aws ecr get-login-password --region "$REGION" \
  | docker login --username AWS --password-stdin "$REGISTRY"

docker buildx build --platform linux/arm64 \
  -f deployment/Dockerfile \
  -t "$REGISTRY/franchise-dev-api:$IMAGE_TAG" \
  --push .

docker buildx build --platform linux/arm64 \
  -f database/Dockerfile \
  -t "$REGISTRY/franchise-dev-migrations:$IMAGE_TAG" \
  --push database

docker buildx build --platform linux/arm64 \
  -f database/bootstrap/Dockerfile \
  -t "$REGISTRY/franchise-dev-bootstrap:$IMAGE_TAG" \
  --push database/bootstrap
```

Obtén los digests:

```sh
API_DIGEST="$(aws ecr describe-images \
  --repository-name franchise-dev-api \
  --image-ids imageTag="$IMAGE_TAG" \
  --query 'imageDetails[0].imageDigest' \
  --output text)"

MIGRATION_DIGEST="$(aws ecr describe-images \
  --repository-name franchise-dev-migrations \
  --image-ids imageTag="$IMAGE_TAG" \
  --query 'imageDetails[0].imageDigest' \
  --output text)"

BOOTSTRAP_DIGEST="$(aws ecr describe-images \
  --repository-name franchise-dev-bootstrap \
  --image-ids imageTag="$IMAGE_TAG" \
  --query 'imageDetails[0].imageDigest' \
  --output text)"

printf 'api=%s\nmigrations=%s\nbootstrap=%s\n' \
  "$API_DIGEST" "$MIGRATION_DIGEST" "$BOOTSTRAP_DIGEST"
```

Actualiza los tres digests en `infrastructure/environments/dev/dev.tfvars`. No continúes usando solo overrides: el archivo debe conservar la imagen deseada para evitar drift futuro.

Habilita solo bootstrap:

```sh
terraform -chdir=infrastructure/environments/dev apply \
  -var-file=dev.tfvars \
  -var enable_api_service=false \
  -var enable_bootstrap_task=true \
  -var enable_migration_task=false
```

Ejecuta bootstrap:

```sh
TASK_DEFINITION="$(terraform -chdir=infrastructure/environments/dev output -raw bootstrap_task_definition_arn)"
NETWORK="$(terraform -chdir=infrastructure/environments/dev output -json bootstrap_run_task_network_configuration)"
NETWORK_CONFIGURATION="$(printf '%s' "$NETWORK" | jq -r \
  '"awsvpcConfiguration={subnets=[" + (.subnets | join(",")) + "],securityGroups=[" + (.security_groups | join(",")) + "],assignPublicIp=" + .assign_public_ip + "}"')"

TASK_ARN="$(aws ecs run-task \
  --cluster franchise-dev-cluster \
  --launch-type FARGATE \
  --task-definition "$TASK_DEFINITION" \
  --network-configuration "$NETWORK_CONFIGURATION" \
  --query 'tasks[0].taskArn' \
  --output text)"

aws ecs wait tasks-stopped \
  --cluster franchise-dev-cluster \
  --tasks "$TASK_ARN"

aws ecs describe-tasks \
  --cluster franchise-dev-cluster \
  --tasks "$TASK_ARN" \
  --query 'tasks[0].containers[0].{exitCode:exitCode,reason:reason}' \
  --output table

aws logs tail /ecs/franchise-dev-database-bootstrap --since 15m
```

El exit code debe ser `0`.

Habilita solo Flyway:

```sh
terraform -chdir=infrastructure/environments/dev apply \
  -var-file=dev.tfvars \
  -var enable_api_service=false \
  -var enable_bootstrap_task=false \
  -var enable_migration_task=true

TASK_DEFINITION="$(terraform -chdir=infrastructure/environments/dev output -raw migration_task_definition_arn)"
NETWORK="$(terraform -chdir=infrastructure/environments/dev output -json migration_run_task_network_configuration)"
NETWORK_CONFIGURATION="$(printf '%s' "$NETWORK" | jq -r \
  '"awsvpcConfiguration={subnets=[" + (.subnets | join(",")) + "],securityGroups=[" + (.security_groups | join(",")) + "],assignPublicIp=" + .assign_public_ip + "}"')"

TASK_ARN="$(aws ecs run-task \
  --cluster franchise-dev-cluster \
  --launch-type FARGATE \
  --task-definition "$TASK_DEFINITION" \
  --network-configuration "$NETWORK_CONFIGURATION" \
  --query 'tasks[0].taskArn' \
  --output text)"

aws ecs wait tasks-stopped \
  --cluster franchise-dev-cluster \
  --tasks "$TASK_ARN"

aws ecs describe-tasks \
  --cluster franchise-dev-cluster \
  --tasks "$TASK_ARN" \
  --query 'tasks[0].containers[0].{exitCode:exitCode,reason:reason}' \
  --output table

aws logs tail /ecs/franchise-dev-migration --since 15m
```

El exit code debe ser `0` y Flyway debe reportar `migrate` y `validate` exitosos.

Habilita la API y retira la task definition de migración:

```sh
terraform -chdir=infrastructure/environments/dev apply \
  -var-file=dev.tfvars \
  -var enable_api_service=true \
  -var enable_bootstrap_task=false \
  -var enable_migration_task=false

aws ecs wait services-stable \
  --cluster franchise-dev-cluster \
  --services franchise-dev-api

API_URL="$(terraform -chdir=infrastructure/environments/dev output -raw api_endpoint)"
curl --fail-with-body --silent --show-error \
  "$API_URL/actuator/health/readiness"
```

Finaliza con un plan sin overrides. Debe indicar cero cambios:

```sh
terraform -chdir=infrastructure/environments/dev plan \
  -detailed-exitcode \
  -var-file=dev.tfvars
```

## Qué no hacer

- No abras RDS al público ni agregues tu IP al security group.
- No recuperes ni imprimas contraseñas de Secrets Manager en la terminal.
- No uses tags mutables como `latest`; Terraform exige digests `sha256`.
- No ejecutes bootstrap y Flyway simultáneamente.
- No destruyas el bootstrap antes de destruir DEV.
- No borres manualmente recursos administrados por Terraform.
- No ejecutes una carga k6 grande antes del smoke de 1 VU.
