# API de franquicias

API reactiva para administrar franquicias, sucursales y productos, construida con Java 21, Spring WebFlux, PostgreSQL y Clean Architecture. El contrato HTTP se encuentra en [`openapi/franchise-api.yaml`](openapi/franchise-api.yaml).

## Arquitectura

El proyecto sigue el Scaffold Clean Architecture de Bancolombia y conserva la regla de dependencias hacia el dominio:

```text
applications/app-service                 Ensamblaje y arranque
domain/model                             Entidades y puertos
domain/usecase                           Casos de uso
infrastructure/driven-adapters           Implementaciones de puertos
infrastructure/entry-points              API HTTP funcional
deployment                               Contenedores, Terraform y operación
database                                 Migraciones y bootstrap PostgreSQL
openapi                                  Contrato público
load-tests                               Pruebas k6
```

`infrastructure/` está reservada para la capa externa de Clean Architecture: entry points, driven adapters y helpers. La infraestructura como código vive en `deployment/terraform/` para no mezclar la arquitectura de la aplicación con la plataforma de despliegue. `database/`, `openapi/`, `load-tests/` y `compose.yaml` permanecen en la raíz porque son capacidades independientes con comandos estándar desde el repositorio.

Los módulos Gradle registrados son `:model`, `:usecase`, `:r2dbc-postgresql`, `:reactive-web` y `:app-service`.

El flujo desplegado es:

```text
Internet HTTPS
    -> API Gateway HTTP API
    -> VPC Link
    -> ALB interno
    -> ECS Fargate ARM64:8080
    -> RDS PostgreSQL privado:5432
```

API Gateway es el único punto público. ECS y RDS se ejecutan en subredes privadas; TLS termina en API Gateway y la conexión ECS-RDS usa `VERIFY_FULL`.

## Requisitos

- Git 2.40 o superior.
- Docker Desktop 4.24 o superior, o Docker Engine 24 o superior en ejecución.
- Docker Compose 2.20 o superior, disponible mediante `docker compose`; se usa la opción `up --wait`.
- Puertos locales 8080 y 5432 disponibles, o puertos alternativos configurados como se indica abajo.
- JDK 21 para ejecutar Gradle fuera de Docker. Java 17 no es suficiente porque el toolchain del proyecto es Java 21.
- Terraform 1.10 o superior para validar o aplicar IaC.
- Shell compatible con POSIX y curl 7.76 o superior para ejecutar los comandos de verificación.
- AWS CLI 2.32.0 o superior con `aws login`, `jq` 1.6 o superior y Docker Buildx 0.12 o superior para operar o desplegar AWS.
- GitHub CLI 2.40 o superior autenticado con `gh auth login` para configurar un pipeline nuevo.

El camino principal con Docker no requiere instalar Java, Gradle, PostgreSQL, Flyway ni k6. Terraform, AWS CLI, `jq` y Buildx solo son necesarios para operar ambientes cloud.

## Desarrollo local

### 1. Verificar herramientas

```sh
git --version
docker --version
docker compose version
docker info
```

Todos los comandos deben finalizar correctamente. Si `docker info` falla, inicia Docker Desktop o el daemon de Docker antes de continuar.

### 2. Clonar el repositorio

```sh
git clone https://github.com/Exloz/api-franquicias.git
cd api-franquicias
```

### 3. Levantar el entorno completo

Este comando construye la API, crea PostgreSQL, espera su health check, ejecuta las cinco migraciones Flyway y arranca WebFlux:

```sh
docker compose config --quiet
docker compose up --build -d --wait api
```

Comprueba el estado de todos los servicios:

```sh
docker compose ps -a
```

`postgres` y `api` deben aparecer saludables. `migrations` debe aparecer como `Exited (0)`; es correcto porque termina después de aplicar y validar el esquema.

Si 8080 o 5432 están ocupados, levanta el entorno con puertos alternativos:

```sh
API_PORT=8081 POSTGRES_PORT=5433 docker compose up --build -d --wait api
```

En ese caso, reemplaza 8080 por 8081 en las URLs siguientes.

### 4. Verificar la API

Readiness debe responder HTTP 200 y `{"status":"UP"}`:

```sh
curl --fail-with-body --silent --show-error \
  http://localhost:8080/actuator/health/readiness
```

Swagger UI queda disponible en:

```text
http://localhost:8080/swagger-ui.html
```

Crea una franquicia para validar el flujo HTTP, el caso de uso y PostgreSQL:

```sh
curl --fail-with-body --silent --show-error \
  --request POST \
  --header 'Content-Type: application/json' \
  --data "{\"name\":\"Franquicia local $(date +%s)\"}" \
  --write-out '\nHTTP %{http_code}\n' \
  http://localhost:8080/api/v1/franchises
```

La respuesta esperada es HTTP 201 con un objeto que contiene `id`, `name` y `version`.

### 5. Inspeccionar problemas

Si el arranque o readiness falla, consulta estado y logs sin recrear el entorno:

```sh
docker compose ps -a
docker compose logs postgres migrations api
```

Para reiniciar desde una base vacía:

```sh
docker compose down -v
docker compose up --build -d --wait api
```

`down -v` elimina permanentemente los datos locales de PostgreSQL.

### 6. Detener el entorno

Conserva los datos para el siguiente arranque:

```sh
docker compose down
```

Elimina contenedores y datos locales:

```sh
docker compose down -v
```

### Ejecución sin Docker

PostgreSQL debe estar disponible y tener las migraciones aplicadas. Configura `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` y `DB_SSL_MODE`, y ejecuta:

```sh
./gradlew :app-service:bootRun
```

El perfil `local` proporciona únicamente defaults de desarrollo. Fuera de ese perfil, host, puerto, base de datos, usuario, contraseña y modo TLS son obligatorios para impedir despliegues con credenciales locales o TLS deshabilitado por accidente.

## Validación

```sh
./gradlew validateStructure
./gradlew test
./gradlew qualityGate jacocoMergedReport
terraform fmt -check -recursive deployment/terraform
docker compose config --quiet
```

La suite completa usa Testcontainers. JaCoCo y las reglas arquitectónicas son gates activos. La configuración PIT permanece en Gradle para ejecución local, pero mutation testing está deshabilitado en CI y no constituye un gate de entrega.

Prueba de carga local:

```sh
K6_VUS=1 K6_DURATION=10s docker compose run --rm k6
```

La carga predeterminada usa 20 VUs durante 2 minutos. Sobre una API externa:

```sh
BASE_URL=https://example.execute-api.us-east-1.amazonaws.com k6 run load-tests/franchise-api.js
```

## Configuración operativa

| Área | Default | Sobrescritura |
|---|---:|---|
| Deadline HTTP | 25s | `API_DEADLINE_TIMEOUT` |
| Payload en memoria | 64KB | `API_MAX_IN_MEMORY_SIZE` |
| Pool R2DBC | 15 local, 10 AWS | `DB_POOL_MAX_SIZE` |
| Adquisición de conexión | 5s | `DB_POOL_MAX_ACQUIRE_TIME` |
| Timeout de conexión | 3s | `DB_CONNECT_TIMEOUT` |
| Lectura DB | 3s por intento, 10s total | `DB_READ_ATTEMPT_TIMEOUT`, `DB_READ_OPERATION_TIMEOUT` |
| Escritura DB | 5s por intento, 15s total | `DB_WRITE_ATTEMPT_TIMEOUT`, `DB_WRITE_OPERATION_TIMEOUT` |
| Retry DB | 3 intentos, 250ms a 2s, jitter 0.5 | `DB_RETRY_*` |
| Paginación | 50 por defecto, máximo 100 | query `limit` |
| CORS | deshabilitado | `CORS_ENABLED`, `CORS_ALLOWED_ORIGINS` |

La gestión expone `health` y `prometheus`. Los histogramas HTTP incluyen SLO buckets de 100ms, 250ms, 500ms, 1s, 2s y 5s. En AWS, `/actuator/prometheus` no es público; CloudWatch recibe las señales operativas definidas por Terraform.

## Despliegue en AWS

La región desplegada es `us-east-1`; el account ID se obtiene de la sesión AWS y no se publica en este documento. Terraform, scripts y procedimientos ampliados se documentan en [`deployment/README.md`](deployment/README.md) y [`deployment/RUNBOOK.md`](deployment/RUNBOOK.md).

### 1. Verificar herramientas y autenticación

```sh
java -version
terraform version
aws --version
jq --version
docker buildx version
gh --version
aws login help >/dev/null
aws login
aws sts get-caller-identity
```

`java -version` debe mostrar Java 21, Terraform debe ser 1.10 o superior y AWS CLI debe ser 2.32.0 o superior. Solicita al administrador de plataforma el ARN del principal bootstrap y verifica la identidad antes de asumir el rol; pertenecer a la misma cuenta no es suficiente:

```sh
export BOOTSTRAP_PRINCIPAL_ARN='<bootstrap-principal-arn>'
export TERRAFORM_APPLY_ROLE_ARN='<terraform-apply-role-arn>'
export AWS_ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
CALLER_ARN="$(aws sts get-caller-identity --query Arn --output text)"
test "$CALLER_ARN" = "$BOOTSTRAP_PRINCIPAL_ARN" \
  || { printf 'Identidad AWS no autorizada: %s\n' "$CALLER_ARN" >&2; exit 1; }
```

Si la cuenta aún no contiene el bootstrap, conserva esa identidad y continúa en el paso 3. El principal bootstrap debe estar habilitado para `aws login`; no uses access keys estáticas.

En una cuenta ya inicializada, Terraform manual requiere asumir el rol de aplicación. Las credenciales son temporales y no se guardan en el repositorio:

```sh
credentials="$(aws sts assume-role \
  --role-arn "$TERRAFORM_APPLY_ROLE_ARN" \
  --role-session-name franchise-operator \
  --output json)"

export AWS_ACCESS_KEY_ID="$(printf '%s' "$credentials" | jq -r '.Credentials.AccessKeyId')"
export AWS_SECRET_ACCESS_KEY="$(printf '%s' "$credentials" | jq -r '.Credentials.SecretAccessKey')"
export AWS_SESSION_TOKEN="$(printf '%s' "$credentials" | jq -r '.Credentials.SessionToken')"
export AWS_REGION=us-east-1
unset AWS_PROFILE

aws sts get-caller-identity
```

### 2. Validar Terraform

```sh
terraform fmt -check -recursive deployment/terraform

for root in bootstrap environments/dev environments/prod; do
  TF_DATA_DIR="$(mktemp -d)"
  export TF_DATA_DIR
  terraform -chdir="deployment/terraform/$root" init -backend=false -input=false
  terraform -chdir="deployment/terraform/$root" validate
  rm -rf "$TF_DATA_DIR"
  unset TF_DATA_DIR
done
```

La validación no accede al estado remoto. Para operar un ambiente existente, inicializa su backend:

```sh
terraform -chdir=deployment/terraform/environments/dev init -input=false
terraform -chdir=deployment/terraform/environments/dev output -json release_digests
```

### 3. Provisionar una cuenta vacía

Este paso se ejecuta una sola vez con el principal bootstrap validado en el paso 1. No lo repitas sobre un bootstrap existente.

Primero crea el bucket de estado, boundaries y rol Terraform usando estado local; luego migra ese estado al backend S3 recién creado:

```sh
ROOT=deployment/terraform/bootstrap

terraform -chdir="$ROOT" init -backend=false -input=false
terraform -chdir="$ROOT" plan \
  -var enable_ci_identity=true \
  -var "bootstrap_principal_arn=$BOOTSTRAP_PRINCIPAL_ARN" \
  -out=bootstrap.tfplan
terraform -chdir="$ROOT" show bootstrap.tfplan
terraform -chdir="$ROOT" apply bootstrap.tfplan
terraform -chdir="$ROOT" init -migrate-state -force-copy -input=false
```

Asume el rol Terraform proporcionado por el administrador de plataforma con el bloque del paso 1 y crea la foundation DEV sin arrancar todavía la API:

```sh
ROOT=deployment/terraform/environments/dev

terraform -chdir="$ROOT" init -input=false
terraform -chdir="$ROOT" plan \
  -var-file=dev.tfvars \
  -var enable_api_service=false \
  -out=dev-foundation.tfplan
terraform -chdir="$ROOT" show dev-foundation.tfplan
terraform -chdir="$ROOT" apply dev-foundation.tfplan
```

Construye y publica las tres imágenes ARM64 con un tag inmutable:

Docker Desktop incluye emulación ARM64. En Docker Engine sobre Debian/Ubuntu x86_64, instala primero QEMU/binfmt desde los repositorios del sistema y verifica el builder:

```sh
sudo apt-get update
sudo apt-get install -y qemu-user-static binfmt-support
docker buildx inspect --bootstrap
```

```sh
ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
AWS_REGION=us-east-1
REGISTRY="$ACCOUNT_ID.dkr.ecr.$AWS_REGION.amazonaws.com"
IMAGE_TAG="initial-$(git rev-parse --short HEAD)-$(date +%Y%m%d%H%M%S)"

aws ecr get-login-password --region "$AWS_REGION" \
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

API_DIGEST="$(aws ecr describe-images \
  --repository-name franchise-dev-api \
  --image-ids imageTag="$IMAGE_TAG" \
  --query 'imageDetails[0].imageDigest' --output text)"
MIGRATION_DIGEST="$(aws ecr describe-images \
  --repository-name franchise-dev-migrations \
  --image-ids imageTag="$IMAGE_TAG" \
  --query 'imageDetails[0].imageDigest' --output text)"
BOOTSTRAP_DIGEST="$(aws ecr describe-images \
  --repository-name franchise-dev-bootstrap \
  --image-ids imageTag="$IMAGE_TAG" \
  --query 'imageDetails[0].imageDigest' --output text)"
```

Registra las tareas con esos digests, ejecuta bootstrap y Flyway, y habilita la API solo después de que ambas tareas terminen con exit code 0:

```sh
terraform -chdir="$ROOT" apply -auto-approve -input=false \
  -var-file=dev.tfvars \
  -var enable_api_service=false \
  -var "api_image_digest=$API_DIGEST" \
  -var "migration_image_digest=$MIGRATION_DIGEST" \
  -var "bootstrap_image_digest=$BOOTSTRAP_DIGEST"

ECS_CLUSTER="$(terraform -chdir="$ROOT" output -raw ecs_cluster_name)"
BOOTSTRAP_TASK="$(terraform -chdir="$ROOT" output -raw bootstrap_task_definition_arn)"
BOOTSTRAP_NETWORK="$(terraform -chdir="$ROOT" output -json bootstrap_run_task_network_configuration | jq -c .)"
MIGRATION_TASK="$(terraform -chdir="$ROOT" output -raw migration_task_definition_arn)"
MIGRATION_NETWORK="$(terraform -chdir="$ROOT" output -json migration_run_task_network_configuration | jq -c .)"

deployment/scripts/run-ecs-task.sh \
  "$ECS_CLUSTER" "$BOOTSTRAP_TASK" "$BOOTSTRAP_NETWORK" database-bootstrap
deployment/scripts/run-ecs-task.sh \
  "$ECS_CLUSTER" "$MIGRATION_TASK" "$MIGRATION_NETWORK" migration

terraform -chdir="$ROOT" apply -auto-approve -input=false \
  -var-file=dev.tfvars \
  -var enable_api_service=true \
  -var "api_image_digest=$API_DIGEST" \
  -var "migration_image_digest=$MIGRATION_DIGEST" \
  -var "bootstrap_image_digest=$BOOTSTRAP_DIGEST"

API_URL="$(terraform -chdir="$ROOT" output -raw api_endpoint)"
deployment/scripts/smoke-api.sh "$API_URL"
```

### 4. Revisar y aplicar cambios de infraestructura en DEV

El plan debe conservar los digests desplegados para no reemplazar una release por los valores históricos de `dev.tfvars`:

```sh
ROOT=deployment/terraform/environments/dev
CURRENT="$(terraform -chdir="$ROOT" output -json release_digests)"
API_DIGEST="$(printf '%s' "$CURRENT" | jq -er '.api')"
MIGRATION_DIGEST="$(printf '%s' "$CURRENT" | jq -er '.migrations')"
BOOTSTRAP_DIGEST="$(printf '%s' "$CURRENT" | jq -er '.bootstrap')"

terraform -chdir="$ROOT" plan \
  -var-file=dev.tfvars \
  -var enable_api_service=true \
  -var "api_image_digest=$API_DIGEST" \
  -var "migration_image_digest=$MIGRATION_DIGEST" \
  -var "bootstrap_image_digest=$BOOTSTRAP_DIGEST" \
  -out=dev.tfplan

terraform -chdir="$ROOT" show dev.tfplan
terraform -chdir="$ROOT" apply dev.tfplan
```

Revisa completamente `terraform show` antes de aplicar. No cambies las claves remotas `bootstrap/infrastructure.tfstate`, `dev/infrastructure.tfstate` o `prod/infrastructure.tfstate`.

### 5. Desplegar aplicación e imágenes

El despliegue normal se realiza mediante `.github/workflows/ci-cd.yaml`, no mediante imágenes locales:

1. Publica una feature branch y abre un pull request hacia `development`.
2. Espera `CI gate`, Terraform, Trivy, JaCoCo y las pruebas requeridas.
3. Integra el pull request; el push a `development` construye imágenes por digest, ejecuta Flyway, despliega DEV y ejecuta smoke tests.
4. Abre un pull request de `development` hacia `main` mediante merge commit.
5. Aprueba el GitHub Environment `prod`; PROD copia exactamente los digests validados en DEV, ejecuta Flyway y despliega sin reconstruir imágenes.

GitHub Actions usa OIDC y requiere estas variables, no access keys:

| Ámbito | Variable |
|---|---|
| Repositorio | `AWS_PLAN_ROLE_ARN` |
| Environment `dev` | `AWS_DEV_APPLY_ROLE_ARN` |
| Environment `dev` | `AWS_DEV_DEPLOY_ROLE_ARN` |
| Environment `prod` | `AWS_PROD_APPLY_ROLE_ARN` |
| Environment `prod` | `AWS_PROD_DEPLOY_ROLE_ARN` |

En un repositorio recién configurado, crea los environments y carga los ARNs generados por Terraform:

```sh
ROOT=deployment/terraform/environments/dev
CI_ROLES="$(terraform -chdir="$ROOT" output -json ci_role_arns)"

gh api --method PUT repos/Exloz/api-franquicias/environments/dev
gh api --method PUT repos/Exloz/api-franquicias/environments/prod

gh variable set AWS_PLAN_ROLE_ARN \
  --body "$(printf '%s' "$CI_ROLES" | jq -r '.plan')"
gh variable set AWS_DEV_APPLY_ROLE_ARN --env dev \
  --body "$(printf '%s' "$CI_ROLES" | jq -r '.apply.dev')"
gh variable set AWS_DEV_DEPLOY_ROLE_ARN --env dev \
  --body "$(printf '%s' "$CI_ROLES" | jq -r '.deploy.dev')"
gh variable set AWS_PROD_APPLY_ROLE_ARN --env prod \
  --body "$(printf '%s' "$CI_ROLES" | jq -r '.apply.prod')"
gh variable set AWS_PROD_DEPLOY_ROLE_ARN --env prod \
  --body "$(printf '%s' "$CI_ROLES" | jq -r '.deploy.prod')"
```

ECS configura `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_SSL_MODE`, `DB_SSL_ROOT_CERT`, `DB_POOL_MAX_SIZE` y `APP_ENVIRONMENT`. `DB_USERNAME` y `DB_PASSWORD` se inyectan desde Secrets Manager; no deben definirse como secretos de GitHub ni variables Terraform.

### URLs desplegadas

| Ambiente | URL base | Readiness |
|---|---|---|
| DEV | `https://sxhuakqh88.execute-api.us-east-1.amazonaws.com` | `https://sxhuakqh88.execute-api.us-east-1.amazonaws.com/actuator/health/readiness` |
| PROD | `https://ei745cwqo9.execute-api.us-east-1.amazonaws.com` | `https://ei745cwqo9.execute-api.us-east-1.amazonaws.com/actuator/health/readiness` |

Los endpoints son outputs de Terraform y pueden cambiar si API Gateway se destruye y recrea. Consulta el valor vigente con:

```sh
terraform -chdir=deployment/terraform/environments/dev output -raw api_endpoint
terraform -chdir=deployment/terraform/environments/prod output -raw api_endpoint
```

## Contrato y errores

La API usa WebFlux funcional con `RouterFunction` y `Handler`. Los errores se representan como `application/problem+json`, incluyen correlación y traducen indisponibilidad transitoria de PostgreSQL a HTTP 503. Las escrituras ambiguas no se reintentan; los `Flux` solo se reintentan antes de emitir el primer elemento.
