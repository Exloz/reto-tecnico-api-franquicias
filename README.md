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
deployment                              Contenedores, Terraform y operación
database                                Migraciones y bootstrap PostgreSQL
openapi                                 Contrato público
load-tests                              Pruebas k6
```

`infrastructure/` está reservada para la capa externa de Clean Architecture: entry points, driven adapters y helpers. La infraestructura como código vive en `deployment/terraform/` para no mezclar la arquitectura de la aplicación con la plataforma de despliegue. `database/`, `openapi/`, `load-tests/` y `compose.yaml` permanecen en la raíz porque son capacidades independientes con comandos estándar desde el repositorio.

Los módulos Gradle registrados son `:model`, `:usecase`, `:r2dbc-postgresql`, `:reactive-web` y `:app-service`.

## Requisitos

- Git.
- Docker Desktop o Docker Engine en ejecución.
- Docker Compose v2, disponible mediante `docker compose`.
- Puertos locales 8080 y 5432 disponibles, o puertos alternativos configurados como se indica abajo.

El camino principal con Docker no requiere instalar Java, Gradle, PostgreSQL, Flyway ni k6. Para ejecutar Gradle fuera del contenedor se necesita un JDK; el wrapper configura el toolchain Java 21. Terraform y AWS CLI solo son necesarios para operar ambientes cloud.

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

## Despliegue

Terraform, scripts, arquitectura AWS y procedimientos se documentan en [`deployment/README.md`](deployment/README.md). Las operaciones de diagnóstico, pausa, recuperación y destrucción están en [`deployment/RUNBOOK.md`](deployment/RUNBOOK.md).

DEV y PROD se despliegan exclusivamente desde ramas protegidas mediante `.github/workflows/ci-cd.yaml`. Las imágenes son inmutables y se identifican por digest; PROD promueve los manifiestos validados en DEV sin reconstruirlos.

## Contrato y errores

La API usa WebFlux funcional con `RouterFunction` y `Handler`. Los errores se representan como `application/problem+json`, incluyen correlación y traducen indisponibilidad transitoria de PostgreSQL a HTTP 503. Las escrituras ambiguas no se reintentan; los `Flux` solo se reintentan antes de emitir el primer elemento.
